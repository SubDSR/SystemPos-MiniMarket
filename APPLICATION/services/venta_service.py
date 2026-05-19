"""
venta_service.py — Servicio para gestión de ventas y sus detalles.

Coordina tres archivos binarios:
  - ventas.dat / ventas.idx      → cabecera de la venta
  - detalles.dat / detalles.idx  → líneas de producto por venta
  - productos.dat / productos.idx → actualización de stock
"""
import csv
from datetime import datetime
from typing import Dict, List, Optional, Tuple

from models.venta import Venta
from models.detalle_venta import DetalleVenta
from services.file_manager import FileManager
from services.producto_service import ProductoService
from utils.config import (
    VENTAS_DAT, VENTAS_IDX, VENTAS_HOLES,
    DETALLES_DAT, DETALLES_IDX, DETALLES_HOLES,
    IGV_RATE,
)
from utils.logger import get_logger

logger = get_logger("venta_service")


class VentaService:
    """
    Gestiona el ciclo completo de una venta:
      1. Crear la venta con cliente (opcional) y calcular montos
      2. Guardar cabecera en ventas.dat
      3. Guardar detalles en detalles.dat
      4. Descontar stock de cada producto en productos.dat
    """

    def __init__(self) -> None:
        self._venta_fm = FileManager(
            dat_path=VENTAS_DAT, idx_path=VENTAS_IDX,
            holes_path=VENTAS_HOLES, model_class=Venta,
        )
        self._detalle_fm = FileManager(
            dat_path=DETALLES_DAT, idx_path=DETALLES_IDX,
            holes_path=DETALLES_HOLES, model_class=DetalleVenta,
        )
        self._prod_svc = ProductoService()

    # ════════════════════════════════════════════════════════════════════════
    # CREAR VENTA
    # ════════════════════════════════════════════════════════════════════════

    def crear_venta(
        self,
        items: List[Tuple[int, int]],   # [(producto_id, cantidad), ...]
        cliente_id: int = 0,
    ) -> Optional[Venta]:
        """
        Registra una venta completa.

        Proceso:
          1. Validar stock disponible para cada producto
          2. Calcular subtotales, IGV (18%) y total
          3. Guardar cabecera en ventas.dat
          4. Guardar cada línea en detalles.dat
          5. Descontar stock en productos.dat

        Args:
            items:      Lista de (producto_id, cantidad)
            cliente_id: ID del cliente (0 = venta anónima)

        Returns:
            Objeto Venta creado, o None si falló validación
        """
        if not items:
            logger.warning("Intento de crear venta sin ítems")
            return None

        # ── 1. Validar stock ─────────────────────────────────────────────────
        detalles_data: List[Dict] = []
        for prod_id, cantidad in items:
            if cantidad <= 0:
                logger.warning(f"Cantidad inválida: prod={prod_id} cant={cantidad}")
                return None
            prod = self._prod_svc.obtener(prod_id)
            if prod is None:
                logger.error(f"Producto id={prod_id} no encontrado")
                return None
            if prod.stock < cantidad:
                logger.warning(
                    f"Stock insuficiente: prod={prod_id} "
                    f"disponible={prod.stock} solicitado={cantidad}"
                )
                return None
            detalles_data.append({
                "prod_id":  prod_id,
                "cantidad": cantidad,
                "precio":   prod.precio,
                "subtotal": round(prod.precio * cantidad, 2),
            })

        # ── 2. Calcular montos ────────────────────────────────────────────────
        subtotal_sum = round(sum(d["subtotal"] for d in detalles_data), 2)
        igv          = round(subtotal_sum * IGV_RATE, 2)
        total        = round(subtotal_sum + igv, 2)
        fecha        = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        # ── 3. Guardar cabecera de venta ──────────────────────────────────────
        venta = Venta(
            cliente_id=cliente_id,
            fecha=fecha,
            subtotal=subtotal_sum,
            igv=igv,
            total=total,
            estado=1,
        )
        self._venta_fm.insert(venta)

        # ── 4. Guardar detalles ───────────────────────────────────────────────
        for d in detalles_data:
            detalle = DetalleVenta(
                venta_id=venta.id,
                producto_id=d["prod_id"],
                cantidad=d["cantidad"],
                precio_unitario=d["precio"],
                subtotal=d["subtotal"],
                estado=1,
            )
            self._detalle_fm.insert(detalle)

        # ── 5. Descontar stock ────────────────────────────────────────────────
        for d in detalles_data:
            self._prod_svc.descontar_stock(d["prod_id"], d["cantidad"])

        logger.info(
            f"Venta registrada: id={venta.id} total=S/{total:.2f} "
            f"ítems={len(items)} cliente={cliente_id}"
        )
        return venta

    # ════════════════════════════════════════════════════════════════════════
    # CONSULTAS
    # ════════════════════════════════════════════════════════════════════════

    def obtener_venta(self, venta_id: int) -> Optional[Venta]:
        """Retorna una venta por ID."""
        return self._venta_fm.find_by_id(venta_id)

    def listar_ventas(self) -> List[Venta]:
        """Todas las ventas activas."""
        return self._venta_fm.find_all()

    def obtener_detalles(self, venta_id: int) -> List[DetalleVenta]:
        """Retorna las líneas de detalle de una venta específica."""
        return [
            d for d in self._detalle_fm.find_all()
            if d.venta_id == venta_id
        ]

    def listar_detalles(self) -> List[DetalleVenta]:
        """Todos los detalles activos."""
        return self._detalle_fm.find_all()

    def ventas_hoy(self) -> List[Venta]:
        """Ventas realizadas en el día de hoy."""
        hoy = datetime.now().strftime("%Y-%m-%d")
        return [v for v in self._venta_fm.find_all() if v.fecha.startswith(hoy)]

    def total_hoy(self) -> float:
        """Suma de totales de las ventas del día."""
        return round(sum(v.total for v in self.ventas_hoy()), 2)

    def contar_ventas(self) -> int:
        return self._venta_fm.count_active()

    # ════════════════════════════════════════════════════════════════════════
    # ANULAR VENTA
    # ════════════════════════════════════════════════════════════════════════

    def anular_venta(self, venta_id: int) -> bool:
        """
        Anula una venta: estado=0 y restaura el stock de cada producto.
        """
        venta = self._venta_fm.find_by_id(venta_id)
        if venta is None:
            return False

        # Restaurar stock
        detalles = self.obtener_detalles(venta_id)
        for det in detalles:
            self._prod_svc.restaurar_stock(det.producto_id, det.cantidad)
            self._detalle_fm.delete(det.id)

        ok = self._venta_fm.delete(venta_id)
        if ok:
            logger.info(f"Venta id={venta_id} anulada correctamente")
        return ok

    # ════════════════════════════════════════════════════════════════════════
    # EXPORTACIÓN
    # ════════════════════════════════════════════════════════════════════════

    def exportar_ventas_csv(self, export_path) -> int:
        """Exporta ventas a CSV para sincronización con el servidor."""
        ventas = self.listar_ventas()
        with open(export_path, "w", encoding="utf-8", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["id", "cliente_id", "fecha",
                              "subtotal", "igv", "total", "estado"])
            for v in ventas:
                writer.writerow([v.id, v.cliente_id, v.fecha,
                                  f"{v.subtotal:.2f}", f"{v.igv:.2f}",
                                  f"{v.total:.2f}", v.estado])
        logger.info(f"Exportadas {len(ventas)} ventas a {export_path}")
        return len(ventas)

    def exportar_detalles_csv(self, export_path) -> int:
        """Exporta detalles de venta a CSV para sincronización."""
        detalles = self.listar_detalles()
        with open(export_path, "w", encoding="utf-8", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["id", "venta_id", "producto_id", "cantidad",
                              "precio_unitario", "subtotal", "estado"])
            for d in detalles:
                writer.writerow([d.id, d.venta_id, d.producto_id,
                                  d.cantidad, f"{d.precio_unitario:.2f}",
                                  f"{d.subtotal:.2f}", d.estado])
        logger.info(f"Exportados {len(detalles)} detalles a {export_path}")
        return len(detalles)
