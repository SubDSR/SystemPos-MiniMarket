import java.io.BufferedReader;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class CreateCrossTab {
    private static final Object WRITE_LOCK = new Object();

    public static void main(String[] args) throws Exception {
        Properties meta = loadMetadata();
        int m = Integer.parseInt(meta.getProperty("dim1.size"));
        int n = Integer.parseInt(meta.getProperty("dim2.size"));
        int p = Integer.parseInt(meta.getProperty("dim3.size"));
        int w = Integer.parseInt(meta.getProperty("W", "8"));
        Path repo = Path.of(meta.getProperty("repositorio", "SERVIDOR_DWH/repositorio_analitico/"));
        Files.createDirectories(repo);

        Path ctab2d = repo.resolve("ventas_2d.ctab");
        Path ctab3d = repo.resolve("ventas_3d.ctab");
        inicializar(ctab2d, (long) m * n, w);
        inicializar(ctab3d, (long) m * n * p, w);

        InetAddress ia = InetAddress.getLocalHost();
        System.out.println("- Hostname  : " + ia.getHostName());
        System.out.println("- IP Address: " + ia.getHostAddress());

        List<Assignment> assignments = leerAssign(m);
        List<Thread> threads = new ArrayList<>();
        for (Assignment assignment : assignments) {
            Thread t = new Thread(() -> procesarNodo(assignment, m, n, p, w, ctab2d, ctab3d),
                "ctab-" + assignment.host());
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();

        System.out.println("[CrossTab] Archivos generados: " + ctab2d + " y " + ctab3d);
    }

    private static Properties loadMetadata() throws Exception {
        Properties props = new Properties();
        try (var in = Files.newInputStream(Path.of("SERVIDOR_DWH", "config", "metadata.properties"))) {
            props.load(in);
        }
        return props;
    }

    private static List<Assignment> leerAssign(int m) throws Exception {
        Path assign = Path.of("SERVIDOR_DWH", "config", "Assign.txt");
        List<Assignment> assignments = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(assign)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                List<Integer> indices = new ArrayList<>();
                for (String raw : parts[1].split(",")) {
                    int idx = Integer.parseInt(raw.strip());
                    if (idx >= 1) indices.add(idx);
                }
                if (!indices.isEmpty()) assignments.add(new Assignment(parts[0], indices));
            }
        }
        if (assignments.isEmpty()) {
            List<Integer> all = new ArrayList<>();
            for (int i = 1; i <= m; i++) all.add(i);
            assignments.add(new Assignment(InetAddress.getLocalHost().getHostName(), all));
        }
        return assignments;
    }

    private static void inicializar(Path file, long cells, int w) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(cells * w);
            raf.seek(0);
            for (long i = 0; i < cells; i++) raf.writeDouble(0.0);
        }
    }

    private static void procesarNodo(Assignment assignment, int m, int n, int p, int w,
                                     Path ctab2d, Path ctab3d) {
        int written = 0;
        try {
            InetAddress ia = InetAddress.getLocalHost();
            Set<Integer> assigned = new HashSet<>(assignment.indices());
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:SERVIDOR_DWH/data/dwh.db");
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                     SELECT f.id_producto, t.mes, f.id_sucursal, SUM(f.total_venta) AS total
                     FROM FACT_VENTAS f
                     JOIN DIM_TIEMPO t ON t.id_tiempo = f.id_tiempo
                     GROUP BY f.id_producto, t.mes, f.id_sucursal
                     """)) {
                while (rs.next()) {
                    int prod = rs.getInt("id_producto");
                    int mes = rs.getInt("mes");
                    int sucursal = rs.getInt("id_sucursal");
                    double total = rs.getDouble("total");
                    if (!assigned.contains(prod) || prod < 1 || prod > m
                            || mes < 1 || mes > n || sucursal < 1 || sucursal > p) continue;
                    acumular2D(ctab2d, prod, mes, n, w, total);
                    acumular3D(ctab3d, prod, mes, sucursal, n, p, w, total);
                    written += 2;
                }
            }
            System.out.println("Nodo [" + assignment.host() + " / " + ia.getHostAddress()
                + "] proceso indices " + assignment.indices() + " - " + written + " celdas escritas");
        } catch (Exception e) {
            System.err.println("Nodo [" + assignment.host() + "] error: " + e.getMessage());
        }
    }

    private static void acumular2D(Path file, int i, int j, int N, int W, double valor) throws Exception {
        // ── FÓRMULAS DE LOCALIZACIÓN (implementar y comentar exactamente) ──

        // LINEAL (1D):
        long offset1D = (long)(j - 1) * W;

        // TABULAR (2D) — Producto x Mes  (N = num_meses):
        long offset2D = ((long)(i - 1) * N + (j - 1)) * W;
        if (offset1D < 0 || offset2D < 0) return;

        synchronized (WRITE_LOCK) {
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
                raf.seek(offset2D);
                double prev = raf.readDouble();
                raf.seek(offset2D);
                raf.writeDouble(prev + valor);
            }
        }
    }

    private static void acumular3D(Path file, int i1, int i2, int i3,
                                   int N, int P, int W, double valor) throws Exception {
        // CÚBICO (3D) — Producto x Mes x Sucursal  (N=dim2, P=dim3):
        long offset3D = ((long)(i1-1) * N * P + (i2-1) * P + (i3-1)) * W;

        // EJEMPLO CONCRETO:
        // Cubo 3D: 10 productos x 12 meses x 3 sucursales, W=8 bytes
        // Celda (Producto=3, Mes=7, Sucursal=2):
        //   offset3D = ((3-1)*12*3 + (7-1)*3 + (2-1)) * 8
        //            = (72 + 18 + 1) * 8 = 91 * 8 = 728 bytes

        synchronized (WRITE_LOCK) {
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
                raf.seek(offset3D);
                double prev = raf.readDouble();
                raf.seek(offset3D);
                raf.writeDouble(prev + valor);
            }
        }
    }

    private record Assignment(String host, List<Integer> indices) { }
}
