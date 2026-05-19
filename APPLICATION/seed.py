"""
seed.py — Generador de datos de ejemplo realistas para el Sistema POS MiniMarket.

Crea datos de demostración que permiten:
  - Visualizar el dashboard con estadísticas reales
  - Verificar la integridad de los archivos binarios (.dat/.idx)
  - Probar todos los flujos del sistema (CRUD, ventas, historial)
  - Demostrar acceso directo y reutilización de holes

Datos generados:
  - 23 productos (5 categorías: Bebidas, Lácteos, Snacks, Abarrotes, Limpieza)
  - 10 clientes con DNI y contacto
  - ~50 ventas distribuidas en los últimos 7 días
  - ~120 líneas de detalle de venta

Ejecución:
    cd APPLICATION
    python seed.py
    python seed.py --reset    # borra todo y regenera
    python seed.py --status   # solo muestra el estado actual
"""
import sys
import io
import argparse
import random
from datetime import datetime, timedelta
from pathlib import Path

# Forzar UTF-8 en la salida estándar (necesario en terminales Windows cp1252)
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
else:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# ── sys.path ──────────────────────────────────────────────────────────────────
APP_DIR = Path(__file__).resolve().parent
if str(APP_DIR) not in sys.path:
    sys.path.insert(0, str(APP_DIR))

from utils.config import (
    init_directories, DATA_DIR, EXPORTS_DIR,
    PRODUCTOS_DAT, CLIENTES_DAT, VENTAS_DAT, DETALLES_DAT,
)
from utils.logger import get_logger
from services.producto_service import ProductoService
from services.cliente_service  import ClienteService
from services.venta_service    import VentaService

log = get_logger("seed")

# ════════════════════════════════════════════════════════════════════════════
# DATOS DE EJEMPLO
# ════════════════════════════════════════════════════════════════════════════

# Formato: (nombre, precio, stock, categoria)
PRODUCTOS_DEMO = [
    # ── Bebidas ───────────────────────────────────────────────────────────
    ("Agua San Luis 500ml",        1.50,  200, "Bebidas"),
    ("Inca Kola 1.5L",             6.50,   80, "Bebidas"),
    ("Coca-Cola 1.5L",             6.00,   80, "Bebidas"),
    ("Sprite 500ml",               3.50,  100, "Bebidas"),
    ("Nectar Gloria Durazno",      2.80,   60, "Bebidas"),
    ("Cerveza Pilsen 650ml",       5.00,   48, "Bebidas"),
    # ── Lácteos ───────────────────────────────────────────────────────────
    ("Leche Gloria Evap. 400g",    4.20,  120, "Lacteos"),
    ("Yogurt Gloria Fresa 500g",   5.50,   50, "Lacteos"),
    ("Queso Fresco 200g",          6.80,   30, "Lacteos"),
    # ── Snacks ────────────────────────────────────────────────────────────
    ("Papas Lays 60g",             2.50,  150, "Snacks"),
    ("Galletas Oreo 39g",          1.80,  100, "Snacks"),
    ("Chifles Natural 50g",        2.00,   80, "Snacks"),
    ("Chocolate Sublime",          2.50,  120, "Snacks"),
    ("Chicles Trident x10",        1.00,  200, "Snacks"),
    # ── Abarrotes ─────────────────────────────────────────────────────────
    ("Arroz Costeno 1kg",          4.50,  100, "Abarrotes"),
    ("Azucar Rubia 1kg",           3.80,   80, "Abarrotes"),
    ("Aceite Cocinero 900ml",      8.90,   60, "Abarrotes"),
    ("Fideos Don Vittorio 500g",   3.20,   90, "Abarrotes"),
    ("Atun Florida 170g",          4.50,   70, "Abarrotes"),
    ("Pan Molde Wonder",           5.90,   40, "Abarrotes"),
    # ── Limpieza ──────────────────────────────────────────────────────────
    ("Jabon Bolivar 240g",         3.50,   80, "Limpieza"),
    ("Detergente Ariel 500g",      7.90,   50, "Limpieza"),
    ("Papel Higienico Elite x4",   8.50,   40, "Limpieza"),
]

