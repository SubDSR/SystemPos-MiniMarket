package com.minimarket.services;

import com.minimarket.models.BinaryRecord;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Gestor de archivos binarios de acceso directo (.dat).
 *
 * CONCEPTO ACADEMICO: Acceso Directo con Registros de Longitud Fija
 * Todos los registros ocupan exactamente recordSize bytes.
 * seek() permite posicionarse en cualquier registro en O(1).
 *
 * Combina:
 *   - archivo .dat: registros binarios de longitud fija
 *   - IndexManager (.idx): mapeo clave → offset para busqueda O(1)
 *   - IndexManager (.holes): lista de slots libres para reutilizacion
 *
 * @param <T> Tipo del modelo (debe implementar BinaryRecord)
 */
public class FileManager<T extends BinaryRecord> {

    private static final Logger LOG = Logger.getLogger(FileManager.class.getName());

    private final Path                  datPath;
    private final int                   recordSize;
    private final Function<byte[], T>   deserializer;
    private final String                entityName;
    final         IndexManager          indexManager;

    /**
     * @param datPath      Ruta al archivo .dat
     * @param idxPath      Ruta al archivo .idx
     * @param holesPath    Ruta al archivo .holes
     * @param recordSize   Tamano fijo de cada registro en bytes
     * @param deserializer Funcion que convierte byte[] → T (referencia de metodo estatico)
     * @param entityName   Nombre de la entidad para logging
     */
    public FileManager(Path datPath, Path idxPath, Path holesPath,
                       int recordSize,
                       CheckedDeserializer<T> deserializer,
                       String entityName) {
        this.datPath      = datPath;
        this.recordSize   = recordSize;
        this.deserializer = wrapDeserializer(deserializer);
        this.entityName   = entityName;
        this.indexManager = new IndexManager(idxPath, holesPath);

        try {
            Files.createDirectories(datPath.getParent());
            if (!Files.exists(datPath)) Files.createFile(datPath);
        } catch (IOException e) {
            LOG.severe("No se pudo inicializar el archivo .dat: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // INSERCION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Inserta un registro en el archivo .dat.
     *
     * Algoritmo:
     *   1. Asignar ID si record.id == 0
     *   2. Buscar slot libre (hole) para reutilizar
     *   3. Si no hay hole → append al final del archivo
     *   4. seek(offset) + write(bytes)
     *   5. Actualizar indice .idx
     *
     * @return ID asignado al registro
     */
    public int insert(T record) {
        if (record.getId() == 0) {
            record.setId(indexManager.nextId());
        }

        long freeOffset = indexManager.getFreeSlot();
        long offset;

        if (freeOffset >= 0) {
            offset = freeOffset;
            LOG.fine("[" + entityName + "] Reutilizando hole: id=" + record.getId() + " offset=" + offset);
        } else {
            try {
                offset = Files.size(datPath);
            } catch (IOException e) {
                offset = 0;
            }
            LOG.fine("[" + entityName + "] Append: id=" + record.getId() + " offset=" + offset);
        }

        writeAt(offset, record.toBytes());
        indexManager.add(record.getId(), offset);

        LOG.info("[" + entityName + "] INSERT id=" + record.getId()
            + " offset=" + offset + " size=" + recordSize + "B");
        return record.getId();
    }

    // ════════════════════════════════════════════════════════════════════════
    // ACTUALIZACION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Actualiza un registro existente escribiendo in-place.
     * Como el tamano es fijo, seek() + write() no afecta registros adyacentes.
     *
     * @return true si se actualizo, false si el ID no existe
     */
    public boolean update(T record) {
        long offset = indexManager.getOffset(record.getId());
        if (offset < 0) {
            LOG.warning("[" + entityName + "] UPDATE: id=" + record.getId() + " no encontrado");
            return false;
        }
        writeAt(offset, record.toBytes());
        LOG.info("[" + entityName + "] UPDATE id=" + record.getId() + " en offset=" + offset);
        return true;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ELIMINACION LOGICA
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Eliminacion logica: escribe estado=0 y libera el slot.
     * El registro permanece en el .dat pero su campo 'estado' queda en 0.
     * El offset pasa a la lista de holes para reutilizacion futura.
     *
     * @return true si se elimino, false si no existia
     */
    public boolean delete(int recordId) {
        T record = findById(recordId);
        if (record == null) {
            LOG.warning("[" + entityName + "] DELETE: id=" + recordId + " no encontrado");
            return false;
        }

        long offset = indexManager.getOffset(recordId);
        record.setEstado(0);
        writeAt(offset, record.toBytes());
        indexManager.remove(recordId); // libera el slot → va a holes

        LOG.info("[" + entityName + "] DELETE LOGICO id=" + recordId
            + " offset=" + offset + " (slot liberado para reutilizacion)");
        return true;
    }

    // ════════════════════════════════════════════════════════════════════════
    // BUSQUEDA
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Busqueda por ID con acceso directo O(1).
     *
     * Algoritmo:
     *   1. offset = index[id]     → O(1) lookup en TreeMap
     *   2. seek(offset)           → posicionamiento directo
     *   3. read(recordSize bytes) → lectura de registro fijo
     *   4. deserialize(bytes)     → construccion del objeto
     */
    public T findById(int recordId) {
        long offset = indexManager.getOffset(recordId);
        if (offset < 0) return null;

        byte[] data = readAt(offset);
        if (data == null) return null;

        T record = deserializer.apply(data);
        if (record == null || record.getEstado() == 0) return null;
        return record;
    }

    /**
     * Retorna todos los registros activos iterando el indice.
     * No realiza escaneo secuencial del .dat completo.
     */
    public List<T> findAll() {
        List<T> result = new ArrayList<>();
        Map<Integer, Long> all = indexManager.getAll();
        if (all.isEmpty()) return result;

        try (RandomAccessFile raf = new RandomAccessFile(datPath.toFile(), "r")) {
            for (Map.Entry<Integer, Long> entry : all.entrySet()) {
                raf.seek(entry.getValue());
                byte[] data = new byte[recordSize];
                try {
                    raf.readFully(data); // garantiza lectura completa del registro
                } catch (EOFException eof) {
                    LOG.warning("[" + entityName + "] Registro incompleto en offset=" + entry.getValue());
                    continue;
                }
                try {
                    T rec = deserializer.apply(data);
                    if (rec != null && rec.getEstado() == 1) result.add(rec);
                } catch (Exception e) {
                    LOG.warning("[" + entityName + "] Error deserializando offset=" + entry.getValue() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.severe("[" + entityName + "] Error leyendo .dat: " + e.getMessage());
        }
        return result;
    }

    /** Retorna TODOS los registros, incluyendo eliminados logicamente. */
    public List<T> findAllIncludingDeleted() {
        List<T> result = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(datPath.toFile(), "r")) {
            byte[] data = new byte[recordSize];
            while (raf.read(data) == recordSize) {
                try {
                    result.add(deserializer.apply(data));
                } catch (Exception ignored) { }
            }
        } catch (IOException e) {
            LOG.severe("[" + entityName + "] Error leyendo .dat: " + e.getMessage());
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ════════════════════════════════════════════════════════════════════════

    /** Numero de registros activos (segun indice). */
    public int countActive() { return indexManager.count(); }

    /**
     * Compactacion fisica: reescribe el .dat eliminando registros borrados
     * y reconstruye el indice desde cero.
     *
     * @return Bytes recuperados (holes x recordSize)
     */
    public int compact() {
        List<T> active = findAll();
        int holesBefore = indexManager.getHolesList().size();

        try (RandomAccessFile raf = new RandomAccessFile(datPath.toFile(), "rw")) {
            raf.setLength(0); // truncar
            for (int i = 0; i < active.size(); i++) {
                T rec = active.get(i);
                long newOffset = (long) i * recordSize;
                raf.seek(newOffset);
                raf.write(rec.toBytes());
                indexManager.getIndexMap().put(rec.getId(), newOffset);
            }
        } catch (IOException e) {
            LOG.severe("[" + entityName + "] Error en compactacion: " + e.getMessage());
            return 0;
        }

        indexManager.getHolesList().clear();
        indexManager.persistAll();

        int recovered = holesBefore * recordSize;
        LOG.info("[" + entityName + "] Compactacion: " + active.size()
            + " registros, " + recovered + " bytes recuperados");
        return recovered;
    }

    /** Acceso al IndexManager para inspeccion. */
    public IndexManager getIndexManager() { return indexManager; }

    // ── I/O de bajo nivel ────────────────────────────────────────────────────

    private void writeAt(long offset, byte[] data) {
        try (RandomAccessFile raf = new RandomAccessFile(datPath.toFile(), "rw")) {
            raf.seek(offset);
            raf.write(data);
        } catch (IOException e) {
            LOG.severe("[" + entityName + "] Error escribiendo en offset=" + offset + ": " + e.getMessage());
        }
    }

    private byte[] readAt(long offset) {
        try (RandomAccessFile raf = new RandomAccessFile(datPath.toFile(), "r")) {
            raf.seek(offset);
            byte[] data = new byte[recordSize];
            raf.readFully(data); // garantiza lectura de exactamente recordSize bytes
            return data;
        } catch (EOFException e) {
            LOG.severe("[" + entityName + "] Registro incompleto en offset=" + offset);
            return null;
        } catch (IOException e) {
            LOG.severe("[" + entityName + "] Error leyendo offset=" + offset + ": " + e.getMessage());
            return null;
        }
    }

    // ── Interfaz auxiliar y wrapper ──────────────────────────────────────────

    @FunctionalInterface
    public interface CheckedDeserializer<T> {
        T deserialize(byte[] data) throws IOException;
    }

    private static <T> Function<byte[], T> wrapDeserializer(CheckedDeserializer<T> d) {
        return bytes -> {
            try { return d.deserialize(bytes); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        };
    }
}

