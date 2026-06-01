package com.minimarket.client.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Venta {
    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int id;
    private int clienteId;
    private int sucursalId;
    private String fecha;
    private double subtotal;
    private double igv;
    private double total;
    private int estado;

    public Venta() {
        this.fecha = LocalDateTime.now().format(FECHA_FMT);
        this.estado = 1;
        this.sucursalId = 1;
    }

    public Venta(int id, int clienteId, String fecha, double subtotal, double igv, double total, int estado) {
        this(id, clienteId, 1, fecha, subtotal, igv, total, estado);
    }

    public Venta(int id, int clienteId, int sucursalId, String fecha,
                 double subtotal, double igv, double total, int estado) {
        this.id = id;
        this.clienteId = clienteId;
        this.sucursalId = sucursalId;
        this.fecha = fecha != null ? fecha : LocalDateTime.now().format(FECHA_FMT);
        this.subtotal = subtotal;
        this.igv = igv;
        this.total = total;
        this.estado = estado;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getClienteId() { return clienteId; }
    public void setClienteId(int clienteId) { this.clienteId = clienteId; }
    public int getSucursalId() { return sucursalId; }
    public void setSucursalId(int sucursalId) { this.sucursalId = sucursalId; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public double getIgv() { return igv; }
    public void setIgv(double igv) { this.igv = igv; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
    public int getEstado() { return estado; }
    public void setEstado(int estado) { this.estado = estado; }
}
