package tank.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/** A single authoritative death, anchored in world space and driven by the match clock. */
record TankDestruction(int x, int y, boolean up, long startedAt) {
    private static final long DURATION_NANOS = 1_000_000_000L;

    boolean isComplete(long now) { return now - startedAt >= DURATION_NANOS; }

    void draw(Graphics2D scene, long now) {
        double progress = Math.max(0, Math.min(1, (now - startedAt) / (double) DURATION_NANOS));
        Graphics2D g = (Graphics2D) scene.create();
        g.translate(x, y + 20.5);
        g.scale(GamePlayPanel.TANK_SCALE, GamePlayPanel.TANK_SCALE);
        drawWreck(g);
        if (progress < 1) {
            // Broad shockwave and a brief white-hot flash, distinct from an ordinary hit.
            double radius = 10 + 90 * progress;
            g.setStroke(new BasicStroke((float) (4 * (1 - progress) + 1)));
            g.setColor(new Color(255, 172, 72, alpha(190 * (1 - progress))));
            g.draw(new Ellipse2D.Double(-radius, -radius, radius * 2, radius * 2));
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI * 2 / 32 + (x % 17) * 0.1;
                double distance = 12 + (40 + i % 5 * 9) * progress;
                double sx = Math.cos(angle) * distance;
                double sy = Math.sin(angle) * distance + 32 * progress * progress;
                g.setColor(new Color(255, 125 + i % 3 * 48, 63, alpha(255 * (1 - progress))));
                g.setStroke(new BasicStroke(i % 3 == 0 ? 3f : 1.6f));
                g.drawLine((int) (sx * 0.65), (int) (sy * 0.65), (int) sx, (int) sy);
                if (i % 4 == 0) {
                    Graphics2D debris = (Graphics2D) g.create();
                    debris.translate(sx, sy);
                    debris.rotate(angle + progress * 8);
                    debris.setColor(new Color(117, 100, 83, alpha(255 * (1 - progress))));
                    debris.fill(new Rectangle2D.Double(-4, -2, 8, 4));
                    debris.dispose();
                }
            }
            for (int i = 0; i < 8; i++) {
                double cloudSize = 16 + 46 * progress + i % 3 * 6;
                double cx = (i - 3.5) * (4 + progress * 8);
                double cy = -8 - progress * (16 + i % 4 * 9);
                g.setColor(new Color(103 + i % 2 * 22, 106 + i % 2 * 22, 107 + i % 2 * 22,
                        alpha(110 * Math.sin(Math.PI * progress))));
                g.fill(new Ellipse2D.Double(cx - cloudSize / 2, cy - cloudSize / 2, cloudSize, cloudSize));
            }
            double flame = 30 * Math.max(0, 1 - progress * 1.7);
            g.setColor(new Color(255, 116, 30, alpha(235 * (1 - progress))));
            g.fill(new Ellipse2D.Double(-flame, -flame, flame * 2, flame * 2));
            double flash = 24 * Math.max(0, 1 - progress * 5);
            g.setColor(new Color(255, 250, 218, alpha(255 * Math.max(0, 1 - progress * 5))));
            g.fill(new Ellipse2D.Double(-flash, -flash, flash * 2, flash * 2));
        }
        g.dispose();
    }

    private void drawWreck(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 155));
        g.fillOval(-46, -29, 92, 58);
        g.setColor(new Color(27, 29, 29));
        g.fillRoundRect(-40, -25, 14, 50, 7, 7);
        g.fillRoundRect(26, -25, 14, 50, 7, 7);
        g.setColor(new Color(64, 59, 52));
        g.fillRoundRect(-28, -15, 56, 31, 8, 8);
        g.setColor(new Color(29, 32, 32));
        g.fillOval(-13, -13, 26, 26);
        g.setStroke(new BasicStroke(7f));
        g.drawLine(0, 0, up ? 10 : -10, up ? -25 : 25);
        g.setColor(new Color(19, 21, 21));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(-22, -9, -4, 9);
        g.drawLine(22, 9, 6, -8);
        g.setColor(new Color(137, 68, 34));
        g.fillOval(-19, 5, 4, 3);
        g.fillOval(16, -10, 3, 3);
    }

    private static int alpha(double value) { return Math.max(0, Math.min(255, (int) value)); }
}
