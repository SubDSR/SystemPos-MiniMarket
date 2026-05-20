package com.minimarket.models;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Modelo de Cliente con serialización binaria de longitud fija.
 *
 * Estructura del registro (131 bytes):
 *   id(4B) + nombre(50B) + dni(11B) + telefono(15B) + email(50B) + estado(1B)
 *
 * Compatible con Python: struct.pack("!I50s11s15s50sB", ...)
 */
public class Cliente implements BinaryRecord {

    public static final int SIZE = 131; // 4 + 50 + 11 + 15 + 50 + 1

    private int    id;
    private String nombre;
    private String dni;
    private String telefono;
    private String email;
    private int    estado;

    public Cliente() {
        this.nombre   = "";
        this.dni      = "";
        this.telefono = "";
        this.email    = "";
        this.estado   = 1;
    }

    public Cliente(int id, String nombre, String dni, String telefono, String email, int estado) {
        this.id       = id;
        this.nombre   = safe(nombre);
        this.dni      = safe(dni);
        this.telefono = safe(telefono);
        this.email    = safe(email);
        this.estado   = estado;
    }

    // ── Serialización ────────────────────────────────────────────────────────

    @Override
    public byte[] toBytes() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(SIZE);
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeInt(id);
            writeFixed(dos, nombre,   50);
            writeFixed(dos, dni,      11);
            writeFixed(dos, telefono, 15);
            writeFixed(dos, email,    50);
            dos.writeByte(estado);
        } catch (IOException e) {
            throw new RuntimeException("Error serializando Cliente", e);
        }
        return bos.toByteArray();
    }

    public static Cliente fromBytes(byte[] data) throws IOException {
        if (data.length != SIZE) {
            throw new IllegalArgumentException(
                "Cliente.fromBytes: se esperaban " + SIZE + " bytes, se recibieron " + data.length);
        }
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        Cliente c = new Cliente();
        c.id       = dis.readInt();
        c.nombre   = readFixed(dis, 50);
        c.dni      = readFixed(dis, 11);
        c.telefono = readFixed(dis, 15);
        c.email    = readFixed(dis, 50);
        c.estado   = dis.readByte() & 0xFF;
        return c;
    }

    // ── Exportación ──────────────────────────────────────────────────────────

    public String toCsvRow() {
        return String.format("%d,%s,%s,%s,%s,%d",
            id, csv(nombre), csv(dni), csv(telefono), csv(email), estado);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void writeFixed(DataOutputStream dos, String s, int len) throws IOException {
        byte[] src = safe(s).getBytes(StandardCharsets.UTF_8);
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

    private static String safe(String s) { return s != null ? s : ""; }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    // ── BinaryRecord ─────────────────────────────────────────────────────────

    @Override public int getId()           { return id; }
    @Override public void setId(int id)    { this.id = id; }
    @Override public int getEstado()       { return estado; }
    @Override public void setEstado(int e) { this.estado = e; }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getNombre()              { return nombre; }
    public void   setNombre(String v)      { nombre = safe(v); }
    public String getDni()                 { return dni; }
    public void   setDni(String v)         { dni = safe(v); }
    public String getTelefono()            { return telefono; }
    public void   setTelefono(String v)    { telefono = safe(v); }
    public String getEmail()               { return email; }
    public void   setEmail(String v)       { email = safe(v); }

    @Override
    public String toString() {
        return String.format("Cliente(id=%d, nombre='%s', dni='%s', estado=%d)",
            id, nombre, dni, estado);
    }
}
