"""
send.py — Agente de sincronización cliente→servidor.

Propósito:
    Lee los datos locales del cliente POS (almacenados en archivos
    binarios .dat), los exporta a CSV y los copia hacia la carpeta
    compartida en red (DATOS/) usando rutas UNC.

Ruta UNC:  \\\\NOMBRE-PC\\DATOS
Fallback:  BASE_DIR/DATOS (cuando la red no está disponible)

Compilación:
    pyinstaller --onefile --name Send send.py
    → genera Send.exe

Uso en producción:
    Send.exe              (ejecuta la sincronización)
    python send.py        (modo desarrollo)
"""
import sys
import shutil
from datetime import datetime
from pathlib import Path
from typing import Callable, Optional

# ── Asegurar imports desde APPLICATION/ ──────────────────────────────────────
APP_DIR = Path(__file__).resolve().parent
if str(APP_DIR) not in sys.path:
    sys.path.insert(0, str(APP_DIR))


class SyncAgent:
    """
    Agente de sincronización local→red.

    Proceso:
        1. Exportar productos.dat  → exports/productos_TIMESTAMP.csv
        2. Exportar clientes.dat   → exports/clientes_TIMESTAMP.csv
        3. Exportar ventas.dat     → exports/ventas_TIMESTAMP.csv
        4. Exportar detalles.dat   → exports/detalles_TIMESTAMP.csv
        5. Copiar todos los CSV a DATOS/ (UNC o fallback local)
        6. Registrar operación en el log de sincronización
    """

    def __init__(
        self,
        log_callback:    Optional[Callable[[str], None]] = None,
        status_callback: Optional[Callable[[str], None]] = None,
    ) -> None:
        from utils.config import (
            EXPORTS_DIR, DATOS_DIR, UNC_DATOS_PATH,
            HOSTNAME, init_directories,
        )
        from utils.logger import get_logger

        init_directories()
        self._exports_dir = EXPORTS_DIR
        self._datos_dir   = DATOS_DIR
        self._unc_path    = UNC_DATOS_PATH
        self._hostname    = HOSTNAME
        self._log_cb      = log_callback or print
        self._status_cb   = status_callback or print
        self._logger      = get_logger("send")
        self._timestamp   = datetime.now().strftime("%Y%m%d_%H%M%S")

    def _log(self, msg: str) -> None:
        self._logger.info(msg)
        self._log_cb(msg)

    def _status(self, msg: str) -> None:
        self._logger.info(msg)
        self._status_cb(msg)

    def run(self) -> bool:
        """
        Ejecuta el proceso completo de sincronización.

        Returns:
            True si todo fue exitoso, False si hubo errores parciales
        """
        self._log(f"{'='*50}")
        self._log(f"Inicio sincronización — {datetime.now()}")
        self._log(f"Hostname: {self._hostname}")
        errors = 0

        # ── Exportar cada entidad ─────────────────────────────────────────────
        exports = self._export_all()

        if not exports:
            self._log("[ADVERTENCIA] No se generaron archivos de exportación.")
            return False

        # ── Determinar destino (UNC o fallback) ───────────────────────────────
        destino = self._resolve_destination()
        self._log(f"Directorio destino: {destino}")

        # ── Copiar archivos al destino ────────────────────────────────────────
        for src_path in exports:
            self._status(f"Copiando: {src_path.name}")
            try:
                dest_file = destino / src_path.name
                shutil.copy2(src_path, dest_file)
                self._log(f"  ✓ {src_path.name} → {dest_file}")
            except Exception as e:
                self._log(f"  ✗ Error copiando {src_path.name}: {e}")
                errors += 1

        # ── Escribir manifiesto ───────────────────────────────────────────────
        self._write_manifest(destino, exports)

        self._log(f"Sincronización finalizada. Errores: {errors}")
        self._status(
            f"✓ Sincronización completada — {len(exports)} archivos"
            if errors == 0
            else f"⚠ Finalizado con {errors} error(es)"
        )
        return errors == 0

    def _export_all(self) -> list:
        """Exporta todas las entidades a CSV y retorna lista de rutas."""
        from services.producto_service import ProductoService
        from services.cliente_service  import ClienteService
        from services.venta_service    import VentaService

        prod_svc  = ProductoService()
        cli_svc   = ClienteService()
        venta_svc = VentaService()

        exports = []
        ts = self._timestamp

        entities = [
            (f"productos_{ts}.csv",  prod_svc.exportar_csv,      "Productos"),
            (f"clientes_{ts}.csv",   cli_svc.exportar_csv,       "Clientes"),
            (f"ventas_{ts}.csv",     venta_svc.exportar_ventas_csv,  "Ventas"),
            (f"detalles_{ts}.csv",   venta_svc.exportar_detalles_csv, "Detalles"),
        ]

        for filename, export_fn, label in entities:
            path = self._exports_dir / filename
            self._status(f"Exportando {label}...")
            try:
                n = export_fn(path)
                self._log(f"  ✓ {label}: {n} registros → {filename}")
                exports.append(path)
            except Exception as e:
                self._log(f"  ✗ Error exportando {label}: {e}")

        return exports

    def _resolve_destination(self) -> Path:
        """
        Determina el directorio destino.
        Intenta la ruta UNC primero; si falla, usa el fallback local.
        """
        # Intentar ruta UNC
        try:
            if self._unc_path.exists():
                self._log(f"Usando ruta UNC: {self._unc_path}")
                return self._unc_path
        except (OSError, PermissionError):
            pass

        # Fallback: carpeta DATOS/ local
        self._log(
            f"Ruta UNC no disponible ({self._unc_path}). "
            f"Usando fallback local: {self._datos_dir}"
        )
        self._datos_dir.mkdir(parents=True, exist_ok=True)
        return self._datos_dir

    def _write_manifest(self, destino: Path, files: list) -> None:
        """Escribe un archivo MANIFEST con el listado de archivos enviados."""
        manifest_path = destino / f"MANIFEST_{self._timestamp}.txt"
        try:
            with open(manifest_path, "w", encoding="utf-8") as f:
                f.write(f"MANIFEST DE SINCRONIZACIÓN\n")
                f.write(f"Fecha:    {datetime.now()}\n")
                f.write(f"Origen:   {self._hostname}\n")
                f.write(f"Archivos: {len(files)}\n")
                f.write("-" * 40 + "\n")
                for fp in files:
                    size = fp.stat().st_size if fp.exists() else 0
                    f.write(f"  {fp.name}  ({size} bytes)\n")
            self._log(f"  ✓ Manifiesto creado: {manifest_path.name}")
        except Exception as e:
            self._log(f"  ✗ No se pudo crear el manifiesto: {e}")


