"""
productos_view.py — CRUD completo de productos.

Layout:
┌─────────────────────────────────────────────────────────────────────────┐
│  [Buscar: ______________________]  [Limpiar búsqueda]  [Compactar]      │
├───────────────────────┬─────────────────────────────────────────────────┤
│  FORMULARIO           │  LISTA DE PRODUCTOS (Treeview)                  │
│  ─────────────────    │  ──────────────────────────────────────────     │
│  ID:       [auto]     │  ID  Nombre  Precio  Stock  Categoría           │
│  Nombre:   [______]   │  1   Agua    1.50    100    Bebidas              │
│  Precio:   [______]   │  2   Pan     0.50    50     Panadería            │
│  Stock:    [______]   │  ...                                            │
│  Categoría:[______]   │                                                 │
│                       │                                                 │
│  [Nuevo] [Guardar]    │                                                 │
│  [Eliminar] [Limpiar] │                                                 │
└───────────────────────┴─────────────────────────────────────────────────┘
"""
import tkinter as tk
from tkinter import ttk, messagebox
from typing import Optional

from utils.config import (
    C_MAIN_BG, C_CARD_BG, C_ACCENT, C_SUCCESS, C_ERROR, C_WARNING,
    C_TEXT, C_TEXT_MUTED, C_BORDER, C_ROW_ODD, C_ROW_EVEN,
    FONT_H3, FONT_BODY, FONT_SMALL, FONT_BTN,
)


