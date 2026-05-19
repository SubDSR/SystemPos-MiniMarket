"""
dashboard_view.py — Panel principal con estadísticas del sistema POS.

Muestra tarjetas de KPI y las últimas ventas del día leídas
directamente desde los archivos binarios .dat.
"""
import tkinter as tk
from tkinter import ttk
from datetime import datetime

from utils.config import (
    C_MAIN_BG, C_CARD_BG, C_ACCENT, C_SUCCESS, C_WARNING, C_ERROR,
    C_TEXT, C_TEXT_MUTED, C_BORDER, C_ROW_ODD, C_ROW_EVEN, C_ROW_SELECT,
    FONT_H1, FONT_H2, FONT_H3, FONT_BODY, FONT_SMALL, FONT_BTN,
    EMPRESA,
)


class KpiCard(tk.Frame):
    """Tarjeta de indicador clave de rendimiento (KPI)."""

    def __init__(self, parent, title: str, value: str,
                 subtitle: str = "", accent: str = C_ACCENT, **kw):
        super().__init__(parent, bg=C_CARD_BG,
                         relief="flat", bd=0, **kw)

        # Barra de color en la parte superior
        tk.Frame(self, bg=accent, height=4).pack(fill="x")

        inner = tk.Frame(self, bg=C_CARD_BG, padx=20, pady=16)
        inner.pack(fill="both", expand=True)

        self._value_var = tk.StringVar(value=value)
        self._sub_var   = tk.StringVar(value=subtitle)

        tk.Label(inner, text=title, font=FONT_SMALL,
                 bg=C_CARD_BG, fg=C_TEXT_MUTED).pack(anchor="w")
        tk.Label(inner, textvariable=self._value_var, font=FONT_H1,
                 bg=C_CARD_BG, fg=C_TEXT).pack(anchor="w", pady=(4, 2))
        tk.Label(inner, textvariable=self._sub_var, font=FONT_SMALL,
                 bg=C_CARD_BG, fg=C_TEXT_MUTED).pack(anchor="w")

    def update(self, value: str, subtitle: str = "") -> None:
        self._value_var.set(value)
        if subtitle:
            self._sub_var.set(subtitle)


