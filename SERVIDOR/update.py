"""
update.py — Procesador de sincronizaciones del servidor central.

Propósito:
    Monitorea la carpeta DATOS/ (carpeta compartida en red, UNC),
    detecta nuevos archivos CSV enviados por los clientes POS,
    lee los registros y los inserta/actualiza en la base de datos
    SQLite consolidada del servidor.

Lógica de deduplicación:
    Cada registro se identifica por (id, sucursal_id).
    Si ya existe → se actualiza (upsert).
    Si no existe → se inserta.

Compilación a ejecutable:
    pyinstaller --onefile --name Update update.py
    → genera Update.exe

Uso:
    python update.py   (modo desarrollo, monitoreo continuo)
    Update.exe         (modo producción)
"""
import csv
import re
import sqlite3
import sys
import threading
import time
from datetime import datetime
from pathlib import Path
from typing import Callable, Optional

# ── Resolver ruta base del proyecto ──────────────────────────────────────────
# sys.frozen es verdadero cuando se ejecuta como Update.exe compilado con
# PyInstaller --onefile.  En ese caso __file__ apunta al directorio temporal
# de extracción (sys._MEIPASS), no al proyecto.  Usamos sys.executable que
# siempre apunta al .exe real en dist/.
if getattr(sys, "frozen", False):
    BASE_DIR     = Path(sys.executable).resolve().parent.parent
    SERVIDOR_DIR = BASE_DIR / "SERVIDOR"
else:
    SERVIDOR_DIR = Path(__file__).resolve().parent
    BASE_DIR     = SERVIDOR_DIR.parent
APP_DIR      = BASE_DIR / "APPLICATION"
DATOS_DIR    = BASE_DIR / "DATOS"
DB_DIR       = SERVIDOR_DIR / "database"
LOGS_DIR     = SERVIDOR_DIR / "logs"

# Asegurar directorios
for d in (DB_DIR, LOGS_DIR):
    d.mkdir(parents=True, exist_ok=True)

SERVER_DB    = DB_DIR / "minimarket.db"
INIT_SQL     = SERVIDOR_DIR / "init_db.sql"
PROCESSED    = SERVIDOR_DIR / "processed_files.txt"


# ════════════════════════════════════════════════════════════════════════════
# BASE DE DATOS
# ════════════════════════════════════════════════════════════════════════════

def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(str(SERVER_DB))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA journal_mode = WAL")
    return conn


def init_database() -> None:
    """Inicializa la base de datos ejecutando init_db.sql."""
    if not INIT_SQL.exists():
        raise FileNotFoundError(f"No se encontró {INIT_SQL}")
    sql = INIT_SQL.read_text(encoding="utf-8")
    with get_connection() as conn:
        conn.executescript(sql)


def get_or_create_sucursal(conn: sqlite3.Connection, hostname: str) -> int:
    """Retorna el ID de la sucursal, creándola si no existe."""
    row = conn.execute(
        "SELECT id FROM sucursales WHERE hostname = ?", (hostname,)
    ).fetchone()
    if row:
        return row["id"]
    cur = conn.execute(
        "INSERT INTO sucursales (hostname, nombre) VALUES (?, ?)",
        (hostname, f"Sucursal {hostname}"),
    )
    conn.commit()
    return cur.lastrowid


# ════════════════════════════════════════════════════════════════════════════
# PROCESADORES POR ENTIDAD
# ════════════════════════════════════════════════════════════════════════════

def _upsert(conn, table: str, pk_cols: list, row: dict) -> str:
    """
    INSERT OR REPLACE genérico.
    Retorna 'inserted' o 'updated'.
    """
    # Verificar si existe
    where   = " AND ".join(f"{c} = ?" for c in pk_cols)
    values  = [row[c] for c in pk_cols]
    exists  = conn.execute(
        f"SELECT 1 FROM {table} WHERE {where}", values
    ).fetchone()

    cols  = ", ".join(row.keys())
    marks = ", ".join("?" * len(row))
    conn.execute(
        f"INSERT OR REPLACE INTO {table} ({cols}) VALUES ({marks})",
        list(row.values()),
    )
    return "updated" if exists else "inserted"


