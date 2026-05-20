package com.minimarket.views;

import com.minimarket.sync.SyncAgent;
import com.minimarket.utils.Config;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Dialogo de sincronizacion: ejecuta SyncAgent en un hilo daemon
 * y muestra el log en tiempo real mediante SwingWorker.
 */
public class SyncDialog extends JDialog {

    private final JTextArea logArea = new JTextArea();
    private final JButton   btnSync = MainWindow.actionBtn("Sincronizar Ahora", Config.C_SUCCESS);
    private final JButton   btnClose = MainWindow.actionBtn("Cerrar", Config.C_TEXT_MUTED);
    private boolean syncing = false;

    public SyncDialog(Frame parent) {
        super(parent, "Sincronizacion con Servidor", true);
        setSize(560, 440);
        setMinimumSize(new Dimension(480, 360));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // Log area
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(15, 23, 42));
        logArea.setForeground(new Color(148, 250, 117));
        logArea.setMargin(new Insets(10, 12, 10, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(30, 41, 59));
        header.setBorder(new EmptyBorder(14, 16, 14, 16));
        JLabel title = new JLabel("Sincronizacion de Datos");
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Exporta CSV y copia a carpeta DATOS/");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sub.setForeground(new Color(148, 163, 184));
        JPanel texts = new JPanel(new BorderLayout(0, 3));
        texts.setOpaque(false);
        texts.add(title, BorderLayout.NORTH);
        texts.add(sub,   BorderLayout.SOUTH);
        header.add(texts, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBackground(new Color(30, 41, 59));
        btnSync.addActionListener(e  -> runSync());
        btnClose.addActionListener(e -> dispose());
        footer.add(btnSync);
        footer.add(btnClose);
        add(footer, BorderLayout.SOUTH);

        appendLog("Listo. Haga clic en 'Sincronizar Ahora' para iniciar.");
        appendLog("Destino primario : " + Config.UNC_DATOS);
        appendLog("Destino fallback : " + Config.DATOS_DIR);
    }

    private void runSync() {
        if (syncing) return;
        syncing = true;
        btnSync.setEnabled(false);
        logArea.setText("");
        appendLog("Iniciando sincronizacion...");

        SwingWorker<SyncAgent.SyncResult, String> worker = new SwingWorker<>() {
            @Override
            protected SyncAgent.SyncResult doInBackground() {
                SyncAgent agent = new SyncAgent(msg -> publish(msg));
                return agent.sincronizar();
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) appendLog(msg);
            }

            @Override
            protected void done() {
                try {
                    SyncAgent.SyncResult res = get();
                    if (res.success) {
                        appendLog("✓ Sincronizacion completada. Archivos: " + res.filesProcessed);
                    } else {
                        appendLog("✗ Error: " + res.errorMessage);
                    }
                } catch (Exception ex) {
                    appendLog("✗ Error inesperado: " + ex.getMessage());
                } finally {
                    syncing = false;
                    btnSync.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void appendLog(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