class DashboardView(tk.Frame):
    """
    Vista de Dashboard: estadísticas en tiempo real desde archivos .dat.
    Se actualiza automáticamente cada 30 segundos.
    """

    def __init__(self, parent, controller) -> None:
        super().__init__(parent, bg=C_MAIN_BG)
        self._ctrl = controller
        self._build_ui()

    def _build_ui(self) -> None:
        self.columnconfigure(0, weight=1)
        self.rowconfigure(1, weight=1)

        # ── Bienvenida ────────────────────────────────────────────────────────
        welcome = tk.Frame(self, bg=C_MAIN_BG, pady=16, padx=24)
        welcome.grid(row=0, column=0, sticky="ew")

        hoy = datetime.now().strftime("%A %d de %B, %Y")
        tk.Label(
            welcome, text=f"Bienvenido al {EMPRESA}",
            font=FONT_H2, bg=C_MAIN_BG, fg=C_TEXT,
        ).pack(anchor="w")
        tk.Label(
            welcome, text=hoy.capitalize(),
            font=FONT_BODY, bg=C_MAIN_BG, fg=C_TEXT_MUTED,
        ).pack(anchor="w")

        # ── Tarjetas KPI ──────────────────────────────────────────────────────
        cards_frame = tk.Frame(self, bg=C_MAIN_BG, padx=24, pady=8)
        cards_frame.grid(row=1, column=0, sticky="new")
        for i in range(4):
            cards_frame.columnconfigure(i, weight=1, uniform="card")

        self._kpi_ventas = KpiCard(
            cards_frame, "Ventas Hoy", "0",
            subtitle="transacciones", accent=C_ACCENT,
        )
        self._kpi_ventas.grid(row=0, column=0, sticky="nsew", padx=(0, 8), pady=8)

        self._kpi_total = KpiCard(
            cards_frame, "Total Hoy", "S/ 0.00",
            subtitle="ingresos", accent=C_SUCCESS,
        )
        self._kpi_total.grid(row=0, column=1, sticky="nsew", padx=4, pady=8)

        self._kpi_productos = KpiCard(
            cards_frame, "Productos", "0",
            subtitle="en inventario", accent=C_WARNING,
        )
        self._kpi_productos.grid(row=0, column=2, sticky="nsew", padx=4, pady=8)

        self._kpi_clientes = KpiCard(
            cards_frame, "Clientes", "0",
            subtitle="registrados", accent="#8b5cf6",
        )
        self._kpi_clientes.grid(row=0, column=3, sticky="nsew", padx=(8, 0), pady=8)

        # ── Tabla de últimas ventas ───────────────────────────────────────────
        table_frame = tk.Frame(self, bg=C_CARD_BG, padx=0, pady=0)
        table_frame.grid(row=2, column=0, sticky="nsew", padx=24, pady=(0, 24))
        table_frame.columnconfigure(0, weight=1)
        table_frame.rowconfigure(1, weight=1)
        self.rowconfigure(2, weight=1)

        # Encabezado de la tabla
        hdr = tk.Frame(table_frame, bg=C_CARD_BG, padx=16, pady=12)
        hdr.grid(row=0, column=0, sticky="ew")
        tk.Label(hdr, text="Últimas Ventas del Día", font=FONT_H3,
                 bg=C_CARD_BG, fg=C_TEXT).pack(side="left")
        self._btn_refresh = tk.Button(
            hdr, text="↻ Actualizar", font=FONT_SMALL,
            bg=C_ACCENT, fg="white", bd=0, padx=12, pady=4,
            cursor="hand2", command=self.on_show,
        )
        self._btn_refresh.pack(side="right")

        # Separador
        tk.Frame(table_frame, bg=C_BORDER, height=1).grid(
            row=0, column=0, sticky="ews"
        )

        # Treeview
        tv_frame = tk.Frame(table_frame, bg=C_CARD_BG)
        tv_frame.grid(row=1, column=0, sticky="nsew")
        tv_frame.rowconfigure(0, weight=1)
        tv_frame.columnconfigure(0, weight=1)

        style = ttk.Style()
        style.configure(
            "Dash.Treeview",
            rowheight=32, font=FONT_BODY,
            background=C_CARD_BG, fieldbackground=C_CARD_BG,
        )
        style.configure(
            "Dash.Treeview.Heading",
            font=("Segoe UI", 9, "bold"), background="#f1f5f9",
        )

        cols = ("id", "fecha", "cliente", "items", "total")
        self._tree = ttk.Treeview(
            tv_frame, columns=cols, show="headings",
            style="Dash.Treeview", height=10,
        )
        headings = {
            "id": ("# Venta", 80),
            "fecha": ("Fecha / Hora", 160),
            "cliente": ("Cliente ID", 100),
            "items": ("Ítems", 80),
            "total": ("Total", 120),
        }
        for col, (hd, w) in headings.items():
            self._tree.heading(col, text=hd)
            self._tree.column(col, width=w, anchor="center" if col != "fecha" else "w")

        self._tree.tag_configure("odd",  background=C_ROW_ODD)
        self._tree.tag_configure("even", background=C_ROW_EVEN)

        scrollbar = ttk.Scrollbar(tv_frame, orient="vertical",
                                   command=self._tree.yview)
        self._tree.configure(yscrollcommand=scrollbar.set)
        self._tree.grid(row=0, column=0, sticky="nsew")
        scrollbar.grid(row=0, column=1, sticky="ns")

        # Botones de acción rápida
        action_frame = tk.Frame(self, bg=C_MAIN_BG, pady=12, padx=24)
        action_frame.grid(row=3, column=0, sticky="ew")

        acciones = [
            ("+ Nueva Venta",       "ventas",    C_ACCENT),
            ("+ Nuevo Producto",    "productos", C_SUCCESS),
            ("+ Nuevo Cliente",     "clientes",  "#8b5cf6"),
        ]
        for txt, nav_key, color in acciones:
            tk.Button(
                action_frame, text=txt, font=FONT_BTN,
                bg=color, fg="white", bd=0, padx=20, pady=8,
                cursor="hand2",
                command=lambda k=nav_key: self._ctrl.navigate(k),
            ).pack(side="left", padx=(0, 8))

    # ════════════════════════════════════════════════════════════════════════
    # ACTUALIZACIÓN DE DATOS
    # ════════════════════════════════════════════════════════════════════════

    def on_show(self) -> None:
        """Refresca los datos al navegar a esta vista."""
        try:
            from services.producto_service import ProductoService
            from services.cliente_service  import ClienteService
            from services.venta_service    import VentaService

            prod_svc   = ProductoService()
            cli_svc    = ClienteService()
            venta_svc  = VentaService()

            ventas_hoy = venta_svc.ventas_hoy()
            total_hoy  = venta_svc.total_hoy()

            self._kpi_ventas.update(
                str(len(ventas_hoy)),
                f"{len(ventas_hoy)} transacciones hoy",
            )
            self._kpi_total.update(
                f"S/ {total_hoy:.2f}",
                "ingresos del día",
            )
            self._kpi_productos.update(
                str(prod_svc.contar()),
                "productos activos",
            )
            self._kpi_clientes.update(
                str(cli_svc.contar()),
                "clientes registrados",
            )

            # Actualizar tabla de ventas
            for row in self._tree.get_children():
                self._tree.delete(row)

            detalles_by_venta = {}
            for det in venta_svc.listar_detalles():
                detalles_by_venta.setdefault(det.venta_id, []).append(det)

            for i, v in enumerate(reversed(ventas_hoy[-20:])):
                n_items = len(detalles_by_venta.get(v.id, []))
                tag     = "odd" if i % 2 == 0 else "even"
                self._tree.insert(
                    "", "end", tags=(tag,),
                    values=(
                        f"#{v.id:04d}",
                        v.fecha,
                        f"CLI-{v.cliente_id:03d}" if v.cliente_id else "Anónimo",
                        f"{n_items} ítem(s)",
                        f"S/ {v.total:.2f}",
                    ),
                )

            self._ctrl.set_status(
                f"Actualizado: {datetime.now().strftime('%H:%M:%S')}",
                "#64748b",
            )
        except Exception as e:
            self._ctrl.set_status(f"Error al cargar datos: {e}", "#ef4444")
