"""
main.py — Punto de entrada del Sistema POS MiniMarket.

Arquitectura: Monolítica de escritorio, basada en archivos binarios
con acceso directo mediante struct + seek(). Sin base de datos en el cliente.

Ejecución:
    python main.py
    o compilado: MiniMarketPOS.exe
"""
import sys
import tkinter as tk
from pathlib import Path

# ── Garantizar que APPLICATION/ esté en sys.path ─────────────────────────────
APP_DIR = Path(__file__).resolve().parent
if str(APP_DIR) not in sys.path:
    sys.path.insert(0, str(APP_DIR))


def main() -> None:
    # 1. Crear directorios necesarios
    from utils.config import init_directories
    init_directories()

    # 2. Configurar logging
    from utils.logger import get_logger
    log = get_logger("main")
    log.info("=" * 60)
    log.info("  MiniMarket POS — Iniciando sistema")
    log.info("=" * 60)

    # 3. Crear y configurar la ventana raíz
    root = tk.Tk()

    # Configurar estilo global de ttk
    from tkinter import ttk
    style = ttk.Style()
    style.theme_use("clam")   # Base limpia para personalizar

    # 4. Crear la ventana principal con el controlador de navegación
    from views.main_window import MainWindow
    app = MainWindow(root)

    log.info("Interfaz gráfica inicializada — esperando interacción del usuario")

    # 5. Iniciar el loop de eventos de Tkinter
    root.mainloop()
    log.info("Sistema POS cerrado por el usuario")


if __name__ == "__main__":
    main()
