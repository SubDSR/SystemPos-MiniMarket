"""
logger.py — Sistema de logging centralizado.
Registra operaciones del cliente POS, sincronizaciones y errores.

Genera un archivo de log diario en APPLICATION/logs/pos_YYYY-MM-DD.log
y también imprime en consola (nivel INFO+).
"""
import logging
import sys
from datetime import datetime
from pathlib import Path


def get_logger(name: str, logs_dir: Path | None = None) -> logging.Logger:
    """
    Crea y devuelve un logger configurado con handlers de archivo y consola.

    Args:
        name:     Nombre del módulo/componente (usado como identificador).
        logs_dir: Directorio de logs. Si es None, se resuelve desde config.

    Returns:
        Logger listo para usar.
    """
    logger = logging.getLogger(name)

    if logger.handlers:          # Evitar duplicar handlers en recargas
        return logger

    logger.setLevel(logging.DEBUG)

    # ── Resolver directorio de logs ──────────────────────────────────────────
    if logs_dir is None:
        from utils.config import LOGS_DIR
        logs_dir = LOGS_DIR

    logs_dir.mkdir(parents=True, exist_ok=True)

    # ── Formato ──────────────────────────────────────────────────────────────
    fmt = logging.Formatter(
        fmt="%(asctime)s | %(levelname)-8s | %(name)-22s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    # ── Handler de archivo (rotación diaria) ─────────────────────────────────
    today     = datetime.now().strftime("%Y-%m-%d")
    log_file  = logs_dir / f"pos_{today}.log"
    fh        = logging.FileHandler(log_file, encoding="utf-8")
    fh.setLevel(logging.DEBUG)
    fh.setFormatter(fmt)

    # ── Handler de consola ───────────────────────────────────────────────────
    ch = logging.StreamHandler(sys.stdout)
    ch.setLevel(logging.INFO)
    ch.setFormatter(fmt)

    logger.addHandler(fh)
    logger.addHandler(ch)
    return logger
