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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Modeless so expiry and disconnect can always close it through the EDT. */
public final class ChallengeDialog extends JDialog {
    private final JButton accept = GameTheme.button("Chấp nhận", true);
    private final JButton reject = GameTheme.dangerButton("Từ chối");
    private final JLabel note;
    private final Runnable onReject;
    private boolean responding;

    public ChallengeDialog(JFrame owner, String opponent, int timeoutSeconds,
                           Runnable onAccept, Runnable onReject) {
        super(owner, "Lời mời thách đấu", false);
        this.onReject = onReject;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(470, 260);
        setLocationRelativeTo(owner);
        JPanel root = GameTheme.background(new BorderLayout(0, 18));
        root.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JLabel title = new JLabel("LỜI MỜI THÁCH ĐẤU", SwingConstants.CENTER);
        title.setForeground(GameTheme.ACCENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        root.add(title, BorderLayout.NORTH);
        JPanel message = new JPanel(new GridLayout(3, 1, 0, 6));
        message.setOpaque(false);
        JLabel name = new JLabel(opponent, SwingConstants.CENTER);
        name.setForeground(GameTheme.TEXT);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 21f));
        JLabel invitation = new JLabel("Muốn đấu với bạn!", SwingConstants.CENTER);
        invitation.setForeground(GameTheme.TEXT);
        note = new JLabel("Lời mời hết hạn sau " + timeoutSeconds + " giây.", SwingConstants.CENTER);
        note.setForeground(GameTheme.MUTED);
        message.add(name);
        message.add(invitation);
        message.add(note);
        root.add(message, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        accept.addActionListener(event -> respond(onAccept));
        reject.addActionListener(event -> respond(onReject));
        buttons.add(accept);
        buttons.add(reject);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { respond(ChallengeDialog.this.onReject); }
        });
    }

    public void setBusy(boolean busy) {
        responding = busy;
        accept.setEnabled(!busy);
        reject.setEnabled(!busy);
        note.setText(busy ? "Đang chờ Server phản hồi…" : "Vui lòng chọn chấp nhận hoặc từ chối.");
    }

    private void respond(Runnable action) {
        if (responding) { return; }
        setBusy(true);
        action.run();
    }
}
