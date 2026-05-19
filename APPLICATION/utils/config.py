"""
config.py — Configuración central del Sistema POS MiniMarket.
Define todas las rutas absolutas, constantes de negocio y parámetros de UI.

Jerarquía de directorios resuelta dinámicamente desde la ubicación de este archivo:
    BASE_DIR/APPLICATION/utils/config.py
        → .parent          = BASE_DIR/APPLICATION/utils
        → .parent.parent   = BASE_DIR/APPLICATION
        → .parent.parent.parent = BASE_DIR   ← raíz del proyecto
"""
import socket
from pathlib import Path

# ════════════════════════════════════════════════════════════════════════════
# RUTAS BASE
# ════════════════════════════════════════════════════════════════════════════
BASE_DIR: Path      = Path(__file__).resolve().parent.parent.parent

APP_DIR: Path       = BASE_DIR / "APPLICATION"
DATA_DIR: Path      = BASE_DIR / "DATA"       # archivos .dat, .idx, .holes
DATOS_DIR: Path     = BASE_DIR / "DATOS"      # carpeta compartida de red (UNC)
SERVIDOR_DIR: Path  = BASE_DIR / "SERVIDOR"

# Subdirectorios de APPLICATION
INDEXES_DIR: Path   = APP_DIR / "indexes"
LOGS_DIR: Path      = APP_DIR / "logs"
EXPORTS_DIR: Path   = APP_DIR / "exports"

# Subdirectorios del SERVIDOR
DB_DIR: Path            = SERVIDOR_DIR / "database"
SERVER_LOGS_DIR: Path   = SERVIDOR_DIR / "logs"

# ════════════════════════════════════════════════════════════════════════════
# RUTA UNC DE RED — Simulación de carpeta compartida
# En una red real: \\NOMBRE-PC\DATOS
# Fallback local:  BASE_DIR/DATOS
# ════════════════════════════════════════════════════════════════════════════
HOSTNAME: str         = socket.gethostname()
UNC_DATOS: str        = f"\\\\{HOSTNAME}\\DATOS"
UNC_DATOS_PATH: Path  = Path(UNC_DATOS)

# ════════════════════════════════════════════════════════════════════════════
# ARCHIVOS DE DATOS BINARIOS (.dat)
# Cada archivo almacena registros de longitud fija accedidos con seek()
# ════════════════════════════════════════════════════════════════════════════
PRODUCTOS_DAT:  Path = DATA_DIR / "productos.dat"
CLIENTES_DAT:   Path = DATA_DIR / "clientes.dat"
VENTAS_DAT:     Path = DATA_DIR / "ventas.dat"
DETALLES_DAT:   Path = DATA_DIR / "detalles.dat"

# ════════════════════════════════════════════════════════════════════════════
# ARCHIVOS DE ÍNDICE (.idx)
# Estructura: [n_entradas(4B)] + [clave(4B)+offset(8B)] × n
# Mapeo: clave_primaria → byte_offset_en_dat
# ════════════════════════════════════════════════════════════════════════════
PRODUCTOS_IDX:  Path = DATA_DIR / "productos.idx"
CLIENTES_IDX:   Path = DATA_DIR / "clientes.idx"
VENTAS_IDX:     Path = DATA_DIR / "ventas.idx"
DETALLES_IDX:   Path = DATA_DIR / "detalles.idx"

# ════════════════════════════════════════════════════════════════════════════
# ARCHIVOS DE ESPACIOS LIBRES (.holes)
# Almacenan offsets de registros eliminados lógicamente para reutilizar
# ════════════════════════════════════════════════════════════════════════════
PRODUCTOS_HOLES: Path = DATA_DIR / "productos.holes"
CLIENTES_HOLES:  Path = DATA_DIR / "clientes.holes"
VENTAS_HOLES:    Path = DATA_DIR / "ventas.holes"
DETALLES_HOLES:  Path = DATA_DIR / "detalles.holes"

# ════════════════════════════════════════════════════════════════════════════
# BASE DE DATOS DEL SERVIDOR (SQLite — solo en SERVIDOR/)
# ════════════════════════════════════════════════════════════════════════════
SERVER_DB: Path = DB_DIR / "minimarket.db"

# ════════════════════════════════════════════════════════════════════════════
# CONSTANTES DE NEGOCIO
# ════════════════════════════════════════════════════════════════════════════
IGV_RATE: float  = 0.18
APP_NAME: str    = "MiniMarket POS"
APP_VERSION: str = "1.0.0"
EMPRESA: str     = "MiniMarket El Ahorro S.A.C."
RUC: str         = "20123456789"

# ════════════════════════════════════════════════════════════════════════════
# PALETA DE COLORES (Interfaz Tkinter)
# ════════════════════════════════════════════════════════════════════════════
C_SIDEBAR_BG     = "#1e293b"
C_SIDEBAR_FG     = "#cbd5e1"
C_SIDEBAR_HOVER  = "#334155"
C_SIDEBAR_ACTIVE = "#3b82f6"
C_MAIN_BG        = "#f1f5f9"
C_CARD_BG        = "#ffffff"
C_ACCENT         = "#3b82f6"
C_ACCENT_DARK    = "#2563eb"
C_SUCCESS        = "#22c55e"
C_ERROR          = "#ef4444"
C_WARNING        = "#f59e0b"
C_TEXT           = "#0f172a"
C_TEXT_MUTED     = "#64748b"
C_BORDER         = "#e2e8f0"
C_ROW_ODD        = "#f8fafc"
C_ROW_EVEN       = "#ffffff"
C_ROW_SELECT     = "#dbeafe"

# ════════════════════════════════════════════════════════════════════════════
# FUENTES
# ════════════════════════════════════════════════════════════════════════════
FONT_H1      = ("Segoe UI", 18, "bold")
FONT_H2      = ("Segoe UI", 14, "bold")
FONT_H3      = ("Segoe UI", 11, "bold")
FONT_BODY    = ("Segoe UI", 10)
FONT_SMALL   = ("Segoe UI", 9)
FONT_MONO    = ("Consolas", 9)
FONT_BTN     = ("Segoe UI", 10, "bold")
FONT_SIDEBAR = ("Segoe UI", 11)


def init_directories() -> None:
    """Crea todos los directorios del proyecto si no existen."""
    dirs = [
        DATA_DIR, DATOS_DIR,
        INDEXES_DIR, LOGS_DIR, EXPORTS_DIR,
        DB_DIR, SERVER_LOGS_DIR,
    ]
    for d in dirs:
        d.mkdir(parents=True, exist_ok=True)
