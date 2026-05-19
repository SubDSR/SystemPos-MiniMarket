"""
clientes_view.py — CRUD completo de clientes.
"""
import tkinter as tk
from tkinter import ttk, messagebox
from typing import Optional

from utils.config import (
    C_MAIN_BG, C_CARD_BG, C_ACCENT, C_SUCCESS, C_ERROR, C_WARNING,
    C_TEXT, C_TEXT_MUTED, C_BORDER, C_ROW_ODD, C_ROW_EVEN,
    FONT_H3, FONT_BODY, FONT_SMALL, FONT_BTN,
)


class ClientesView(tk.Frame):
    """Vista de mantenimiento de clientes con CRUD completo."""

    def __init__(self, parent, controller) -> None:
        super().__init__(parent, bg=C_MAIN_BG)
        self._ctrl       = controller
        self._editing_id: Optional[int] = None

        from services.cliente_service import ClienteService
        self._svc = ClienteService()

        self._build_ui()

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
        tk.Entry(bar, textvariable=self._search_var, font=FONT_BODY, width=28,
                 relief="solid", bd=1).pack(side="left", padx=(6, 12))

        tk.Button(
            bar, text="✕ Limpiar", font=FONT_SMALL,
            bg="#f1f5f9", fg=C_TEXT_MUTED, bd=0, padx=10, pady=4,
            cursor="hand2", command=self._clear_search,
        ).pack(side="left")

        self._count_lbl = tk.Label(bar, text="0 clientes",
                                    font=FONT_SMALL, bg=C_CARD_BG, fg=C_TEXT_MUTED)
        self._count_lbl.pack(side="right", padx=16)

    def _build_main(self) -> None:
        paned = tk.PanedWindow(self, orient="horizontal",
                               bg=C_MAIN_BG, sashwidth=6)
        paned.grid(row=1, column=0, sticky="nsew", padx=16, pady=12)
        self._build_form(paned)
        self._build_table(paned)

    def _build_form(self, parent) -> None:
        form = tk.Frame(parent, bg=C_CARD_BG)
        parent.add(form, minsize=280, width=300)

        tk.Frame(form, bg="#8b5cf6", height=4).pack(fill="x")
        hdr = tk.Frame(form, bg=C_CARD_BG, pady=12, padx=16)
        hdr.pack(fill="x")
        self._form_title = tk.Label(hdr, text="Nuevo Cliente",
                                     font=FONT_H3, bg=C_CARD_BG, fg=C_TEXT)
        self._form_title.pack(anchor="w")
        tk.Frame(form, bg=C_BORDER, height=1).pack(fill="x")

        ff = tk.Frame(form, bg=C_CARD_BG, padx=16, pady=12)
        ff.pack(fill="x")

        self._id_var   = tk.StringVar(value="(auto)")
        self._nom_var  = tk.StringVar()
        self._dni_var  = tk.StringVar()
        self._tel_var  = tk.StringVar()
        self._email_var = tk.StringVar()

        campos = [
            ("ID",          self._id_var,    False),
            ("Nombre *",    self._nom_var,   True),
            ("DNI *",       self._dni_var,   True),
            ("Teléfono",    self._tel_var,   True),
            ("Email",       self._email_var, True),
        ]
        for label, var, editable in campos:
            f = tk.Frame(ff, bg=C_CARD_BG, pady=5)
            f.pack(fill="x")
            tk.Label(f, text=label, font=FONT_SMALL,
                     bg=C_CARD_BG, fg=C_TEXT_MUTED, width=12, anchor="w").pack(side="left")
            state = "normal" if editable else "readonly"
            tk.Entry(f, textvariable=var, font=FONT_BODY,
                     relief="solid", bd=1, state=state).pack(side="left", fill="x", expand=True)

        self._msg_var = tk.StringVar()
        tk.Label(ff, textvariable=self._msg_var, font=FONT_SMALL,
                 bg=C_CARD_BG, fg=C_ERROR, wraplength=240).pack(anchor="w", pady=(6, 0))

        bf = tk.Frame(form, bg=C_CARD_BG, padx=16, pady=12)
        bf.pack(fill="x")
        buttons = [
            ("+ Nuevo",    self._new,    C_ACCENT),
            ("✓ Guardar",  self._save,   C_SUCCESS),
            ("✕ Eliminar", self._delete, C_ERROR),
            ("⟳ Limpiar",  self._clear,  "#64748b"),
        ]
        for i, (txt, cmd, color) in enumerate(buttons):
            tk.Button(
                bf, text=txt, font=FONT_BTN,
                bg=color, fg="white", bd=0, padx=8, pady=7,
                cursor="hand2", command=cmd,
            ).grid(row=i // 2, column=i % 2, padx=4, pady=3, sticky="ew")
        bf.columnconfigure(0, weight=1)
        bf.columnconfigure(1, weight=1)

    def _build_table(self, parent) -> None:
        tc = tk.Frame(parent, bg=C_CARD_BG)
        parent.add(tc, minsize=500)

        style = ttk.Style()
        style.configure("Cli.Treeview", rowheight=30, font=FONT_BODY,
                         background=C_CARD_BG, fieldbackground=C_CARD_BG)
        style.configure("Cli.Treeview.Heading",
                         font=("Segoe UI", 9, "bold"), background="#f1f5f9")
        style.map("Cli.Treeview", background=[("selected", "#f3e8ff")])

        cols = ("id", "nombre", "dni", "telefono", "email")
        self._tree = ttk.Treeview(tc, columns=cols, show="headings",
                                   style="Cli.Treeview")
        config = {
            "id":       ("ID",        60,  "center"),
            "nombre":   ("Nombre",    200, "w"),
            "dni":      ("DNI",       90,  "center"),
            "telefono": ("Teléfono",  110, "center"),
            "email":    ("Email",     180, "w"),
        }
        for col, (hd, w, anch) in config.items():
            self._tree.heading(col, text=hd)
            self._tree.column(col, width=w, anchor=anch)

        self._tree.tag_configure("odd",  background=C_ROW_ODD)
        self._tree.tag_configure("even", background=C_ROW_EVEN)

        sb = ttk.Scrollbar(tc, orient="vertical", command=self._tree.yview)
        self._tree.configure(yscrollcommand=sb.set)
        self._tree.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")
        self._tree.bind("<<TreeviewSelect>>", self._on_row_select)

    def on_show(self) -> None:
        self._load_table()

    def _load_table(self, clientes=None) -> None:
        for r in self._tree.get_children():
            self._tree.delete(r)
        if clientes is None:
            clientes = self._svc.listar()
        for i, c in enumerate(clientes):
            tag = "odd" if i % 2 == 0 else "even"
            self._tree.insert("", "end", iid=str(c.id), tags=(tag,),
                               values=(c.id, c.nombre, c.dni, c.telefono, c.email))
        self._count_lbl.config(text=f"{len(clientes)} cliente(s)")

    def _on_row_select(self, _=None) -> None:
        sel = self._tree.selection()
        if not sel:
            return
        c = self._svc.obtener(int(sel[0]))
        if c:
            self._editing_id = c.id
            self._id_var.set(str(c.id))
            self._nom_var.set(c.nombre)
            self._dni_var.set(c.dni)
            self._tel_var.set(c.telefono)
            self._email_var.set(c.email)
            self._form_title.config(text=f"Editando Cliente #{c.id}")
            self._msg_var.set("")

    def _on_search(self) -> None:
        t = self._search_var.get().strip()
        self._load_table(self._svc.buscar(t) if t else None)

    def _clear_search(self) -> None:
        self._search_var.set("")
        self._load_table()

    def _new(self) -> None:
        self._clear()
        self._form_title.config(text="Nuevo Cliente")

    def _save(self) -> None:
        nombre = self._nom_var.get().strip()
        dni    = self._dni_var.get().strip()
        tel    = self._tel_var.get().strip()
        email  = self._email_var.get().strip()

        if not nombre:
            self._show_msg("El nombre es obligatorio.", C_ERROR)
            return
        if not dni:
            self._show_msg("El DNI es obligatorio.", C_ERROR)
            return

        try:
            if self._editing_id:
                self._svc.actualizar(self._editing_id, nombre=nombre,
                                     dni=dni, telefono=tel, email=email)
                self._show_msg(f"Cliente #{self._editing_id} actualizado.", C_SUCCESS)
            else:
                c = self._svc.crear(nombre, dni, tel, email)
                self._show_msg(f"Cliente #{c.id} creado.", C_SUCCESS)
            self._load_table()
            self._clear()
        except ValueError as e:
            self._show_msg(str(e), C_ERROR)

    def _delete(self) -> None:
        if not self._editing_id:
            self._show_msg("Seleccione un cliente para eliminar.", C_WARNING)
            return
        if not messagebox.askyesno("Confirmar",
                                    f"¿Eliminar cliente #{self._editing_id}?"):
            return
        if self._svc.eliminar(self._editing_id):
            self._show_msg(f"Cliente #{self._editing_id} eliminado.", C_SUCCESS)
            self._load_table()
            self._clear()
        else:
            self._show_msg("No se pudo eliminar.", C_ERROR)

    def _clear(self) -> None:
        self._editing_id = None
        for var in (self._id_var, self._nom_var, self._dni_var,
                    self._tel_var, self._email_var):
            var.set("")
        self._id_var.set("(auto)")
        self._form_title.config(text="Nuevo Cliente")
        self._msg_var.set("")

    def _show_msg(self, msg: str, color: str = C_ERROR) -> None:
        self._msg_var.set(msg)
        self._ctrl.set_status(msg, color)