# Formato: (nombre, dni, telefono, email)
CLIENTES_DEMO = [
    ("Carlos Mamani Quispe",    "45678901", "987654321", "c.mamani@gmail.com"),
    ("Maria Lopez Torres",      "32145678", "976543210", "m.lopez@hotmail.com"),
    ("Juan Huanca Flores",      "71234567", "965432109", "j.huanca@gmail.com"),
    ("Rosa Condori Paredes",    "58976543", "954321098", "r.condori@yahoo.com"),
    ("Pedro Vasquez Lima",      "63214567", "943210987", "p.vasquez@outlook.com"),
    ("Ana Quispe Mendoza",      "29876543", "932109876", "a.quispe@gmail.com"),
    ("Luis Gutierrez Salas",    "40123456", "921098765", "l.gutierrez@gmail.com"),
    ("Carmen Ramos Chavez",     "55432198", "910987654", "c.ramos@hotmail.com"),
    ("Jorge Llanos Apaza",      "67891234", "999888777", "j.llanos@gmail.com"),
    ("Elena Soto Palomino",     "38765432", "988776655", "e.soto@yahoo.com"),
]

# Plantillas de compras realistas: (lista de índices de productos, es_cliente)
# Los índices apuntan a PRODUCTOS_DEMO (base 1, se resuelven después de insertar)
COMPRAS_PLANTILLAS = [
    # Compra de desayuno
    ([1, 7, 11], True),     # agua + leche + galletas
    ([7, 8, 11, 14], True), # leche + yogurt + galletas + chicles
    ([1, 1, 11, 11], False),# 2 aguas + 2 galletas (anónimo)
    # Compra de almuerzo
    ([15, 16, 17, 19], True), # arroz + azúcar + aceite + atún
    ([15, 18, 19], False),    # arroz + fideos + atún (anónimo)
    ([17, 15, 16, 20], True), # aceite + arroz + azúcar + pan molde
    # Snacks y bebidas
    ([1, 2, 10, 13], True),  # agua + inca kola + papas + chocolate
    ([2, 3, 10, 12], False), # inca kola + coca-cola + papas + chifles
    ([4, 5, 10, 11], True),  # sprite + néctar + papas + galletas
    ([6, 10, 12, 13], False),# cerveza + papas + chifles + chocolate
    # Limpieza del hogar
    ([21, 22, 23], True),    # jabón + detergente + papel higiénico
    ([21, 22], False),       # jabón + detergente
    # Compras mixtas
    ([1, 7, 15, 21], True),  # agua + leche + arroz + jabón
    ([2, 9, 15, 18], True),  # inca kola + queso + arroz + fideos
    ([3, 7, 10, 22], False), # cola + leche + papas + detergente
    ([1, 5, 11, 13], True),  # agua + néctar + galletas + chocolate
    ([4, 8, 14], False),     # sprite + yogurt + chicles
    ([6, 12, 13], True),     # cerveza + chifles + chocolate
    ([15, 16, 7, 21], True), # arroz + azúcar + leche + jabón
    ([1, 2, 3], False),      # bebidas variadas (anónimo)
]


# ════════════════════════════════════════════════════════════════════════════
# UTILIDADES
# ════════════════════════════════════════════════════════════════════════════

def _print_header(title: str) -> None:
    width = 62
    print(f"\n{'═' * width}")
    print(f"  {title}")
    print(f"{'═' * width}")


def _print_section(title: str) -> None:
    print(f"\n  ── {title} {'─' * (56 - len(title))}")


def _print_ok(msg: str) -> None:
    print(f"    ✓  {msg}")


def _print_skip(msg: str) -> None:
    print(f"    ·  {msg} (ya existe)")


def _print_info(msg: str) -> None:
    print(f"    ℹ  {msg}")


# ════════════════════════════════════════════════════════════════════════════
# RESETEO
# ════════════════════════════════════════════════════════════════════════════

def reset_data() -> None:
    """Elimina todos los archivos binarios de DATA/ para empezar de cero."""
    _print_header("RESETEO DE DATOS")
    extensions = (".dat", ".idx", ".holes")
    deleted = 0
    for ext in extensions:
        for f in DATA_DIR.glob(f"*{ext}"):
            f.unlink()
            _print_ok(f"Eliminado: {f.name}")
            deleted += 1
    if deleted == 0:
        _print_info("No había archivos que eliminar.")
    else:
        _print_info(f"{deleted} archivo(s) eliminado(s).")


# ════════════════════════════════════════════════════════════════════════════
# SEMBRADO DE PRODUCTOS
# ════════════════════════════════════════════════════════════════════════════

