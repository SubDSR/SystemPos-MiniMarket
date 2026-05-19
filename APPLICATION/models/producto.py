"""
producto.py — Modelo de Producto con serialización binaria de longitud fija.

Estructura del registro (97 bytes por registro):
┌────────────┬──────────────────┬──────────────┬──────────────┬──────────────────┬──────────────┐
│  id        │  nombre          │  precio      │  stock       │  categoria       │  estado      │
│  4 bytes   │  50 bytes        │  8 bytes     │  4 bytes     │  30 bytes        │  1 byte      │
│ (uint32)   │  (char[50])      │  (float64)   │  (uint32)    │  (char[30])      │  (uint8)     │
└────────────┴──────────────────┴──────────────┴──────────────┴──────────────────┴──────────────┘
Total: 4 + 50 + 8 + 4 + 30 + 1 = 97 bytes

Offset de cada campo:
  id        → offset 0
  nombre    → offset 4
  precio    → offset 54
  stock     → offset 62
  categoria → offset 66
  estado    → offset 96
"""
import struct
from dataclasses import dataclass, field
from typing import ClassVar


@dataclass
class Producto:
    """
    Representa un producto del inventario del minimarket.

    Serialización: struct big-endian "!I50sdI30sB"
    Tamaño fijo:   97 bytes por registro
    """

    # ─── Constantes de serialización ─────────────────────────────────────────
    # ! = big-endian (network byte order)
    # I = unsigned int  (4 bytes) → id, stock
    # 50s = char[50]   (50 bytes) → nombre
    # d = double        (8 bytes) → precio
    # 30s = char[30]   (30 bytes) → categoria
    # B = unsigned char (1 byte)  → estado
    FORMAT: ClassVar[str] = "!I50sdI30sB"
    SIZE:   ClassVar[int] = struct.calcsize("!I50sdI30sB")  # 97

    id:        int   = 0
    nombre:    str   = ""
    precio:    float = 0.0
    stock:     int   = 0
    categoria: str   = ""
    estado:    int   = 1   # 1=activo, 0=eliminado lógicamente

    # ─── Empaquetado binario ─────────────────────────────────────────────────

    def pack(self) -> bytes:
        """
        Serializa el registro a exactamente SIZE bytes.

        Proceso:
          1. Codificar strings a UTF-8
          2. Truncar/rellenar a longitud fija con bytes nulos
          3. Empaquetar con struct.pack usando FORMAT

        Returns:
            bytes de longitud Producto.SIZE (97 bytes)
        """
        nombre_b    = self.nombre.encode("utf-8")[:50].ljust(50, b"\x00")
        categoria_b = self.categoria.encode("utf-8")[:30].ljust(30, b"\x00")
        return struct.pack(
            self.FORMAT,
            self.id,
            nombre_b,
            self.precio,
            self.stock,
            categoria_b,
            self.estado,
        )

    # ─── Desempaquetado binario ──────────────────────────────────────────────

    @classmethod
    def unpack(cls, data: bytes) -> "Producto":
        """
        Deserializa SIZE bytes a un objeto Producto.

        Args:
            data: bytes de exactamente SIZE bytes leídos del .dat con seek()

        Returns:
            Instancia de Producto

        Raises:
            ValueError: si len(data) != SIZE
        """
        if len(data) != cls.SIZE:
            raise ValueError(
                f"Producto.unpack: se esperaban {cls.SIZE} bytes, "
                f"se recibieron {len(data)}"
            )
        raw = struct.unpack(cls.FORMAT, data)
        return cls(
            id=raw[0],
            nombre=raw[1].rstrip(b"\x00").decode("utf-8", errors="replace").strip(),
            precio=raw[2],
            stock=raw[3],
            categoria=raw[4].rstrip(b"\x00").decode("utf-8", errors="replace").strip(),
            estado=raw[5],
        )

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "nombre": self.nombre,
            "precio": self.precio,
            "stock": self.stock,
            "categoria": self.categoria,
            "estado": self.estado,
        }

    def to_csv_row(self) -> str:
        """Genera una línea CSV para exportación hacia el servidor."""
        return (
            f"{self.id},{self.nombre},{self.precio:.2f},"
            f"{self.stock},{self.categoria},{self.estado}"
        )

    def __repr__(self) -> str:
        return (
            f"Producto(id={self.id}, nombre={self.nombre!r}, "
            f"precio=S/{self.precio:.2f}, stock={self.stock}, "
            f"cat={self.categoria!r}, estado={self.estado})"
        )
