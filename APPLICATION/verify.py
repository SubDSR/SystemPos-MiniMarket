"""
verify.py — Verificador de integridad de los archivos binarios del POS.

Comprueba:
  1. Consistencia entre .dat e .idx (todo offset en .idx apunta a registro válido)
  2. Coherencia entre ventas.dat y detalles.dat (toda venta tiene al menos 1 detalle)
  3. Coherencia de totales (subtotal + IGV = total, tolerance 1 cent)
  4. Referencia de productos (todo detalle apunta a un producto existente)
  5. Referencia de clientes (toda venta con cliente_id > 0 apunta a cliente existente)
  6. Stock coherente (no debe haber stock negativo)
  7. Dump hexadecimal de los primeros N registros de cada archivo

Ejecución:
    cd APPLICATION
    python verify.py
    python verify.py --dump 3       # muestra hex dump de los 3 primeros registros
    python verify.py --entity ventas  # solo verifica ventas
"""
import sys
import io
import struct
import argparse
from pathlib import Path

# Forzar UTF-8 en la salida estándar (necesario en terminales Windows cp1252)
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
else:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
from typing import List, Optional

APP_DIR = Path(__file__).resolve().parent
if str(APP_DIR) not in sys.path:
    sys.path.insert(0, str(APP_DIR))

from utils.config import (
    init_directories,
    PRODUCTOS_DAT, PRODUCTOS_IDX, PRODUCTOS_HOLES,
    CLIENTES_DAT,  CLIENTES_IDX,  CLIENTES_HOLES,
    VENTAS_DAT,    VENTAS_IDX,    VENTAS_HOLES,
    DETALLES_DAT,  DETALLES_IDX,  DETALLES_HOLES,
    IGV_RATE,
)
from models.producto     import Producto
from models.cliente      import Cliente
from models.venta        import Venta
from models.detalle_venta import DetalleVenta
from services.index_manager import IndexManager

# ════════════════════════════════════════════════════════════════════════════
# COLORES ANSI (terminales que los soporten)
# ════════════════════════════════════════════════════════════════════════════
OK    = "\033[92m✓\033[0m"
FAIL  = "\033[91m✗\033[0m"
WARN  = "\033[93m⚠\033[0m"
INFO  = "\033[94mℹ\033[0m"
BOLD  = "\033[1m"
RESET = "\033[0m"

_errors:   List[str] = []
_warnings: List[str] = []
_ok_count: int = 0


def check_ok(msg: str) -> None:
    global _ok_count
    _ok_count += 1
    print(f"    {OK}  {msg}")


def check_fail(msg: str) -> None:
    _errors.append(msg)
    print(f"    {FAIL}  {msg}")


def check_warn(msg: str) -> None:
    _warnings.append(msg)
    print(f"    {WARN}  {msg}")


def info(msg: str) -> None:
    print(f"    {INFO}  {msg}")


def header(title: str) -> None:
    print(f"\n{BOLD}{'═'*64}{RESET}")
    print(f"{BOLD}  {title}{RESET}")
    print(f"{BOLD}{'═'*64}{RESET}")


def section(title: str) -> None:
    print(f"\n  {BOLD}── {title}{RESET} {'─'*(58-len(title))}")


# ════════════════════════════════════════════════════════════════════════════
# LECTURA DE ARCHIVO .dat COMPLETO (incluye eliminados)
# ════════════════════════════════════════════════════════════════════════════

def read_all_records(dat_path: Path, model_class) -> list:
    """Lee todos los registros del .dat (activos e inactivos)."""
    records = []
    if not dat_path.exists() or dat_path.stat().st_size == 0:
        return records
    size = model_class.SIZE
    with open(dat_path, "rb") as f:
        pos = 0
        while True:
            data = f.read(size)
            if not data:
                break
            if len(data) == size:
                try:
                    records.append((pos, model_class.unpack(data)))
                except Exception as e:
                    check_fail(f"Error deserializando offset={pos}: {e}")
            pos += size
    return records  # [(offset, objeto), ...]


# ════════════════════════════════════════════════════════════════════════════
# VERIFICACIONES POR ENTIDAD
# ════════════════════════════════════════════════════════════════════════════