def seed_productos(svc: ProductoService) -> dict:
    """
    Inserta los productos de demostración.
    Retorna un dict { nombre_normalizado → producto_id }.
    """
    _print_section("Productos")
    id_map = {}
    existentes = {p.nombre.lower(): p.id for p in svc.listar()}

    for nombre, precio, stock, categoria in PRODUCTOS_DEMO:
        key = nombre.lower()
        if key in existentes:
            _print_skip(f"{nombre:<35} S/{precio:.2f}  stock={stock}")
            id_map[nombre] = existentes[key]
        else:
            p = svc.crear(nombre, precio, stock, categoria)
            _print_ok(f"#{p.id:02d} {nombre:<33} S/{precio:.2f}  stock={stock}  [{categoria}]")
            id_map[nombre] = p.id

    return id_map


# ════════════════════════════════════════════════════════════════════════════
# SEMBRADO DE CLIENTES
# ════════════════════════════════════════════════════════════════════════════

def seed_clientes(svc: ClienteService) -> dict:
    """
    Inserta los clientes de demostración.
    Retorna dict { indice_base1 → cliente_id }.
    """
    _print_section("Clientes")
    id_map = {}

    for i, (nombre, dni, tel, email) in enumerate(CLIENTES_DEMO, start=1):
        existente = svc.buscar_por_dni(dni)
        if existente:
            _print_skip(f"{nombre:<30} DNI: {dni}")
            id_map[i] = existente.id
        else:
            c = svc.crear(nombre, dni, tel, email)
            _print_ok(f"#{c.id:02d} {nombre:<28} DNI: {dni}")
            id_map[i] = c.id

    return id_map


# ════════════════════════════════════════════════════════════════════════════
# SEMBRADO DE VENTAS
# ════════════════════════════════════════════════════════════════════════════

def seed_ventas(
    venta_svc:  VentaService,
    prod_svc:   ProductoService,
    cli_svc:    ClienteService,
    prod_id_list: list,
    cli_id_list:  list,
) -> list:
    """
    Crea ventas históricas distribuidas en los últimos 7 días.

    Estrategia de fechas:
      - Día 7 atrás: 3-4 ventas (apertura de semana)
      - Días 6-2:    5-7 ventas por día (actividad normal)
      - Ayer:        6-8 ventas (pico)
      - Hoy:         4-6 ventas (hasta la hora actual)

    Para fechas pasadas se modifica el campo 'fecha' del registro
    directamente mediante update() después de la inserción.
    """
    _print_section("Ventas históricas (últimos 7 días)")
    now   = datetime.now()
    created = []

    # Definir distribución de ventas por día
    distribucion = [
        (7, 4),   # hace 7 días → 4 ventas
        (6, 5),   # hace 6 días → 5 ventas
        (5, 6),   # hace 5 días → 6 ventas
        (4, 7),   # hace 4 días → 7 ventas
        (3, 6),   # hace 3 días → 6 ventas
        (2, 7),   # hace 2 días → 7 ventas
        (1, 8),   # ayer        → 8 ventas
        (0, 5),   # hoy         → 5 ventas
    ]

    # Horas de operación: 7:00 am – 9:00 pm (distribuidas aleatoriamente)
    horas_pico = list(range(8, 13)) + list(range(17, 21))
    horas_normal = list(range(7, 21))

    # Reutilizar plantillas cíclicamente
    plantilla_idx = 0
    total_ventas = sum(n for _, n in distribucion)
    ventas_existentes = venta_svc.contar_ventas()

    if ventas_existentes >= total_ventas:
        _print_skip(f"{ventas_existentes} ventas ya presentes en el sistema")
        return []

    random.seed(42)  # Semilla fija para reproducibilidad

    # Forzar recarga del índice de productos dentro de VentaService.
    # El ProductoService interno se construyó antes de que los productos
    # fueran insertados, así que su índice en memoria estaba vacío.
    venta_svc._prod_svc._fm.index_manager.reload()

    for dias_atras, n_ventas in distribucion:
        fecha_base = now - timedelta(days=dias_atras)
        dia_str    = fecha_base.strftime("%d/%m/%Y")
        dia_ventas = []

        # Elegir horas aleatorias para este día
        if dias_atras == 0:
            horas_disponibles = list(range(7, now.hour + 1)) or [8]
        else:
            horas_disponibles = horas_normal

        horas_elegidas = sorted(random.choices(
            horas_pico if dias_atras in (1, 2) else horas_disponibles,
            k=n_ventas
        ))

        for hora in horas_elegidas:
            minuto  = random.randint(0, 59)
            segundo = random.randint(0, 59)
            fecha_dt = fecha_base.replace(
                hour=hora, minute=minuto, second=segundo,
                microsecond=0,
            )
            fecha_str = fecha_dt.strftime("%Y-%m-%d %H:%M:%S")

            # Seleccionar plantilla de compra
            plantilla, tiene_cliente = COMPRAS_PLANTILLAS[plantilla_idx % len(COMPRAS_PLANTILLAS)]
            plantilla_idx += 1

            # Construir ítems: (prod_id_real, cantidad)
            items = []
            for prod_idx_1based in plantilla:
                prod_idx = prod_idx_1based - 1  # base 0
                if prod_idx < len(prod_id_list):
                    prod_id = prod_id_list[prod_idx]
                    # Cantidad: 1-3 unidades aleatorias
                    cantidad = random.choices([1, 2, 3], weights=[60, 30, 10])[0]
                    items.append((prod_id, cantidad))

            if not items:
                continue

            # Asignar cliente (60% con cliente registrado)
            if tiene_cliente and cli_id_list and random.random() < 0.6:
                cliente_id = random.choice(cli_id_list)
            else:
                cliente_id = 0

            # Crear la venta con la fecha del día de hoy (se corregirá)
            venta = venta_svc.crear_venta(items, cliente_id)
            if venta is None:
                continue  # Stock agotado; omitir esta venta

            # Corregir la fecha al día histórico
            if dias_atras > 0 or fecha_str < now.strftime("%Y-%m-%d %H:%M:%S"):
                venta.fecha = fecha_str
                venta_svc._venta_fm.update(venta)

            dia_ventas.append(venta)
            created.append(venta)

        total_dia = sum(v.total for v in dia_ventas)
        _print_ok(
            f"{dia_str}  →  {len(dia_ventas):2d} venta(s)  "
            f"recaudado: S/ {total_dia:8.2f}"
        )

    return created


