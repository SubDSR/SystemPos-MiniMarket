"""
historial_view.py — Historial completo de ventas con detalles expandibles.
"""
import tkinter as tk
from tkinter import ttk, messagebox
from datetime import datetime

from utils.config import (
    C_MAIN_BG, C_CARD_BG, C_ACCENT, C_SUCCESS, C_ERROR, C_WARNING,
    C_TEXT, C_TEXT_MUTED, C_BORDER, C_ROW_ODD, C_ROW_EVEN,
    FONT_H3, FONT_BODY, FONT_SMALL, FONT_BTN,
)


class HistorialView(tk.Frame):
    """Vista de historial de ventas con capacidad de ver detalles y anular."""

    def __init__(self, parent, controller) -> None:
        super().__init__(parent, bg=C_MAIN_BG)
        self._ctrl = controller

        from services.venta_service    import VentaService
        from services.producto_service import ProductoService
        from services.cliente_service  import ClienteService

        self._svc      = VentaService()
        self._prod_svc = ProductoService()
        self._cli_svc  = ClienteService()

        self._build_ui()

    def _build_ui(self) -> None:
        self.columnconfigure(0, weight=1)
        self.rowconfigure(1, weight=1)

        # ── Toolbar ───────────────────────────────────────────────────────────
        bar = tk.Frame(self, bg=C_CARD_BG, pady=10, padx=20)
        bar.grid(row=0, column=0, sticky="ew")

        tk.Label(bar, text="Filtrar por fecha:", font=FONT_BODY,
                 bg=C_CARD_BG, fg=C_TEXT).pack(side="left")

        self._fecha_var = tk.StringVar(
            value=datetime.now().strftime("%Y-%m-%d")
        )
        tk.Entry(bar, textvariable=self._fecha_var, font=FONT_BODY,
                 width=12, relief="solid", bd=1).pack(side="left", padx=6)

        tk.Button(bar, text="Buscar", font=FONT_SMALL,
                  bg=C_ACCENT, fg="white", bd=0, padx=10, pady=4,
                  cursor="hand2", command=self._load_table).pack(side="left", padx=(0, 16))

        tk.Button(bar, text="Mostrar todas", font=FONT_SMALL,
                  bg="#f1f5f9", fg=C_TEXT_MUTED, bd=0, padx=10, pady=4,
                  cursor="hand2", command=self._show_all).pack(side="left")

        self._count_lbl = tk.Label(bar, text="0 ventas",
                                    font=FONT_SMALL, bg=C_CARD_BG, fg=C_TEXT_MUTED)
        self._count_lbl.pack(side="right", padx=16)

        # ── Tabla principal de ventas ──────────────────────────────────────────
        main = tk.Frame(self, bg=C_MAIN_BG)
        main.grid(row=1, column=0, sticky="nsew", padx=16, pady=12)
        main.columnconfigure(0, weight=1)
        main.rowconfigure(0, weight=3)
        main.rowconfigure(1, weight=2)

        # Ventas
        vc = tk.Frame(main, bg=C_CARD_BG)
        vc.grid(row=0, column=0, sticky="nsew", pady=(0, 8))
        vc.rowconfigure(1, weight=1)
        vc.columnconfigure(0, weight=1)

        tk.Label(vc, text="Ventas", font=FONT_H3,
                 bg=C_CARD_BG, fg=C_TEXT, padx=16, pady=10).grid(
            row=0, column=0, sticky="w"
        )
        tk.Frame(vc, bg=C_BORDER, height=1).grid(row=0, column=0, sticky="ews")

        vtf = tk.Frame(vc, bg=C_CARD_BG)
        vtf.grid(row=1, column=0, sticky="nsew")
        vtf.rowconfigure(0, weight=1)
        vtf.columnconfigure(0, weight=1)

        style = ttk.Style()
        style.configure("Hist.Treeview", rowheight=30, font=FONT_BODY,
                         background=C_CARD_BG, fieldbackground=C_CARD_BG)
        style.configure("Hist.Treeview.Heading",
                         font=("Segoe UI", 9, "bold"), background="#f1f5f9")
        style.map("Hist.Treeview", background=[("selected", "#dbeafe")])

        vcols = ("id", "fecha", "cliente", "subtotal", "igv", "total")
        self._v_tree = ttk.Treeview(vtf, columns=vcols, show="headings",
                                     style="Hist.Treeview")
        vconfig = {
            "id":       ("# Venta", 70,  "center"),
            "fecha":    ("Fecha",   160, "w"),
            "cliente":  ("Cliente", 100, "center"),
            "subtotal": ("Subtotal", 100, "e"),
            "igv":      ("IGV",      80, "e"),
            "total":    ("Total",   110, "e"),
        }
        for col, (hd, w, anch) in vconfig.items():
            self._v_tree.heading(col, text=hd)
            self._v_tree.column(col, width=w, anchor=anch)

        self._v_tree.tag_configure("odd",  background=C_ROW_ODD)
        self._v_tree.tag_configure("even", background=C_ROW_EVEN)

        vsb = ttk.Scrollbar(vtf, orient="vertical", command=self._v_tree.yview)
        self._v_tree.configure(yscrollcommand=vsb.set)
        self._v_tree.grid(row=0, column=0, sticky="nsew")
        vsb.grid(row=0, column=1, sticky="ns")
        self._v_tree.bind("<<TreeviewSelect>>", self._on_venta_select)

        # Botón anular venta
        btn_bar = tk.Frame(vc, bg=C_CARD_BG, padx=16, pady=6)
        btn_bar.grid(row=2, column=0, sticky="ew")
        tk.Button(btn_bar, text="✕ Anular Venta seleccionada", font=FONT_SMALL,
                  bg="#fee2e2", fg=C_ERROR, bd=0, padx=12, pady=4,
                  cursor="hand2", command=self._anular_venta).pack(side="right")

        # Detalles
        dc = tk.Frame(main, bg=C_CARD_BG)
        dc.grid(row=1, column=0, sticky="nsew")
        dc.rowconfigure(1, weight=1)
        dc.columnconfigure(0, weight=1)

        tk.Label(dc, text="Detalle de la Venta Seleccionada", font=FONT_H3,
                 bg=C_CARD_BG, fg=C_TEXT, padx=16, pady=10).grid(
            row=0, column=0, sticky="w"
        )
        tk.Frame(dc, bg=C_BORDER, height=1).grid(row=0, column=0, sticky="ews")

        dtf = tk.Frame(dc, bg=C_CARD_BG)
        dtf.grid(row=1, column=0, sticky="nsew")
        dtf.rowconfigure(0, weight=1)
        dtf.columnconfigure(0, weight=1)

        dcols = ("prod_id", "nombre", "cantidad", "precio", "subtotal")
        self._d_tree = ttk.Treeview(dtf, columns=dcols, show="headings",
                                     style="Hist.Treeview")
        dconfig = {
            "prod_id":  ("Prod. ID", 80,  "center"),
            "nombre":   ("Producto", 260, "w"),
            "cantidad": ("Cant.",    70,  "center"),
            "precio":   ("P.Unit.",  100, "e"),
            "subtotal": ("Subtotal", 110, "e"),
        }
        for col, (hd, w, anch) in dconfig.items():
            self._d_tree.heading(col, text=hd)
            self._d_tree.column(col, width=w, anchor=anch)

        dsb = ttk.Scrollbar(dtf, orient="vertical", command=self._d_tree.yview)
        self._d_tree.configure(yscrollcommand=dsb.set)
        self._d_tree.grid(row=0, column=0, sticky="nsew")
        dsb.grid(row=0, column=1, sticky="ns")

    def on_show(self) -> None:
        self._load_table()

    def _load_table(self) -> None:
        for r in self._v_tree.get_children():
            self._v_tree.delete(r)

        fecha = self._fecha_var.get().strip()
        ventas = self._svc.listar_ventas()
        if fecha:
            ventas = [v for v in ventas if v.fecha.startswith(fecha)]

        for i, v in enumerate(ventas):
            tag = "odd" if i % 2 == 0 else "even"
            self._v_tree.insert(
                "", "end", iid=str(v.id), tags=(tag,),
                values=(
                    f"#{v.id:04d}", v.fecha,
                    f"CLI-{v.cliente_id:03d}" if v.cliente_id else "Anónimo",
                    f"S/ {v.subtotal:.2f}", f"S/ {v.igv:.2f}", f"S/ {v.total:.2f}",
                ),
            )

        self._count_lbl.config(text=f"{len(ventas)} venta(s)")
        for r in self._d_tree.get_children():
            self._d_tree.delete(r)

    def _show_all(self) -> None:
        self._fecha_var.set("")
        self._load_table()

    def _on_venta_select(self, _=None) -> None:
        sel = self._v_tree.selection()
        if not sel:
            return
        venta_id = int(sel[0].strip("#"))
        for r in self._d_tree.get_children():
            self._d_tree.delete(r)

        detalles = self._svc.obtener_detalles(venta_id)
        for i, d in enumerate(detalles):
            prod = self._prod_svc.obtener(d.producto_id)
            nombre = prod.nombre if prod else f"ID:{d.producto_id}"
            tag = "odd" if i % 2 == 0 else "even"
            self._d_tree.insert(
                "", "end", tags=(tag,),
                values=(
                    d.producto_id, nombre,
                    d.cantidad,
                    f"S/ {d.precio_unitario:.2f}",
                    f"S/ {d.subtotal:.2f}",
                ),
            )

    def _anular_venta(self) -> None:
        sel = self._v_tree.selection()
        if not sel:
            messagebox.showwarning("Aviso", "Seleccione una venta para anular.")
            return
        venta_id = int(sel[0].strip("#"))
        if not messagebox.askyesno(
            "Anular Venta",
            f"¿Anular la venta #{venta_id:04d}?\n"
            "Se restaurará el stock de los productos.",
        ):
            return
        if self._svc.anular_venta(venta_id):
            messagebox.showinfo("Éxito", f"Venta #{venta_id:04d} anulada.")
            self._load_table()
        else:
            messagebox.showerror("Error", "No se pudo anular la venta.")
