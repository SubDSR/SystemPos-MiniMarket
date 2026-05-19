"""
sync_dialog.py — Diálogo de sincronización con la carpeta de red (UNC).
"""
import tkinter as tk
from tkinter import ttk
import threading

from utils.config import (
    C_CARD_BG, C_ACCENT, C_SUCCESS, C_ERROR, C_TEXT, C_TEXT_MUTED,
    FONT_H3, FONT_BODY, FONT_SMALL, FONT_BTN,
)


class SyncDialog(tk.Toplevel):
    """Ventana modal para ejecutar la sincronización hacia el servidor."""

    def __init__(self, parent) -> None:
        super().__init__(parent)
        self.title("Sincronizar con Servidor")
        self.geometry("480x360")
        self.resizable(False, False)
        self.configure(bg=C_CARD_BG)
        self.transient(parent)
        self.grab_set()

        self._build_ui()
        self.after(100, self._start_sync)

    def _build_ui(self) -> None:
        # Cabecera
        tk.Frame(self, bg=C_ACCENT, height=4).pack(fill="x")
        hdr = tk.Frame(self, bg=C_CARD_BG, padx=20, pady=16)
        hdr.pack(fill="x")
        tk.Label(hdr, text="☁  Sincronización con Servidor Central",
                 font=FONT_H3, bg=C_CARD_BG, fg=C_TEXT).pack(anchor="w")
        tk.Label(hdr, text="Exporta datos locales y los copia a la carpeta de red (UNC).",
                 font=FONT_SMALL, bg=C_CARD_BG, fg=C_TEXT_MUTED).pack(anchor="w")

        # Progreso
        prog_frame = tk.Frame(self, bg=C_CARD_BG, padx=20, pady=10)
        prog_frame.pack(fill="x")

        self._progress = ttk.Progressbar(prog_frame, mode="indeterminate",
                                          length=440)
        self._progress.pack(fill="x", pady=(0, 8))

        self._status_var = tk.StringVar(value="Iniciando...")
        tk.Label(prog_frame, textvariable=self._status_var, font=FONT_SMALL,
                 bg=C_CARD_BG, fg=C_TEXT_MUTED).pack(anchor="w")

        # Log
        log_frame = tk.Frame(self, bg=C_CARD_BG, padx=20, pady=4)
        log_frame.pack(fill="both", expand=True)

        self._log = tk.Text(
            log_frame, height=10, font=("Consolas", 9),
            bg="#0f172a", fg="#94fa75", relief="flat",
            state="disabled", wrap="word",
        )
        sb = ttk.Scrollbar(log_frame, orient="vertical", command=self._log.yview)
        self._log.configure(yscrollcommand=sb.set)
        self._log.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")

        # Botón cerrar
        btn_bar = tk.Frame(self, bg=C_CARD_BG, padx=20, pady=12)
        btn_bar.pack(fill="x")
        self._close_btn = tk.Button(
            btn_bar, text="Cerrar", font=FONT_BTN,
            bg="#64748b", fg="white", bd=0, padx=20, pady=7,
            cursor="hand2", command=self.destroy, state="disabled",
        )
        self._close_btn.pack(side="right")

    def _log_msg(self, msg: str) -> None:
        self._log.config(state="normal")
        self._log.insert("end", msg + "\n")
        self._log.see("end")
        self._log.config(state="disabled")

    def _start_sync(self) -> None:
        self._progress.start(10)
        thread = threading.Thread(target=self._run_sync, daemon=True)
        thread.start()

    def _run_sync(self) -> None:
        try:
            import sys
            from pathlib import Path
            # Asegurarse que APPLICATION está en el path
            app_dir = Path(__file__).parent.parent
            if str(app_dir) not in sys.path:
                sys.path.insert(0, str(app_dir))

            # Importar y ejecutar send
            self._update_status("Importando módulo de sincronización...")
            from send import SyncAgent
            agent = SyncAgent(log_callback=self._log_msg,
                              status_callback=self._update_status)
            agent.run()
        except Exception as e:
            self._log_msg(f"[ERROR] {e}")
            self._update_status(f"Error: {e}")
        finally:
            self._progress.stop()
            self.after(0, lambda: self._close_btn.config(state="normal"))

    def _update_status(self, msg: str) -> None:
        self.after(0, lambda: self._status_var.set(msg))
        self._log_msg(f"[INFO] {msg}")
