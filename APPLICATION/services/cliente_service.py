"""
cliente_service.py — Servicio CRUD para clientes.
Toda persistencia se delega al FileManager (struct + seek()).
"""
import csv
from typing import List, Optional

from models.cliente import Cliente
from services.file_manager import FileManager
from utils.config import CLIENTES_DAT, CLIENTES_IDX, CLIENTES_HOLES
from utils.logger import get_logger

logger = get_logger("cliente_service")


class ClienteService:
    """Operaciones CRUD sobre clientes usando archivos binarios de acceso directo."""

    def __init__(self) -> None:
        self._fm = FileManager(
            dat_path=CLIENTES_DAT,
            idx_path=CLIENTES_IDX,
            holes_path=CLIENTES_HOLES,
            model_class=Cliente,
        )

    # ── Crear ────────────────────────────────────────────────────────────────

    def crear(self, nombre: str, dni: str, telefono: str = "", email: str = "") -> Cliente:
        """Registra un nuevo cliente."""
        if not nombre.strip():
            raise ValueError("El nombre del cliente no puede estar vacío.")
        if not dni.strip():
            raise ValueError("El DNI no puede estar vacío.")
        # Verificar DNI duplicado
        if self.buscar_por_dni(dni):
            raise ValueError(f"Ya existe un cliente con DNI {dni}.")

        cliente = Cliente(
            nombre=nombre.strip(), dni=dni.strip(),
            telefono=telefono.strip(), email=email.strip(), estado=1,
        )
        self._fm.insert(cliente)
        logger.info(f"Cliente creado: {cliente}")
        return cliente

    # ── Leer ─────────────────────────────────────────────────────────────────

    def obtener(self, cliente_id: int) -> Optional[Cliente]:
        """Acceso directo O(1) por ID."""
        return self._fm.find_by_id(cliente_id)

    def listar(self) -> List[Cliente]:
        """Todos los clientes activos."""
        return self._fm.find_all()

    def buscar(self, termino: str) -> List[Cliente]:
        """Búsqueda por nombre o DNI."""
        t = termino.lower().strip()
        return [
            c for c in self._fm.find_all()
            if t in c.nombre.lower() or t in c.dni.lower()
        ]

    def buscar_por_dni(self, dni: str) -> Optional[Cliente]:
        """Busca un cliente específico por su DNI."""
        dni_clean = dni.strip()
        for c in self._fm.find_all():
            if c.dni == dni_clean:
                return c
        return None

    # ── Actualizar ───────────────────────────────────────────────────────────

    def actualizar(
        self,
        cliente_id: int,
        nombre:   Optional[str] = None,
        dni:      Optional[str] = None,
        telefono: Optional[str] = None,
        email:    Optional[str] = None,
    ) -> Optional[Cliente]:
        """Modifica campos de un cliente existente."""
        cli = self._fm.find_by_id(cliente_id)
        if cli is None:
            logger.warning(f"Cliente id={cliente_id} no encontrado para actualizar")
            return None

        if nombre   is not None: cli.nombre   = nombre.strip()
        if dni      is not None: cli.dni      = dni.strip()
        if telefono is not None: cli.telefono = telefono.strip()
        if email    is not None: cli.email    = email.strip()

        self._fm.update(cli)
        logger.info(f"Cliente actualizado: {cli}")
        return cli

    # ── Eliminar ─────────────────────────────────────────────────────────────

    def eliminar(self, cliente_id: int) -> bool:
        """Eliminación lógica (estado=0)."""
        ok = self._fm.delete(cliente_id)
        if ok:
            logger.info(f"Cliente id={cliente_id} eliminado lógicamente")
        return ok

    # ── Utilidades ───────────────────────────────────────────────────────────

    def contar(self) -> int:
        return self._fm.count_active()

    def exportar_csv(self, export_path) -> int:
        """Exporta todos los clientes activos a CSV para sincronización."""
        clientes = self.listar()
        with open(export_path, "w", encoding="utf-8", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["id", "nombre", "dni", "telefono", "email", "estado"])
            for c in clientes:
                writer.writerow([c.id, c.nombre, c.dni,
                                  c.telefono, c.email, c.estado])
        logger.info(f"Exportados {len(clientes)} clientes a {export_path}")
        return len(clientes)
