"""
main_window.py — Ventana principal del Sistema POS MiniMarket.

Layout:
┌─────────────┬──────────────────────────────────────────────────────────┐
│  SIDEBAR    │  HEADER (título + versión)                               │
│  ─────────  ├──────────────────────────────────────────────────────────┤
│  Dashboard  │                                                          │
│  Productos  │          ÁREA DE CONTENIDO (frames intercambiables)      │
│  Clientes   │                                                          │
│  Ventas     │                                                          │
│  ─────────  │                                                          │
│  Sincroniz. │                                                          │
│  ─────────  │                                                          │
│  Salir      │                                                          │
└─────────────┴──────────────────────────────────────────────────────────┘
"""
import tkinter as tk
from tkinter import messagebox
from typing import Dict, Optional

from utils.config import (
    APP_NAME, APP_VERSION, EMPRESA,
    C_SIDEBAR_BG, C_SIDEBAR_FG, C_SIDEBAR_HOVER, C_SIDEBAR_ACTIVE,
    C_MAIN_BG, C_CARD_BG, C_ACCENT, C_TEXT,
    C_ERROR, C_SUCCESS,
    FONT_SIDEBAR, FONT_H2, FONT_SMALL, FONT_BTN,
)


class SidebarButton(tk.Frame):
    """Botón estilizado para la barra lateral de navegación."""

    def __init__(
        self,
        parent,
        text: str,
        icon: str,
        command,
        **kwargs,
    ) -> None:
        super().__init__(parent, bg=C_SIDEBAR_BG, cursor="hand2", **kwargs)
        self._command  = command
        self._active   = False

        self._frame = tk.Frame(self, bg=C_SIDEBAR_BG)
        self._frame.pack(fill="x")

        self._icon_lbl = tk.Label(
            self._frame, text=icon, font=("Segoe UI", 13),
            bg=C_SIDEBAR_BG, fg=C_SIDEBAR_FG, width=3,
        )
        self._icon_lbl.pack(side="left", padx=(12, 4), pady=10)

        self._text_lbl = tk.Label(
            self._frame, text=text, font=FONT_SIDEBAR,
            bg=C_SIDEBAR_BG, fg=C_SIDEBAR_FG, anchor="w",
        )
        self._text_lbl.pack(side="left", fill="x", expand=True, pady=10)

        # Indicador de selección (barra lateral izquierda)
        self._indicator = tk.Frame(self, bg=C_SIDEBAR_BG, width=4)
        self._indicator.place(x=0, y=0, relheight=1)

        for widget in (self, self._frame, self._icon_lbl, self._text_lbl):
            widget.bind("<Button-1>", lambda e: self._command())
            widget.bind("<Enter>",    self._on_enter)
            widget.bind("<Leave>",    self._on_leave)

    def _on_enter(self, _):
        if not self._active:
            self._set_bg(C_SIDEBAR_HOVER)

    def _on_leave(self, _):
        if not self._active:
            self._set_bg(C_SIDEBAR_BG)

    def _set_bg(self, color: str) -> None:
        self._frame["bg"]    = color
        self._icon_lbl["bg"] = color
        self._text_lbl["bg"] = color

    def set_active(self, active: bool) -> None:
        self._active = active
        if active:
            self._set_bg(C_SIDEBAR_ACTIVE)
            self._indicator.config(bg="white")
            self._icon_lbl.config(fg="white")
            self._text_lbl.config(fg="white", font=(*FONT_SIDEBAR[:2], "bold"))
        else:
            self._set_bg(C_SIDEBAR_BG)
            self._indicator.config(bg=C_SIDEBAR_BG)
            self._icon_lbl.config(fg=C_SIDEBAR_FG)
            self._text_lbl.config(fg=C_SIDEBAR_FG, font=FONT_SIDEBAR)