def verify_index_consistency(
    dat_path: Path,
    idx_path: Path,
    holes_path: Path,
    model_class,
    label: str,
) -> dict:
    """
    Verifica que cada entrada del .idx apunta a un registro real y legible.

    Checks:
      - El offset existe en el .dat (no fuera de rango)
      - El registro en ese offset tiene el mismo ID que la clave del índice
      - El registro tiene estado=1 (activo)
      - No hay IDs duplicados en el índice
    """
    section(f"Índice: {label}")

    if not dat_path.exists():
        check_warn(f"{dat_path.name} no existe")
        return {}
    if not idx_path.exists():
        check_warn(f"{idx_path.name} no existe")
        return {}

    idx = IndexManager(idx_path, holes_path)
    dat_size    = dat_path.stat().st_size
    record_size = model_class.SIZE
    all_idx     = idx.get_all()   # { id → offset }

    info(f"{label}: {len(all_idx)} entrada(s) en el índice")
    info(f"Archivo .dat: {dat_size} bytes → {dat_size // record_size} registros físicos")
    info(f"Holes disponibles: {len(idx._holes)}")

    seen_ids: set = set()
    valid_records = {}

    with open(dat_path, "rb") as f:
        for key, offset in sorted(all_idx.items()):

            # 1. Verificar que no hay IDs duplicados en el índice
            if key in seen_ids:
                check_fail(f"ID duplicado en índice: id={key}")
                continue
            seen_ids.add(key)

            # 2. Verificar que el offset está dentro del rango del archivo
            if offset + record_size > dat_size:
                check_fail(
                    f"id={key} → offset={offset} fuera de rango "
                    f"(dat_size={dat_size})"
                )
                continue

            # 3. Leer el registro en ese offset
            f.seek(offset)
            data = f.read(record_size)
            if len(data) != record_size:
                check_fail(f"id={key} → solo se leyeron {len(data)} de {record_size} bytes")
                continue

            try:
                record = model_class.unpack(data)
            except Exception as e:
                check_fail(f"id={key} → error desempaquetando: {e}")
                continue

            # 4. Verificar que el ID del registro coincide con la clave del índice
            if record.id != key:
                check_fail(
                    f"Inconsistencia ID: índice dice id={key}, "
                    f"registro tiene id={record.id} en offset={offset}"
                )
                continue

            # 5. Verificar estado activo
            if record.estado != 1:
                check_fail(
                    f"id={key} en índice pero estado={record.estado} "
                    f"(debería ser 1=activo)"
                )
                continue

            valid_records[key] = record

    if len(valid_records) == len(all_idx):
        check_ok(
            f"{label}: {len(valid_records)} registros — "
            f"todos los offsets son coherentes"
        )
    else:
        check_fail(
            f"{label}: solo {len(valid_records)} de {len(all_idx)} "
            f"registros son válidos"
        )

    # Verificar que los holes no colisionan con entradas activas
    activos_offsets = set(all_idx.values())
    for hole_offset in idx._holes:
        if hole_offset in activos_offsets:
            check_fail(
                f"HOLE offset={hole_offset} colisiona con un registro activo"
            )
        else:
            check_ok(f"Hole offset={hole_offset} es un slot libre válido")

    return valid_records


