"""
index_manager.py — Gestor de índices de acceso directo (.idx y .holes).

CONCEPTO ACADÉMICO: Índice de Acceso Directo
═══════════════════════════════════════════════════════════════════════════════
El índice actúa como una tabla de mapeo que relaciona cada clave primaria
con su posición física (byte offset) dentro del archivo .dat:

    Archivo .idx en disco:
    ┌──────────────────────────────────────────────────────────────┐
    │  n_entradas (4 bytes)                                        │ ← Cabecera
    ├──────────────┬───────────────────────────────────────────────┤
    │  key=1 (4B)  │  offset=0   (8B)   → registro 1 en .dat[0]   │ ← Entrada 1
    │  key=2 (4B)  │  offset=97  (8B)   → registro 2 en .dat[97]  │ ← Entrada 2
    │  key=3 (4B)  │  offset=194 (8B)   → registro 3 en .dat[194] │ ← Entrada 3
    └──────────────┴───────────────────────────────────────────────┘

    En memoria: dict{ 1→0, 2→97, 3→194 }

BÚSQUEDA O(1):
    offset = index[id]          # lookup en dict
    file.seek(offset)           # posicionamiento directo
    data   = file.read(SIZE)    # lectura de registro de tamaño fijo

ARCHIVO .holes — Reutilización de Espacios Libres:
    Cuando se elimina un registro lógicamente, su offset queda disponible
    para ser reutilizado en la próxima inserción, evitando fragmentación:
    ┌───────────────────────┐
    │  n_holes (4 bytes)    │
    ├───────────────────────┤
    │  offset=97  (8 bytes) │  ← espacio libre del registro eliminado
    │  offset=291 (8 bytes) │
    └───────────────────────┘
"""
import struct
from pathlib import Path
from typing import Dict, List, Optional

# ── Formato de una entrada de índice: clave (4B) + offset (8B) = 12 bytes ────
IDX_ENTRY_FMT  = "!IQ"
IDX_ENTRY_SIZE = struct.calcsize(IDX_ENTRY_FMT)   # 12

# ── Formato de la cabecera: número de entradas (4B) ──────────────────────────
HDR_FMT  = "!I"
HDR_SIZE = struct.calcsize(HDR_FMT)               # 4