def process_productos(conn, rows: list, sucursal_id: int) -> dict:
    stats = {"inserted": 0, "updated": 0, "errors": 0}
    for row in rows:
        try:
            data = {
                "id": int(row["id"]),
                "sucursal_id": sucursal_id,
                "nombre": row["nombre"],
                "precio": float(row["precio"]),
                "stock": int(row["stock"]),
                "categoria": row.get("categoria", ""),
                "estado": int(row.get("estado", 1)),
            }
            result = _upsert(conn, "productos", ["id", "sucursal_id"], data)
            stats[result] += 1
        except Exception as e:
            stats["errors"] += 1
    return stats


def process_clientes(conn, rows: list, sucursal_id: int) -> dict:
    stats = {"inserted": 0, "updated": 0, "errors": 0}
    for row in rows:
        try:
            data = {
                "id": int(row["id"]),
                "sucursal_id": sucursal_id,
                "nombre": row["nombre"],
                "dni": row["dni"],
                "telefono": row.get("telefono", ""),
                "email": row.get("email", ""),
                "estado": int(row.get("estado", 1)),
            }
            result = _upsert(conn, "clientes", ["id", "sucursal_id"], data)
            stats[result] += 1
        except Exception as e:
            stats["errors"] += 1
    return stats


def process_ventas(conn, rows: list, sucursal_id: int) -> dict:
    stats = {"inserted": 0, "updated": 0, "errors": 0}
    for row in rows:
        try:
            data = {
                "id": int(row["id"]),
                "sucursal_id": sucursal_id,
                "cliente_id": int(row.get("cliente_id", 0)),
                "fecha": row["fecha"],
                "subtotal": float(row["subtotal"]),
                "igv": float(row["igv"]),
                "total": float(row["total"]),
                "estado": int(row.get("estado", 1)),
            }
            result = _upsert(conn, "ventas", ["id", "sucursal_id"], data)
            stats[result] += 1
        except Exception as e:
            stats["errors"] += 1
    return stats


def process_detalles(conn, rows: list, sucursal_id: int) -> dict:
    stats = {"inserted": 0, "updated": 0, "errors": 0}
    for row in rows:
        try:
            data = {
                "id": int(row["id"]),
                "sucursal_id": sucursal_id,
                "venta_id": int(row["venta_id"]),
                "producto_id": int(row["producto_id"]),
                "cantidad": int(row["cantidad"]),
                "precio_unitario": float(row["precio_unitario"]),
                "subtotal": float(row["subtotal"]),
                "estado": int(row.get("estado", 1)),
            }
            result = _upsert(conn, "detalle_ventas", ["id", "sucursal_id"], data)
            stats[result] += 1
        except Exception as e:
            stats["errors"] += 1
    return stats


PROCESSORS = {
    "productos": process_productos,
    "clientes":  process_clientes,
    "ventas":    process_ventas,
    "detalles":  process_detalles,
}


# ════════════════════════════════════════════════════════════════════════════
# PROCESADOR DE ARCHIVOS
# ════════════════════════════════════════════════════════════════════════════

def get_processed_set() -> set:
    if not PROCESSED.exists():
        return set()
    return set(PROCESSED.read_text(encoding="utf-8").splitlines())


def mark_processed(filename: str) -> None:
    with open(PROCESSED, "a", encoding="utf-8") as f:
        f.write(filename + "\n")


def detect_entity(filename: str) -> Optional[str]:
    """
    Detecta la entidad a partir del nombre del archivo.
    Ejemplos: productos_20250101_120000.csv → 'productos'
    """
    for entity in PROCESSORS:
        if filename.lower().startswith(entity):
            return entity
    return None


