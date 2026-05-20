package com.minimarket.server;

import com.minimarket.server.database.DatabaseManager;
import com.minimarket.server.sync.SyncMonitor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;

/**
 * Servidor Central MiniMarket POS — Java SE + Swing.
 *
 * Interfaz grafica que permite:
 *   - Iniciar / detener el monitor de sincronizacion
 *   - Escanear la carpeta DATOS/ manualmente
 *   - Ver el log de operaciones en tiempo real
 */
public class ServerApp extends JFrame {

    // Resolucion de rutas (independiente de Config del cliente)
    private static final Path BASE_DIR;
    private static final Path SERVIDOR_DIR;
    private static final Path DATOS_DIR;
    private static final Path DB_PATH;

    static {
        String home = System.getenv("MINIMARKET_HOME");
        Path base;
        if (home != null && !home.isBlank()) {
            base = Path.of(home).toAbsolutePath();
        } else {
            base = Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent();
        }
        BASE_DIR     = base;
        SERVIDOR_DIR = base.resolve("SERVIDOR");
        DATOS_DIR    = base.resolve("DATOS");
        DB_PATH      = SERVIDOR_DIR.resolve("database").resolve("minimarket.db");
    }

    private final DatabaseManager dbManager;
    private final SyncMonitor     monitor;

    private final JTextArea logArea     = buildLogArea();
    private final JLabel    statusLabel = new JLabel("  Detenido");
    private final JButton   toggleBtn   = actionBtn("Iniciar Monitor",
                                            new Color(34, 197, 94));

    public ServerApp() {
        super("MiniMarket POS — Servidor Central");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(720, 580);
        setMinimumSize(new Dimension(580, 420));
        setLocationRelativeTo(null);

        dbManager = new DatabaseManager(DB_PATH);
        monitor   = new SyncMonitor(SERVIDOR_DIR, DATOS_DIR, dbManager, 10);
        monitor.setLogCallback(msg -> SwingUtilities.invokeLater(() -> appendLog(msg)));
        monitor.setStatusCallback(msg -> SwingUtilities.invokeLater(
            () -> statusLabel.setText("  " + msg)));

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                monitor.stop();
            }
        });

        // Inicializar BD al arrancar
        try {
            dbManager.initDatabase();
            appendLog("[OK]   Base de datos lista: " + DB_PATH);
            appendLog("[INFO] Carpeta monitoreada: " + DATOS_DIR);
            appendLog("[INFO] Intervalo de escaneo: 10s");
            appendLog("---");
        } catch (Exception e) {
            appendLog("[ERROR] BD: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONSTRUCCION DE UI
    // ════════════════════════════════════════════════════════════════════════

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBackground(new Color(15, 23, 42));
        header.setBorder(new EmptyBorder(18, 20, 14, 20));

        JLabel title = new JLabel("Servidor de Actualizacion — MiniMarket POS");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Color.WHITE);

        JLabel dbLbl = new JLabel("Base de datos: " + DB_PATH);
        dbLbl.setFont(new Font("Monospaced", Font.PLAIN, 11));
        dbLbl.setForeground(new Color(100, 116, 139));

        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusLabel.setForeground(new Color(34, 197, 94));

        JPanel info = new JPanel(new BorderLayout(0, 3));
        info.setOpaque(false);
        info.add(title,       BorderLayout.NORTH);
        info.add(dbLbl,       BorderLayout.CENTER);
        info.add(statusLabel, BorderLayout.SOUTH);
        header.add(info, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        footer.setBackground(new Color(15, 23, 42));

        JButton scanBtn  = actionBtn("Escanear Ahora", new Color(59, 130, 246));
        JButton clearBtn = actionBtn("Limpiar Log",    new Color(71, 85, 105));

        toggleBtn.addActionListener(e -> toggleMonitor());
        scanBtn.addActionListener(e -> {
            scanBtn.setEnabled(false);
            SwingWorker<Integer, Void> w = new SwingWorker<>() {
                @Override protected Integer doInBackground() { return monitor.scanOnce(); }
                @Override protected void done() {
                    try { appendLog("[Manual] " + get() + " archivo(s) procesado(s)"); }
                    catch (Exception ex) { appendLog("[Manual] Error: " + ex.getMessage()); }
                    scanBtn.setEnabled(true);
                }
            };
            w.execute();
        });
        clearBtn.addActionListener(e -> logArea.setText(""));

        footer.add(toggleBtn);
        footer.add(scanBtn);
        footer.add(clearBtn);
        return footer;
    }

    private static JTextArea buildLogArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setBackground(new Color(15, 23, 42));
        area.setForeground(new Color(148, 250, 117));
        area.setCaretColor(new Color(148, 250, 117));
        area.setMargin(new Insets(10, 14, 10, 14));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    // ════════════════════════════════════════════════════════════════════════
    // LOGICA
    // ════════════════════════════════════════════════════════════════════════

    private void toggleMonitor() {
        if (!monitor.isRunning()) {
            monitor.start();
            toggleBtn.setText("Detener Monitor");
            toggleBtn.setBackground(new Color(239, 68, 68));
            statusLabel.setText("  Monitoreando...");
            statusLabel.setForeground(new Color(34, 197, 94));
        } else {
            monitor.stop();
            toggleBtn.setText("Iniciar Monitor");
            toggleBtn.setBackground(new Color(34, 197, 94));
            statusLabel.setText("  Detenido");
            statusLabel.setForeground(new Color(100, 116, 139));
        }
    }

    private void appendLog(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private static JButton actionBtn(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = getBackground();
                if (getModel().isPressed()) c = c.darker();
                else if (getModel().isRollover()) c = c.brighter();
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 16, 8, 16));
        return btn;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ENTRY POINT
    // ════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) { }

        SwingUtilities.invokeLater(() -> {
            ServerApp app = new ServerApp();
            app.setVisible(true);
        });
    }
}
