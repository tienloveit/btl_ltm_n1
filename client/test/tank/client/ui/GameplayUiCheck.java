package tank.client.ui;

import tank.client.model.GameState;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

final class GameplayUiCheck {
    private static int checks;

    static int run() throws Exception {
        for (int[] size : new int[][]{{900, 540}, {1920, 1080}, {3840, 2160}}) {
            for (boolean p1 : new boolean[]{true, false}) {
                GamePlayPanel game = new GamePlayPanel(p1 ? "alice" : "bob", () -> { }, () -> { },
                        () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
                game.start("geometry", "alice", "bob");
                game.setOpaque(true);
                game.setBackground(GameTheme.BACKGROUND);
                game.setState(new GameState("geometry", 129,
                        new GameState.Tank(280, 80, 49, 1), new GameState.Tank(520, 20, 45, 0),
                        List.of(new GameState.Bullet("alice", 400, 220))));
                BufferedImage image = LobbyUiCheck.capture(game,
                        "compact-game-" + size[0] + "-p" + (p1 ? 1 : 2), size[0], size[1]);
                Component arena = named(game, "arena");
                ArenaViewport viewport = ArenaViewport.fit(arena.getWidth(), arena.getHeight());
                check(arena.getHeight() > size[1] * 0.75, "compact HUD leaves most height for the battlefield");
                check(close(viewport.bounds().getWidth() / viewport.bounds().getHeight(), 800d / 420d),
                        "battlefield aspect ratio remains unchanged");
                Point offset = SwingUtilities.convertPoint(arena, 0, 0, game);
                assertPixel(image, viewport, offset, p1, 300, 390,
                        p1 ? GameTheme.ACCENT : new Color(255, 161, 105));
                assertPixel(image, viewport, offset, p1, 540, 30,
                        p1 ? new Color(255, 161, 105) : GameTheme.ACCENT);
                assertPixel(image, viewport, offset, p1, 400, 220, GameTheme.CYAN);
                assertDifferentPixel(image, viewport, offset, p1, 305, 390,
                        p1 ? GameTheme.ACCENT : new Color(255, 161, 105));
                assertDifferentPixel(image, viewport, offset, p1, 545, 30,
                        p1 ? new Color(255, 161, 105) : GameTheme.ACCENT);
                check(close(viewport.bounds().getWidth(), arena.getWidth() - 12)
                                || close(viewport.bounds().getHeight(), arena.getHeight() - 12),
                        "field uses the largest fit, without reducing the map to shrink tanks");
                for (int tankX : new int[]{32, 400, 768}) {
                    for (int tankY : new int[]{ArenaViewport.PLAYER2_Y, ArenaViewport.PLAYER1_Y}) {
                        Rectangle2D hitbox = viewport.transform(p1).createTransformedShape(
                                new Rectangle2D.Double(tankX - 21.42, tankY + 8.6425, 42.84, 23.715)).getBounds2D();
                        check(close(hitbox.getWidth(), 42.84 * viewport.scale())
                                        && close(hitbox.getHeight(), 23.715 * viewport.scale()),
                                "hitbox scales with the tank's world transform in both perspectives");
                        check(viewport.bounds().contains(hitbox), "hitbox stays inside the field at movement limits");
                    }
                }
                game.stopEffects();
            }
        }
        for (int[] size : new int[][]{{700, 1100}, {2500, 600}}) {
            ArenaViewport view = ArenaViewport.fit(size[0], size[1]);
            check(view.x() >= 0 && view.y() >= 0 && view.bounds().getMaxX() <= size[0]
                    && view.bounds().getMaxY() <= size[1], "unusual aspect ratios fit without cropping");
        }
        return checks;
    }

    private static void assertPixel(BufferedImage image, ArenaViewport view, Point offset, boolean p1,
                                    double x, double y, Color color) {
        Point2D projected = view.transform(p1).transform(new Point2D.Double(x, y), null);
        int actual = image.getRGB(offset.x + (int) Math.round(projected.getX()),
                offset.y + (int) Math.round(projected.getY())) & 0xffffff;
        check(actual == (color.getRGB() & 0xffffff), "tank and bullet pixels match server world coordinates");
    }

    private static void assertDifferentPixel(BufferedImage image, ArenaViewport view, Point offset, boolean p1,
                                             double x, double y, Color oldHullColor) {
        Point2D projected = view.transform(p1).transform(new Point2D.Double(x, y), null);
        int actual = image.getRGB(offset.x + (int) Math.round(projected.getX()),
                offset.y + (int) Math.round(projected.getY())) & 0xffffff;
        check(actual != (oldHullColor.getRGB() & 0xffffff), "old hull edge lies outside the smaller colored hull");
    }

    private static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) { return child; }
            if (child instanceof Container container) {
                Component found = named(container, name);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 0.0001; }
    private static void check(boolean valid, String message) {
        if (!valid) { throw new AssertionError(message); }
        checks++;
    }
}