def extract_hostname(filename: str) -> str:
    """Extrae el hostname del nombre del archivo si está disponible."""
    # Formato esperado: entidad_YYYYMMDD_HHMMSS.csv (hostname en MANIFEST)
    return "localhost"


def process_csv_file(
    filepath: Path,
    log_fn: Callable[[str], None] = print,
) -> bool:
    """
    Procesa un archivo CSV de sincronización e inserta en SQLite.

    Args:
        filepath: Ruta al archivo CSV
        log_fn:   Función de logging

    Returns:
        True si se procesó correctamente
    """
    entity = detect_entity(filepath.name)
    if entity is None:
        log_fn(f"[SKIP] Entidad desconocida: {filepath.name}")
        return False

    log_fn(f"[PROC] {filepath.name} → entidad={entity}")

    try:
        with open(filepath, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            rows   = list(reader)
    except Exception as e:
        log_fn(f"[ERROR] Leyendo {filepath.name}: {e}")
        return False

    if not rows:
        log_fn(f"[SKIP] {filepath.name} sin registros")
        mark_processed(filepath.name)
        return True

    try:
        conn = get_connection()
        sucursal_id = get_or_create_sucursal(conn, extract_hostname(filepath.name))
        processor   = PROCESSORS[entity]
        stats       = processor(conn, rows, sucursal_id)

        # Actualizar última sincronización de la sucursal
        conn.execute(
            "UPDATE sucursales SET ultima_sync = ? WHERE id = ?",
            (datetime.now().strftime("%Y-%m-%d %H:%M:%S"), sucursal_id),
        )

        # Registrar en sync_log
        conn.execute(
            """INSERT INTO sync_log
               (sucursal_id, archivo, entidad, registros_proc, registros_dup, registros_err)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (
                sucursal_id, filepath.name, entity,
                stats.get("inserted", 0),
                stats.get("updated", 0),
                stats.get("errors", 0),
            ),
        )
        conn.commit()
        conn.close()

        log_fn(
            f"[OK]   {filepath.name} — "
            f"insertados={stats.get('inserted',0)} "
            f"actualizados={stats.get('updated',0)} "
            f"errores={stats.get('errors',0)}"
        )
        mark_processed(filepath.name)
        return True

    except Exception as e:
        log_fn(f"[ERROR] Procesando {filepath.name}: {e}")
        return False


# ════════════════════════════════════════════════════════════════════════════
# MONITOR CONTINUO
# ════════════════════════════════════════════════════════════════════════════

class UpdateAgent:
    """
    Agente de actualización del servidor.
    Monitorea DATOS/ cada N segundos y procesa nuevos archivos CSV.
    """

    def __init__(
        self,
        datos_dir:       Path = DATOS_DIR,
        interval_secs:   int  = 10,
        log_callback:    Optional[Callable[[str], None]] = None,
        status_callback: Optional[Callable[[str], None]] = None,
    ) -> None:
        self._datos_dir    = datos_dir
        self._interval     = interval_secs
        self._log_cb       = log_callback or print
        self._status_cb    = status_callback or (lambda m: None)
        self._running      = False
        self._thread:      Optional[threading.Thread] = None

    def _log(self, msg: str) -> None:
        ts = datetime.now().strftime("%H:%M:%S")
        full = f"[{ts}] {msg}"
        self._log_cb(full)

    def start(self) -> None:
        """Inicia el monitoreo en un hilo secundario."""
        if self._running:
            return
        self._running = True
        self._thread  = threading.Thread(target=self._loop, daemon=True)
        self._thread.start()
        self._log(f"Monitor iniciado — carpeta: {self._datos_dir}")
        self._log(f"Intervalo de escaneo: {self._interval}s")

    def stop(self) -> None:
        self._running = False
        self._log("Monitor detenido.")

    def scan_once(self) -> int:
        """Escanea una vez y procesa todos los archivos nuevos."""
        processed = get_processed_set()
        csv_files = sorted(self._datos_dir.glob("*.csv"))
        new_files = [f for f in csv_files if f.name not in processed]

        if not new_files:
            return 0

        self._log(f"Encontrados {len(new_files)} archivo(s) nuevo(s)")
        ok_count = 0
        for fp in new_files:
            if process_csv_file(fp, self._log):
                ok_count += 1
                self._status_cb(f"Procesado: {fp.name}")

        return ok_count

    def _loop(self) -> None:
        """Loop de monitoreo continuo."""
        init_database()
        self._log("Base de datos inicializada.")
        self._log("Monitoreando carpeta DATOS/...")

        while self._running:
            try:
                n = self.scan_once()
                if n > 0:
                    self._log(f"Ciclo: {n} archivo(s) procesado(s)")
            except Exception as e:
                self._log(f"[ERROR] en ciclo: {e}")
            time.sleep(self._interval)


# ════════════════════════════════════════════════════════════════════════════
# PUNTO DE ENTRADA (GUI de Update.exe)
# ════════════════════════════════════════════════════════════════════════════

def main() -> None:
    import tkinter as tk
    from tkinter import ttk, scrolledtext

    root = tk.Tk()
    root.title("MiniMarket POS — Servidor de Actualización")
    root.geometry("600x480")
    root.configure(bg="#0f172a")
    root.resizable(True, True)

    # Header
    tk.Label(root, text="🖥  Servidor de Actualización — MiniMarket POS",
             font=("Segoe UI", 13, "bold"),
             bg="#0f172a", fg="white").pack(pady=(16, 2))
    tk.Label(root, text=f"Monitoreando: {DATOS_DIR}",
             font=("Consolas", 9), bg="#0f172a", fg="#94a3b8").pack()

    # Estado
    status_var = tk.StringVar(value="⏸ Detenido")
    tk.Label(root, textvariable=status_var, font=("Segoe UI", 10),
             bg="#0f172a", fg="#22c55e").pack(pady=4)

    # Log
    log_box = scrolledtext.ScrolledText(
        root, font=("Consolas", 9), bg="#1e293b", fg="#94fa75",
        relief="flat", height=18, state="disabled",
    )
    log_box.pack(fill="both", expand=True, padx=16, pady=8)

    def append(msg: str) -> None:
        log_box.config(state="normal")
        log_box.insert("end", msg + "\n")
        log_box.see("end")
        log_box.config(state="disabled")

    agent = UpdateAgent(
        log_callback=lambda m: root.after(0, lambda msg=m: append(msg)),
        status_callback=lambda m: root.after(0, lambda msg=m: status_var.set(f"✓ {msg}")),
    )

    # Botones
    btn_frame = tk.Frame(root, bg="#0f172a")
    btn_frame.pack(pady=(0, 12))

    def toggle():
        if not agent._running:
            agent.start()
            start_btn.config(text="⏹ Detener", bg="#ef4444")
            status_var.set("▶ Monitoreando...")
        else:
            agent.stop()
            start_btn.config(text="▶ Iniciar Monitor", bg="#22c55e")
            status_var.set("⏸ Detenido")

    def scan_once():
        threading.Thread(
            target=lambda: agent.scan_once(), daemon=True
        ).start()

    start_btn = tk.Button(
        btn_frame, text="▶ Iniciar Monitor",
        font=("Segoe UI", 10, "bold"),
        bg="#22c55e", fg="white", bd=0, padx=18, pady=7,
        cursor="hand2", command=toggle,
    )
    start_btn.pack(side="left", padx=6)

    tk.Button(
        btn_frame, text="⟳ Escanear Ahora",
        font=("Segoe UI", 10),
        bg="#3b82f6", fg="white", bd=0, padx=16, pady=7,
        cursor="hand2", command=scan_once,
    ).pack(side="left", padx=6)

    # Inicializar BD al arrancar
    try:
        init_database()
        append(f"[OK] Base de datos inicializada: {SERVER_DB}")
    except Exception as e:
        append(f"[ERROR] BD: {e}")

    root.mainloop()


if __name__ == "__main__":
    main()
