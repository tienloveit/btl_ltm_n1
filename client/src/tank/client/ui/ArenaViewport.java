package tank.client.ui;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/** The authoritative 800 x 420 world is fitted once, including every sprite and effect. */
record ArenaViewport(double x, double y, double scale) {
    static final int WIDTH = 800;
    static final int HEIGHT = 420;
    static final int PLAYER1_Y = 370;
    static final int PLAYER2_Y = 10;

    static ArenaViewport fit(int width, int height) {
        double scale = Math.min(Math.max(1, width - 12) / (double) WIDTH,
                Math.max(1, height - 12) / (double) HEIGHT);
        return new ArenaViewport((width - WIDTH * scale) / 2,
                (height - HEIGHT * scale) / 2, scale);
    }

    AffineTransform transform(boolean localIsPlayer1) {
        AffineTransform transform = new AffineTransform();
        transform.translate(x, y);
        transform.scale(scale, scale);
        if (!localIsPlayer1) {
            transform.translate(0, HEIGHT);
            transform.scale(1, -1);
        }
        return transform;
    }

    Rectangle2D bounds() {
        return new Rectangle2D.Double(x, y, WIDTH * scale, HEIGHT * scale);
    }
}
