"""
producto_service.py — Servicio CRUD para productos.
Toda persistencia se delega al FileManager (struct + seek()).
"""
from typing import List, Optional

from models.producto import Producto
from services.file_manager import FileManager
from utils.config import PRODUCTOS_DAT, PRODUCTOS_IDX, PRODUCTOS_HOLES
from utils.logger import get_logger

logger = get_logger("producto_service")


class ProductoService:
    """Operaciones CRUD sobre productos usando archivos binarios de acceso directo."""

    def __init__(self) -> None:
        self._fm = FileManager(
            dat_path=PRODUCTOS_DAT,
            idx_path=PRODUCTOS_IDX,
            holes_path=PRODUCTOS_HOLES,
            model_class=Producto,
        )

    # ── Crear ────────────────────────────────────────────────────────────────

    def crear(self, nombre: str, precio: float, stock: int, categoria: str) -> Producto:
        """Registra un nuevo producto en el inventario."""
        if not nombre.strip():
            raise ValueError("El nombre del producto no puede estar vacío.")
        if precio < 0:
            raise ValueError("El precio no puede ser negativo.")
        if stock < 0:
            raise ValueError("El stock no puede ser negativo.")

        producto = Producto(nombre=nombre.strip(), precio=precio,
                            stock=stock, categoria=categoria.strip(), estado=1)
        self._fm.insert(producto)
        logger.info(f"Producto creado: {producto}")
        return producto

    # ── Leer ─────────────────────────────────────────────────────────────────

    def obtener(self, producto_id: int) -> Optional[Producto]:
        """Acceso directo O(1) por ID."""
        return self._fm.find_by_id(producto_id)

    def listar(self) -> List[Producto]:
        """Todos los productos activos."""
        return self._fm.find_all()

    def buscar(self, termino: str) -> List[Producto]:
        """Búsqueda por nombre o categoría (filtra sobre resultados del índice)."""
        t = termino.lower().strip()
        return [
            p for p in self._fm.find_all()
            if t in p.nombre.lower() or t in p.categoria.lower()
        ]

    def obtener_por_ids(self, ids: List[int]) -> List[Producto]:
        """Carga múltiples productos por lista de IDs."""
        result = []
        for pid in ids:
            p = self._fm.find_by_id(pid)
            if p:
                result.append(p)
        return result

    # ── Actualizar ───────────────────────────────────────────────────────────

    def actualizar(
        self,
        producto_id: int,
        nombre:    Optional[str]   = None,
        precio:    Optional[float] = None,
        stock:     Optional[int]   = None,
        categoria: Optional[str]   = None,
    ) -> Optional[Producto]:
        """Modifica campos de un producto existente (escritura in-place)."""
        prod = self._fm.find_by_id(producto_id)
        if prod is None:
            logger.warning(f"Producto id={producto_id} no encontrado para actualizar")
            return None

        if nombre    is not None: prod.nombre    = nombre.strip()
        if precio    is not None: prod.precio    = precio
        if stock     is not None: prod.stock     = stock
        if categoria is not None: prod.categoria = categoria.strip()

        self._fm.update(prod)
        logger.info(f"Producto actualizado: {prod}")
        return prod

    def descontar_stock(self, producto_id: int, cantidad: int) -> bool:
        """
        Descuenta 'cantidad' unidades del stock al procesar una venta.
        Retorna False si el stock es insuficiente.
        """
        prod = self._fm.find_by_id(producto_id)
        if prod is None:
            logger.error(f"Producto id={producto_id} no encontrado para descontar stock")
            return False
        if prod.stock < cantidad:
            logger.warning(
                f"Stock insuficiente: id={producto_id} "
                f"disponible={prod.stock} solicitado={cantidad}"
            )
            return False
        prod.stock -= cantidad
        return self._fm.update(prod)

    def restaurar_stock(self, producto_id: int, cantidad: int) -> bool:
        """Restaura stock al anular una venta."""
        prod = self._fm.find_by_id(producto_id)
        if prod is None:
            return False
        prod.stock += cantidad
        return self._fm.update(prod)

    # ── Eliminar ─────────────────────────────────────────────────────────────

    def eliminar(self, producto_id: int) -> bool:
        """Eliminación lógica (estado=0). El slot queda disponible para reutilizar."""
        ok = self._fm.delete(producto_id)
        if ok:
            logger.info(f"Producto id={producto_id} eliminado lógicamente")
        return ok

    # ── Utilidades ───────────────────────────────────────────────────────────

    def contar(self) -> int:
        return self._fm.count_active()

    def compactar(self) -> int:
        """Compactación física del archivo .dat."""
        return self._fm.compact()

    def exportar_csv(self, export_path) -> int:
        """Exporta todos los productos activos a CSV para sincronización."""
        productos = self.listar()
        with open(export_path, "w", encoding="utf-8") as f:
            f.write("id,nombre,precio,stock,categoria,estado\n")
            for p in productos:
                f.write(p.to_csv_row() + "\n")
        logger.info(f"Exportados {len(productos)} productos a {export_path}")
        return len(productos)
