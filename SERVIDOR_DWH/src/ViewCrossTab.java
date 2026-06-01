import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Properties;

public class ViewCrossTab {
    public static void main(String[] args) throws Exception {
        Properties meta = new Properties();
        try (var in = java.nio.file.Files.newInputStream(Path.of("SERVIDOR_DWH", "config", "metadata.properties"))) {
            meta.load(in);
        }
        int m = Integer.parseInt(meta.getProperty("dim1.size"));
        int n = Integer.parseInt(meta.getProperty("dim2.size"));
        int p = Integer.parseInt(meta.getProperty("dim3.size"));
        int w = Integer.parseInt(meta.getProperty("W", "8"));
        Path repo = Path.of(meta.getProperty("repositorio", "SERVIDOR_DWH/repositorio_analitico/"));

        String cubo = args.length == 0 ? "ventas_2d" : args[0];
        if ("ventas_3d".equalsIgnoreCase(cubo)) {
            imprimir3D(repo.resolve("ventas_3d.ctab"), m, n, p, w);
        } else {
            imprimir2D(repo.resolve("ventas_2d.ctab"), m, n, w);
        }
    }

    private static void imprimir2D(Path file, int m, int n, int w) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            System.out.printf("%-20s", "Producto\\Mes");
            for (int mes = 1; mes <= n; mes++) System.out.printf("%10d", mes);
            System.out.println();
            for (int prod = 1; prod <= m; prod++) {
                System.out.printf("%-20s", "Producto_" + prod);
                for (int mes = 1; mes <= n; mes++) {
                    long offset = ((long)(prod - 1) * n + (mes - 1)) * w;
                    raf.seek(offset);
                    System.out.printf("%10.2f", raf.readDouble());
                }
                System.out.println();
            }
        }
    }

    private static void imprimir3D(Path file, int m, int n, int p, int w) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            for (int sucursal = 1; sucursal <= p; sucursal++) {
                System.out.println("Sucursal_" + sucursal);
                System.out.printf("%-20s", "Producto\\Mes");
                for (int mes = 1; mes <= n; mes++) System.out.printf("%10d", mes);
                System.out.println();
                for (int prod = 1; prod <= m; prod++) {
                    System.out.printf("%-20s", "Producto_" + prod);
                    for (int mes = 1; mes <= n; mes++) {
                        long offset = ((long)(prod - 1) * n * p + (mes - 1) * p + (sucursal - 1)) * w;
                        raf.seek(offset);
                        System.out.printf("%10.2f", raf.readDouble());
                    }
                    System.out.println();
                }
                System.out.println();
            }
        }
    }
}
