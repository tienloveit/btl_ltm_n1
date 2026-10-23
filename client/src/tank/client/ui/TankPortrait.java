package tank.client.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BasicStroke;

/** Small vector tank portrait for the two waiting-room player cards. */
final class TankPortrait extends JComponent {
    private final Color bodyColor;
    private final boolean facesRight;

    TankPortrait(Color bodyColor, boolean facesRight) {
        this.bodyColor = bodyColor;
        this.facesRight = facesRight;
        setOpaque(false);
        setPreferredSize(new Dimension(180, 100));
        setMaximumSize(new Dimension(180, 100));
        setMinimumSize(new Dimension(132, 68));
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float scale = Math.min(getWidth() / 132f, getHeight() / 86f);
        if (scale <= 0f) { g.dispose(); return; }
        g.translate((getWidth() - 132f * scale) / 2f, (getHeight() - 86f * scale) / 2f);
        g.scale(scale, scale);
        if (!facesRight) {
            g.translate(132, 0);
            g.scale(-1, 1);
        }

        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(12, 69, 108, 10);
        g.setColor(new Color(12, 22, 25));
        g.fillRoundRect(12, 45, 105, 27, 22, 22);
        g.setColor(new Color(76, 92, 92));
        g.setStroke(new BasicStroke(1.3f));
        g.drawRoundRect(13, 46, 103, 25, 22, 22);
        for (int x = 22; x <= 102; x += 16) {
            g.setColor(new Color(47, 63, 64)); g.fillOval(x, 50, 17, 17);
            g.setColor(new Color(99, 119, 114)); g.drawOval(x, 50, 17, 17);
            g.fillOval(x + 6, 56, 5, 5);
        }
        g.setColor(bodyColor.darker());
        g.fillPolygon(new int[]{17, 28, 99, 116, 110, 20}, new int[]{45, 34, 34, 46, 54, 54}, 6);
        g.setColor(bodyColor);
        g.fillPolygon(new int[]{27, 34, 96, 107, 104, 24}, new int[]{34, 29, 29, 41, 46, 46}, 6);
        g.setColor(bodyColor.darker());
        g.fillPolygon(new int[]{42, 48, 77, 89, 84, 40}, new int[]{25, 18, 18, 28, 37, 37}, 6);
        g.setColor(bodyColor);
        g.fillPolygon(new int[]{48, 53, 74, 83, 78, 44}, new int[]{18, 15, 15, 24, 30, 30}, 6);
        g.setColor(new Color(30, 45, 46)); g.fillRoundRect(58, 12, 16, 5, 3, 3);
        g.setColor(bodyColor.darker()); g.fillRect(79, 25, 45, 7);
        g.setColor(bodyColor); g.fillRect(81, 24, 43, 3);
        g.setColor(new Color(22, 34, 35)); g.fillRect(122, 23, 6, 11);
        g.setColor(new Color(228, 240, 223, 140));
        g.drawLine(30, 34, 94, 34); g.drawLine(49, 19, 73, 19);
        g.drawLine(33, 37, 30, 44); g.drawLine(51, 37, 49, 44);
        g.drawLine(87, 37, 90, 44);
        g.setColor(new Color(235, 234, 201)); g.fillRoundRect(99, 40, 6, 3, 2, 2);
        g.dispose();
    }
}
