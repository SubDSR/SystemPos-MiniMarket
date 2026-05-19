"""
file_manager.py — Gestor de archivos de acceso directo (.dat).

CONCEPTO ACADÉMICO: Acceso Directo con Archivos Binarios de Registro Fijo
═══════════════════════════════════════════════════════════════════════════════
Un archivo .dat almacena registros de longitud FIJA. Dado que todos los
registros ocupan exactamente RECORD_SIZE bytes, la posición de cualquier
registro se puede calcular o consultar en O(1) con seek():

    Estructura del archivo .dat:
    ┌─────────────────────────────────────────────────────────┐
    │  Registro 0  →  offset = 0                             │
    │  [  id  |  nombre  |  precio  |  stock  |  cat  |  est ]│
    ├─────────────────────────────────────────────────────────┤
    │  Registro 1  →  offset = RECORD_SIZE                   │
    │  [  id  |  nombre  |  precio  |  stock  |  cat  |  est ]│
    ├─────────────────────────────────────────────────────────┤
    │  Registro N  →  offset = N × RECORD_SIZE               │
    └─────────────────────────────────────────────────────────┘

ALGORITMO DE INSERCIÓN:
    1. ¿Hay slots libres (holes)?  → reutilizar offset del hole
    2. Si no hay holes             → offset = fin del archivo (append)
    3. file.seek(offset)           → posicionarse en el lugar correcto
    4. file.write(record.pack())   → escribir registro de tamaño fijo
    5. index.add(id, offset)       → actualizar índice .idx

ALGORITMO DE BÚSQUEDA POR ID (O(1)):
    offset = index.get_offset(id)  → lookup en dict
    file.seek(offset)              → posicionamiento directo
    data = file.read(RECORD_SIZE)  → leer exactamente SIZE bytes
    return Model.unpack(data)      → deserializar

ELIMINACIÓN LÓGICA:
    - Se escribe estado=0 en el campo 'estado' del registro
    - Se remueve del índice (ya no es accesible por ID)
    - El offset se agrega a la lista de holes para reutilización
"""
from __future__ import annotations

from pathlib import Path
from typing import Generic, List, Optional, Type, TypeVar

from services.index_manager import IndexManager
from utils.logger import get_logger

T = TypeVar("T")
logger = get_logger("file_manager")


