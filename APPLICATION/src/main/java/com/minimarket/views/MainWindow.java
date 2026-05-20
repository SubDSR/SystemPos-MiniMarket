package com.minimarket.views;

import com.minimarket.services.ClienteService;
import com.minimarket.services.ProductoService;
import com.minimarket.services.VentaService;
import com.minimarket.utils.Config;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ventana principal: sidebar de navegacion + CardLayout de vistas.
 */
public class MainWindow extends JFrame {

    public interface Refreshable {
        void refresh();
    }

    private final CardLayout cardLayout   = new CardLayout();
    private final JPanel     contentPanel = new JPanel(cardLayout);
    private final Map<String, JPanel> views = new LinkedHashMap<>();
    private JButton activeNavButton;

    private final ProductoService productoSvc = new ProductoService();
    private final ClienteService  clienteSvc  = new ClienteService();
    private final VentaService    ventaSvc    = new VentaService();

    public MainWindow() {
        super(Config.APP_NAME + " — " + Config.EMPRESA);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 780);
        setMinimumSize(new Dimension(960, 620));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildSidebar(), BorderLayout.WEST);
        contentPanel.setBackground(Config.C_MAIN_BG);
        add(contentPanel, BorderLayout.CENTER);

        addView("dashboard", new DashboardView(ventaSvc, productoSvc, clienteSvc));
        addView("productos", new ProductosView(productoSvc));
        addView("clientes",  new ClientesView(clienteSvc));
        addView("ventas",    new VentasView(ventaSvc, productoSvc, clienteSvc));
        addView("historial", new HistorialView(ventaSvc, productoSvc));

        navigateTo("dashboard");
    }

    private void addView(String key, JPanel panel) {
        views.put(key, panel);
        contentPanel.add(panel, key);
    }

    public void navigateTo(String key) {
        cardLayout.show(contentPanel, key);
        JPanel view = views.get(key);
        if (view instanceof Refreshable r) r.refresh();
    }

    // ════════════════════════════════════════════════════════════════════════
    // SIDEBAR
    // ════════════════════════════════════════════════════════════════════════

    private JPanel buildSidebar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(Config.C_SIDEBAR_BG);
        bar.setPreferredSize(new Dimension(215, 0));

        bar.add(sidebarHeader());
        bar.add(hsep());

        String[][] nav = {
            {"  Dashboard",  "dashboard"},
            {"  Productos",  "productos"},
            {"  Clientes",   "clientes"},
            {"  Ventas",     "ventas"},
            {"  Historial",  "historial"},
        };
        JButton[] btns = new JButton[nav.length];
        for (int i = 0; i < nav.length; i++) {
            JButton b = navBtn(nav[i][0]);
            String key = nav[i][1];
            int idx = i;
            b.addActionListener(e -> { navigateTo(key); activate(btns[idx]); });
            btns[i] = b;
            bar.add(b);
        }

        bar.add(Box.createVerticalGlue());
        bar.add(hsep());

        JButton syncBtn = navBtn("  Sincronizar");
        syncBtn.setForeground(new Color(134, 239, 172));
        syncBtn.addActionListener(e -> new SyncDialog(this).setVisible(true));
        bar.add(syncBtn);
        bar.add(Box.createRigidArea(new Dimension(0, 14)));

        activate(btns[0]);
        return bar;
    }

    private JPanel sidebarHeader() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(26, 18, 22, 18));

        JLabel title = new JLabel("MiniMarket");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel("Sistema POS — Java SE");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sub.setForeground(Config.C_TEXT_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 4)));
        p.add(sub);
        return p;
    }

    private JButton navBtn(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        btn.setForeground(Config.C_SIDEBAR_FG);
        btn.setBackground(Config.C_SIDEBAR_BG);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(new EmptyBorder(11, 10, 11, 10));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (btn != activeNavButton) { btn.setBackground(Config.C_SIDEBAR_HOVER); btn.repaint(); }
            }
            @Override public void mouseExited(MouseEvent e) {
                if (btn != activeNavButton) { btn.setBackground(Config.C_SIDEBAR_BG); btn.repaint(); }
            }
        });
        return btn;
    }

    private void activate(JButton btn) {
        if (activeNavButton != null) {
            activeNavButton.setBackground(Config.C_SIDEBAR_BG);
            activeNavButton.setForeground(Config.C_SIDEBAR_FG);
            activeNavButton.repaint();
        }
        activeNavButton = btn;
        btn.setBackground(Config.C_ACTIVE_BTN);
        btn.setForeground(Color.WHITE);
        btn.repaint();
    }

    private JSeparator hsep() {
        JSeparator s = new JSeparator();
        s.setForeground(Config.C_SIDEBAR_HOVER);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return s;
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS DE ESTILO (usados por las vistas hijas)
    // ════════════════════════════════════════════════════════════════════════

    /** Boton de accion con fondo solido y esquinas redondeadas. */
    public static JButton actionBtn(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
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
        btn.setBorder(new EmptyBorder(8, 18, 8, 18));
        return btn;
    }

    /** Panel blanco con borde (tarjeta). */
    public static JPanel card(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Config.C_BORDER, 1),
            new EmptyBorder(16, 18, 16, 18)
        ));
        return p;
    }

    public static JLabel pageTitle(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 22));
        lbl.setForeground(Config.C_TEXT);
        return lbl;
    }

    public static JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        lbl.setForeground(Config.C_TEXT_MUTED);
        return lbl;
    }
}
