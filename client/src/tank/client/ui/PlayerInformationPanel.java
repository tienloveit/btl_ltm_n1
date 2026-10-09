package tank.client.ui;

import tank.client.model.PlayerProfile;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

public final class PlayerInformationPanel extends JPanel {
    private final JLabel usernameValue = new JLabel();
    private final JLabel matchesValue = new JLabel();
    private final JLabel scoreValue = new JLabel();
    private final JLabel winsValue = new JLabel();
    private final JLabel lossesValue = new JLabel();
    private final JLabel drawsValue = new JLabel();
    private final JLabel statusValue = new JLabel();

    public PlayerInformationPanel() {
        super(new BorderLayout(0, 16));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 28, 28));

        JPanel identity = new JPanel(new BorderLayout(0, 5));
        identity.setOpaque(false);
        JLabel caption = new JLabel("PILOT PROFILE");
        caption.setFont(caption.getFont().deriveFont(Font.BOLD, 11f));
        caption.setForeground(GameTheme.ACCENT);
        usernameValue.setFont(usernameValue.getFont().deriveFont(Font.BOLD, 25f));
        usernameValue.setForeground(GameTheme.TEXT);
        identity.add(caption, BorderLayout.NORTH);
        identity.add(usernameValue, BorderLayout.CENTER);

        JPanel stats = new JPanel(new GridLayout(2, 3, 12, 12));
        stats.setOpaque(false);
        stats.add(statCard("MATCHES PLAYED", matchesValue, GameTheme.CYAN));
        stats.add(statCard("TOTAL SCORE", scoreValue, GameTheme.ACCENT));
        stats.add(statCard("VICTORIES", winsValue, GameTheme.ACCENT));
        stats.add(statCard("DEFEATS", lossesValue, new java.awt.Color(245, 133, 110)));
        stats.add(statCard("DRAWS", drawsValue, GameTheme.CYAN));
        stats.add(statCard("STATUS", statusValue, GameTheme.ACCENT));
        add(identity, BorderLayout.NORTH);
        add(stats, BorderLayout.CENTER);
    }

    private static JPanel statCard(String caption, JLabel value, java.awt.Color accent) {
        JPanel card = GameTheme.card(new BorderLayout(0, 9));
        card.setBorder(BorderFactory.createEmptyBorder(15, 17, 15, 17));
        JLabel title = new JLabel(caption);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 10f));
        title.setForeground(GameTheme.MUTED);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 23f));
        value.setForeground(accent);
        card.add(title, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    public void setProfile(PlayerProfile profile) {
        usernameValue.setText(profile.getUsername());
        matchesValue.setText(Integer.toString(profile.getMatchesPlayed()));
        scoreValue.setText(Integer.toString(profile.getScore()));
        winsValue.setText(Integer.toString(profile.getWins()));
        lossesValue.setText(Integer.toString(profile.getLosses()));
        drawsValue.setText(Integer.toString(profile.getDraws()));
        statusValue.setText(profile.getStatus());
    }

    public void setStatus(String status) {
        statusValue.setText(status);
    }
}