class FileManager(Generic[T]):
    """
    Gestiona un archivo de datos binario (.dat) con acceso directo.

    Combina:
      - Archivo .dat: registros binarios de longitud fija
      - Archivo .idx: índice clave→offset para acceso O(1)
      - Archivo .holes: lista de slots libres para reutilización

    Args:
        dat_path:    Ruta al archivo .dat
        idx_path:    Ruta al archivo .idx
        holes_path:  Ruta al archivo .holes
        model_class: Clase del modelo (debe tener FORMAT, SIZE, pack(), unpack())
    """

    def __init__(
        self,
        dat_path:    Path,
        idx_path:    Path,
        holes_path:  Path,
        model_class: Type[T],
    ) -> None:
        self._dat_path    = dat_path
        self._model       = model_class
        self._record_size: int = model_class.SIZE          # type: ignore[attr-defined]
        self._index       = IndexManager(idx_path, holes_path)

        # Crear el archivo .dat si no existe
        dat_path.parent.mkdir(parents=True, exist_ok=True)
        if not dat_path.exists():
            dat_path.touch()

    # ════════════════════════════════════════════════════════════════════════
    # INSERCIÓN
    # ════════════════════════════════════════════════════════════════════════

    def insert(self, record: T) -> int:
        """
        Inserta un registro en el archivo .dat.

        Proceso:
          1. Asignar ID automático si record.id == 0
          2. Buscar slot libre (hole) para reutilizar espacio
          3. Si no hay hole → agregar al final del archivo
          4. Escribir el registro con seek() + write()
          5. Actualizar el índice .idx

        Args:
            record: Objeto del modelo a insertar

        Returns:
            ID asignado al registro
        """
        # 1. Asignar ID si no tiene
        if getattr(record, "id", 0) == 0:
            record.id = self._index.next_id()   # type: ignore[attr-defined]

        # 2. Determinar posición de escritura
        free_offset = self._index.get_free_slot()
        if free_offset is not None:
            offset = free_offset
            logger.debug(
                f"[{self._model.__name__}] Reutilizando hole: "
                f"id={record.id} → offset={offset}"   # type: ignore[attr-defined]
            )
        else:
            # Append al final
            offset = self._dat_path.stat().st_size
            logger.debug(
                f"[{self._model.__name__}] Append: "
                f"id={record.id} → offset={offset}"   # type: ignore[attr-defined]
            )

        # 3. Escribir en la posición determinada
        mode = "r+b" if self._dat_path.stat().st_size > 0 else "wb"
        with open(self._dat_path, mode) as f:
            f.seek(offset)
            f.write(record.pack())   # type: ignore[attr-defined]

        # 4. Actualizar índice: id → offset
        self._index.add(record.id, offset)  # type: ignore[attr-defined]
        logger.info(
            f"[{self._model.__name__}] INSERT id={record.id} "  # type: ignore[attr-defined]
            f"offset={offset} size={self._record_size}B"
        )
        return record.id   # type: ignore[attr-defined]

    # ════════════════════════════════════════════════════════════════════════
    # ACTUALIZACIÓN
    # ════════════════════════════════════════════════════════════════════════

    def update(self, record: T) -> bool:
        """
        Actualiza un registro existente escribiendo in-place.

        Como el tamaño es fijo, seek() al offset conocido y sobreescribir
        garantiza que no se altere ningún otro registro adyacente.

        Args:
            record: Objeto actualizado (mismo ID)

        Returns:
            True si se actualizó, False si el ID no existe
        """
        record_id = getattr(record, "id", None)
        offset    = self._index.get_offset(record_id)

        if offset is None:
            logger.warning(
                f"[{self._model.__name__}] UPDATE: id={record_id} no encontrado"
            )
            return False

        with open(self._dat_path, "r+b") as f:
            f.seek(offset)
            f.write(record.pack())   # type: ignore[attr-defined]

        logger.info(
            f"[{self._model.__name__}] UPDATE id={record_id} en offset={offset}"
        )
        return True

    # ════════════════════════════════════════════════════════════════════════
    # ELIMINACIÓN LÓGICA
    # ════════════════════════════════════════════════════════════════════════

    def delete(self, record_id: int) -> bool:
        """
        Eliminación lógica: escribe estado=0 y libera el slot.

        El registro permanece físicamente en el archivo pero su campo
        'estado' queda en 0, indicando que está inactivo. El offset
        se agrega a la lista de holes para reutilización futura.

        Args:
            record_id: ID del registro a eliminar

        Returns:
            True si se eliminó, False si no existía
        """
        record = self.find_by_id(record_id)
        if record is None:
            logger.warning(
                f"[{self._model.__name__}] DELETE: id={record_id} no encontrado"
            )
            return False

        record.estado = 0   # type: ignore[attr-defined]
        offset        = self._index.get_offset(record_id)

        # Escribir registro con estado=0
        with open(self._dat_path, "r+b") as f:
            f.seek(offset)
            f.write(record.pack())  # type: ignore[attr-defined]

        # Liberar slot: sacar del índice, agregar a holes
        self._index.remove(record_id)
        logger.info(
            f"[{self._model.__name__}] DELETE LÓGICO id={record_id} "
            f"offset={offset} (liberado para reutilización)"
        )
        return True

    # ════════════════════════════════════════════════════════════════════════
    # BÚSQUEDA
    # ════════════════════════════════════════════════════════════════════════

    def find_by_id(self, record_id: int) -> Optional[T]:
        """
        Búsqueda por ID con acceso directo O(1).

        Algoritmo:
          1. offset = index[id]        → O(1) lookup
          2. file.seek(offset)         → posicionamiento directo
          3. data = file.read(SIZE)    → lectura de registro fijo
          4. return Model.unpack(data) → deserialización

        Args:
            record_id: ID del registro a buscar

        Returns:
            Instancia del modelo, o None si no existe o está eliminado
        """
        offset = self._index.get_offset(record_id)
        if offset is None:
            return None

        with open(self._dat_path, "rb") as f:
            f.seek(offset)
            data = f.read(self._record_size)

        if len(data) != self._record_size:
            logger.error(
                f"[{self._model.__name__}] Registro corrupto en offset={offset} "
                f"(leídos {len(data)} de {self._record_size} bytes)"
            )
            return None

        record = self._model.unpack(data)  # type: ignore[attr-defined]

        # Doble verificación: el registro debe estar activo
        if getattr(record, "estado", 1) == 0:
            return None

        return record

    def find_all(self) -> List[T]:
        """
        Retorna todos los registros activos.

        Itera el índice (que solo contiene activos) y hace seek() a cada
        offset. No realiza escaneo secuencial del .dat completo.

        Returns:
            Lista ordenada por ID de todos los registros activos
        """
        result   = []
        all_idx  = self._index.get_all()   # { id → offset }

        if not all_idx or not self._dat_path.exists():
            return result

        with open(self._dat_path, "rb") as f:
            for key, offset in sorted(all_idx.items()):
                f.seek(offset)
                data = f.read(self._record_size)
                if len(data) != self._record_size:
                    logger.warning(
                        f"[{self._model.__name__}] Registro incompleto "
                        f"en offset={offset}"
                    )
                    continue
                try:
                    rec = self._model.unpack(data)  # type: ignore[attr-defined]
                    if getattr(rec, "estado", 1) == 1:
                        result.append(rec)
                except Exception as e:
                    logger.error(
                        f"[{self._model.__name__}] Error unpack offset={offset}: {e}"
                    )

        return result

    def find_all_including_deleted(self) -> List[T]:
        """Retorna TODOS los registros, incluyendo eliminados lógicamente."""
        result = []
        if not self._dat_path.exists() or self._dat_path.stat().st_size == 0:
            return result

        with open(self._dat_path, "rb") as f:
            while True:
                data = f.read(self._record_size)
                if not data:
                    break
                if len(data) == self._record_size:
                    try:
                        result.append(
                            self._model.unpack(data)  # type: ignore[attr-defined]
                        )
                    except Exception:
                        pass
        return result

    # ════════════════════════════════════════════════════════════════════════
    # UTILIDADES
    # ════════════════════════════════════════════════════════════════════════

    def count_active(self) -> int:
        """Número de registros activos (según índice)."""
        return self._index.count()

    def compact(self) -> int:
        """
        Compactación física: reescribe el .dat eliminando registros borrados
        y reconstruye el índice desde cero.

        Útil para liberar espacio en disco tras muchas eliminaciones.

        Returns:
            Número de bytes recuperados (holes × record_size)
        """
        active_records = self.find_all()
        holes_before   = len(self._index._holes)   # type: ignore[attr-defined]

        with open(self._dat_path, "wb") as f:
            for i, rec in enumerate(active_records):
                new_offset = i * self._record_size
                f.write(rec.pack())   # type: ignore[attr-defined]
                # Actualizar offset en el índice
                self._index._index[rec.id] = new_offset   # type: ignore[attr-defined]

        # Limpiar holes: ya no hay espacios libres tras compactación
        self._index._holes.clear()   # type: ignore[attr-defined]
        self._index._save_index()    # type: ignore[attr-defined]
        self._index._save_holes()    # type: ignore[attr-defined]

        recovered = holes_before * self._record_size
        logger.info(
            f"[{self._model.__name__}] Compactación: "
            f"{len(active_records)} registros, {recovered} bytes recuperados"
        )
        return recovered

    @property
    def index_manager(self) -> IndexManager:
        """Acceso directo al IndexManager (para inspección/debug)."""
        return self._index