# ════════════════════════════════════════════════════════════════════════════
# DEMOSTRACIÓN DE ELIMINACIÓN LÓGICA Y HOLES
# ════════════════════════════════════════════════════════════════════════════

def demo_eliminacion_y_reutilizacion(prod_svc: ProductoService) -> None:
    """
    Demuestra el ciclo completo:
      1. Crear producto temporal
      2. Eliminarlo lógicamente (estado=0, slot → holes)
      3. Crear otro producto (reutiliza el hole)
    """
    _print_section("Demo: Eliminación lógica y reutilización de holes")

    # 1. Crear producto temporal
    p_temp = prod_svc.crear(
        nombre="Producto Temporal (eliminar)",
        precio=9.99, stock=1, categoria="Demo",
    )
    offset_antes = prod_svc._fm._index.get_offset(p_temp.id)
    _print_ok(f"Creado #{p_temp.id} 'Producto Temporal' en offset={offset_antes}")

    # 2. Eliminar lógicamente
    prod_svc.eliminar(p_temp.id)
    holes_count = len(prod_svc._fm.index_manager._holes)
    _print_ok(
        f"Eliminado lógicamente #{p_temp.id} — "
        f"offset={offset_antes} liberado → {holes_count} hole(s) disponible(s)"
    )

    # 3. Verificar que ya no aparece en listar()
    encontrado = prod_svc.obtener(p_temp.id)
    assert encontrado is None, "El registro eliminado no debe ser accesible por ID"
    _print_ok("Verificado: ya no es accesible por find_by_id()")

    # 4. Crear nuevo producto (reutiliza el hole)
    p_nuevo = prod_svc.crear(
        nombre="Mascarillas Circulon x10",
        precio=12.50, stock=25, categoria="Cuidado Personal",
    )
    offset_nuevo = prod_svc._fm._index.get_offset(p_nuevo.id)
    reutilizado = (offset_nuevo == offset_antes)
    _print_ok(
        f"Creado #{p_nuevo.id} 'Mascarillas Circulon' en offset={offset_nuevo} "
        f"{'← REUTILIZÓ el hole!' if reutilizado else '(nuevo al final)'}"
    )


# ════════════════════════════════════════════════════════════════════════════
# REPORTE DE ESTADO
# ════════════════════════════════════════════════════════════════════════════