class ProductosView(tk.Frame):
    """Vista de mantenimiento de productos con CRUD completo."""

    def __init__(self, parent, controller) -> None:
        super().__init__(parent, bg=C_MAIN_BG)
        self._ctrl       = controller
        self._editing_id: Optional[int] = None

        from services.producto_service import ProductoService
        self._svc = ProductoService()

        self._build_ui()

    # ════════════════════════════════════════════════════════════════════════
    # UI
    # ════════════════════════════════════════════════════════════════════════

    def _build_ui(self) -> None:
        self.columnconfigure(0, weight=1)
        self.rowconfigure(1, weight=1)
        self._build_toolbar()
        self._build_main()

    def _build_toolbar(self) -> None:
        bar = tk.Frame(self, bg=C_CARD_BG, pady=10, padx=20)
        bar.grid(row=0, column=0, sticky="ew")

        tk.Label(bar, text="Buscar:", font=FONT_BODY,
                 bg=C_CARD_BG, fg=C_TEXT).pack(side="left")
        self._search_var = tk.StringVar()
        self._search_var.trace_add("write", lambda *_: self._on_search())
        entry = tk.Entry(bar, textvariable=self._search_var,
                         font=FONT_BODY, width=28,
                         relief="solid", bd=1)
        entry.pack(side="left", padx=(6, 12))

        tk.Button(
            bar, text="✕ Limpiar", font=FONT_SMALL,
            bg="#f1f5f9", fg=C_TEXT_MUTED, bd=0, padx=10, pady=4,
            cursor="hand2", command=self._clear_search,
        ).pack(side="left", padx=(0, 12))

        tk.Button(
            bar, text="⬡ Compactar Archivo", font=FONT_SMALL,
            bg="#f1f5f9", fg=C_TEXT_MUTED, bd=0, padx=10, pady=4,
            cursor="hand2", command=self._compact,
        ).pack(side="right")

        self._count_lbl = tk.Label(bar, text="0 productos",
                                    font=FONT_SMALL, bg=C_CARD_BG, fg=C_TEXT_MUTED)
        self._count_lbl.pack(side="right", padx=16)

    def _build_main(self) -> None:
        paned = tk.PanedWindow(self, orient="horizontal",
                               bg=C_MAIN_BG, sashwidth=6,
                               sashrelief="flat", sashpad=4)
        paned.grid(row=1, column=0, sticky="nsew", padx=16, pady=12)

        self._build_form(paned)
        self._build_table(paned)

    def _build_form(self, parent) -> None:
        form_card = tk.Frame(parent, bg=C_CARD_BG)
        parent.add(form_card, minsize=280, width=300)

        # Título del formulario
        tk.Frame(form_card, bg=C_ACCENT, height=4).pack(fill="x")
        hdr = tk.Frame(form_card, bg=C_CARD_BG, pady=12, padx=16)
        hdr.pack(fill="x")
        self._form_title = tk.Label(hdr, text="Nuevo Producto",
                                     font=FONT_H3, bg=C_CARD_BG, fg=C_TEXT)
        self._form_title.pack(anchor="w")

        tk.Frame(form_card, bg=C_BORDER, height=1).pack(fill="x")

        # Campos del formulario
        fields_frame = tk.Frame(form_card, bg=C_CARD_BG, padx=16, pady=12)
        fields_frame.pack(fill="x")

        # ID (solo lectura)
        self._id_var   = tk.StringVar(value="(auto)")
        self._nom_var  = tk.StringVar()
        self._pre_var  = tk.StringVar()
        self._stk_var  = tk.StringVar()
        self._cat_var  = tk.StringVar()

        campos = [
            ("ID",         self._id_var,  False),
            ("Nombre *",   self._nom_var, True),
            ("Precio (S/)*",self._pre_var, True),
            ("Stock *",    self._stk_var, True),
            ("Categoría",  self._cat_var, True),
        ]
        self._entries = {}
        for label, var, editable in campos:
            f = tk.Frame(fields_frame, bg=C_CARD_BG, pady=5)
            f.pack(fill="x")
            tk.Label(f, text=label, font=FONT_SMALL,
                     bg=C_CARD_BG, fg=C_TEXT_MUTED, width=14, anchor="w").pack(side="left")
            state = "normal" if editable else "readonly"
            e = tk.Entry(f, textvariable=var, font=FONT_BODY,
                         relief="solid", bd=1, state=state)
            e.pack(side="left", fill="x", expand=True)
            self._entries[label] = e

        # Categorías sugeridas
        categorias = ["Bebidas", "Panadería", "Lácteos", "Snacks",
                      "Limpieza", "Verduras", "Frutas", "Carnes", "Otros"]
        cat_combo = ttk.Combobox(fields_frame, textvariable=self._cat_var,
                                  values=categorias, font=FONT_BODY)
        cat_combo.pack(fill="x", pady=(0, 4))

        # Nota de campos obligatorios
        tk.Label(fields_frame, text="* Campos obligatorios",
                 font=FONT_SMALL, bg=C_CARD_BG, fg="#94a3b8").pack(anchor="w")

        # Mensajes de validación
        self._msg_var = tk.StringVar()
        self._msg_lbl = tk.Label(
            fields_frame, textvariable=self._msg_var,
            font=FONT_SMALL, bg=C_CARD_BG, fg=C_ERROR, wraplength=240,
        )
        self._msg_lbl.pack(anchor="w", pady=(4, 0))

        # Botones de acción
        btn_frame = tk.Frame(form_card, bg=C_CARD_BG, padx=16, pady=12)
        btn_frame.pack(fill="x")

        buttons = [
            ("+ Nuevo",    self._new,    C_ACCENT),
            ("✓ Guardar",  self._save,   C_SUCCESS),
            ("✕ Eliminar", self._delete, C_ERROR),
            ("⟳ Limpiar",  self._clear,  "#64748b"),
        ]
        for i, (txt, cmd, color) in enumerate(buttons):
            tk.Button(
                btn_frame, text=txt, font=FONT_BTN,
                bg=color, fg="white", bd=0, padx=8, pady=7,
                cursor="hand2", command=cmd,
            ).grid(row=i // 2, column=i % 2, padx=4, pady=3, sticky="ew")
        btn_frame.columnconfigure(0, weight=1)
        btn_frame.columnconfigure(1, weight=1)

    def _build_table(self, parent) -> None:
        table_card = tk.Frame(parent, bg=C_CARD_BG)
        parent.add(table_card, minsize=500)

        # Estilo del Treeview
        style = ttk.Style()
        style.configure("Prod.Treeview", rowheight=30, font=FONT_BODY,
                         background=C_CARD_BG, fieldbackground=C_CARD_BG,
                         borderwidth=0)
        style.configure("Prod.Treeview.Heading",
                         font=("Segoe UI", 9, "bold"),
                         background="#f1f5f9", relief="flat")
        style.map("Prod.Treeview", background=[("selected", "#dbeafe")])

        cols = ("id", "nombre", "precio", "stock", "categoria")
        self._tree = ttk.Treeview(table_card, columns=cols,
                                   show="headings", style="Prod.Treeview")

        config = {
            "id":        ("ID",        60,  "center"),
            "nombre":    ("Nombre",    200, "w"),
            "precio":    ("Precio",    90,  "center"),
            "stock":     ("Stock",     80,  "center"),
            "categoria": ("Categoría", 130, "w"),
        }
        for col, (hd, w, anch) in config.items():
            self._tree.heading(col, text=hd,
                                command=lambda c=col: self._sort_by(c))
            self._tree.column(col, width=w, anchor=anch, minwidth=50)

        self._tree.tag_configure("odd",   background=C_ROW_ODD)
        self._tree.tag_configure("even",  background=C_ROW_EVEN)
        self._tree.tag_configure("low",   background="#fef9c3")  # stock bajo
        self._tree.tag_configure("empty", background="#fee2e2")  # sin stock

        sb_y = ttk.Scrollbar(table_card, orient="vertical",
                              command=self._tree.yview)
        self._tree.configure(yscrollcommand=sb_y.set)

        self._tree.pack(side="left", fill="both", expand=True)
        sb_y.pack(side="right", fill="y")

        self._tree.bind("<<TreeviewSelect>>", self._on_row_select)
        self._tree.bind("<Double-1>",         self._on_row_select)

    # ════════════════════════════════════════════════════════════════════════
    # CARGA DE DATOS
    # ════════════════════════════════════════════════════════════════════════

    def on_show(self) -> None:
        """Refresca la tabla al mostrar esta vista."""
        self._load_table()

    def _load_table(self, productos=None) -> None:
        for row in self._tree.get_children():
            self._tree.delete(row)

        if productos is None:
            productos = self._svc.listar()

        for i, p in enumerate(productos):
            if p.stock == 0:
                tag = "empty"
            elif p.stock <= 5:
                tag = "low"
            else:
                tag = "odd" if i % 2 == 0 else "even"

            self._tree.insert(
                "", "end", iid=str(p.id), tags=(tag,),
                values=(
                    p.id,
                    p.nombre,
                    f"S/ {p.precio:.2f}",
                    p.stock,
                    p.categoria,
                ),
            )

        self._count_lbl.config(text=f"{len(productos)} producto(s)")

    # ════════════════════════════════════════════════════════════════════════
    # EVENTOS
    # ════════════════════════════════════════════════════════════════════════

    def _on_row_select(self, _=None) -> None:
        sel = self._tree.selection()
        if not sel:
            return
        prod_id = int(sel[0])
        p = self._svc.obtener(prod_id)
        if p:
            self._editing_id = p.id
            self._id_var.set(str(p.id))
            self._nom_var.set(p.nombre)
            self._pre_var.set(f"{p.precio:.2f}")
            self._stk_var.set(str(p.stock))
            self._cat_var.set(p.categoria)
            self._form_title.config(text=f"Editando #{p.id}")
            self._msg_var.set("")

    def _on_search(self) -> None:
        term = self._search_var.get().strip()
        if term:
            self._load_table(self._svc.buscar(term))
        else:
            self._load_table()

    def _clear_search(self) -> None:
        self._search_var.set("")
        self._load_table()

    def _sort_by(self, col: str) -> None:
        items = [(self._tree.set(c, col), c) for c in self._tree.get_children("")]
        try:
            items.sort(key=lambda x: float(x[0].replace("S/ ", "").replace(",", "")))
        except ValueError:
            items.sort(key=lambda x: x[0].lower())
        for i, (_, iid) in enumerate(items):
            self._tree.move(iid, "", i)

    # ════════════════════════════════════════════════════════════════════════
    # ACCIONES CRUD
    # ════════════════════════════════════════════════════════════════════════

    def _new(self) -> None:
        self._clear()
        self._form_title.config(text="Nuevo Producto")
        # Enfocar el campo nombre
        list(self._entries.values())[1].focus_set()

    def _save(self) -> None:
        """Crear o actualizar producto."""
        nombre = self._nom_var.get().strip()
        cat    = self._cat_var.get().strip()

        try:
            precio = float(self._pre_var.get())
            stock  = int(self._stk_var.get())
        except ValueError:
            self._show_msg("Precio y Stock deben ser valores numéricos.", C_ERROR)
            return

        if not nombre:
            self._show_msg("El nombre del producto es obligatorio.", C_ERROR)
            return

        try:
            if self._editing_id:
                self._svc.actualizar(
                    self._editing_id,
                    nombre=nombre, precio=precio,
                    stock=stock, categoria=cat,
                )
                self._show_msg(f"Producto #{self._editing_id} actualizado.", C_SUCCESS)
            else:
                p = self._svc.crear(nombre, precio, stock, cat)
                self._show_msg(f"Producto #{p.id} creado exitosamente.", C_SUCCESS)

            self._load_table()
            self._clear()
        except ValueError as e:
            self._show_msg(str(e), C_ERROR)

    def _delete(self) -> None:
        if not self._editing_id:
            self._show_msg("Seleccione un producto para eliminar.", C_WARNING)
            return
        if not messagebox.askyesno(
            "Confirmar Eliminación",
            f"¿Eliminar el producto #{self._editing_id}?\n"
            "Esta acción es lógica y el espacio quedará disponible.",
        ):
            return
        if self._svc.eliminar(self._editing_id):
            self._show_msg(f"Producto #{self._editing_id} eliminado.", C_SUCCESS)
            self._load_table()
            self._clear()
        else:
            self._show_msg("No se pudo eliminar el producto.", C_ERROR)

    def _clear(self) -> None:
        self._editing_id = None
        self._id_var.set("(auto)")
        self._nom_var.set("")
        self._pre_var.set("")
        self._stk_var.set("")
        self._cat_var.set("")
        self._form_title.config(text="Nuevo Producto")
        self._msg_var.set("")

    def _compact(self) -> None:
        if messagebox.askyesno("Compactar", "¿Compactar el archivo productos.dat?\n"
                                "Se reorganizarán los registros físicamente."):
            freed = self._svc.compactar()
            self._load_table()
            messagebox.showinfo("Compactación", f"Completada. Bytes recuperados: {freed}")

    def _show_msg(self, msg: str, color: str = C_ERROR) -> None:
        self._msg_var.set(msg)
        self._msg_lbl.config(fg=color)
        self._ctrl.set_status(msg, color)
