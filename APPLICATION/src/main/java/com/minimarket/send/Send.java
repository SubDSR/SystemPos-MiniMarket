package com.minimarket.send;

import com.minimarket.sync.SyncAgent;
import com.minimarket.utils.AppLogger;
import com.minimarket.utils.Config;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * ============================================================
 * MÓDULO SEND — Sincronización Autónoma de Datos
 * ============================================================
 * Ejecuta la exportación y sincronización de archivos .dat
 * hacia la carpeta compartida DATOS/ (ruta UNC de red).
 *
 * ARQUITECTURA: Componente de sincronización unidireccional
 *   Cliente POS (archivos .dat) → exporta CSV → copia a DATOS/
 *
 * Rutas UNC soportadas:
 *   \\HOSTNAME\DATOS  (ruta de red Windows)
 *   Fallback: carpeta local DATOS/ (si UNC no disponible)
 *
 * Uso:
 *   java -jar Send.jar              → Interfaz gráfica Swing
 *   java -jar Send.jar --headless   → Modo consola (sin GUI)
 * ============================================================
 */
public class Send {

    private static final Logger LOG = Logger.getLogger(Send.class.getName());
    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HH_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        AppLogger.init();
        Config.initDirectories();

        boolean headless = args.length > 0 &&
            args[0].equalsIgnoreCase("--headless");

