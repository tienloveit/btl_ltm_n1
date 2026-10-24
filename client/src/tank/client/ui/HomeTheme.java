package tank.client.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Scoped styling for home, profile and waiting-room screens. */
final class HomeTheme {
    static final Color BACKGROUND = new Color(15, 23, 27);
    static final Color PANEL = new Color(26, 39, 43);
    static final Color ALT = new Color(23, 35, 39);
    static final Color RAISED = new Color(36, 54, 58);
    static final Color EDGE = new Color(53, 73, 76);
    static final Color TEXT = new Color(234, 241, 236);
    static final Color MUTED = new Color(157, 177, 175);
    static final Color LIME = new Color(169, 213, 109);
    static final Color CYAN = new Color(111, 193, 191);
    static final Color GOLD = new Color(218, 186, 117);
    static final Color CORAL = new Color(214, 145, 129);
    private HomeTheme() { }

    static JPanel card(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = graphics(graphics);
                g.setPaint(new GradientPaint(0, 0, PANEL.brighter(), getWidth(), getHeight(), PANEL));
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g.setColor(EDGE); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g.dispose(); super.paintComponent(graphics);
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    static void button(JButton button, String icon) {
        button.putClientProperty("home-button", Boolean.TRUE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setIconTextGap(9);
        button.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        if (icon != null) { button.setIcon(new HomeIcon(icon, button, null, 18)); }
        button.setDisabledIcon(button.getIcon());
        button.setUI(new BasicButtonUI() {
            @Override protected void paintText(Graphics g, JComponent component, Rectangle rect, String text) {
                if (!button.isEnabled()) {
                    g.setColor(button.isSelected() ? BACKGROUND : MUTED);
                    BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, -1, rect.x,
                            rect.y + g.getFontMetrics().getAscent());
                } else { super.paintText(g, component, rect, text); }
            }
        });
    }

    static Graphics2D graphics(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    static void backdrop(Graphics graphics, int w, int h) {
        Graphics2D g = graphics(graphics);
        g.setPaint(new GradientPaint(0, 0, BACKGROUND, w, h, new Color(24, 37, 39)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(126, 168, 151, 9));
        for (int x = 0; x < w; x += 48) { g.drawLine(x, 0, x, h); }
        for (int y = 0; y < h; y += 48) { g.drawLine(0, y, w, y); }
        g.dispose();
    }

    static Color rowColor(JTable table, int row, boolean selected) {
        Object hover = table.getClientProperty("home-hover-row");
        if (selected || Integer.valueOf(row).equals(hover)) { return RAISED; }
        Object nameColumn = table.getClientProperty("home-name-column");
        Object own = table.getClientProperty("home-own-username");
        if (nameColumn instanceof Integer column && own != null && row >= 0
                && own.toString().equalsIgnoreCase(String.valueOf(table.getModel().getValueAt(
                        table.convertRowIndexToModel(row), column)))) {
            return new Color(37, 52, 41);
        }
        return row % 2 == 0 ? PANEL : ALT;
    }

    static void table(JTable table) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        table.setRowHeight(44);
        table.setBackground(PANEL); table.setForeground(TEXT);
        table.setSelectionBackground(RAISED); table.setSelectionForeground(TEXT);
        table.setGridColor(new Color(43, 61, 65));
        table.setShowHorizontalLines(true); table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(new Cell());
        }
        var header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setPreferredSize(new Dimension(0, 40));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value,
                    boolean selected, boolean focused, int row, int column) {
                super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                setHorizontalAlignment(CENTER); setFont(source.getTableHeader().getFont());
                setBackground(new Color(20, 31, 35)); setForeground(MUTED);
                setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, EDGE));
                return this;
            }
        });
        MouseAdapter hover = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                table.putClientProperty("home-hover-row", table.rowAtPoint(event.getPoint())); table.repaint();
            }
            @Override public void mouseExited(MouseEvent event) {
                table.putClientProperty("home-hover-row", -1); table.repaint();
            }
        };
        table.addMouseMotionListener(hover); table.addMouseListener(hover);
    }

    static void scroll(JScrollPane scroll) {
        scroll.setBorder(BorderFactory.createLineBorder(EDGE));
        scroll.getViewport().setBackground(PANEL);
        scroll.setBackground(PANEL);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        for (JScrollBar bar : new JScrollBar[]{scroll.getVerticalScrollBar(), scroll.getHorizontalScrollBar()}) {
            bar.setUI(new BasicScrollBarUI() {
                @Override protected void configureScrollBarColors() { thumbColor = EDGE; trackColor = ALT; }
                @Override protected JButton createDecreaseButton(int orientation) { return arrow(); }
                @Override protected JButton createIncreaseButton(int orientation) { return arrow(); }
                private JButton arrow() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
            });
        }
    }

    static class Cell extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setHorizontalAlignment(CENTER); setVerticalAlignment(CENTER);
            setFont(table.getFont()); setForeground(TEXT);
            setBackground(rowColor(table, row, selected));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            setToolTipText(value == null ? null : value.toString());
            setIcon(null);
            Object nameColumn = table.getClientProperty("home-name-column");
            if (Integer.valueOf(table.convertColumnIndexToModel(column)).equals(nameColumn)) {
                setFont(table.getFont().deriveFont(Font.BOLD));
                setIcon(new HomeIcon("avatar", this, null, Math.round(26 * table.getFont().getSize2D() / 15f), String.valueOf(value)));
                setIconTextGap(Math.round(table.getFont().getSize2D() * .55f));
            }
            return this;
        }
    }

    static final class RankCell extends Cell {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            int rank = value instanceof Number number ? number.intValue() : 0;
            if (rank >= 1 && rank <= 3) {
                setForeground(rank == 1 ? GOLD : rank == 2 ? new Color(184, 202, 208) : new Color(194, 149, 113));
                setIcon(new HomeIcon("trophy", this, null, Math.round(18 * table.getFont().getSize2D() / 15f))); setIconTextGap(6);
                setFont(table.getFont().deriveFont(Font.BOLD));
            }
            return this;
        }
    }

    static final class BadgeCell extends Cell {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setOpaque(false); setFont(table.getFont().deriveFont(Font.BOLD, table.getFont().getSize2D() * .87f));
            setForeground(badgeColor(String.valueOf(value)));
            return this;
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = graphics(graphics);
            g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight());
            int w = Math.min(getWidth() - 12, getFontMetrics(getFont()).stringWidth(getText()) + 26);
            int h = Math.min(getHeight() - 10, getFontMetrics(getFont()).getHeight() + 10);
            g.setColor(new Color(getForeground().getRed(), getForeground().getGreen(), getForeground().getBlue(), 24));
            g.fillRoundRect((getWidth() - w) / 2, (getHeight() - h) / 2, w, h, 12, 12);
            g.dispose(); super.paintComponent(graphics);
        }
    }

    static Color badgeColor(String text) {
        if (text.contains("Thua")) { return CORAL; }
        if (text.contains("Hòa") || text.contains("chuẩn bị")) { return GOLD; }
        if (text.contains("chơi")) { return CYAN; }
        if (text.contains("Thắng") || text.contains("SẴN SÀNG") || text.contains("Đang chờ")) { return LIME; }
        return MUTED;
    }

    static JLabel badge(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = graphics(graphics);
                g.setColor(new Color(getForeground().getRed(), getForeground().getGreen(), getForeground().getBlue(), 20));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12); g.dispose();
                super.paintComponent(graphics);
            }
        };
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(MUTED); label.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return label;
    }
}
