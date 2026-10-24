package tank.client.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Modeless so expiry and disconnect can always close it through the EDT. */
public final class ChallengeDialog extends JDialog {
    private final JButton accept = GameTheme.button("Chấp nhận", true);
    private final JButton reject = GameTheme.button("Từ chối", false);
    private final JLabel note;
    private final Runnable onReject;
    private boolean responding;

    public ChallengeDialog(JFrame owner, String opponent, int timeoutSeconds,
                           Runnable onAccept, Runnable onReject) {
        super(owner, "Lời mời thách đấu", false);
        this.onReject = onReject;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(520, 360);
        JPanel root = GameTheme.background(new BorderLayout());
        root.putClientProperty("home-background", Boolean.TRUE);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel card = HomeTheme.card(new BorderLayout(0, 16));
        card.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        JLabel title = new JLabel("LỜI MỜI THÁCH ĐẤU", SwingConstants.CENTER);
        title.setForeground(HomeTheme.LIME);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setIcon(new HomeIcon("tank", title, HomeTheme.LIME, 24));
        title.setIconTextGap(10);
        card.add(title, BorderLayout.NORTH);
        JPanel message = new JPanel(new BorderLayout(0, 12));
        message.setOpaque(false);
        JLabel name = new JLabel(opponent, SwingConstants.CENTER);
        name.setForeground(HomeTheme.TEXT);
        name.setFont(new Font("Segoe UI", Font.BOLD, 24));
        name.setIcon(new HomeIcon("avatar", name, null, 40, opponent));
        name.setIconTextGap(12);
        name.setToolTipText(opponent);
        JLabel invitation = new JLabel("muốn thách đấu bạn", SwingConstants.CENTER);
        invitation.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        invitation.setForeground(HomeTheme.MUTED);
        JPanel identity = new JPanel(new BorderLayout(0, 6));
        identity.setOpaque(false);
        identity.add(name, BorderLayout.CENTER);
        identity.add(invitation, BorderLayout.SOUTH);
        message.add(identity, BorderLayout.CENTER);
        note = HomeTheme.badge("Lời mời hết hạn sau " + timeoutSeconds + " giây.");
        note.setForeground(HomeTheme.GOLD);
        note.setIcon(new HomeIcon("history", note, HomeTheme.GOLD, 16));
        note.setIconTextGap(8);
        message.add(note, BorderLayout.SOUTH);
        card.add(message, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        HomeTheme.button(accept, "shield");
        HomeTheme.button(reject, "logout");
        reject.setForeground(HomeTheme.CORAL);
        accept.addActionListener(event -> respond(onAccept));
        reject.addActionListener(event -> respond(onReject));
        buttons.add(accept);
        buttons.add(reject);
        card.add(buttons, BorderLayout.SOUTH);
        root.add(card);
        setContentPane(root);
        setMinimumSize(new Dimension(470, 320));
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { respond(ChallengeDialog.this.onReject); }
        });
    }

    public void setBusy(boolean busy) {
        responding = busy;
        accept.setEnabled(!busy);
        reject.setEnabled(!busy);
        note.setText(busy ? "Đang chờ Server phản hồi…" : "Vui lòng chọn chấp nhận hoặc từ chối.");
        note.setForeground(busy ? HomeTheme.CYAN : HomeTheme.GOLD);
    }

    private void respond(Runnable action) {
        if (responding) { return; }
        setBusy(true);
        action.run();
    }
}