# ── Punto de entrada para ejecución directa o como .EXE ──────────────────────
def main() -> None:
    import tkinter as tk
    from tkinter import ttk, scrolledtext, messagebox

    # GUI simple para Send.exe
    root = tk.Tk()
    root.title("MiniMarket POS — Sincronización")
    root.geometry("500x400")
    root.configure(bg="#1e293b")
    root.resizable(False, False)

    tk.Label(root, text="☁  Sincronización con Servidor",
             font=("Segoe UI", 14, "bold"),
             bg="#1e293b", fg="white").pack(pady=(20, 4))
    tk.Label(root, text="Exporta datos locales y los copia a la carpeta DATOS/ de red.",
             font=("Segoe UI", 9),
             bg="#1e293b", fg="#94a3b8").pack()

    log_box = scrolledtext.ScrolledText(
        root, height=14, bg="#0f172a", fg="#94fa75",
        font=("Consolas", 9), relief="flat", state="disabled",
    )
    log_box.pack(fill="both", expand=True, padx=16, pady=12)

    status_var = tk.StringVar(value="Listo para sincronizar.")
    tk.Label(root, textvariable=status_var, font=("Segoe UI", 9),
             bg="#1e293b", fg="#94a3b8").pack()

    def append_log(msg: str) -> None:
        log_box.config(state="normal")
        log_box.insert("end", msg + "\n")
        log_box.see("end")
        log_box.config(state="disabled")

    def run_sync() -> None:
        sync_btn.config(state="disabled", text="Sincronizando...")
        try:
            agent = SyncAgent(
                log_callback=lambda m: root.after(0, lambda: append_log(m)),
                status_callback=lambda m: root.after(0, lambda: status_var.set(m)),
            )
            ok = agent.run()
            if ok:
                messagebox.showinfo("Éxito", "Sincronización completada correctamente.")
            else:
                messagebox.showwarning("Advertencia", "Sincronización con errores (ver log).")
        except Exception as e:
            append_log(f"[CRÍTICO] {e}")
            messagebox.showerror("Error", str(e))
        finally:
            sync_btn.config(state="normal", text="▶ Sincronizar Ahora")

    import threading
    sync_btn = tk.Button(
        root, text="▶ Sincronizar Ahora",
        font=("Segoe UI", 11, "bold"),
        bg="#3b82f6", fg="white", bd=0, padx=20, pady=8,
        cursor="hand2",
        command=lambda: threading.Thread(target=run_sync, daemon=True).start(),
    )
    sync_btn.pack(pady=(4, 16))

    root.mainloop()


if __name__ == "__main__":
    main()
