"""
ventas_view.py — Pantalla de Nueva Venta (POS).

Layout:
┌─────────────────────────────────────────────────────────────────────────┐
│  Cliente:  [Buscar por DNI ___________]  [Nombre del cliente encontrado]│
├─────────────────────────────────────────────────────────────────────────┤
│  Producto: [Buscar ___________]  Cantidad: [___]  [+ Agregar al carrito]│
├─────────────────────────────────────────────────────────────────────────┤
│  CARRITO DE VENTA                                                       │
│  Producto          Cant  Precio Unit   Subtotal   [Quitar]              │
│  ─────────────────────────────────────────────────────────────          │
│  Agua mineral x2   2     S/ 1.50       S/ 3.00   [×]                   │
│  Pan integral x1   1     S/ 0.50       S/ 0.50   [×]                   │
├─────────────────────────────────────────────────────────────────────────┤
│                              Subtotal:  S/  3.50                        │
│                              IGV(18%):  S/  0.63                        │
│                              TOTAL:     S/  4.13                        │
├─────────────────────────────────────────────────────────────────────────┤
│  [✕ Cancelar]                               [✓ Registrar Venta]        │
└─────────────────────────────────────────────────────────────────────────┘
"""
import tkinter as tk
from tkinter import ttk, messagebox
from typing import Dict, List, Optional, Tuple

from utils.config import (
    C_MAIN_BG, C_CARD_BG, C_ACCENT, C_SUCCESS, C_ERROR, C_WARNING,
    C_TEXT, C_TEXT_MUTED, C_BORDER, C_ROW_ODD, C_ROW_EVEN,
    FONT_H2, FONT_H3, FONT_BODY, FONT_SMALL, FONT_BTN, FONT_MONO,
    IGV_RATE,
)


