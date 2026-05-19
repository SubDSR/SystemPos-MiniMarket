"""
detalle_venta.py — Modelo de Detalle de Venta con serialización binaria.

Estructura del registro (33 bytes por registro):
┌──────────┬──────────────┬──────────────┬──────────────┬──────────────────┬──────────────┬──────────┐
│  id      │  venta_id    │ producto_id  │  cantidad    │ precio_unitario  │  subtotal    │  estado  │
│  4 bytes │  4 bytes     │  4 bytes     │  4 bytes     │  8 bytes         │  8 bytes     │  1 byte  │
└──────────┴──────────────┴──────────────┴──────────────┴──────────────────┴──────────────┴──────────┘
Total: 4 + 4 + 4 + 4 + 8 + 8 + 1 = 33 bytes
"""
import struct
from dataclasses import dataclass
from typing import ClassVar


@dataclass
class DetalleVenta:
    """
    Representa una línea de detalle de una venta (producto + cantidad).

    Serialización: struct big-endian "!IIIIddB"
    Tamaño fijo:   33 bytes por registro
    """

    # ! = big-endian
    # I = unsigned int (4 bytes) × 4 → id, venta_id, producto_id, cantidad
    # d = double       (8 bytes) × 2 → precio_unitario, subtotal
    # B = unsigned char (1 byte)     → estado
    FORMAT: ClassVar[str] = "!IIIIddB"
    SIZE:   ClassVar[int] = struct.calcsize("!IIIIddB")  # 33

    id:              int   = 0
    venta_id:        int   = 0
    producto_id:     int   = 0
    cantidad:        int   = 0
    precio_unitario: float = 0.0
    subtotal:        float = 0.0
    estado:          int   = 1

    def pack(self) -> bytes:
        """Serializa el detalle a 33 bytes de longitud fija."""
        return struct.pack(
            self.FORMAT,
            self.id,
            self.venta_id,
            self.producto_id,
            self.cantidad,
            self.precio_unitario,
            self.subtotal,
            self.estado,
        )

    @classmethod
    def unpack(cls, data: bytes) -> "DetalleVenta":
        """Deserializa 33 bytes a un objeto DetalleVenta."""
        if len(data) != cls.SIZE:
            raise ValueError(
                f"DetalleVenta.unpack: se esperaban {cls.SIZE} bytes, "
                f"se recibieron {len(data)}"
            )
        raw = struct.unpack(cls.FORMAT, data)
        return cls(
            id=raw[0], venta_id=raw[1], producto_id=raw[2], cantidad=raw[3],
            precio_unitario=raw[4], subtotal=raw[5], estado=raw[6],
        )

    def to_dict(self) -> dict:
        return {
            "id": self.id, "venta_id": self.venta_id,
            "producto_id": self.producto_id, "cantidad": self.cantidad,
            "precio_unitario": self.precio_unitario,
            "subtotal": self.subtotal, "estado": self.estado,
        }

    def to_csv_row(self) -> str:
        return (
            f"{self.id},{self.venta_id},{self.producto_id},"
            f"{self.cantidad},{self.precio_unitario:.2f},{self.subtotal:.2f},{self.estado}"
        )

    def __repr__(self) -> str:
        return (
            f"DetalleVenta(id={self.id}, venta={self.venta_id}, "
            f"prod={self.producto_id}, cant={self.cantidad}, "
            f"sub=S/{self.subtotal:.2f})"
        )
