"""
build.py — Script de compilación automática de ejecutables .EXE.

Genera tres ejecutables usando PyInstaller:
  1. MiniMarketPOS.exe  ← Aplicación cliente POS (main.py)
  2. Send.exe           ← Agente de sincronización (send.py)
  3. Update.exe         ← Procesador de archivos del servidor (update.py)

Uso:
    python build.py
    python build.py --only pos
    python build.py --only send
    python build.py --only update

Prerequisitos:
    pip install pyinstaller

Los ejecutables se generan en:
    dist/MiniMarketPOS.exe
    dist/Send.exe
    dist/Update.exe
"""
import io
import subprocess
import sys
import shutil
from pathlib import Path

# Forzar UTF-8 en la consola de Windows (evita UnicodeEncodeError en cp1252)
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
else:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE_DIR = Path(__file__).resolve().parent
APP_DIR  = BASE_DIR / "APPLICATION"
SRV_DIR  = BASE_DIR / "SERVIDOR"


def run_pyinstaller(
    script:      Path,
    name:        str,
    extra_paths: list = None,
    windowed:    bool = True,
) -> bool:
    """
    Ejecuta PyInstaller para compilar un script a .EXE.

    Args:
        script:      Ruta al archivo .py a compilar
        name:        Nombre del ejecutable de salida (sin .exe)
        extra_paths: Rutas adicionales para incluir en --paths
        windowed:    True = sin consola (modo GUI), False = con consola

    Returns:
        True si la compilación fue exitosa
    """
    if not shutil.which("pyinstaller"):
        print("[ERROR] PyInstaller no encontrado. Instálalo con: pip install pyinstaller")
        return False

    cmd = [
        sys.executable, "-m", "PyInstaller",
        "--onefile",                        # Un solo .exe sin carpetas
        "--name", name,                     # Nombre del ejecutable
        "--distpath", str(BASE_DIR / "dist"),  # Carpeta de salida
        "--workpath", str(BASE_DIR / "build" / "work"),  # Archivos temporales
        "--specpath", str(BASE_DIR / "build" / "spec"),  # Archivos .spec
        "--clean",                          # Limpiar build anterior
    ]

    if windowed:
        cmd.append("--windowed")            # Sin ventana de consola

    # Agregar rutas de módulos adicionales
    paths = extra_paths or []
    for p in paths:
        cmd += ["--paths", str(p)]

    cmd.append(str(script))

    print(f"\n{'─'*60}")
    print(f"  Compilando: {name}.exe")
    print(f"  Script:     {script}")
    print(f"{'─'*60}")

    result = subprocess.run(cmd, capture_output=False)
    if result.returncode == 0:
        exe_path = BASE_DIR / "dist" / f"{name}.exe"
        size_mb  = exe_path.stat().st_size / (1024 * 1024) if exe_path.exists() else 0
        print(f"\n  ✓ {name}.exe generado exitosamente ({size_mb:.1f} MB)")
        return True
    else:
        print(f"\n  ✗ Error compilando {name}.exe (código: {result.returncode})")
        return False


def build_pos() -> bool:
    """Compila la aplicación cliente POS."""
    return run_pyinstaller(
        script      = APP_DIR / "main.py",
        name        = "MiniMarketPOS",
        extra_paths = [str(APP_DIR)],
        windowed    = True,
    )


def build_send() -> bool:
    """
    Compila Send.exe — Agente de sincronizacion cliente-servidor.

    Send.exe:
      - Lee datos de APPLICATION/DATA/
      - Exporta a APPLICATION/exports/
      - Copia los CSV a la ruta UNC o DATOS/ (fallback)
    """
    return run_pyinstaller(
        script      = APP_DIR / "send.py",
        name        = "Send",
        extra_paths = [str(APP_DIR)],
        windowed    = True,
    )


def build_update() -> bool:
    """
    Compila Update.exe — Procesador de archivos del servidor.

    Update.exe:
      - Monitorea la carpeta DATOS/ (UNC o fallback local)
      - Lee los CSV recibidos de los clientes
      - Los inserta en la base de datos SQLite del servidor
    """
    return run_pyinstaller(
        script      = SRV_DIR / "update.py",
        name        = "Update",
        extra_paths = [str(SRV_DIR)],
        windowed    = True,
    )


def build_server() -> bool:
    """Compila la aplicación servidor."""
    return run_pyinstaller(
        script      = SRV_DIR / "server.py",
        name        = "Server",
        extra_paths = [str(SRV_DIR)],
        windowed    = True,
    )


def main() -> None:
    import argparse
    parser = argparse.ArgumentParser(
        description="Compilador del Sistema POS MiniMarket"
    )
    parser.add_argument(
        "--only",
        choices=["pos", "send", "update", "server"],
        help="Compilar solo un ejecutable específico",
        default=None,
    )
    args = parser.parse_args()

    # Crear carpetas de salida
    (BASE_DIR / "dist").mkdir(exist_ok=True)
    (BASE_DIR / "build" / "work").mkdir(parents=True, exist_ok=True)
    (BASE_DIR / "build" / "spec").mkdir(parents=True, exist_ok=True)

    print("=" * 60)
    print("  Sistema POS MiniMarket — Compilación de Ejecutables")
    print("=" * 60)

    results = {}

    if args.only == "pos" or args.only is None:
        results["MiniMarketPOS.exe"] = build_pos()

    if args.only == "send" or args.only is None:
        results["Send.exe"] = build_send()

    if args.only == "update" or args.only is None:
        results["Update.exe"] = build_update()

    if args.only == "server" or args.only is None:
        results["Server.exe"] = build_server()

    # Resumen
    print(f"\n{'='*60}")
    print("  Resumen de compilación:")
    print(f"{'─'*60}")
    for name, ok in results.items():
        status = "✓ OK" if ok else "✗ FALLÓ"
        print(f"  {name:<25} {status}")

    print(f"{'='*60}")
    if all(results.values()):
        print(f"  Todos los ejecutables en:  {BASE_DIR / 'dist'}")
    else:
        print("  Algunos ejecutables fallaron. Revisa los errores arriba.")

    print()


if __name__ == "__main__":
    main()
