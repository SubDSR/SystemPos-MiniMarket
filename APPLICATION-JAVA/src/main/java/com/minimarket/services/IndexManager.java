package com.minimarket.services;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * Gestor de índices de acceso directo (.idx) y espacios libres (.holes).
 *
 * CONCEPTO ACADEMICO: Indice de Acceso Directo
 * El indice mapea cada clave primaria a su byte offset en el archivo .dat,
 * permitiendo busqueda O(1) mediante RandomAccessFile.seek().
 *
 * Formato del archivo .idx:
 *   [4 bytes: n_entradas][12 bytes por entrada: 4B clave + 8B offset] x n
 *
 * Formato del archivo .holes:
 *   [4 bytes: n_holes][8 bytes por offset] x n
 *
 * Compatible byte a byte con el formato Python (big-endian, DataOutputStream
 * produce el mismo orden que struct.pack "!IQ").
 */
public class IndexManager {

    private static final Logger LOG = Logger.getLogger(IndexManager.class.getName());

    private final Path idxPath;
    private final Path holesPath;
    private final Map<Integer, Long> index; // clave → byte offset
    private final List<Long>         holes; // offsets libres reutilizables

    public IndexManager(Path idxPath, Path holesPath) {
        this.idxPath   = idxPath;
        this.holesPath = holesPath;
        this.index     = new TreeMap<>(); // TreeMap mantiene el orden por clave
        this.holes     = new ArrayList<>();
        loadIndex();
        loadHoles();
    }

    // ════════════════════════════════════════════════════════════════════════
    // CARGA DESDE DISCO
    // ════════════════════════════════════════════════════════════════════════

    private void loadIndex() {
        index.clear();
        if (!Files.exists(idxPath)) return;

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(idxPath)))) {

            int n = dis.readInt(); // número de entradas
            for (int i = 0; i < n; i++) {
                int  key    = dis.readInt();
                long offset = dis.readLong();
                index.put(key, offset);
            }
        } catch (IOException e) {
            LOG.warning("No se pudo cargar el indice " + idxPath + ": " + e.getMessage());
        }
    }

    private void loadHoles() {
        holes.clear();
        if (!Files.exists(holesPath)) return;

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(holesPath)))) {

            int n = dis.readInt();
            for (int i = 0; i < n; i++) {
                holes.add(dis.readLong());
            }
        } catch (IOException e) {
            LOG.warning("No se pudo cargar holes " + holesPath + ": " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // PERSISTENCIA EN DISCO
    // ════════════════════════════════════════════════════════════════════════

    private void saveIndex() {
        ensureParent(idxPath);
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(idxPath)))) {

            dos.writeInt(index.size());
            for (Map.Entry<Integer, Long> e : index.entrySet()) {
                dos.writeInt(e.getKey());
                dos.writeLong(e.getValue());
            }
        } catch (IOException e) {
            LOG.severe("No se pudo guardar el indice " + idxPath + ": " + e.getMessage());
        }
    }

    private void saveHoles() {
        ensureParent(holesPath);
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(holesPath)))) {

            dos.writeInt(holes.size());
            for (long offset : holes) {
                dos.writeLong(offset);
            }
        } catch (IOException e) {
            LOG.severe("No se pudo guardar holes " + holesPath + ": " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // API PUBLICA
    // ════════════════════════════════════════════════════════════════════════

    /** Retorna el byte offset del registro con la clave dada, o -1 si no existe. */
    public long getOffset(int key) {
        Long off = index.get(key);
        return off != null ? off : -1L;
    }

    /** Registra clave → offset en el indice y persiste. */
    public void add(int key, long offset) {
        index.put(key, offset);
        saveIndex();
    }

    /**
     * Elimina la entrada del indice y agrega el offset a los holes.
     * Retorna el offset liberado, o -1 si no existia.
     */
    public long remove(int key) {
        Long offset = index.remove(key);
        if (offset != null) {
            holes.add(offset);
            saveIndex();
            saveHoles();
            return offset;
        }
        return -1L;
    }

    /**
     * Obtiene y consume el primer slot libre disponible.
     * Retorna -1 si no hay ninguno (señal de append al final).
     */
    public long getFreeSlot() {
        if (holes.isEmpty()) return -1L;
        long offset = holes.remove(0);
        saveHoles();
        return offset;
    }

    /** Genera el proximo ID disponible (max + 1). */
    public int nextId() {
        if (index.isEmpty()) return 1;
        return Collections.max(index.keySet()) + 1;
    }

    /** Retorna copia del mapa indice completo. */
    public Map<Integer, Long> getAll() { return new TreeMap<>(index); }

    /** Verifica si una clave esta en el indice. */
    public boolean exists(int key) { return index.containsKey(key); }

    /** Numero de entradas activas. */
    public int count() { return index.size(); }

    /** Recarga indice y holes desde disco (util tras compactacion). */
    public void reload() {
        loadIndex();
        loadHoles();
    }

    // Acceso interno para compactacion (solo FileManager lo usa)
    Map<Integer, Long>  getIndexMap()  { return index; }
    List<Long>          getHolesList() { return holes; }
    void                persistAll()   { saveIndex(); saveHoles(); }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private static void ensureParent(Path path) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ignored) { }
    }

    public String debugDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("IndexManager(").append(idxPath.getFileName()).append(")\n");
        sb.append("  Entradas activas: ").append(index.size()).append("\n");
        sb.append("  Holes disponibles: ").append(holes.size()).append("\n");
        for (Map.Entry<Integer, Long> e : index.entrySet()) {
            sb.append(String.format("    key=%6d → offset=%10d bytes%n", e.getKey(), e.getValue()));
        }
        return sb.toString();
    }
}
