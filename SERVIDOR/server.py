"""
server.py — Aplicación servidor del Sistema POS MiniMarket.

Interfaz gráfica del servidor que muestra:
- Estado de la base de datos SQLite consolidada
- Últimas sincronizaciones recibidas
- Estadísticas consolidadas de todas las sucursales
- Consultas SQL sobre datos consolidados

Ejecutar:
    python server.py
    o compilado: Server.exe
"""
import sys
import sqlite3
import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
from datetime import datetime
from pathlib import Path
import threading

# ── Rutas ─────────────────────────────────────────────────────────────────────
# Igual que update.py: cuando se compila como Server.exe, __file__ apunta al
# directorio temporal de PyInstaller.  Usamos sys.executable en su lugar.
if getattr(sys, "frozen", False):
    BASE_DIR     = Path(sys.executable).resolve().parent.parent
    SERVIDOR_DIR = BASE_DIR / "SERVIDOR"
else:
    SERVIDOR_DIR = Path(__file__).resolve().parent
    BASE_DIR     = SERVIDOR_DIR.parent
DATOS_DIR    = BASE_DIR / "DATOS"
DB_DIR       = SERVIDOR_DIR / "database"
SERVER_DB    = DB_DIR / "minimarket.db"
INIT_SQL     = SERVIDOR_DIR / "init_db.sql"

# Colores
C_BG   = "#0f172a"
C_CARD = "#1e293b"
C_FG   = "#e2e8f0"
C_MUTED = "#64748b"
C_ACCENT = "#3b82f6"
C_SUCCESS = "#22c55e"
C_ERROR   = "#ef4444"


def get_conn():
    if not SERVER_DB.exists():
        from update import init_database
        init_database()
    conn = sqlite3.connect(str(SERVER_DB))
    conn.row_factory = sqlite3.Row
    return conn


