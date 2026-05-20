package com.minimarket.models;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Modelo de Producto con serialización binaria de longitud fija.
 *
 * Estructura del registro (97 bytes):
 *   id(4B) + nombre(50B) + precio(8B) + stock(4B) + categoria(30B) + estado(1B)
 *
 * Compatible con el formato Python: struct.pack("!I50sdI30sB", ...)
 * usando big-endian, que es el orden predeterminado de DataOutputStream.
 */
public class Producto implements BinaryRecord {

    public static final int SIZE = 97; // 4 + 50 + 8 + 4 + 30 + 1

    private int    id;
    private String nombre;
    private double precio;
    private int    stock;
    private String categoria;
    private int    estado; // 1=activo, 0=eliminado lógicamente

    public Producto() {
        this.nombre    = "";
        this.categoria = "";
        this.estado    = 1;
    }

    public Producto(int id, String nombre, double precio, int stock, String categoria, int estado) {
        this.id        = id;
        this.nombre    = nombre != null ? nombre : "";
        this.precio    = precio;
        this.stock     = stock;
        this.categoria = categoria != null ? categoria : "";
        this.estado    = estado;
    }

    // ── Serialización ────────────────────────────────────────────────────────

    @Override
    public byte[] toBytes() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(SIZE);
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeInt(id);
            writeFixedString(dos, nombre, 50);
            dos.writeDouble(precio);
            dos.writeInt(stock);
            writeFixedString(dos, categoria, 30);
            dos.writeByte(estado);
        } catch (IOException e) {
            throw new RuntimeException("Error serializando Producto", e);
        }
        return bos.toByteArray();
    }

    public static Producto fromBytes(byte[] data) throws IOException {
        if (data.length != SIZE) {
            throw new IllegalArgumentException(
                "Producto.fromBytes: se esperaban " + SIZE + " bytes, se recibieron " + data.length);
        }
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        Producto p = new Producto();
        p.id        = dis.readInt();
        p.nombre    = readFixedString(dis, 50);
        p.precio    = dis.readDouble();
        p.stock     = dis.readInt();
        p.categoria = readFixedString(dis, 30);
        p.estado    = dis.readByte() & 0xFF;
        return p;
    }

    // ── Exportación ──────────────────────────────────────────────────────────

    public String toCsvRow() {
        return String.format("%d,%s,%.2f,%d,%s,%d",
            id, escapeCsv(nombre), precio, stock, escapeCsv(categoria), estado);
    }

    // ── Helpers binarios ─────────────────────────────────────────────────────

    private static void writeFixedString(DataOutputStream dos, String s, int len) throws IOException {
        byte[] src = (s != null ? s : "").getBytes(StandardCharsets.UTF_8);
        byte[] dst = new byte[len];
        System.arraycopy(src, 0, dst, 0, Math.min(src.length, len));
        dos.write(dst);
    }

    static String readFixedString(DataInputStream dis, int len) throws IOException {
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        int end = 0;
        while (end < len && bytes[end] != 0) end++;
        return new String(bytes, 0, end, StandardCharsets.UTF_8).strip();
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ── BinaryRecord ─────────────────────────────────────────────────────────

    @Override public int getId()             { return id; }
    @Override public void setId(int id)      { this.id = id; }
    @Override public int getEstado()         { return estado; }
    @Override public void setEstado(int e)   { this.estado = e; }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getNombre()                { return nombre; }
    public void   setNombre(String nombre)   { this.nombre = nombre != null ? nombre : ""; }
    public double getPrecio()                { return precio; }
    public void   setPrecio(double precio)   { this.precio = precio; }
    public int    getStock()                 { return stock; }
    public void   setStock(int stock)        { this.stock = stock; }
    public String getCategoria()             { return categoria; }
    public void   setCategoria(String cat)   { this.categoria = cat != null ? cat : ""; }

    @Override
    public String toString() {
        return String.format("Producto(id=%d, nombre='%s', precio=S/%.2f, stock=%d, cat='%s', estado=%d)",
            id, nombre, precio, stock, categoria, estado);
    }
}
