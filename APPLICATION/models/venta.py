"""
venta.py — Modelo de Venta con serialización binaria de longitud fija.

Estructura del registro (52 bytes por registro):
┌──────────┬──────────────┬──────────────┬──────────────┬──────────────┬──────────────┬──────────┐
│  id      │  cliente_id  │  fecha       │  subtotal    │  igv         │  total       │  estado  │
│  4 bytes │  4 bytes     │  19 bytes    │  8 bytes     │  8 bytes     │  8 bytes     │  1 byte  │
└──────────┴──────────────┴──────────────┴──────────────┴──────────────┴──────────────┴──────────┘
Total: 4 + 4 + 19 + 8 + 8 + 8 + 1 = 52 bytes

Formato de fecha: "YYYY-MM-DD HH:MM:SS" (19 caracteres ASCII)
"""
import struct
from dataclasses import dataclass, field
from datetime import datetime
from typing import ClassVar


@dataclass
class Venta:
    """
    Representa una transacción de venta realizada en el POS.

    Serialización: struct big-endian "!II19sdddB"
    Tamaño fijo:   52 bytes por registro
    """

    # ! = big-endian
    # I   = unsigned int (4 bytes)  × 2 → id, cliente_id
    # 19s = char[19]    (19 bytes)      → fecha "YYYY-MM-DD HH:MM:SS"
    # d   = double      (8 bytes)  × 3 → subtotal, igv, total
    # B   = unsigned char (1 byte)      → estado
    FORMAT: ClassVar[str] = "!II19sdddB"
    SIZE:   ClassVar[int] = struct.calcsize("!II19sdddB")  # 52

    id:         int   = 0
    cliente_id: int   = 0
    fecha:      str   = field(
        default_factory=lambda: datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    )
    subtotal:   float = 0.0
    igv:        float = 0.0
    total:      float = 0.0
    estado:     int   = 1   # 1=activo, 0=anulado

    def pack(self) -> bytes:
        """Serializa la venta a 52 bytes de longitud fija."""
        fecha_b = self.fecha.encode("utf-8")[:19].ljust(19, b"\x00")
        return struct.pack(
            self.FORMAT,
            self.id,
            self.cliente_id,
            fecha_b,
            self.subtotal,
            self.igv,
            self.total,
            self.estado,
        )

    @classmethod
    def unpack(cls, data: bytes) -> "Venta":
        """Deserializa 52 bytes a un objeto Venta."""
        if len(data) != cls.SIZE:
            raise ValueError(
                f"Venta.unpack: se esperaban {cls.SIZE} bytes, "
                f"se recibieron {len(data)}"
            )
        raw = struct.unpack(cls.FORMAT, data)
        return cls(
            id=raw[0],
            cliente_id=raw[1],
            fecha=raw[2].rstrip(b"\x00").decode("utf-8", errors="replace"),
            subtotal=raw[3],
            igv=raw[4],
            total=raw[5],
            estado=raw[6],
        )

    def to_dict(self) -> dict:
        return {
            "id": self.id, "cliente_id": self.cliente_id, "fecha": self.fecha,
            "subtotal": self.subtotal, "igv": self.igv,
            "total": self.total, "estado": self.estado,
        }

    def to_csv_row(self) -> str:
        return (
            f"{self.id},{self.cliente_id},{self.fecha},"
            f"{self.subtotal:.2f},{self.igv:.2f},{self.total:.2f},{self.estado}"
        )

    def __repr__(self) -> str:
        return (
            f"Venta(id={self.id}, cliente_id={self.cliente_id}, "
            f"fecha={self.fecha!r}, total=S/{self.total:.2f}, estado={self.estado})"
        )
