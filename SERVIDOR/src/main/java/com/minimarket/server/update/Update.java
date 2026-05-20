package com.minimarket.server.update;

import com.minimarket.server.database.DatabaseManager;
import com.minimarket.server.sync.SyncMonitor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * ============================================================
 * MÓDULO UPDATE — Procesador de Sincronización del Servidor
 * ============================================================
 * Monitorea la carpeta DATOS/ en busca de archivos CSV enviados
 * por los clientes POS y los consolida en la base de datos SQLite.
 *
 * ARQUITECTURA: Procesador central de sincronización
 *   DATOS/ (CSV recibidos) → parser CSV → UPSERT SQLite
 *
 * Funciones:
 *   - Monitoreo automático cada 10 segundos
 *   - Detección por prefijo de archivo (productos_, clientes_, etc.)
 *   - UPSERT transaccional con rollback en errores
 *   - Registro de auditoría en sync_log
 *   - Prevención de duplicados con processed_files.txt
 *
 * Uso:
 *   java -jar Update.jar              → Interfaz gráfica Swing
 *   java -jar Update.jar --headless   → Monitoreo continuo (consola)
 *   java -jar Update.jar --once       → Un scan y termina (consola)
 * ============================================================
 */
public class Update {

    private static final Logger LOG = Logger.getLogger(Update.class.getName());
    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HH_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── Resolución de rutas ──────────────────────────────────
    private static final Path PROJECT_ROOT;
    private static final Path DATOS_DIR;
    private static final Path SERVIDOR_DIR;
    private static final Path DB_PATH;
    private static final Path SERVER_LOGS_DIR;

    static {
        String home = System.getenv("MINIMARKET_HOME");
        Path base;
        if (home != null && !home.isBlank()) {
            base = Path.of(home).toAbsolutePath();
        } else {
            Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            String cwdName = cwd.getFileName().toString();
            if (cwdName.equalsIgnoreCase("dist")) {
                // Ejecutando desde SERVIDOR/dist/ → sube dos niveles
                base = cwd.getParent().getParent();
            } else if (cwdName.equalsIgnoreCase("SERVIDOR")) {
                // Ejecutando desde SERVIDOR/ → sube un nivel
                base = cwd.getParent();
            } else {
                // Asumir que es el directorio raíz del proyecto
                base = cwd;
            }
        }
        PROJECT_ROOT    = base;
        DATOS_DIR       = base.resolve("DATOS");
        SERVIDOR_DIR    = base.resolve("SERVIDOR");
        DB_PATH         = SERVIDOR_DIR.resolve("database").resolve("minimarket.db");
        SERVER_LOGS_DIR = SERVIDOR_DIR.resolve("logs");
    }

    public static void main(String[] args) throws Exception {
        initLogging();
        ensureDirectories();

        boolean headless = args.length > 0 &&
            (args[0].equalsIgnoreCase("--headless") ||
             args[0].equalsIgnoreCase("--once"));
        boolean once = args.length > 0 &&
            args[0].equalsIgnoreCase("--once");

        if (headless) {
            runHeadless(once);
        } else {
            SwingUtilities.invokeLater(Update::showGui);
        }
    }

    // ════════════════════════════════════════════════════════
    // MODO CONSOLA (headless)
    // ════════════════════════════════════════════════════════