class IndexManager:
    """
    Gestiona el índice binario (.idx) y la lista de espacios libres (.holes)
    para un archivo de datos .dat.

    Provee:
      - Acceso O(1) a registros vía byte offset
      - Reutilización de slots de registros eliminados
      - Persistencia en disco en formato binario de longitud fija
    """

    def __init__(self, idx_path: Path, holes_path: Path) -> None:
        self._idx_path:   Path           = idx_path
        self._holes_path: Path           = holes_path
        self._index:      Dict[int, int] = {}    # { id → byte_offset }
        self._holes:      List[int]      = []    # [ byte_offset_libre, ... ]
        self._load_index()
        self._load_holes()

    # ════════════════════════════════════════════════════════════════════════
    # CARGA DESDE DISCO
    # ════════════════════════════════════════════════════════════════════════

    def _load_index(self) -> None:
        """
        Lee el archivo .idx y puebla el diccionario _index.

        Formato:
            [4 bytes: n_entradas] [ (4B key + 8B offset) × n_entradas ]
        """
        self._index.clear()
        if not self._idx_path.exists():
            return

        with open(self._idx_path, "rb") as f:
            hdr = f.read(HDR_SIZE)
            if len(hdr) < HDR_SIZE:
                return
            (n,) = struct.unpack(HDR_FMT, hdr)

            for _ in range(n):
                entry = f.read(IDX_ENTRY_SIZE)
                if len(entry) < IDX_ENTRY_SIZE:
                    break
                key, offset = struct.unpack(IDX_ENTRY_FMT, entry)
                self._index[key] = offset

    def _load_holes(self) -> None:
        """
        Lee el archivo .holes y puebla la lista _holes.

        Formato:
            [4 bytes: n_holes] [ 8B offset × n_holes ]
        """
        self._holes.clear()
        if not self._holes_path.exists():
            return

        with open(self._holes_path, "rb") as f:
            hdr = f.read(HDR_SIZE)
            if len(hdr) < HDR_SIZE:
                return
            (n,) = struct.unpack(HDR_FMT, hdr)

            for _ in range(n):
                raw = f.read(8)
                if len(raw) < 8:
                    break
                (offset,) = struct.unpack("!Q", raw)
                self._holes.append(offset)

    # ════════════════════════════════════════════════════════════════════════
    # PERSISTENCIA EN DISCO
    # ════════════════════════════════════════════════════════════════════════

    def _save_index(self) -> None:
        """Escribe el índice completo al archivo .idx."""
        self._idx_path.parent.mkdir(parents=True, exist_ok=True)
        with open(self._idx_path, "wb") as f:
            f.write(struct.pack(HDR_FMT, len(self._index)))
            # Ordenar por clave para facilitar búsqueda binaria futura
            for key in sorted(self._index):
                f.write(struct.pack(IDX_ENTRY_FMT, key, self._index[key]))

    def _save_holes(self) -> None:
        """Escribe la lista de holes al archivo .holes."""
        self._holes_path.parent.mkdir(parents=True, exist_ok=True)
        with open(self._holes_path, "wb") as f:
            f.write(struct.pack(HDR_FMT, len(self._holes)))
            for offset in self._holes:
                f.write(struct.pack("!Q", offset))

    # ════════════════════════════════════════════════════════════════════════
    # API PÚBLICA
    # ════════════════════════════════════════════════════════════════════════

    def get_offset(self, key: int) -> Optional[int]:
        """
        Retorna el byte offset del registro con la clave dada.

        Complejidad: O(1) — lookup en diccionario Python.

        Args:
            key: Clave primaria (ID del registro)

        Returns:
            Byte offset en el archivo .dat, o None si no existe
        """
        return self._index.get(key)

    def add(self, key: int, offset: int) -> None:
        """
        Registra key→offset en el índice y persiste en disco.

        Args:
            key:    Clave primaria
            offset: Byte offset en el archivo .dat
        """
        self._index[key] = offset
        self._save_index()

    def remove(self, key: int) -> Optional[int]:
        """
        Elimina la entrada del índice y agrega el offset a los holes.

        Al liberar el offset, queda disponible para ser reutilizado
        en la próxima inserción, evitando desperdiciar espacio en disco.

        Args:
            key: Clave primaria a eliminar

        Returns:
            Byte offset liberado, o None si no existía
        """
        offset = self._index.pop(key, None)
        if offset is not None:
            self._holes.append(offset)
            self._save_index()
            self._save_holes()
        return offset

    def get_free_slot(self) -> Optional[int]:
        """
        Obtiene y consume un slot libre (espacio reutilizable).

        Returns:
            Byte offset de un slot libre, o None si no hay ninguno
        """
        if self._holes:
            offset = self._holes.pop(0)
            self._save_holes()
            return offset
        return None

    def get_all(self) -> Dict[int, int]:
        """Retorna copia del índice completo { key → offset }."""
        return dict(self._index)

    def get_all_keys(self) -> List[int]:
        """Retorna lista de todas las claves activas."""
        return list(self._index.keys())

    def exists(self, key: int) -> bool:
        """Verifica si una clave está en el índice."""
        return key in self._index

    def count(self) -> int:
        """Número de entradas activas en el índice."""
        return len(self._index)

    def next_id(self) -> int:
        """
        Genera el próximo ID disponible (max_id_actual + 1).

        Returns:
            1 si no hay registros, max+1 en caso contrario
        """
        return max(self._index.keys(), default=0) + 1

    def reload(self) -> None:
        """Recarga índice y holes desde disco (útil post-compactación)."""
        self._load_index()
        self._load_holes()

    def debug_dump(self) -> str:
        """Representación textual del índice para depuración."""
        lines = [f"IndexManager({self._idx_path.name})"]
        lines.append(f"  Entradas activas: {len(self._index)}")
        lines.append(f"  Holes disponibles: {len(self._holes)}")
        for k, v in sorted(self._index.items()):
            lines.append(f"    key={k:6d} → offset={v:10d} bytes")
        return "\n".join(lines)
