package tank.client.ui;

import javax.swing.JTable;
import javax.swing.table.TableModel;
import java.awt.*;

/** Table background is painted only below data, never over interactive cells. */
final class HomeTable extends JTable {
    private String emptyTitle, emptyHint;
    HomeTable(TableModel model, String title, String hint) {
        super(model); emptyTitle = title; emptyHint = hint;
    }
    void emptyMessage(String title, String hint) { emptyTitle = title; emptyHint = hint; repaint(); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        int top = getRowCount() * getRowHeight();
        if (getHeight() - top < 50) { return; }
        Graphics2D g = HomeTheme.graphics(graphics);
        g.clipRect(0, top, getWidth(), getHeight() - top);
        float scale = getFont().getSize2D() / 15f;
        int step = Math.max(28, Math.round(40 * scale));
        g.setColor(new Color(131, 174, 157, 8));
        for (int x = 0; x < getWidth(); x += step) { g.drawLine(x, top, x, getHeight()); }
        for (int y = top; y < getHeight(); y += step) { g.drawLine(0, y, getWidth(), y); }
        if (getRowCount() == 0) {
            int cx = getWidth() / 2, cy = Math.max(Math.round(72 * scale), Math.min(getHeight() / 2, Math.round(170 * scale)));
            HomeIcon icon = new HomeIcon("tank", this, new Color(169, 213, 109, 100), Math.round(52 * scale));
            icon.paintIcon(this, g, cx - icon.getIconWidth() / 2, cy - icon.getIconHeight());
            g.setFont(getFont().deriveFont(Font.BOLD, 17 * scale)); g.setColor(HomeTheme.TEXT);
            g.drawString(emptyTitle, cx - g.getFontMetrics().stringWidth(emptyTitle) / 2, cy + Math.round(25 * scale));
            g.setFont(getFont().deriveFont(13 * scale)); g.setColor(HomeTheme.MUTED);
            g.drawString(emptyHint, cx - g.getFontMetrics().stringWidth(emptyHint) / 2, cy + Math.round(49 * scale));
        } else if (getHeight() - top > 120 * scale) {
            HomeIcon icon = new HomeIcon("tank", this, new Color(169, 213, 109, 15), Math.round(160 * scale));
            icon.paintIcon(this, g, getWidth() - icon.getIconWidth() - 35, getHeight() - icon.getIconHeight() - 20);
        }
        g.dispose();
    }
}