    private static void runHeadless(boolean once) {
        System.out.println("════════════════════════════════════════════════");
        System.out.println("  MiniMarket POS — Módulo UPDATE v1.0.0");
        System.out.println("════════════════════════════════════════════════");
        System.out.println("  Timestamp  : " + LocalDateTime.now().format(DT_FMT));
        System.out.println("  Raíz       : " + PROJECT_ROOT);
        System.out.println("  DATOS/     : " + DATOS_DIR);
        System.out.println("  SQLite BD  : " + DB_PATH);
        System.out.println("  Modo       : " + (once ? "scan único" : "monitoreo continuo"));
        System.out.println("════════════════════════════════════════════════");
        System.out.println();

        DatabaseManager dbMgr = new DatabaseManager(DB_PATH);
        dbMgr.initDatabase();
        System.out.println("  [DB] Base de datos inicializada: " + DB_PATH.getFileName());

        SyncMonitor monitor = new SyncMonitor(SERVIDOR_DIR, DATOS_DIR, dbMgr, 10);
        monitor.setLogCallback(msg -> {
            System.out.println(msg);
            LOG.info(msg);
        });

        if (once) {
            System.out.println("  Ejecutando scan único...");
            int n = monitor.scanOnce();
            System.out.println();
            System.out.println("  ✓ Archivos procesados: " + n);
            System.exit(0);
        } else {
            System.out.println("  Iniciando monitoreo continuo (Ctrl+C para detener)...");
            System.out.println();
            monitor.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n  Deteniendo monitor...");
                monitor.stop();
                System.out.println("  Monitor detenido.");
            }, "ShutdownHook"));

            // Mantener el proceso activo
            try { Thread.currentThread().join(); }
            catch (InterruptedException ignored) {}
        }
    }

    // ════════════════════════════════════════════════════════
    // MODO GRÁFICO (Swing GUI)
    // ════════════════════════════════════════════════════════

    private static void showGui() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        DatabaseManager dbMgr = new DatabaseManager(DB_PATH);
        SyncMonitor monitor = new SyncMonitor(SERVIDOR_DIR, DATOS_DIR, dbMgr, 10);

        JFrame frame = new JFrame("Update — Procesador de Sincronización");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(720, 580);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        // ── Header ──────────────────────────────────────────
        JPanel header = buildHeader();

        // ── Panel de info ────────────────────────────────────
        JLabel statusChip = buildStatusChip("DETENIDO", new Color(248, 113, 113));

        JPanel infoPanel = new JPanel(new GridLayout(3, 4, 8, 4));
        infoPanel.setBackground(new Color(30, 41, 59));
        infoPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        infoPanel.add(infoLbl("DATOS/:", new Color(148, 163, 184)));
        infoPanel.add(infoLbl(DATOS_DIR.toString(), Color.WHITE));
        infoPanel.add(infoLbl("Intervalo:", new Color(148, 163, 184)));
        infoPanel.add(infoLbl("10 segundos", new Color(96, 165, 250)));
        infoPanel.add(infoLbl("SQLite:", new Color(148, 163, 184)));
        infoPanel.add(infoLbl(DB_PATH.getFileName().toString(), Color.WHITE));
        infoPanel.add(infoLbl("Estado:", new Color(148, 163, 184)));
        infoPanel.add(statusChip);
        infoPanel.add(infoLbl("Raíz:", new Color(148, 163, 184)));
        JLabel rootLbl = infoLbl(PROJECT_ROOT.toString(), new Color(100, 116, 139));
        rootLbl.setToolTipText(PROJECT_ROOT.toString());
        infoPanel.add(rootLbl);
        infoPanel.add(new JLabel(""));
        infoPanel.add(new JLabel(""));

        // ── Área de log ──────────────────────────────────────
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(10, 15, 30));
        logArea.setForeground(new Color(74, 222, 128));
        logArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(30, 41, 59), 1));

        appendLog(logArea, "Update listo — Raíz: " + PROJECT_ROOT);
        appendLog(logArea, "DATOS/: " + DATOS_DIR);
        appendLog(logArea, "SQLite: " + DB_PATH);
        appendLog(logArea, "─────────────────────────────────────────────────");

        // ── Botones ──────────────────────────────────────────
        JButton btnStart = buildBtn("Iniciar Monitor",  new Color(21, 128, 61));
        JButton btnScan  = buildBtn("Escanear Ahora",   new Color(59, 130, 246));
        JButton btnClear = buildBtn("Limpiar Log",      new Color(71, 85, 105));
        JButton btnClose = buildBtn("Cerrar",           new Color(127, 29, 29));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBackground(new Color(30, 41, 59));
        btnRow.add(btnClear);
        btnRow.add(btnScan);
        btnRow.add(btnStart);
        btnRow.add(btnClose);

        // ── Callbacks del monitor ─────────────────────────────
        monitor.setLogCallback(msg -> SwingUtilities.invokeLater(() -> {
            appendLog(logArea, msg);
            LOG.info(msg);
        }));
        monitor.setStatusCallback(msg -> SwingUtilities.invokeLater(() ->
            statusChip.setText(msg)));

        // ── Eventos ──────────────────────────────────────────
        btnStart.addActionListener(e -> {
            if (monitor.isRunning()) {
                monitor.stop();
                btnStart.setText("Iniciar Monitor");
                btnStart.setBackground(new Color(21, 128, 61));
                updateChip(statusChip, "DETENIDO", new Color(248, 113, 113));
                appendLog(logArea, "Monitor detenido manualmente.");
            } else {
                monitor.start();
                btnStart.setText("Detener Monitor");
                btnStart.setBackground(new Color(185, 28, 28));
                updateChip(statusChip, "MONITOREANDO", new Color(74, 222, 128));
            }
        });

        btnScan.addActionListener(e -> {
            btnScan.setEnabled(false);
            appendLog(logArea, "Scan manual iniciado...");
            new Thread(() -> {
                int n = monitor.scanOnce();
                SwingUtilities.invokeLater(() -> {
                    appendLog(logArea, "Scan manual completado: "
                        + n + " archivo(s) procesado(s).");
                    btnScan.setEnabled(true);
                });
            }, "ManualScan").start();
        });

        btnClear.addActionListener(e -> {
            logArea.setText("");
            appendLog(logArea, "Log limpiado — " + LocalDateTime.now().format(DT_FMT));
        });

        btnClose.addActionListener(e -> {
            monitor.stop();
            frame.dispose();
            System.exit(0);
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                monitor.stop();
                frame.dispose();
                System.exit(0);
            }
        });

        // ── Layout ───────────────────────────────────────────
        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(header,    BorderLayout.NORTH);
        topArea.add(infoPanel, BorderLayout.SOUTH);

        frame.setLayout(new BorderLayout(0, 0));
        frame.add(topArea, BorderLayout.NORTH);
        frame.add(scroll,  BorderLayout.CENTER);
        frame.add(btnRow,  BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    // ════════════════════════════════════════════════════════
    // BUILDERS DE UI
    // ════════════════════════════════════════════════════════

    private static JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(15, 23, 42));
        p.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel("MÓDULO UPDATE — Procesador de Sincronización");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel(
            "Monitorea DATOS/ → parsea CSV → consolida en SQLite (cada 10s)");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sub.setForeground(new Color(148, 163, 184));

        JPanel texts = new JPanel(new GridLayout(2, 1, 2, 2));
        texts.setOpaque(false);
        texts.add(title);
        texts.add(sub);
        p.add(texts, BorderLayout.WEST);

        JLabel ver = new JLabel("v1.0.0  ");
        ver.setFont(new Font("SansSerif", Font.PLAIN, 10));
        ver.setForeground(new Color(71, 85, 105));
        p.add(ver, BorderLayout.EAST);
        return p;
    }

    private static JLabel infoLbl(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(color);
        return l;
    }

    private static JLabel buildStatusChip(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setForeground(color);
        return lbl;
    }

    private static JButton buildBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(160, 34));
        return btn;
    }

    private static void updateChip(JLabel chip, String text, Color color) {
        chip.setText(text);
        chip.setForeground(color);
    }

    private static void appendLog(JTextArea area, String msg) {
        String ts = LocalDateTime.now().format(HH_FMT);
        area.append("[" + ts + "] " + msg + "\n");
        area.setCaretPosition(area.getDocument().getLength());
    }

    // ════════════════════════════════════════════════════════
    // INICIALIZACIÓN
    // ════════════════════════════════════════════════════════

    private static void initLogging() {
        try {
            Files.createDirectories(SERVER_LOGS_DIR);
            Logger root = Logger.getLogger("");
            root.setLevel(Level.INFO);
            // Eliminar handlers de consola para no duplicar output
            for (Handler h : root.getHandlers()) {
                if (h instanceof ConsoleHandler) {
                    root.removeHandler(h);
                }
            }
            FileHandler fh = new FileHandler(
                SERVER_LOGS_DIR.resolve("update_%g.log").toString(),
                2 * 1024 * 1024, // 2 MB por archivo
                3,               // máximo 3 archivos rotativos
                true);           // append
            fh.setFormatter(new SimpleFormatter());
            root.addHandler(fh);
            // Mantener consola para headless
            ConsoleHandler ch = new ConsoleHandler();
            ch.setLevel(Level.WARNING);
            root.addHandler(ch);
        } catch (Exception ignored) {}
    }

    private static void ensureDirectories() {
        try {
            Files.createDirectories(DATOS_DIR);
            Files.createDirectories(DB_PATH.getParent());
            Files.createDirectories(SERVER_LOGS_DIR);
        } catch (Exception ignored) {}
    }
}