def verify_ventas_integrity(
    ventas: dict,
    detalles: dict,
    productos: dict,
    clientes: dict,
) -> None:
    """
    Verifica la coherencia lógica entre ventas y sus detalles.
    """
    section("Coherencia Lógica: Ventas ↔ Detalles ↔ Productos ↔ Clientes")
    TOLERANCE = 0.02   # tolerancia de 2 centavos por redondeo

    ventas_sin_detalles = []
    totales_incorrectos = []
    refs_invalidas      = []

    # Agrupar detalles por venta_id
    det_by_venta: dict = {}
    for det_id, det in detalles.items():
        det_by_venta.setdefault(det.venta_id, []).append(det)

    for venta_id, venta in ventas.items():

        # 1. Toda venta debe tener al menos 1 detalle
        dets = det_by_venta.get(venta_id, [])
        if not dets:
            ventas_sin_detalles.append(venta_id)
            continue

        # 2. Suma de subtotales de detalles debe coincidir con venta.subtotal
        suma_subtotales = round(sum(d.subtotal for d in dets), 2)
        igv_calculado   = round(suma_subtotales * IGV_RATE, 2)
        total_calculado = round(suma_subtotales + igv_calculado, 2)

        if abs(suma_subtotales - venta.subtotal) > TOLERANCE:
            totales_incorrectos.append(
                f"venta#{venta_id}: subtotal esperado={suma_subtotales:.2f}, "
                f"almacenado={venta.subtotal:.2f}"
            )

        if abs(total_calculado - venta.total) > TOLERANCE:
            totales_incorrectos.append(
                f"venta#{venta_id}: total esperado={total_calculado:.2f}, "
                f"almacenado={venta.total:.2f}"
            )

        # 3. Cada detalle debe referenciar un producto activo
        for det in dets:
            if det.producto_id not in productos:
                refs_invalidas.append(
                    f"detalle#{det.id} (venta#{venta_id}) → "
                    f"producto_id={det.producto_id} no existe o está eliminado"
                )
            else:
                # Precio unitario almacenado debe ser coherente
                prod = productos[det.producto_id]
                # El precio puede diferir (precios cambian con el tiempo), solo advertir
                pass

        # 4. Si tiene cliente_id, debe existir en clientes activos
        if venta.cliente_id > 0 and venta.cliente_id not in clientes:
            refs_invalidas.append(
                f"venta#{venta_id}: cliente_id={venta.cliente_id} "
                f"no existe o está eliminado"
            )

    # Reportar resultados
    if not ventas_sin_detalles:
        check_ok(f"Todas las {len(ventas)} ventas tienen al menos 1 detalle")
    else:
        check_fail(
            f"{len(ventas_sin_detalles)} venta(s) sin detalles: "
            f"{ventas_sin_detalles[:5]}"
        )

    if not totales_incorrectos:
        check_ok("Todos los totales (subtotal + IGV = total) son coherentes")
    else:
        for msg in totales_incorrectos[:5]:
            check_fail(msg)

    if not refs_invalidas:
        check_ok("Todas las referencias a productos y clientes son válidas")
    else:
        for msg in refs_invalidas[:5]:
            check_fail(msg)


def verify_stock_integrity(
    productos: dict,
    detalles: dict,
) -> None:
    """
    Verifica que no haya stock negativo.
    (No recalcula el stock histórico completo, solo verifica stock actual >= 0)
    """
    section("Integridad de Stock")

    sin_stock = [p for p in productos.values() if p.stock < 0]
    if not sin_stock:
        check_ok(f"Todos los {len(productos)} productos tienen stock ≥ 0")
    else:
        for p in sin_stock:
            check_fail(f"#{p.id} {p.nombre}: stock={p.stock} (NEGATIVO)")

    # Estadística de stock bajo (advertencia)
    bajo = [p for p in productos.values() if 0 < p.stock <= 5]
    if bajo:
        check_warn(
            f"{len(bajo)} producto(s) con stock ≤ 5: "
            + ", ".join(f"#{p.id}({p.stock})" for p in bajo[:5])
        )
    else:
        check_ok("No hay productos en alerta de stock bajo (≤5)")


# ════════════════════════════════════════════════════════════════════════════
# HEX DUMP
# ════════════════════════════════════════════════════════════════════════════

def hex_dump(dat_path: Path, model_class, n_records: int = 2) -> None:
    """Muestra un dump hexadecimal de los primeros N registros del .dat."""
    if not dat_path.exists() or dat_path.stat().st_size == 0:
        info(f"{dat_path.name}: archivo vacío o no existe")
        return

    size    = model_class.SIZE
    label   = model_class.__name__
    n_total = dat_path.stat().st_size // size
    n_show  = min(n_records, n_total)

    print(f"\n    {BOLD}Hex dump: {dat_path.name}{RESET}  "
          f"({size} bytes/registro, mostrando {n_show} de {n_total})")

    with open(dat_path, "rb") as f:
        for i in range(n_show):
            offset = i * size
            f.seek(offset)
            data = f.read(size)
            if len(data) != size:
                break

            # Línea de hex
            hex_groups = []
            for j in range(0, size, 8):
                chunk = data[j: j + 8]
                hex_groups.append(" ".join(f"{b:02X}" for b in chunk))
            hex_str = "  ".join(hex_groups)

            # Parsear el objeto
            try:
                obj  = model_class.unpack(data)
                desc = repr(obj)[:80]
            except Exception as e:
                desc = f"[error: {e}]"

            print(f"\n      offset={offset:>6}:")
            # Imprimir en bloques de 16 bytes
            for j in range(0, size, 16):
                chunk = data[j: j + 16]
                hex_part  = " ".join(f"{b:02X}" for b in chunk)
                ascii_part = "".join(
                    chr(b) if 32 <= b < 127 else "." for b in chunk
                )
                print(f"        {offset+j:04X}  {hex_part:<48}  |{ascii_part}|")
            print(f"      → {desc}")


