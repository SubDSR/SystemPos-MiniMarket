package com.minimarket.models;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Modelo de Venta con serialización binaria de longitud fija.
 *
 * Estructura del registro (52 bytes):
 *   id(4B) + cliente_id(4B) + fecha(19B) + subtotal(8B) + igv(8B) + total(8B) + estado(1B)
 *
 * Compatible con Python: struct.pack("!II19sdddB", ...)
 * Formato de fecha: "YYYY-MM-DD HH:MM:SS" (19 caracteres ASCII)
 */
public class Venta implements BinaryRecord {

    public static final int SIZE = 52; // 4 + 4 + 19 + 8 + 8 + 8 + 1

    static final DateTimeFormatter FECHA_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int    id;
    private int    clienteId;
    private String fecha;
    private double subtotal;
    private double igv;
    private double total;
    private int    estado;

    public Venta() {
        this.fecha  = LocalDateTime.now().format(FECHA_FMT);
        this.estado = 1;
    }

    public Venta(int id, int clienteId, String fecha,
                 double subtotal, double igv, double total, int estado) {
        this.id        = id;
        this.clienteId = clienteId;
        this.fecha     = fecha != null ? fecha : LocalDateTime.now().format(FECHA_FMT);
        this.subtotal  = subtotal;
        this.igv       = igv;
        this.total     = total;
        this.estado    = estado;
    }

    // ── Serialización ────────────────────────────────────────────────────────

    @Override
    public byte[] toBytes() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(SIZE);
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeInt(id);
            dos.writeInt(clienteId);
            writeFixed(dos, fecha, 19);
            dos.writeDouble(subtotal);
            dos.writeDouble(igv);
            dos.writeDouble(total);
            dos.writeByte(estado);
        } catch (IOException e) {
            throw new RuntimeException("Error serializando Venta", e);
        }
        return bos.toByteArray();
    }

    public static Venta fromBytes(byte[] data) throws IOException {
        if (data.length != SIZE) {
            throw new IllegalArgumentException(
                "Venta.fromBytes: se esperaban " + SIZE + " bytes, se recibieron " + data.length);
        }
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        Venta v = new Venta();
        v.id        = dis.readInt();
        v.clienteId = dis.readInt();
        v.fecha     = readFixed(dis, 19);
        v.subtotal  = dis.readDouble();
        v.igv       = dis.readDouble();
        v.total     = dis.readDouble();
        v.estado    = dis.readByte() & 0xFF;
        return v;
    }

    // ── Exportación ──────────────────────────────────────────────────────────

    public String toCsvRow() {
        return String.format("%d,%d,%s,%.2f,%.2f,%.2f,%d",
            id, clienteId, fecha, subtotal, igv, total, estado);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void writeFixed(DataOutputStream dos, String s, int len) throws IOException {
        byte[] src = (s != null ? s : "").getBytes(StandardCharsets.UTF_8);
        byte[] dst = new byte[len];
        System.arraycopy(src, 0, dst, 0, Math.min(src.length, len));
        dos.write(dst);
    }

    private static String readFixed(DataInputStream dis, int len) throws IOException {
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        int end = 0;
        while (end < len && bytes[end] != 0) end++;
        return new String(bytes, 0, end, StandardCharsets.UTF_8).strip();
    }

    // ── BinaryRecord ─────────────────────────────────────────────────────────

    @Override public int getId()           { return id; }
    @Override public void setId(int id)    { this.id = id; }
    @Override public int getEstado()       { return estado; }
    @Override public void setEstado(int e) { this.estado = e; }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public int    getClienteId()            { return clienteId; }
    public void   setClienteId(int v)       { clienteId = v; }
    public String getFecha()                { return fecha; }
    public void   setFecha(String v)        { fecha = v; }
    public double getSubtotal()             { return subtotal; }
    public void   setSubtotal(double v)     { subtotal = v; }
    public double getIgv()                  { return igv; }
    public void   setIgv(double v)          { igv = v; }
    public double getTotal()                { return total; }
    public void   setTotal(double v)        { total = v; }

    @Override
    public String toString() {
        return String.format("Venta(id=%d, clienteId=%d, fecha='%s', total=S/%.2f, estado=%d)",
            id, clienteId, fecha, total, estado);
    }
}
