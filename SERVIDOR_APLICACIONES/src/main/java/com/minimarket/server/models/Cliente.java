package com.minimarket.server.models;

public class Cliente {
    private int id;
    private String nombre;
    private String dni;
    private String telefono;
    private String email;
    private int estado;

    public Cliente(int id, String nombre, String dni, String telefono, String email, int estado) {
        this.id = id;
        this.nombre = nombre != null ? nombre : "";
        this.dni = dni != null ? dni : "";
        this.telefono = telefono != null ? telefono : "";
        this.email = email != null ? email : "";
        this.estado = estado;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDni() { return dni; }
    public String getTelefono() { return telefono; }
    public String getEmail() { return email; }
    public int getEstado() { return estado; }
}
