package tank.client.ui;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.awt.*;
import java.awt.geom.Path2D;

/** Font-scaled vector marks for the home screens; no bitmap or platform emoji dependency. */
final class HomeIcon implements Icon {
    private final String kind;
    private final JComponent owner;
    private final Color color;
    private final int size;
    private final float baseFont;
    private final String username;

    HomeIcon(String kind, JComponent owner, Color color, int size) {
        this(kind, owner, color, size, "");
    }

    HomeIcon(String kind, JComponent owner, Color color, int size, String username) {
        this.kind = kind; this.owner = owner; this.color = color; this.size = size; this.username = username;
        baseFont = owner.getFont().getSize2D();
    }

    @Override public int getIconWidth() { return Math.max(12, Math.round(size * owner.getFont().getSize2D() / baseFont)); }
    @Override public int getIconHeight() { return getIconWidth(); }

    @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);
        g.scale(getIconWidth() / 24.0, getIconHeight() / 24.0);
        Color ink = color == null ? c.getForeground() : color;
        g.setColor(ink);
        g.setStroke(new BasicStroke(1.65f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case "avatar" -> {
                Color tint = Color.getHSBColor((Math.floorMod(username.hashCode(), 65) + 100) / 360f, .25f, .50f);
                g.setColor(tint.darker()); g.fillRoundRect(0, 0, 24, 24, 8, 8);
                g.setColor(tint); g.drawRoundRect(1, 1, 22, 22, 7, 7);
                String initials = username.isBlank() ? "?" : username.substring(0, Math.min(2, username.length())).toUpperCase(java.util.Locale.ROOT);
                g.setColor(HomeTheme.TEXT); g.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g.drawString(initials, (24 - g.getFontMetrics().stringWidth(initials)) / 2f, 16);
            }
            case "tank", "lobby" -> {
                g.drawRoundRect(2, 12, 20, 8, 4, 4);
                g.fillRoundRect(6, 8, 12, 7, 2, 2);
                g.drawLine(12, 8, 12, 3); g.drawLine(11, 3, 13, 3);
                for (int i = 6; i <= 18; i += 4) { g.fillOval(i - 1, 15, 2, 2); }
            }
            case "trophy", "score" -> {
                g.drawRoundRect(7, 3, 10, 11, 3, 3);
                g.drawArc(2, 4, 8, 9, 90, 180); g.drawArc(14, 4, 8, 9, -90, 180);
                g.drawLine(12, 14, 12, 20); g.drawLine(7, 21, 17, 21);
            }
            case "shield", "wins" -> {
                Path2D p = new Path2D.Float();
                p.moveTo(12, 2); p.lineTo(21, 6); p.lineTo(19, 15); p.quadTo(16, 20, 12, 22);
                p.quadTo(8, 20, 5, 15); p.lineTo(3, 6); p.closePath(); g.draw(p);
                g.drawLine(8, 12, 11, 15); g.drawLine(11, 15, 16, 9);
            }
            case "history", "matches" -> {
                g.drawArc(4, 3, 17, 18, -60, 290); g.drawLine(4, 3, 4, 9); g.drawLine(4, 9, 9, 9);
                g.drawLine(12, 7, 12, 13); g.drawLine(12, 13, 16, 15);
            }
            case "losses", "logout" -> {
                g.drawLine(5, 5, 19, 19); g.drawLine(19, 5, 5, 19);
            }
            case "draws" -> {
                g.drawRoundRect(3, 3, 18, 18, 5, 5);
                g.drawLine(7, 9, 17, 9); g.drawLine(7, 15, 17, 15);
            }
            case "settings" -> {
                Path2D gear = new Path2D.Double();
                for (int i = 0; i < 32; i++) {
                    double angle = Math.PI * 2 * i / 32;
                    double radius = i % 4 == 0 || i % 4 == 3 ? 10 : 8;
                    double px = 12 + Math.cos(angle) * radius, py = 12 + Math.sin(angle) * radius;
                    if (i == 0) { gear.moveTo(px, py); } else { gear.lineTo(px, py); }
                }
                gear.closePath(); g.draw(gear); g.drawOval(8, 8, 8, 8);
            }
            default -> {
                g.drawOval(3, 3, 18, 18); g.drawOval(8, 8, 8, 8);
                g.drawLine(12, 1, 12, 5); g.drawLine(12, 19, 12, 23);
                g.drawLine(1, 12, 5, 12); g.drawLine(19, 12, 23, 12);
            }
        }
        g.dispose();
    }
}