def print_status(
    prod_svc:  ProductoService,
    cli_svc:   ClienteService,
    venta_svc: VentaService,
) -> None:
    """Imprime un resumen completo del estado del sistema."""
    _print_header("ESTADO DEL SISTEMA POS")

    productos = prod_svc.listar()
    clientes  = cli_svc.listar()
    ventas    = venta_svc.listar_ventas()
    detalles  = venta_svc.listar_detalles()

    # ── Estadísticas generales ────────────────────────────────────────────
    _print_section("Estadísticas Generales")
    total_ingresos = sum(v.total for v in ventas)
    ventas_hoy     = venta_svc.ventas_hoy()
    total_hoy      = venta_svc.total_hoy()
    items_total    = sum(d.cantidad for d in detalles)

    _print_info(f"Productos activos:      {len(productos):>6}")
    _print_info(f"Clientes registrados:   {len(clientes):>6}")
    _print_info(f"Ventas totales:         {len(ventas):>6}")
    _print_info(f"Ítems vendidos total:   {items_total:>6}")
    _print_info(f"Ingresos totales:       S/ {total_ingresos:>10.2f}")
    _print_info(f"Ventas hoy:             {len(ventas_hoy):>6}")
    _print_info(f"Ingresos hoy:           S/ {total_hoy:>10.2f}")

    # ── Archivos binarios ─────────────────────────────────────────────────
    _print_section("Archivos Binarios en DATA/")
    files_info = [
        ("productos.dat",  PRODUCTOS_DAT,  97,  "Productos"),
        ("clientes.dat",   CLIENTES_DAT,  131, "Clientes"),
        ("ventas.dat",     VENTAS_DAT,    52,  "Ventas"),
        ("detalles.dat",   DETALLES_DAT,  33,  "Detalles"),
    ]
    for fname, fpath, rec_size, label in files_info:
        if fpath.exists():
            size_bytes   = fpath.stat().st_size
            n_registros  = size_bytes // rec_size
            _print_info(
                f"{fname:<22} {size_bytes:>7} bytes  "
                f"{n_registros:>4} registros × {rec_size}B"
            )
        else:
            _print_info(f"{fname:<22} (no existe)")

    # ── Índices ───────────────────────────────────────────────────────────
    _print_section("Estado de Índices (.idx / .holes)")
    for svc_obj, label in [
        (prod_svc._fm.index_manager, "productos"),
        (cli_svc._fm.index_manager,  "clientes"),
        (venta_svc._venta_fm.index_manager,  "ventas"),
        (venta_svc._detalle_fm.index_manager, "detalles"),
    ]:
        n_activos = svc_obj.count()
        n_holes   = len(svc_obj._holes)
        next_id   = svc_obj.next_id()
        _print_info(
            f"{label:<12}  activos={n_activos:>4}  "
            f"holes={n_holes:>3}  next_id={next_id}"
        )

    # ── Productos con stock bajo ───────────────────────────────────────────
    bajos = [p for p in productos if p.stock <= 10]
    if bajos:
        _print_section("Alerta: Productos con Stock ≤ 10 unidades")
        for p in sorted(bajos, key=lambda x: x.stock):
            _print_info(f"#{p.id:02d} {p.nombre:<35} stock={p.stock:>3}  [{p.categoria}]")

    # ── Top 5 clientes por número de ventas ───────────────────────────────
    _print_section("Clientes con más compras")
    cliente_compras: dict = {}
    for v in ventas:
        if v.cliente_id > 0:
            cliente_compras[v.cliente_id] = cliente_compras.get(v.cliente_id, 0) + 1
    top5 = sorted(cliente_compras.items(), key=lambda x: x[1], reverse=True)[:5]
    for cli_id, n_compras in top5:
        c = cli_svc.obtener(cli_id)
        if c:
            _print_info(f"#{cli_id:02d} {c.nombre:<35} {n_compras} compra(s)")

    # ── Ventas por día (resumen semanal) ──────────────────────────────────
    _print_section("Resumen de Ventas por Día (últimos 7 días)")
    ventas_por_dia: dict = {}
    for v in ventas:
        dia = v.fecha[:10]
        if dia not in ventas_por_dia:
            ventas_por_dia[dia] = {"count": 0, "total": 0.0}
        ventas_por_dia[dia]["count"]  += 1
        ventas_por_dia[dia]["total"]  += v.total

    now = datetime.now()
    for dias_atras in range(7, -1, -1):
        dia = (now - timedelta(days=dias_atras)).strftime("%Y-%m-%d")
        if dia in ventas_por_dia:
            info  = ventas_por_dia[dia]
            barra = "█" * min(20, info["count"] * 2)
            _print_info(
                f"{dia}  {barra:<22}  "
                f"{info['count']:2d} venta(s)  S/ {info['total']:8.2f}"
            )

    # ── Productos más vendidos ─────────────────────────────────────────────
    _print_section("Top 5 Productos Más Vendidos")
    ventas_prod: dict = {}
    for d in detalles:
        if d.producto_id not in ventas_prod:
            ventas_prod[d.producto_id] = {"cantidad": 0, "total": 0.0}
        ventas_prod[d.producto_id]["cantidad"] += d.cantidad
        ventas_prod[d.producto_id]["total"]    += d.subtotal

    top_prod = sorted(ventas_prod.items(),
                      key=lambda x: x[1]["cantidad"], reverse=True)[:5]
    for prod_id, stats in top_prod:
        p = prod_svc.obtener(prod_id)
        if p:
            _print_info(
                f"#{prod_id:02d} {p.nombre:<35} "
                f"{stats['cantidad']:3d} uds  S/ {stats['total']:8.2f}"
            )


