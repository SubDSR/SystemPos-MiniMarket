"""
cliente.py — Modelo de Cliente con serialización binaria de longitud fija.

Estructura del registro (131 bytes por registro):
┌──────────┬──────────────────┬──────────┬──────────────┬──────────────────┬──────────┐
│  id      │  nombre          │  dni     │  telefono    │  email           │  estado  │
│  4 bytes │  50 bytes        │  11 bytes│  15 bytes    │  50 bytes        │  1 byte  │
└──────────┴──────────────────┴──────────┴──────────────┴──────────────────┴──────────┘
Total: 4 + 50 + 11 + 15 + 50 + 1 = 131 bytes

Offsets:
  id       → 0
  nombre   → 4
  dni      → 54
  telefono → 65
  email    → 80
  estado   → 130
"""
import struct
from dataclasses import dataclass
from typing import ClassVar


@dataclass
class Cliente:
    """
    Representa un cliente del minimarket.

    Serialización: struct big-endian "!I50s11s15s50sB"
    Tamaño fijo:   131 bytes por registro
    """

    # ! = big-endian
    # I   = unsigned int (4 bytes)  → id
    # 50s = char[50]    (50 bytes)  → nombre
    # 11s = char[11]    (11 bytes)  → dni
    # 15s = char[15]    (15 bytes)  → telefono
    # 50s = char[50]    (50 bytes)  → email
    # B   = unsigned char (1 byte)  → estado
    FORMAT: ClassVar[str] = "!I50s11s15s50sB"
    SIZE:   ClassVar[int] = struct.calcsize("!I50s11s15s50sB")  # 131

    id:       int = 0
    nombre:   str = ""
    dni:      str = ""
    telefono: str = ""
    email:    str = ""
    estado:   int = 1   # 1=activo, 0=eliminado

    def pack(self) -> bytes:
        """Serializa el cliente a 131 bytes de longitud fija."""
        return struct.pack(
            self.FORMAT,
            self.id,
            self.nombre.encode("utf-8")[:50].ljust(50, b"\x00"),
            self.dni.encode("utf-8")[:11].ljust(11, b"\x00"),
            self.telefono.encode("utf-8")[:15].ljust(15, b"\x00"),
            self.email.encode("utf-8")[:50].ljust(50, b"\x00"),
            self.estado,
        )

    @classmethod
    def unpack(cls, data: bytes) -> "Cliente":
        """Deserializa 131 bytes a un objeto Cliente."""
        if len(data) != cls.SIZE:
            raise ValueError(
                f"Cliente.unpack: se esperaban {cls.SIZE} bytes, "
                f"se recibieron {len(data)}"
            )
        raw = struct.unpack(cls.FORMAT, data)
        return cls(
            id=raw[0],
            nombre=raw[1].rstrip(b"\x00").decode("utf-8", errors="replace").strip(),
            dni=raw[2].rstrip(b"\x00").decode("utf-8", errors="replace").strip(),
            telefono=raw[3].rstrip(b"\x00").decode("utf-8", errors="replace").strip(),
            email=raw[4].rstrip(b"\x00").decode("utf-8", errors="replace").strip(),
            estado=raw[5],
        )

    def to_dict(self) -> dict:
        return {
            "id": self.id, "nombre": self.nombre, "dni": self.dni,
            "telefono": self.telefono, "email": self.email, "estado": self.estado,
        }

    def to_csv_row(self) -> str:
        return f"{self.id},{self.nombre},{self.dni},{self.telefono},{self.email},{self.estado}"

    def __repr__(self) -> str:
        return (
            f"Cliente(id={self.id}, nombre={self.nombre!r}, "
            f"dni={self.dni!r}, estado={self.estado})"
        )