class ServerApp:
    """Aplicación de gestión del servidor central."""

    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        root.title("MiniMarket POS — Servidor Central")
        root.geometry("1000x680")
        root.configure(bg=C_BG)
        root.resizable(True, True)

        self._update_agent = None
        self._build_ui()
        self._refresh()

    def _build_ui(self) -> None:
        root = self.root
        root.columnconfigure(0, weight=1)
        root.rowconfigure(1, weight=1)

        # ── Header ───────────────────────────────────────────────────────────
        hdr = tk.Frame(root, bg="#1e3a5f", height=64)
        hdr.grid(row=0, column=0, sticky="ew")
        hdr.grid_propagate(False)
        hdr.columnconfigure(1, weight=1)

        tk.Label(hdr, text="🖥  MiniMarket POS — Servidor Central",
                 font=("Segoe UI", 14, "bold"),
                 bg="#1e3a5f", fg="white").grid(row=0, column=0, padx=20, pady=18)

        self._status_var = tk.StringVar(value="DB: No conectada")
        tk.Label(hdr, textvariable=self._status_var,
                 font=("Segoe UI", 9), bg="#1e3a5f", fg="#94a3b8").grid(
            row=0, column=1, sticky="e", padx=20
        )

        # ── Notebook de secciones ─────────────────────────────────────────────
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("Dark.TNotebook", background=C_BG, borderwidth=0)
        style.configure("Dark.TNotebook.Tab",
                         background="#1e293b", foreground="#94a3b8",
                         padding=[16, 8], font=("Segoe UI", 10))
        style.map("Dark.TNotebook.Tab",
                  background=[("selected", "#3b82f6")],
                  foreground=[("selected", "white")])

        nb = ttk.Notebook(root, style="Dark.TNotebook")
        nb.grid(row=1, column=0, sticky="nsew", padx=16, pady=12)

        self._build_dashboard_tab(nb)
        self._build_sync_tab(nb)
        self._build_query_tab(nb)
        self._build_monitor_tab(nb)

    def _dark_frame(self, parent, **kw) -> tk.Frame:
        return tk.Frame(parent, bg=C_CARD, **kw)

    def _label(self, parent, text, **kw):
        defaults = {"bg": C_CARD, "fg": C_FG, "font": ("Segoe UI", 10)}
        defaults.update(kw)
        return tk.Label(parent, text=text, **defaults)

    def _tv_style(self, name: str) -> str:
        style = ttk.Style()
        style.configure(f"{name}.Treeview",
                         background="#1e293b", fieldbackground="#1e293b",
                         foreground=C_FG, rowheight=28, font=("Segoe UI", 9))
        style.configure(f"{name}.Treeview.Heading",
                         background="#0f172a", foreground="#94a3b8",
                         font=("Segoe UI", 9, "bold"))
        style.map(f"{name}.Treeview", background=[("selected", "#3b82f6")])
        return f"{name}.Treeview"

    # ── Tab 1: Dashboard ─────────────────────────────────────────────────────

    def _build_dashboard_tab(self, nb) -> None:
        frame = self._dark_frame(nb, padx=16, pady=12)
        nb.add(frame, text="📊 Dashboard")
        frame.columnconfigure(list(range(3)), weight=1, uniform="kpi")
        frame.rowconfigure(1, weight=1)

        self._kpi_vars = {}
        kpis = [
            ("total_ventas",    "Total Ventas", C_ACCENT),
            ("total_ingresos",  "Ingresos Totales", C_SUCCESS),
            ("total_productos", "Productos", "#f59e0b"),
            ("total_clientes",  "Clientes", "#8b5cf6"),
            ("total_sucursales","Sucursales", "#06b6d4"),
            ("ultima_sync",     "Última Sync", C_MUTED),
        ]
        for i, (key, label, color) in enumerate(kpis):
            card = tk.Frame(frame, bg="#334155", padx=12, pady=10)
            card.grid(row=i // 3, column=i % 3, padx=6, pady=6, sticky="nsew")
            tk.Frame(card, bg=color, height=3).pack(fill="x")
            tk.Label(card, text=label, font=("Segoe UI", 9),
                     bg="#334155", fg=C_MUTED).pack(anchor="w", pady=(4, 0))
            var = tk.StringVar(value="—")
            tk.Label(card, textvariable=var, font=("Segoe UI", 16, "bold"),
                     bg="#334155", fg="white").pack(anchor="w")
            self._kpi_vars[key] = var

        # Tabla resumen por sucursal
        tv_frame = tk.Frame(frame, bg=C_CARD)
        tv_frame.grid(row=2, column=0, columnspan=3, sticky="nsew", pady=(8, 0))
        tv_frame.rowconfigure(0, weight=1)
        tv_frame.columnconfigure(0, weight=1)
        frame.rowconfigure(2, weight=1)

        style_name = self._tv_style("Srv")
        cols = ("sucursal", "ultima_sync", "num_ventas", "total")
        self._dash_tree = ttk.Treeview(tv_frame, columns=cols,
                                        show="headings", style=style_name)
        for col, hd, w in [
            ("sucursal",    "Sucursal",    160),
            ("ultima_sync", "Última Sync", 160),
            ("num_ventas",  "Ventas",      80),
            ("total",       "Total",       120),
        ]:
            self._dash_tree.heading(col, text=hd)
            self._dash_tree.column(col, width=w)

        sb = ttk.Scrollbar(tv_frame, orient="vertical",
                           command=self._dash_tree.yview)
        self._dash_tree.configure(yscrollcommand=sb.set)
        self._dash_tree.grid(row=0, column=0, sticky="nsew")
        sb.grid(row=0, column=1, sticky="ns")

        tk.Button(frame, text="↻ Actualizar", font=("Segoe UI", 9),
                  bg=C_ACCENT, fg="white", bd=0, padx=12, pady=4,
                  cursor="hand2", command=self._refresh).grid(
            row=3, column=2, sticky="e", pady=(8, 0)
        )

    # ── Tab 2: Sincronizaciones ───────────────────────────────────────────────

    def _build_sync_tab(self, nb) -> None:
        frame = self._dark_frame(nb, padx=16, pady=12)
        nb.add(frame, text="☁ Sincronizaciones")
        frame.columnconfigure(0, weight=1)
        frame.rowconfigure(0, weight=1)

        sn = self._tv_style("Sync")
        cols = ("archivo", "entidad", "sucursal", "insertados", "actualizados", "errores", "fecha")
        self._sync_tree = ttk.Treeview(frame, columns=cols,
                                        show="headings", style=sn)
        cfg = [
            ("archivo",      "Archivo",      200),
            ("entidad",      "Entidad",      90),
            ("sucursal",     "Sucursal",     120),
            ("insertados",   "Insertados",   90),
            ("actualizados", "Actualizados", 100),
            ("errores",      "Errores",      70),
            ("fecha",        "Procesado",    140),
        ]
        for col, hd, w in cfg:
            self._sync_tree.heading(col, text=hd)
            self._sync_tree.column(col, width=w)

        sb = ttk.Scrollbar(frame, orient="vertical", command=self._sync_tree.yview)
        self._sync_tree.configure(yscrollcommand=sb.set)
        self._sync_tree.grid(row=0, column=0, sticky="nsew")
        sb.grid(row=0, column=1, sticky="ns")

    # ── Tab 3: Consultas SQL ──────────────────────────────────────────────────

    def _build_query_tab(self, nb) -> None:
        frame = self._dark_frame(nb, padx=16, pady=12)
        nb.add(frame, text="🔍 Consultas SQL")
        frame.columnconfigure(0, weight=1)
        frame.rowconfigure(1, weight=1)
        frame.rowconfigure(3, weight=2)

        queries = {
            "Ventas hoy":       "SELECT * FROM v_ventas_resumen WHERE fecha = date('now','localtime');",
            "Más vendidos":     "SELECT * FROM v_productos_mas_vendidos LIMIT 10;",
            "Todas sucursales": "SELECT * FROM sucursales;",
            "Sync log (10)":    "SELECT * FROM sync_log ORDER BY id DESC LIMIT 10;",
        }
        self._query_var = tk.StringVar()

        btn_bar = tk.Frame(frame, bg=C_CARD)
        btn_bar.grid(row=0, column=0, sticky="ew", pady=(0, 8))
        for label, sql in queries.items():
            tk.Button(
                btn_bar, text=label, font=("Segoe UI", 9),
                bg="#334155", fg=C_FG, bd=0, padx=10, pady=4,
                cursor="hand2",
                command=lambda s=sql: self._run_query(s),
            ).pack(side="left", padx=4)

        self._sql_entry = tk.Text(
            frame, height=4, font=("Consolas", 9),
            bg="#1e293b", fg="#94fa75", relief="solid", bd=1,
            insertbackground="white",
        )
        self._sql_entry.grid(row=1, column=0, sticky="nsew", pady=(0, 4))
        self._sql_entry.insert("1.0", "SELECT * FROM ventas LIMIT 10;")

        tk.Button(
            frame, text="▶ Ejecutar", font=("Segoe UI", 9, "bold"),
            bg=C_ACCENT, fg="white", bd=0, padx=14, pady=5,
            cursor="hand2",
            command=lambda: self._run_query(self._sql_entry.get("1.0", "end")),
        ).grid(row=2, column=0, sticky="w", pady=(0, 8))

        self._query_result = scrolledtext.ScrolledText(
            frame, font=("Consolas", 9),
            bg="#0f172a", fg="#e2e8f0", relief="flat",
            state="disabled",
        )
        self._query_result.grid(row=3, column=0, sticky="nsew")

    def _run_query(self, sql: str) -> None:
        self._sql_entry.delete("1.0", "end")
        self._sql_entry.insert("1.0", sql.strip())
        self._query_result.config(state="normal")
        self._query_result.delete("1.0", "end")
        try:
            conn = get_conn()
            cur  = conn.execute(sql.strip())
            rows = cur.fetchall()
            if rows:
                cols = [d[0] for d in cur.description]
                header = "  |  ".join(f"{c:<18}" for c in cols)
                self._query_result.insert("end", header + "\n")
                self._query_result.insert("end", "─" * len(header) + "\n")
                for r in rows:
                    line = "  |  ".join(f"{str(v):<18}" for v in r)
                    self._query_result.insert("end", line + "\n")
                self._query_result.insert("end", f"\n{len(rows)} fila(s)\n")
            else:
                self._query_result.insert("end", "Sin resultados o consulta DML ejecutada.\n")
            conn.commit()
            conn.close()
        except Exception as e:
            self._query_result.insert("end", f"ERROR: {e}\n")
        self._query_result.config(state="disabled")

    # ── Tab 4: Monitor de DATOS/ ─────────────────────────────────────────────

    def _build_monitor_tab(self, nb) -> None:
        frame = self._dark_frame(nb, padx=16, pady=12)
        nb.add(frame, text="📡 Monitor")
        frame.columnconfigure(0, weight=1)
        frame.rowconfigure(1, weight=1)

        ctrl = tk.Frame(frame, bg=C_CARD)
        ctrl.grid(row=0, column=0, sticky="ew", pady=(0, 8))

        self._monitor_status = tk.StringVar(value="⏸ Monitor detenido")
        tk.Label(ctrl, textvariable=self._monitor_status,
                 font=("Segoe UI", 10), bg=C_CARD, fg=C_SUCCESS).pack(side="left")

        def toggle_monitor():
            from update import UpdateAgent
            if self._update_agent is None or not self._update_agent._running:
                self._update_agent = UpdateAgent(
                    log_callback=lambda m: self.root.after(
                        0, lambda msg=m: self._append_monitor(msg)
                    ),
                )
                self._update_agent.start()
                self._monitor_status.set("▶ Monitoreando DATOS/...")
                mon_btn.config(text="⏹ Detener", bg=C_ERROR)
            else:
                self._update_agent.stop()
                self._monitor_status.set("⏸ Monitor detenido")
                mon_btn.config(text="▶ Iniciar Monitor", bg=C_SUCCESS)

        mon_btn = tk.Button(
            ctrl, text="▶ Iniciar Monitor",
            font=("Segoe UI", 9, "bold"),
            bg=C_SUCCESS, fg="white", bd=0, padx=14, pady=5,
            cursor="hand2", command=toggle_monitor,
        )
        mon_btn.pack(side="right", padx=4)

        tk.Button(
            ctrl, text="⟳ Escanear Ahora",
            font=("Segoe UI", 9),
            bg=C_ACCENT, fg="white", bd=0, padx=14, pady=5,
            cursor="hand2",
            command=lambda: threading.Thread(
                target=self._scan_once, daemon=True
            ).start(),
        ).pack(side="right", padx=4)

        self._monitor_log = scrolledtext.ScrolledText(
            frame, font=("Consolas", 9),
            bg="#0f172a", fg="#94fa75", relief="flat",
            state="disabled",
        )
        self._monitor_log.grid(row=1, column=0, sticky="nsew")

    def _append_monitor(self, msg: str) -> None:
        self._monitor_log.config(state="normal")
        self._monitor_log.insert("end", msg + "\n")
        self._monitor_log.see("end")
        self._monitor_log.config(state="disabled")

    def _scan_once(self) -> None:
        from update import UpdateAgent, init_database
        init_database()
        agent = UpdateAgent(
            log_callback=lambda m: self.root.after(
                0, lambda msg=m: self._append_monitor(msg)
            ),
        )
        n = agent.scan_once()
        self.root.after(0, self._refresh)
        self.root.after(0, lambda: self._append_monitor(
            f"Escaneo completado: {n} archivo(s) procesado(s)"
        ))

    # ── Actualizar datos ──────────────────────────────────────────────────────

    def _refresh(self) -> None:
        try:
            conn = get_conn()

            # KPIs
            r = conn.execute("SELECT COUNT(*) FROM ventas WHERE estado=1").fetchone()
            self._kpi_vars["total_ventas"].set(str(r[0]))

            r = conn.execute("SELECT COALESCE(SUM(total),0) FROM ventas WHERE estado=1").fetchone()
            self._kpi_vars["total_ingresos"].set(f"S/ {r[0]:.2f}")

            r = conn.execute("SELECT COUNT(*) FROM productos WHERE estado=1").fetchone()
            self._kpi_vars["total_productos"].set(str(r[0]))

            r = conn.execute("SELECT COUNT(*) FROM clientes WHERE estado=1").fetchone()
            self._kpi_vars["total_clientes"].set(str(r[0]))

            r = conn.execute("SELECT COUNT(*) FROM sucursales WHERE activo=1").fetchone()
            self._kpi_vars["total_sucursales"].set(str(r[0]))

            r = conn.execute("SELECT MAX(procesado_en) FROM sync_log").fetchone()
            self._kpi_vars["ultima_sync"].set(r[0] or "—")

            # Tabla sucursales
            for row in self._dash_tree.get_children():
                self._dash_tree.delete(row)

            rows = conn.execute("""
                SELECT s.nombre, s.ultima_sync,
                       COUNT(v.id) AS n_ventas,
                       COALESCE(SUM(v.total),0) AS total
                FROM sucursales s
                LEFT JOIN ventas v ON v.sucursal_id = s.id AND v.estado=1
                WHERE s.activo=1
                GROUP BY s.id
            """).fetchall()
            for r in rows:
                self._dash_tree.insert("", "end", values=(
                    r["nombre"], r["ultima_sync"] or "—",
                    r["n_ventas"], f"S/ {r['total']:.2f}",
                ))

            # Tabla sync log
            for row in self._sync_tree.get_children():
                self._sync_tree.delete(row)

            slogs = conn.execute("""
                SELECT sl.archivo, sl.entidad,
                       s.nombre AS sucursal,
                       sl.registros_proc, sl.registros_dup,
                       sl.registros_err, sl.procesado_en
                FROM sync_log sl
                LEFT JOIN sucursales s ON s.id = sl.sucursal_id
                ORDER BY sl.id DESC LIMIT 50
            """).fetchall()
            for r in slogs:
                self._sync_tree.insert("", "end", values=(
                    r["archivo"], r["entidad"],
                    r["sucursal"] or "—",
                    r["registros_proc"], r["registros_dup"],
                    r["registros_err"], r["procesado_en"],
                ))

            self._status_var.set(
                f"DB: {SERVER_DB.name} | "
                f"Última actualización: {datetime.now().strftime('%H:%M:%S')}"
            )
            conn.close()
        except Exception as e:
            self._status_var.set(f"Error: {e}")


def main() -> None:
    # Inicializar BD antes de abrir la GUI
    try:
        from update import init_database
        init_database()
    except Exception as e:
        print(f"Advertencia inicializando BD: {e}")

    root = tk.Tk()
    app  = ServerApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
