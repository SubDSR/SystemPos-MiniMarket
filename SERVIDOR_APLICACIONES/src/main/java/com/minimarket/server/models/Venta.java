package com.minimarket.server.models;

public class Venta {
    private int id;
    private int clienteId;
    private int sucursalId;
    private String fecha;
    private double subtotal;
    private double igv;
    private double total;
    private int estado;

    public Venta(int id, int clienteId, int sucursalId, String fecha,
                 double subtotal, double igv, double total, int estado) {
        this.id = id;
        this.clienteId = clienteId;
        this.sucursalId = sucursalId;
        this.fecha = fecha != null ? fecha : "";
        this.subtotal = subtotal;
        this.igv = igv;
        this.total = total;
        this.estado = estado;
    }

    public int getId() { return id; }
    public int getClienteId() { return clienteId; }
    public int getSucursalId() { return sucursalId; }
    public String getFecha() { return fecha; }
    public double getSubtotal() { return subtotal; }
    public double getIgv() { return igv; }
    public double getTotal() { return total; }
    public int getEstado() { return estado; }
}