# ════════════════════════════════════════════════════════════════════════════
# PUNTO DE ENTRADA
# ════════════════════════════════════════════════════════════════════════════

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generador de datos de ejemplo para el Sistema POS MiniMarket"
    )
    parser.add_argument(
        "--reset",  action="store_true",
        help="Eliminar todos los datos existentes antes de sembrar",
    )
    parser.add_argument(
        "--status", action="store_true",
        help="Solo mostrar el estado actual sin insertar datos",
    )
    args = parser.parse_args()

    init_directories()

    prod_svc  = ProductoService()
    cli_svc   = ClienteService()
    venta_svc = VentaService()

    if args.status:
        print_status(prod_svc, cli_svc, venta_svc)
        return

    _print_header("SEMBRADO DE DATOS DE EJEMPLO — MiniMarket El Ahorro")

    if args.reset:
        reset_data()
        # Re-instanciar servicios para que lean el estado limpio
        prod_svc  = ProductoService()
        cli_svc   = ClienteService()
        venta_svc = VentaService()

    # ── 1. Productos ─────────────────────────────────────────────────────────
    prod_name_map = seed_productos(prod_svc)
    prod_ids      = [prod_name_map[nombre] for nombre, *_ in PRODUCTOS_DEMO]

    # ── 2. Clientes ───────────────────────────────────────────────────────────
    cli_idx_map  = seed_clientes(cli_svc)
    cli_ids      = list(cli_idx_map.values())

    # ── 3. Ventas históricas ──────────────────────────────────────────────────
    ventas_creadas = seed_ventas(venta_svc, prod_svc, cli_svc, prod_ids, cli_ids)

    # ── 4. Demo eliminación lógica + reutilización ────────────────────────────
    demo_eliminacion_y_reutilizacion(prod_svc)

    # ── 5. Resumen final ──────────────────────────────────────────────────────
    _print_header("RESUMEN FINAL")
    total_prod  = prod_svc.contar()
    total_cli   = cli_svc.contar()
    total_ven   = venta_svc.contar_ventas()
    total_det   = len(venta_svc.listar_detalles())
    total_ing   = sum(v.total for v in venta_svc.listar_ventas())

    print(f"""
  Sistema poblado con datos de ejemplo:

    Productos:          {total_prod:>5} registros  [{PRODUCTOS_DAT.stat().st_size:>7} bytes en productos.dat]
    Clientes:           {total_cli:>5} registros  [{CLIENTES_DAT.stat().st_size:>7} bytes en clientes.dat]
    Ventas:             {total_ven:>5} registros  [{VENTAS_DAT.stat().st_size:>7} bytes en ventas.dat]
    Detalles de venta:  {total_det:>5} registros  [{DETALLES_DAT.stat().st_size:>7} bytes en detalles.dat]
    ──────────────────────────────────────────
    Ingresos totales:   S/ {total_ing:.2f}

  Ejecución:
    python main.py      ← Abrir el POS y ver los datos
    python verify.py    ← Verificar integridad binaria
    python seed.py --status  ← Ver estadísticas detalladas
""")

    log.info(
        f"Seed completado: {total_prod} productos, {total_cli} clientes, "
        f"{total_ven} ventas, S/{total_ing:.2f} en ingresos"
    )


if __name__ == "__main__":
    main()
