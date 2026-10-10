package tank.client.ui;

import tank.client.controller.ClientController;
import tank.client.model.PlayerProfile;
import tank.client.model.PlayerSummary;
import tank.client.model.RoomState;
import tank.client.model.GameState;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises real Swing controls on the EDT and exports offscreen previews. */
public final class LobbyUiCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PlayerFrame frame = null;
            ChallengeDialog dialog = null;
            try {
                List<PlayerSummary> roster = List.of(
                        new PlayerSummary("doanngocquang", 100, "ONLINE"),
                        new PlayerSummary("player_a", 422, "ONLINE"),
                        new PlayerSummary("player_b", 421, "PLAYING"),
                        new PlayerSummary("player_c", 321, "WAITING"),
                        new PlayerSummary("player_d", 231, "ONLINE"));
                AtomicReference<String> target = new AtomicReference<>();
                OnlinePlayersPanel panel = new OnlinePlayersPanel("doanngocquang", target::set);
                panel.setPlayers(roster);
                JTable table = find(panel, JTable.class);
                check(table.getRowCount() == 4, "own account omitted");
                check(table.getColumnCount() == 4 && "Thao tác".equals(table.getColumnName(3)), "four labeled lobby columns");
                check(table.isCellEditable(0, 3), "available opponent can be challenged");
                check(!table.isCellEditable(1, 3) && !table.isCellEditable(2, 3), "busy opponents disabled");
                table.editCellAt(0, 3);
                ((JButton) table.getEditorComponent()).doClick();
                check("player_a".equals(target.get()), "button sends the selected username");
                check(!table.isCellEditable(3, 3), "pending send prevents double-click requests");
                panel.setRequesting(false);
                panel.setPlayers(List.of(new PlayerSummary("doanngocquang", 100, "WAITING"), roster.get(1)));
                check(!table.isCellEditable(0, 3), "own busy status disables challenges");

                frame = new PlayerFrame(new ClientController("127.0.0.1", 5000),
                        new PlayerProfile("doanngocquang", 13, 100, 10, 2, 1, "ONLINE"));
                frame.updatePlayers(roster);
                capture((JComponent) frame.getContentPane(), "lobby", 960, 560);
                frame.showPending("challenge-preview", "player_a", 30);
                capture((JComponent) frame.getContentPane(), "pending", 960, 560);
                frame.closeChallenge("challenge-preview", "ACCEPTED");
                frame.showRoom(new RoomState("12345678-0000-0000-0000-000000000000", "doanngocquang", "player_a", 3));
                capture((JComponent) frame.getContentPane(), "room", 960, 560);
                frame.showMatchStarted(true);
                capture((JComponent) frame.getContentPane(), "handoff", 960, 560);
                frame.showGame("12345678-0000-0000-0000-000000000000", "doanngocquang", "player_a");
                frame.updateGame(new GameState("12345678-0000-0000-0000-000000000000", 179,
                        new GameState.Tank(400, 100, 49, 1), new GameState.Tank(280, 80, 45, 0),
                        List.of(new GameState.Bullet("doanngocquang", 400, 220))));
                capture((JComponent) frame.getContentPane(), "gameplay", 960, 560);
                frame.showHitEffect("12345678-0000-0000-0000-000000000000", 400, 220);
                capture((JComponent) frame.getContentPane(), "hit-effect", 960, 560);
                frame.showGameOver("12345678-0000-0000-0000-000000000000", "doanngocquang", "Đối thủ đã hết HP.");
                capture((JComponent) frame.getContentPane(), "game-over", 960, 560);

                AtomicInteger accepts = new AtomicInteger(), rejects = new AtomicInteger();
                dialog = new ChallengeDialog(frame, "doanngocquang", 30, accepts::incrementAndGet, rejects::incrementAndGet);
                capture((JComponent) dialog.getContentPane(), "invitation", 470, 240);
                findButton(dialog.getContentPane(), "Chấp nhận").doClick();
                findButton(dialog.getContentPane(), "Chấp nhận").doClick();
                check(accepts.get() == 1, "accept disabled while waiting for response");
                dialog.dispose();
                dialog = new ChallengeDialog(frame, "player_a", 30, accepts::incrementAndGet, rejects::incrementAndGet);
                dialog.dispatchEvent(new java.awt.event.WindowEvent(dialog, java.awt.event.WindowEvent.WINDOW_CLOSING));
                check(rejects.get() == 1, "closing invitation sends rejection");
                System.out.println("PASS: " + checks + " Swing checks; previews: client/out/preview/");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                if (dialog != null) { dialog.dispose(); }
                if (frame != null) { frame.dispose(); }
            }
        });
    }

    private static void capture(JComponent component, String name, int width, int height) throws Exception {
        component.setSize(width, height);
        layout(component);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        component.printAll(graphics);
        graphics.dispose();
        Path directory = Path.of("client", "out", "preview");
        Files.createDirectories(directory);
        ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container container) { layout(container); }
        }
    }

    private static <T> T find(Container parent, Class<T> type) {
        for (Component component : parent.getComponents()) {
            if (type.isInstance(component)) { return type.cast(component); }
            if (component instanceof Container container) {
                T result = find(container, type);
                if (result != null) { return result; }
            }
        }
        return null;
    }

    private static JButton findButton(Container parent, String text) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) { return button; }
            if (component instanceof Container container) {
                JButton result = findButton(container, text);
                if (result != null) { return result; }
            }
        }
        return null;
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
        checks++;
    }
}
