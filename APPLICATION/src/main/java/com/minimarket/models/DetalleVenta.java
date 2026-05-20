package com.minimarket.models;

import java.io.*;

/**
 * Modelo de DetalleVenta con serialización binaria de longitud fija.
 *
 * Estructura del registro (33 bytes):
 *   id(4B) + venta_id(4B) + producto_id(4B) + cantidad(4B)
 *   + precio_unitario(8B) + subtotal(8B) + estado(1B)
 *
 * Compatible con Python: struct.pack("!IIIIddB", ...)
 */
public class DetalleVenta implements BinaryRecord {

    public static final int SIZE = 33; // 4 + 4 + 4 + 4 + 8 + 8 + 1

    private int    id;
    private int    ventaId;
    private int    productoId;
    private int    cantidad;
    private double precioUnitario;
    private double subtotal;
    private int    estado;

    public DetalleVenta() { this.estado = 1; }

    public DetalleVenta(int id, int ventaId, int productoId, int cantidad,
                        double precioUnitario, double subtotal, int estado) {
        this.id             = id;
        this.ventaId        = ventaId;
        this.productoId     = productoId;
        this.cantidad       = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal       = subtotal;
        this.estado         = estado;
    }

    // ── Serialización ────────────────────────────────────────────────────────

    @Override
    public byte[] toBytes() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(SIZE);
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeInt(id);
            dos.writeInt(ventaId);
            dos.writeInt(productoId);
            dos.writeInt(cantidad);
            dos.writeDouble(precioUnitario);
            dos.writeDouble(subtotal);
            dos.writeByte(estado);
        } catch (IOException e) {
            throw new RuntimeException("Error serializando DetalleVenta", e);
        }
        return bos.toByteArray();
    }

    public static DetalleVenta fromBytes(byte[] data) throws IOException {
        if (data.length != SIZE) {
            throw new IllegalArgumentException(
                "DetalleVenta.fromBytes: se esperaban " + SIZE + " bytes, se recibieron " + data.length);
        }
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        DetalleVenta d = new DetalleVenta();
        d.id             = dis.readInt();
        d.ventaId        = dis.readInt();
        d.productoId     = dis.readInt();
        d.cantidad       = dis.readInt();
        d.precioUnitario = dis.readDouble();
        d.subtotal       = dis.readDouble();
        d.estado         = dis.readByte() & 0xFF;
        return d;
    }

    // ── Exportación ──────────────────────────────────────────────────────────

    public String toCsvRow() {
        return String.format("%d,%d,%d,%d,%.2f,%.2f,%d",
            id, ventaId, productoId, cantidad, precioUnitario, subtotal, estado);
    }

    // ── BinaryRecord ─────────────────────────────────────────────────────────

    @Override public int getId()           { return id; }
    @Override public void setId(int id)    { this.id = id; }
    @Override public int getEstado()       { return estado; }
    @Override public void setEstado(int e) { this.estado = e; }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public int    getVentaId()              { return ventaId; }
    public void   setVentaId(int v)         { ventaId = v; }
    public int    getProductoId()           { return productoId; }
    public void   setProductoId(int v)      { productoId = v; }
    public int    getCantidad()             { return cantidad; }
    public void   setCantidad(int v)        { cantidad = v; }
    public double getPrecioUnitario()       { return precioUnitario; }
    public void   setPrecioUnitario(double v) { precioUnitario = v; }
    public double getSubtotal()             { return subtotal; }
    public void   setSubtotal(double v)     { subtotal = v; }

    @Override
    public String toString() {
        return String.format("DetalleVenta(id=%d, venta=%d, prod=%d, cant=%d, sub=S/%.2f)",
            id, ventaId, productoId, cantidad, subtotal);
    }
}
