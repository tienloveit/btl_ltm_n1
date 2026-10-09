package tank.client.ui;

import tank.client.controller.ClientController;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

public final class LoginFrame extends JFrame {
    private final ClientController controller;
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JLabel messageLabel = new JLabel(" ");
    private final JButton loginButton = GameTheme.button("LOGIN", true);

    public LoginFrame(ClientController controller) {
        super("Tank Arena - Command Access");
        this.controller = controller;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(540, 650);
        setLocationRelativeTo(null);
        setResizable(false);
        GameTheme.styleWindow(this);

        loginButton.addActionListener(event -> login());
        loginButton.setAlignmentX(CENTER_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(GameTheme.FORM_COLUMN_WIDTH, 48));

        JPanel root = GameTheme.background(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(28, 42, 28, 42));
        JPanel content = GameTheme.card(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(32, 38, 26, 38));

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JLabel kicker = label("TACTICAL NETWORK  /  PLAYER ACCESS", 11f, GameTheme.ACCENT);
        kicker.setAlignmentX(CENTER_ALIGNMENT);
        JLabel title = label("TANK ARENA", 30f, GameTheme.TEXT);
        title.setAlignmentX(CENTER_ALIGNMENT);
        JLabel subtitle = label("Enter the battlefield with your squad.", 13f, GameTheme.MUTED);
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        form.add(kicker);
        form.add(Box.createVerticalStrut(10));
        form.add(title);
        form.add(Box.createVerticalStrut(6));
        form.add(subtitle);
        form.add(Box.createVerticalStrut(30));

        JPanel fields = new JPanel(new GridLayout(2, 1, 0, 18));
        fields.setOpaque(false);
        fields.add(fieldBlock("USERNAME", usernameField));
        fields.add(fieldBlock("PASSWORD", passwordField));
        Dimension fieldsSize = new Dimension(GameTheme.FORM_COLUMN_WIDTH, 170);
        fields.setAlignmentX(CENTER_ALIGNMENT);
        fields.setMinimumSize(fieldsSize);
        fields.setPreferredSize(fieldsSize);
        fields.setMaximumSize(fieldsSize);
        form.add(fields);
        form.add(Box.createVerticalStrut(24));
        form.add(loginButton);
        form.add(Box.createVerticalStrut(14));
        messageLabel.setFont(messageLabel.getFont().deriveFont(12f));
        messageLabel.setForeground(GameTheme.CYAN);
        messageLabel.setAlignmentX(CENTER_ALIGNMENT);
        form.add(messageLabel);

        JLabel noAccount = label("NEW RECRUIT?", 11f, GameTheme.MUTED);
        JButton signUpLink = GameTheme.button("SIGN UP", false);
        signUpLink.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        signUpLink.addActionListener(event -> controller.showRegister());
        JPanel signUpRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        signUpRow.setOpaque(false);
        signUpRow.add(noAccount);
        signUpRow.add(signUpLink);

        content.add(form, BorderLayout.CENTER);
        content.add(signUpRow, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
    }

    private static JPanel fieldBlock(String caption, JTextField field) {
        JPanel block = new JPanel(new BorderLayout(0, 8));
        block.setOpaque(false);
        JLabel label = label(caption, 11f, GameTheme.MUTED);
        block.add(label, BorderLayout.NORTH);
        GameTheme.styleField(field);
        block.add(field, BorderLayout.CENTER);
        block.setAlignmentX(LEFT_ALIGNMENT);
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        return block;
    }

    private static JLabel label(String text, float size, java.awt.Color color) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, size));
        label.setForeground(color);
        return label;
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            setMessage("Nhập username và mật khẩu.");
            return;
        }
        setMessage("Đang kết nối và đăng nhập...");
        loginButton.setEnabled(false);
        controller.login(username, password);
    }

    public void setMessage(String message) {
        messageLabel.setText(message == null ? " " : message);
        loginButton.setEnabled(true);
    }
}