class MainWindow:
    """
    Ventana principal del POS. Gestiona la navegación entre vistas
    mediante el patrón de Frame intercambiable (muestra/oculta frames).
    """

    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self._buttons:      Dict[str, SidebarButton] = {}
        self._frames:       Dict[str, tk.Frame]      = {}
        self._current_view: Optional[str]            = None
        self._status_var    = tk.StringVar(value="Sistema listo")

        self._build_ui()
        self._load_views()
        self.navigate("dashboard")

    # ════════════════════════════════════════════════════════════════════════
    # CONSTRUCCIÓN DE LA UI
    # ════════════════════════════════════════════════════════════════════════

    def _build_ui(self) -> None:
        """Construye el layout principal: sidebar + área de contenido."""
        self.root.title(f"{APP_NAME}  v{APP_VERSION}")
        self.root.geometry("1200x720")
        self.root.minsize(960, 600)
        self.root.configure(bg=C_SIDEBAR_BG)
        self.root.resizable(True, True)

        # Icono de ventana (texto fallback si no hay .ico)
        try:
            self.root.iconbitmap("assets/icon.ico")
        except Exception:
            pass

        # ── Layout de dos columnas ────────────────────────────────────────────
        self.root.columnconfigure(1, weight=1)
        self.root.rowconfigure(0, weight=1)

        self._build_sidebar()
        self._build_content_area()

    def _build_sidebar(self) -> None:
        """Construye la barra lateral de navegación."""
        sidebar = tk.Frame(self.root, bg=C_SIDEBAR_BG, width=220)
        sidebar.grid(row=0, column=0, sticky="nsew")
        sidebar.grid_propagate(False)

        # ── Logo / nombre empresa ─────────────────────────────────────────────
        logo_frame = tk.Frame(sidebar, bg=C_SIDEBAR_BG, pady=20)
        logo_frame.pack(fill="x")

        tk.Label(
            logo_frame, text="🛒", font=("Segoe UI", 28),
            bg=C_SIDEBAR_BG, fg="white",
        ).pack()
        tk.Label(
            logo_frame, text=APP_NAME, font=("Segoe UI", 13, "bold"),
            bg=C_SIDEBAR_BG, fg="white",
        ).pack()
        tk.Label(
            logo_frame, text=EMPRESA, font=FONT_SMALL,
            bg=C_SIDEBAR_BG, fg="#94a3b8",
            wraplength=200, justify="center",
        ).pack(pady=(2, 0))

        # ── Separador ─────────────────────────────────────────────────────────
        tk.Frame(sidebar, bg="#334155", height=1).pack(fill="x", padx=16, pady=8)

        # ── Botones de navegación ─────────────────────────────────────────────
        nav_items = [
            ("dashboard",  "📊",  "Dashboard"),
            ("productos",  "📦",  "Productos"),
            ("clientes",   "👤",  "Clientes"),
            ("ventas",     "🧾",  "Nueva Venta"),
            ("historial",  "📋",  "Historial"),
        ]
        for key, icon, label in nav_items:
            btn = SidebarButton(
                sidebar, text=label, icon=icon,
                command=lambda k=key: self.navigate(k),
            )
            btn.pack(fill="x")
            self._buttons[key] = btn

        # ── Separador ─────────────────────────────────────────────────────────
        tk.Frame(sidebar, bg="#334155", height=1).pack(fill="x", padx=16, pady=8)

        # ── Sincronizar ───────────────────────────────────────────────────────
        sync_btn = SidebarButton(
            sidebar, text="Sincronizar", icon="☁",
            command=self._on_sync,
        )
        sync_btn.pack(fill="x")
        self._buttons["sync"] = sync_btn

        # ── Espaciador ────────────────────────────────────────────────────────
        tk.Frame(sidebar, bg=C_SIDEBAR_BG).pack(fill="both", expand=True)

        # ── Versión + Salir ───────────────────────────────────────────────────
        tk.Label(
            sidebar, text=f"v{APP_VERSION}", font=FONT_SMALL,
            bg=C_SIDEBAR_BG, fg="#475569",
        ).pack(pady=(0, 4))

        salir_btn = SidebarButton(
            sidebar, text="Salir", icon="✕",
            command=self._on_exit,
        )
        salir_btn.pack(fill="x")

    def _build_content_area(self) -> None:
        """Construye el área de contenido a la derecha del sidebar."""
        content_wrapper = tk.Frame(self.root, bg=C_MAIN_BG)
        content_wrapper.grid(row=0, column=1, sticky="nsew")
        content_wrapper.rowconfigure(1, weight=1)
        content_wrapper.columnconfigure(0, weight=1)

        # ── Header ────────────────────────────────────────────────────────────
        header = tk.Frame(content_wrapper, bg=C_CARD_BG, height=56)
        header.grid(row=0, column=0, sticky="ew")
        header.grid_propagate(False)
        header.columnconfigure(0, weight=1)

        self._header_title = tk.Label(
            header, text="Dashboard", font=FONT_H2,
            bg=C_CARD_BG, fg=C_TEXT, padx=24,
        )
        self._header_title.pack(side="left", pady=12)

        # Barra de estado en el header
        status_frame = tk.Frame(header, bg=C_CARD_BG)
        status_frame.pack(side="right", padx=20)
        self._status_lbl = tk.Label(
            status_frame, textvariable=self._status_var,
            font=FONT_SMALL, bg=C_CARD_BG, fg="#64748b",
        )
        self._status_lbl.pack()

        # Línea divisora bajo el header
        tk.Frame(content_wrapper, bg="#e2e8f0", height=1).grid(
            row=0, column=0, sticky="ews"
        )

        # ── Área de frames intercambiables ────────────────────────────────────
        self.content_area = tk.Frame(content_wrapper, bg=C_MAIN_BG)
        self.content_area.grid(row=1, column=0, sticky="nsew")
        self.content_area.rowconfigure(0, weight=1)
        self.content_area.columnconfigure(0, weight=1)

    # ════════════════════════════════════════════════════════════════════════
    # CARGA DE VISTAS
    # ════════════════════════════════════════════════════════════════════════

    def _load_views(self) -> None:
        """Crea e inicializa todos los frames de vistas."""
        from views.dashboard_view import DashboardView
        from views.productos_view import ProductosView
        from views.clientes_view  import ClientesView
        from views.ventas_view    import VentasView
        from views.historial_view import HistorialView

        view_classes = {
            "dashboard": (DashboardView,  "Dashboard"),
            "productos":  (ProductosView, "Gestión de Productos"),
            "clientes":   (ClientesView,  "Gestión de Clientes"),
            "ventas":     (VentasView,    "Nueva Venta"),
            "historial":  (HistorialView, "Historial de Ventas"),
        }

        for key, (ViewClass, _) in view_classes.items():
            try:
                frame = ViewClass(self.content_area, self)
                frame.grid(row=0, column=0, sticky="nsew")
                self._frames[key] = frame
            except Exception as e:
                # Frame de error como fallback
                err_frame = tk.Frame(self.content_area, bg=C_MAIN_BG)
                err_frame.grid(row=0, column=0, sticky="nsew")
                tk.Label(
                    err_frame,
                    text=f"Error cargando vista '{key}':\n{e}",
                    fg=C_ERROR, bg=C_MAIN_BG, font=FONT_BTN,
                ).pack(expand=True)
                self._frames[key] = err_frame

        self._view_titles = {k: v[1] for k, v in view_classes.items()}

    # ════════════════════════════════════════════════════════════════════════
    # NAVEGACIÓN
    # ════════════════════════════════════════════════════════════════════════

    def navigate(self, view_key: str) -> None:
        """
        Cambia la vista activa mostrando el frame correspondiente
        y ocultando el anterior.
        """
        if view_key not in self._frames:
            return

        # Desactivar botón anterior
        if self._current_view and self._current_view in self._buttons:
            self._buttons[self._current_view].set_active(False)

        # Ocultar frame anterior
        if self._current_view and self._current_view in self._frames:
            self._frames[self._current_view].grid_remove()

        # Mostrar nuevo frame
        self._frames[view_key].grid()
        self._current_view = view_key

        # Activar botón
        if view_key in self._buttons:
            self._buttons[view_key].set_active(True)

        # Actualizar título del header
        title = self._view_titles.get(view_key, view_key.capitalize())
        self._header_title.config(text=title)

        # Notificar a la vista que fue activada (para refrescar datos)
        frame = self._frames[view_key]
        if hasattr(frame, "on_show"):
            frame.on_show()

    # ════════════════════════════════════════════════════════════════════════
    # ACCIONES GLOBALES
    # ════════════════════════════════════════════════════════════════════════

    def _on_sync(self) -> None:
        """Lanza el proceso de sincronización con la carpeta de red."""
        from views.sync_dialog import SyncDialog
        SyncDialog(self.root)

    def _on_exit(self) -> None:
        if messagebox.askyesno("Salir", "¿Desea cerrar el sistema POS?",
                               icon="question"):
            self.root.quit()
            self.root.destroy()

    def set_status(self, msg: str, color: str = "#64748b") -> None:
        """Actualiza el mensaje de estado en el header."""
        self._status_var.set(msg)
        self._status_lbl.config(fg=color)