        if (headless) {
            runHeadless();
        } else {
            SwingUtilities.invokeLater(Send::showGui);
        }
    }

    // ════════════════════════════════════════════════════════
    // MODO CONSOLA (headless)
    // ════════════════════════════════════════════════════════

    private static void runHeadless() {
        System.out.println("════════════════════════════════════════════════");
        System.out.println("  MiniMarket POS — Módulo SEND v1.0.0");
        System.out.println("════════════════════════════════════════════════");
        System.out.println("  Timestamp : " + LocalDateTime.now().format(DT_FMT));
        System.out.println("  Hostname  : " + Config.HOSTNAME);
        System.out.println("  UNC Datos : " + Config.UNC_DATOS);
        System.out.println("  Fallback  : " + Config.DATOS_DIR);
        System.out.println("  Exports   : " + Config.EXPORTS_DIR);
        System.out.println("════════════════════════════════════════════════");
        System.out.println();

        SyncAgent agent = new SyncAgent(msg -> {
            System.out.println("  [SYNC] " + msg);
            LOG.info(msg);
        });

        SyncAgent.SyncResult result = agent.sincronizar();

        System.out.println();
        if (result.success) {
            System.out.println("  ✓ Sincronización exitosa");
            System.out.println("  ✓ Archivos procesados: " + result.filesProcessed);
            System.exit(0);
        } else {
            System.err.println("  ✗ Error en sincronización: " + result.errorMessage);
            System.exit(1);
        }
    }

    // ════════════════════════════════════════════════════════
    // MODO GRÁFICO (Swing GUI)
    // ════════════════════════════════════════════════════════

    private static void showGui() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JFrame frame = new JFrame("Send — Sincronización MiniMarket POS");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 540);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        // ── Header ──────────────────────────────────────────
        JPanel header = buildHeader();

        // ── Info panel ──────────────────────────────────────
        JPanel infoPanel = new JPanel(new GridLayout(3, 2, 6, 4));
        infoPanel.setBackground(new Color(30, 41, 59));
        infoPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        infoPanel.add(infoLabel("Hostname:", new Color(148, 163, 184)));
        infoPanel.add(infoLabel(Config.HOSTNAME, Color.WHITE));
        infoPanel.add(infoLabel("Ruta UNC:", new Color(148, 163, 184)));
        infoPanel.add(infoLabel(Config.UNC_DATOS, new Color(96, 165, 250)));
        infoPanel.add(infoLabel("Exports:", new Color(148, 163, 184)));
        infoPanel.add(infoLabel(Config.EXPORTS_DIR.toString(), new Color(148, 163, 184)));

        // ── Área de log ──────────────────────────────────────
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(15, 23, 42));
        logArea.setForeground(new Color(74, 222, 128));
        logArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(30, 41, 59), 1));

        appendLog(logArea, "Sistema listo — Hostname: " + Config.HOSTNAME);
        appendLog(logArea, "Ruta UNC configurada: " + Config.UNC_DATOS);
        appendLog(logArea, "Carpeta local fallback: " + Config.DATOS_DIR);
        appendLog(logArea, "Directorio de exports: " + Config.EXPORTS_DIR);
        appendLog(logArea, "─────────────────────────────────────────────────");
        appendLog(logArea, "Haga clic en 'Sincronizar Ahora' para iniciar.");

        // ── Status bar ───────────────────────────────────────
        JLabel statusLabel = buildStatusLabel("En espera", new Color(148, 163, 184));

        // ── Botones ──────────────────────────────────────────
        JButton btnSync  = buildButton("Sincronizar Ahora", new Color(37, 99, 235));
        JButton btnClear = buildButton("Limpiar Log",       new Color(71, 85, 105));
        JButton btnClose = buildButton("Cerrar",            new Color(127, 29, 29));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBackground(new Color(30, 41, 59));
        btnRow.add(btnClear);
        btnRow.add(btnSync);
        btnRow.add(btnClose);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(btnRow,       BorderLayout.CENTER);
        bottom.add(statusLabel,  BorderLayout.SOUTH);

        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(header,    BorderLayout.NORTH);
        topArea.add(infoPanel, BorderLayout.SOUTH);

        frame.setLayout(new BorderLayout(0, 0));
        frame.add(topArea, BorderLayout.NORTH);
        frame.add(scroll,  BorderLayout.CENTER);
        frame.add(bottom,  BorderLayout.SOUTH);

        // ── Eventos ──────────────────────────────────────────
        btnSync.addActionListener(e -> {
            btnSync.setEnabled(false);
            updateStatus(statusLabel, "Sincronizando...", new Color(250, 204, 21));

            appendLog(logArea, "");
            appendLog(logArea, "═══ Iniciando sincronización: "
                + LocalDateTime.now().format(DT_FMT) + " ═══");

            new Thread(() -> {
                SyncAgent agent = new SyncAgent(msg -> SwingUtilities.invokeLater(() -> {
                    appendLog(logArea, msg);
                    LOG.info(msg);
                }));

                SyncAgent.SyncResult result = agent.sincronizar();

                SwingUtilities.invokeLater(() -> {
                    if (result.success) {
                        appendLog(logArea,
                            "═══ COMPLETADO: " + result.filesProcessed
                            + " archivos sincronizados ═══");
                        updateStatus(statusLabel,
                            "✓ Sincronización exitosa — "
                            + LocalDateTime.now().format(DT_FMT),
                            new Color(74, 222, 128));
                    } else {
                        appendLog(logArea, "✗ ERROR: " + result.errorMessage);
                        updateStatus(statusLabel, "✗ Error en sincronización",
                            new Color(248, 113, 113));
                    }
                    btnSync.setEnabled(true);
                });
            }, "SyncThread").start();
        });

        btnClear.addActionListener(e -> {
            logArea.setText("");
            appendLog(logArea, "Log limpiado — " + LocalDateTime.now().format(DT_FMT));
        });

        btnClose.addActionListener(e -> frame.dispose());

        frame.setVisible(true);
    }

    // ════════════════════════════════════════════════════════
    // BUILDERS DE UI
    // ════════════════════════════════════════════════════════

    private static JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(15, 23, 42));
        p.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel("MÓDULO SEND — Sincronización de Datos");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Exporta .dat → CSV → DATOS/ (ruta UNC o fallback local)");
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

    private static JLabel infoLabel(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lbl.setForeground(color);
        return lbl;
    }

    private static JButton buildButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(170, 34));
        return btn;
    }

    private static JLabel buildStatusLabel(String text, Color color) {
        JLabel lbl = new JLabel("  " + text);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lbl.setForeground(color);
        lbl.setOpaque(true);
        lbl.setBackground(new Color(10, 15, 30));
        lbl.setPreferredSize(new Dimension(0, 24));
        return lbl;
    }

    private static void appendLog(JTextArea area, String msg) {
        String ts = LocalDateTime.now().format(HH_FMT);
        area.append("[" + ts + "] " + msg + "\n");
        area.setCaretPosition(area.getDocument().getLength());
    }

    private static void updateStatus(JLabel lbl, String text, Color color) {
        lbl.setText("  " + text);
        lbl.setForeground(color);
    }
}