class VentasView(tk.Frame):
    """Vista de punto de venta para registrar nuevas transacciones."""

    def __init__(self, parent, controller) -> None:
        super().__init__(parent, bg=C_MAIN_BG)
        self._ctrl = controller

        from services.producto_service import ProductoService
        from services.cliente_service  import ClienteService
        from services.venta_service    import VentaService

        self._prod_svc  = ProductoService()
        self._cli_svc   = ClienteService()
        self._venta_svc = VentaService()

        # Carrito: { producto_id → (nombre, cantidad, precio_unit, subtotal) }
        self._carrito: Dict[int, dict] = {}
        self._cliente_id: int = 0

        self._build_ui()

    # ════════════════════════════════════════════════════════════════════════
    # UI
    # ════════════════════════════════════════════════════════════════════════

    def _build_ui(self) -> None:
        self.columnconfigure(0, weight=1)
        self.rowconfigure(2, weight=1)  # Carrito expande

        self._build_client_panel()
        self._build_product_panel()
        self._build_cart()
        self._build_totals()
        self._build_actions()

    def _build_client_panel(self) -> None:
        panel = tk.Frame(self, bg=C_CARD_BG, padx=20, pady=12)
        panel.grid(row=0, column=0, sticky="ew", padx=16, pady=(12, 4))
        panel.columnconfigure(3, weight=1)

        tk.Label(panel, text="Cliente:", font=FONT_BODY,
                 bg=C_CARD_BG, fg=C_TEXT).grid(row=0, column=0, padx=(0, 8))

        self._dni_var  = tk.StringVar()
        self._cli_name = tk.StringVar(value="Venta anónima")

        dni_entry = tk.Entry(panel, textvariable=self._dni_var,
                             font=FONT_BODY, width=14, relief="solid", bd=1)
        dni_entry.grid(row=0, column=1, padx=(0, 8))
        dni_entry.bind("<Return>", lambda e: self._buscar_cliente())

        tk.Button(
            panel, text="Buscar", font=FONT_SMALL,
            bg=C_ACCENT, fg="white", bd=0, padx=12, pady=4,
            cursor="hand2", command=self._buscar_cliente,
        ).grid(row=0, column=2, padx=(0, 16))

        tk.Label(panel, textvariable=self._cli_name, font=FONT_BODY,
                 bg=C_CARD_BG, fg=C_TEXT_MUTED).grid(row=0, column=3, sticky="w")

        tk.Button(
            panel, text="✕ Quitar cliente", font=FONT_SMALL,
            bg="#f1f5f9", fg=C_TEXT_MUTED, bd=0, padx=10, pady=4,
            cursor="hand2", command=self._clear_client,
        ).grid(row=0, column=4, padx=(8, 0))

    def _build_product_panel(self) -> None:
        panel = tk.Frame(self, bg=C_CARD_BG, padx=20, pady=12)
        panel.grid(row=1, column=0, sticky="ew", padx=16, pady=4)

        tk.Label(panel, text="Producto:", font=FONT_BODY,
                 bg=C_CARD_BG, fg=C_TEXT).pack(side="left", padx=(0, 8))

        self._prod_search_var = tk.StringVar()
        self._prod_search_var.trace_add("write", self._on_prod_search)
        prod_entry = tk.Entry(panel, textvariable=self._prod_search_var,
                              font=FONT_BODY, width=28, relief="solid", bd=1)
        prod_entry.pack(side="left", padx=(0, 4))

        # Listbox de sugerencias de productos
        self._suggest_frame = tk.Frame(self, bg="white", relief="solid", bd=1)
        self._suggest_list  = tk.Listbox(
            self._suggest_frame, font=FONT_BODY, height=5,
            bg="white", selectbackground="#dbeafe",
            relief="flat", bd=0, cursor="hand2",
        )
        self._suggest_list.pack(fill="both", expand=True)
        self._suggest_list.bind("<<ListboxSelect>>", self._on_suggest_select)

        self._prod_selected_id: Optional[int] = None
        self._prod_selected_nom = tk.StringVar(value="")
        self._prod_selected_pre = tk.StringVar(value="")

        tk.Label(panel, textvariable=self._prod_selected_nom, font=FONT_SMALL,
                 bg=C_CARD_BG, fg=C_ACCENT).pack(side="left", padx=8)

        tk.Label(panel, text="Cantidad:", font=FONT_BODY,
                 bg=C_CARD_BG, fg=C_TEXT).pack(side="left", padx=(8, 4))

        self._cant_var = tk.StringVar(value="1")
        cant_entry = tk.Entry(panel, textvariable=self._cant_var,
                              font=FONT_BODY, width=5, relief="solid", bd=1,
                              justify="center")
        cant_entry.pack(side="left", padx=(0, 8))
        cant_entry.bind("<Return>", lambda e: self._agregar_al_carrito())

        tk.Button(
            panel, text="+ Agregar", font=FONT_BTN,
            bg=C_SUCCESS, fg="white", bd=0, padx=14, pady=6,
            cursor="hand2", command=self._agregar_al_carrito,
        ).pack(side="left")

    def _build_cart(self) -> None:
        cart_card = tk.Frame(self, bg=C_CARD_BG)
        cart_card.grid(row=2, column=0, sticky="nsew", padx=16, pady=4)
        cart_card.columnconfigure(0, weight=1)
        cart_card.rowconfigure(1, weight=1)

        hdr = tk.Frame(cart_card, bg=C_CARD_BG, padx=16, pady=10)
        hdr.grid(row=0, column=0, sticky="ew")
        tk.Label(hdr, text="🛒 Carrito de Venta", font=FONT_H3,
                 bg=C_CARD_BG, fg=C_TEXT).pack(side="left")
        tk.Button(
            hdr, text="✕ Vaciar carrito", font=FONT_SMALL,
            bg="#fee2e2", fg=C_ERROR, bd=0, padx=10, pady=3,
            cursor="hand2", command=self._vaciar_carrito,
        ).pack(side="right")

        tk.Frame(cart_card, bg=C_BORDER, height=1).grid(row=0, column=0, sticky="ews")

        tv_frame = tk.Frame(cart_card, bg=C_CARD_BG)
        tv_frame.grid(row=1, column=0, sticky="nsew")
        tv_frame.rowconfigure(0, weight=1)
        tv_frame.columnconfigure(0, weight=1)

        style = ttk.Style()
        style.configure("Cart.Treeview", rowheight=34, font=FONT_BODY,
                         background=C_CARD_BG, fieldbackground=C_CARD_BG)
        style.configure("Cart.Treeview.Heading",
                         font=("Segoe UI", 9, "bold"), background="#f1f5f9")
        style.map("Cart.Treeview", background=[("selected", "#dbeafe")])

        cols = ("prod_id", "nombre", "cantidad", "precio", "subtotal")
        self._tree = ttk.Treeview(tv_frame, columns=cols,
                                   show="headings", style="Cart.Treeview")
        config = {
            "prod_id":  ("ID",           60,  "center"),
            "nombre":   ("Producto",     260, "w"),
            "cantidad": ("Cant.",        70,  "center"),
            "precio":   ("Precio Unit.", 110, "center"),
            "subtotal": ("Subtotal",     110, "center"),
        }
        for col, (hd, w, anch) in config.items():
            self._tree.heading(col, text=hd)
            self._tree.column(col, width=w, anchor=anch)

        self._tree.tag_configure("odd",  background=C_ROW_ODD)
        self._tree.tag_configure("even", background=C_ROW_EVEN)

        sb = ttk.Scrollbar(tv_frame, orient="vertical", command=self._tree.yview)
        self._tree.configure(yscrollcommand=sb.set)
        self._tree.grid(row=0, column=0, sticky="nsew")
        sb.grid(row=0, column=1, sticky="ns")
        self._tree.bind("<Delete>", lambda e: self._quitar_seleccionado())

        # Botón quitar en la tabla
        self._tree.bind("<<TreeviewSelect>>", self._on_cart_select)

    def _build_totals(self) -> None:
        totals = tk.Frame(self, bg=C_CARD_BG)
        totals.grid(row=3, column=0, sticky="ew", padx=16, pady=4)

        inner = tk.Frame(totals, bg=C_CARD_BG, padx=20, pady=12)
        inner.pack(side="right")

        self._sub_var   = tk.StringVar(value="S/ 0.00")
        self._igv_var   = tk.StringVar(value="S/ 0.00")
        self._total_var = tk.StringVar(value="S/ 0.00")

        rows = [
            ("Subtotal (sin IGV):", self._sub_var,   FONT_BODY, C_TEXT),
            (f"IGV ({IGV_RATE*100:.0f}%):",          self._igv_var,   FONT_BODY, C_TEXT_MUTED),
            ("TOTAL A PAGAR:",       self._total_var, FONT_H2,   C_ACCENT),
        ]
        for i, (label, var, font, color) in enumerate(rows):
            tk.Label(inner, text=label, font=font,
                     bg=C_CARD_BG, fg=C_TEXT_MUTED).grid(
                row=i, column=0, sticky="e", padx=(0, 16), pady=2
            )
            tk.Label(inner, textvariable=var, font=font,
                     bg=C_CARD_BG, fg=color, width=12).grid(
                row=i, column=1, sticky="e", pady=2
            )

        # Separador sobre el total
        tk.Frame(inner, bg=C_BORDER, height=1).grid(
            row=2, column=0, columnspan=2, sticky="ew", pady=(4, 0)
        )

        # Botón quitar ítem
        self._btn_remove = tk.Button(
            totals, text="✕ Quitar ítem", font=FONT_SMALL,
            bg="#fee2e2", fg=C_ERROR, bd=0, padx=12, pady=6,
            cursor="hand2", command=self._quitar_seleccionado, state="disabled",
        )
        self._btn_remove.pack(side="left", padx=20)

    def _build_actions(self) -> None:
        actions = tk.Frame(self, bg=C_CARD_BG, pady=12, padx=20)
        actions.grid(row=4, column=0, sticky="ew", padx=16, pady=(0, 16))

        self._msg_var = tk.StringVar()
        tk.Label(actions, textvariable=self._msg_var, font=FONT_SMALL,
                 bg=C_CARD_BG, fg=C_ERROR).pack(side="left")

        tk.Button(
            actions, text="✕ Cancelar", font=FONT_BTN,
            bg="#f1f5f9", fg=C_TEXT, bd=0, padx=20, pady=9,
            cursor="hand2", command=self._cancelar,
        ).pack(side="right", padx=(8, 0))

        tk.Button(
            actions, text="✓ Registrar Venta", font=FONT_BTN,
            bg=C_SUCCESS, fg="white", bd=0, padx=24, pady=9,
            cursor="hand2", command=self._registrar_venta,
        ).pack(side="right")

    # ════════════════════════════════════════════════════════════════════════
    # LÓGICA DE LA VISTA
    # ════════════════════════════════════════════════════════════════════════

    def on_show(self) -> None:
        # Recargar índices para ver productos y clientes creados en otras vistas
        self._prod_svc._fm.index_manager.reload()
        self._cli_svc._fm.index_manager.reload()

    def _buscar_cliente(self) -> None:
        dni = self._dni_var.get().strip()
        if not dni:
            return
        cli = self._cli_svc.buscar_por_dni(dni)
        if cli:
            self._cliente_id = cli.id
            self._cli_name.set(f"✓  {cli.nombre}  (ID: {cli.id})")
        else:
            self._cliente_id = 0
            self._cli_name.set("⚠ Cliente no encontrado")

    def _clear_client(self) -> None:
        self._cliente_id = 0
        self._dni_var.set("")
        self._cli_name.set("Venta anónima")

    def _on_prod_search(self, *_) -> None:
        term = self._prod_search_var.get().strip()
        self._suggest_list.delete(0, "end")
        self._prod_selected_id = None
        self._prod_selected_nom.set("")

        if len(term) < 2:
            self._suggest_frame.place_forget()
            return

        productos = self._prod_svc.buscar(term)[:8]
        if not productos:
            self._suggest_frame.place_forget()
            return

        for p in productos:
            self._suggest_list.insert("end",
                f"[{p.id}] {p.nombre} — S/ {p.precio:.2f}  (stock: {p.stock})")

        # Posicionar el listbox bajo el entry
        widget = self._prod_search_var
        x = 16 + 20 + 70  # aprox posición x del entry
        y = 16 + 12 + 52 + 12 + 46  # aprox posición y
        self._suggest_frame.place(x=x, y=y, width=380)

    def _on_suggest_select(self, _=None) -> None:
        sel = self._suggest_list.curselection()
        if not sel:
            return
        txt = self._suggest_list.get(sel[0])
        # Extraer ID del formato "[ID] nombre ..."
        prod_id = int(txt.split("]")[0].strip("["))
        p = self._prod_svc.obtener(prod_id)
        if p:
            self._prod_selected_id = p.id
            self._prod_selected_nom.set(f"✓ {p.nombre}")
            self._prod_selected_pre.set(f"S/ {p.precio:.2f}")
            self._prod_search_var.set(p.nombre)
            self._suggest_frame.place_forget()

    def _agregar_al_carrito(self) -> None:
        if not self._prod_selected_id:
            # Intentar buscar si hay texto exacto
            term = self._prod_search_var.get().strip()
            if term:
                productos = self._prod_svc.buscar(term)
                if len(productos) == 1:
                    self._prod_selected_id = productos[0].id
                else:
                    self._show_msg("Seleccione un producto de la lista.", C_WARNING)
                    return
            else:
                self._show_msg("Ingrese un producto para agregar.", C_WARNING)
                return

        try:
            cant = int(self._cant_var.get())
            if cant <= 0:
                raise ValueError
        except ValueError:
            self._show_msg("La cantidad debe ser un número entero positivo.", C_ERROR)
            return

        prod = self._prod_svc.obtener(self._prod_selected_id)
        if not prod:
            self._show_msg("Producto no encontrado.", C_ERROR)
            return

        # Calcular total en carrito para este producto
        en_carrito = self._carrito.get(prod.id, {}).get("cantidad", 0)
        if prod.stock < (en_carrito + cant):
            self._show_msg(
                f"Stock insuficiente: disponible={prod.stock}, en carrito={en_carrito}",
                C_ERROR,
            )
            return

        if prod.id in self._carrito:
            self._carrito[prod.id]["cantidad"] += cant
            self._carrito[prod.id]["subtotal"] = round(
                self._carrito[prod.id]["cantidad"] * prod.precio, 2
            )
        else:
            self._carrito[prod.id] = {
                "nombre":   prod.nombre,
                "cantidad": cant,
                "precio":   prod.precio,
                "subtotal": round(prod.precio * cant, 2),
            }

        self._refresh_cart()
        self._prod_search_var.set("")
        self._prod_selected_id = None
        self._prod_selected_nom.set("")
        self._cant_var.set("1")
        self._show_msg(f"✓ {prod.nombre} × {cant} agregado.", C_SUCCESS)

    def _refresh_cart(self) -> None:
        """Actualiza la tabla del carrito y los totales."""
        for r in self._tree.get_children():
            self._tree.delete(r)

        subtotal = 0.0
        for i, (pid, item) in enumerate(self._carrito.items()):
            tag = "odd" if i % 2 == 0 else "even"
            self._tree.insert(
                "", "end", iid=str(pid), tags=(tag,),
                values=(
                    pid,
                    item["nombre"],
                    item["cantidad"],
                    f"S/ {item['precio']:.2f}",
                    f"S/ {item['subtotal']:.2f}",
                ),
            )
            subtotal += item["subtotal"]

        subtotal = round(subtotal, 2)
        igv      = round(subtotal * IGV_RATE, 2)
        total    = round(subtotal + igv, 2)

        self._sub_var.set(f"S/ {subtotal:.2f}")
        self._igv_var.set(f"S/ {igv:.2f}")
        self._total_var.set(f"S/ {total:.2f}")

    def _on_cart_select(self, _=None) -> None:
        sel = self._tree.selection()
        self._btn_remove.config(state="normal" if sel else "disabled")

    def _quitar_seleccionado(self) -> None:
        sel = self._tree.selection()
        if not sel:
            return
        pid = int(sel[0])
        if pid in self._carrito:
            nombre = self._carrito[pid]["nombre"]
            del self._carrito[pid]
            self._refresh_cart()
            self._show_msg(f"'{nombre}' removido del carrito.", C_WARNING)

    def _vaciar_carrito(self) -> None:
        if not self._carrito:
            return
        if messagebox.askyesno("Vaciar carrito", "¿Vaciar todo el carrito?"):
            self._carrito.clear()
            self._refresh_cart()
            self._show_msg("Carrito vaciado.", C_WARNING)

    def _registrar_venta(self) -> None:
        if not self._carrito:
            self._show_msg("El carrito está vacío.", C_WARNING)
            return

        items = [
            (pid, item["cantidad"])
            for pid, item in self._carrito.items()
        ]

        try:
            venta = self._venta_svc.crear_venta(items, self._cliente_id)
        except Exception as e:
            self._show_msg(f"Error: {e}", C_ERROR)
            return

        if venta is None:
            self._show_msg("No se pudo registrar la venta (verifique el stock).",
                           C_ERROR)
            return

        messagebox.showinfo(
            "Venta Registrada",
            f"✓ Venta #{venta.id:04d} registrada exitosamente\n\n"
            f"Fecha:    {venta.fecha}\n"
            f"Subtotal: S/ {venta.subtotal:.2f}\n"
            f"IGV 18%:  S/ {venta.igv:.2f}\n"
            f"Total:    S/ {venta.total:.2f}",
        )
        self._ctrl.set_status(
            f"Venta #{venta.id} registrada — S/ {venta.total:.2f}", C_SUCCESS
        )
        self._cancelar()

    def _cancelar(self) -> None:
        self._carrito.clear()
        self._refresh_cart()
        self._clear_client()
        self._prod_search_var.set("")
        self._prod_selected_id = None
        self._prod_selected_nom.set("")
        self._cant_var.set("1")
        self._msg_var.set("")

    def _show_msg(self, msg: str, color: str = C_ERROR) -> None:
        self._msg_var.set(msg)
        self._ctrl.set_status(msg, color)