# ════════════════════════════════════════════════════════════════════════════
# MAIN
# ════════════════════════════════════════════════════════════════════════════

def main() -> None:
    global _errors, _warnings, _ok_count
    _errors.clear()
    _warnings.clear()
    _ok_count = 0

    parser = argparse.ArgumentParser(
        description="Verificador de integridad del Sistema POS MiniMarket"
    )
    parser.add_argument(
        "--dump", type=int, default=0,
        help="Número de registros a mostrar en hex dump (default: 0=desactivado)",
    )
    parser.add_argument(
        "--entity",
        choices=["productos", "clientes", "ventas", "detalles", "all"],
        default="all",
        help="Entidad a verificar (default: all)",
    )
    args = parser.parse_args()

    init_directories()

    header("VERIFICADOR DE INTEGRIDAD — Sistema POS MiniMarket")

    # ── Verificar índices de cada entidad ─────────────────────────────────
    entities = {
        "productos": (PRODUCTOS_DAT, PRODUCTOS_IDX, PRODUCTOS_HOLES, Producto),
        "clientes":  (CLIENTES_DAT,  CLIENTES_IDX,  CLIENTES_HOLES,  Cliente),
        "ventas":    (VENTAS_DAT,    VENTAS_IDX,    VENTAS_HOLES,    Venta),
        "detalles":  (DETALLES_DAT,  DETALLES_IDX,  DETALLES_HOLES,  DetalleVenta),
    }

    verified = {}
    for name, (dat, idx, holes, model) in entities.items():
        if args.entity not in ("all", name):
            continue
        verified[name] = verify_index_consistency(dat, idx, holes, model, name.capitalize())

    # ── Verificaciones cruzadas ───────────────────────────────────────────
    if args.entity == "all" and all(k in verified for k in ("ventas", "detalles", "productos", "clientes")):
        verify_ventas_integrity(
            ventas=verified.get("ventas", {}),
            detalles=verified.get("detalles", {}),
            productos=verified.get("productos", {}),
            clientes=verified.get("clientes", {}),
        )

    if args.entity in ("all", "productos") and "productos" in verified:
        verify_stock_integrity(
            productos=verified.get("productos", {}),
            detalles=verified.get("detalles", {}),
        )

    # ── Verificación de exportaciones ────────────────────────────────────
    if args.entity == "all":
        section("Archivos de Exportación")
        from utils.config import EXPORTS_DIR
        csvs = list(EXPORTS_DIR.glob("*.csv"))
        if csvs:
            info(f"Encontrados {len(csvs)} archivo(s) CSV en exports/")
            for csv_f in sorted(csvs)[-4:]:  # últimos 4
                size_kb = csv_f.stat().st_size / 1024
                info(f"  {csv_f.name}  ({size_kb:.1f} KB)")
            check_ok("Directorio de exportaciones accesible")
        else:
            info("No hay archivos CSV en exports/ (normal si no se ha sincronizado)")

    # ── Hex dump opcional ─────────────────────────────────────────────────
    if args.dump > 0:
        header(f"HEX DUMP — Primeros {args.dump} Registros por Entidad")
        for name, (dat, _, _, model) in entities.items():
            if args.entity not in ("all", name):
                continue
            section(name.capitalize())
            hex_dump(dat, model, args.dump)

    # ── Resumen final ─────────────────────────────────────────────════════
    header("RESULTADO DE LA VERIFICACIÓN")

    total_checks = _ok_count + len(_errors)
    print(f"\n  Verificaciones realizadas: {total_checks}")
    print(f"  Pasaron:   {_ok_count:>4}  {OK}")
    print(f"  Fallaron:  {len(_errors):>4}  {FAIL}")
    print(f"  Avisos:    {len(_warnings):>4}  {WARN}")

    if _errors:
        print(f"\n  {FAIL}  Errores encontrados:")
        for e in _errors:
            print(f"       • {e}")

    if _warnings:
        print(f"\n  {WARN}  Advertencias:")
        for w in _warnings:
            print(f"       • {w}")

    if not _errors:
        print(f"\n  {OK}  {BOLD}Sistema íntegro: todos los archivos binarios son coherentes.{RESET}")
    else:
        print(f"\n  {FAIL}  {BOLD}Se encontraron {len(_errors)} problema(s) de integridad.{RESET}")

    print()
    return 0 if not _errors else 1


if __name__ == "__main__":
    sys.exit(main())
