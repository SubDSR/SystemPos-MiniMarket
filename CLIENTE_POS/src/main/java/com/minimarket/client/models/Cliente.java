package com.minimarket.client.models;

public class Cliente {
    private int id;
    private String nombre;
    private String dni;
    private String telefono;
    private String email;
    private int estado;

    public Cliente() {
        this.nombre = "";
        this.dni = "";
        this.telefono = "";
        this.email = "";
        this.estado = 1;
    }

    public Cliente(int id, String nombre, String dni, String telefono, String email, int estado) {
        this.id = id;
        this.nombre = safe(nombre);
        this.dni = safe(dni);
        this.telefono = safe(telefono);
        this.email = safe(email);
        this.estado = estado;
    }

    private static String safe(String value) { return value != null ? value : ""; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = safe(nombre); }
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = safe(dni); }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = safe(telefono); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = safe(email); }
    public int getEstado() { return estado; }
    public void setEstado(int estado) { this.estado = estado; }
}
