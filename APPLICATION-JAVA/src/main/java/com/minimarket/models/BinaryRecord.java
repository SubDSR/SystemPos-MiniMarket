package com.minimarket.models;

/**
 * Contrato para modelos que se serializan a registros binarios de longitud fija.
 * Permite que FileManager opere de forma genérica sobre cualquier entidad.
 */
public interface BinaryRecord {
    int getId();
    void setId(int id);
    int getEstado();
    void setEstado(int estado);
    byte[] toBytes();
}
