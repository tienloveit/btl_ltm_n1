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
import java.util.Arrays;

public final class RegisterFrame extends JFrame {
    private final ClientController controller;
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JPasswordField repeatPasswordField = new JPasswordField(20);
    private final JTextField emailField = new JTextField(20);
    private final JLabel messageLabel = new JLabel(" ");
    private final JButton signUpButton = GameTheme.button("SIGN UP", true);

    public RegisterFrame(ClientController controller) {
        super("Tank Arena - Recruit Registration");
        this.controller = controller;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(540, 730);
        setLocationRelativeTo(null);
        setResizable(false);
        GameTheme.styleWindow(this);

        signUpButton.addActionListener(event -> submitRegistration());
        signUpButton.setAlignmentX(CENTER_ALIGNMENT);
        signUpButton.setMaximumSize(new Dimension(GameTheme.FORM_COLUMN_WIDTH, 48));
        JButton backButton = GameTheme.button("BACK TO LOGIN", false);
        backButton.addActionListener(event -> controller.showLogin());

        JPanel root = GameTheme.background(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(26, 42, 26, 42));
        JPanel card = GameTheme.card(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(27, 38, 22, 38));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel kicker = label("TACTICAL NETWORK  /  NEW RECRUIT", 11f, GameTheme.ACCENT);
        kicker.setAlignmentX(CENTER_ALIGNMENT);
        JLabel title = label("JOIN THE ARENA", 27f, GameTheme.TEXT);
        title.setAlignmentX(CENTER_ALIGNMENT);
        JLabel subtitle = label("Create your pilot profile to get started.", 13f, GameTheme.MUTED);
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        content.add(kicker);
        content.add(Box.createVerticalStrut(8));
        content.add(title);
        content.add(Box.createVerticalStrut(5));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(22));

        JPanel fields = new JPanel(new GridLayout(4, 1, 0, 12));
        fields.setOpaque(false);
        fields.add(fieldBlock("USERNAME", usernameField));
        fields.add(fieldBlock("PASSWORD", passwordField));
        fields.add(fieldBlock("REPEAT PASSWORD", repeatPasswordField));
        fields.add(fieldBlock("EMAIL", emailField));
        Dimension fieldsSize = new Dimension(GameTheme.FORM_COLUMN_WIDTH, 308);
        fields.setAlignmentX(CENTER_ALIGNMENT);
        fields.setMinimumSize(fieldsSize);
        fields.setPreferredSize(fieldsSize);
        fields.setMaximumSize(fieldsSize);
        content.add(fields);
        content.add(Box.createVerticalStrut(18));
        content.add(signUpButton);

        messageLabel.setFont(messageLabel.getFont().deriveFont(12f));
        messageLabel.setForeground(GameTheme.CYAN);
        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        footer.add(messageLabel, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        actions.setOpaque(false);
        actions.add(backButton);
        footer.add(actions, BorderLayout.SOUTH);

        card.add(content, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        root.add(card, BorderLayout.CENTER);
        setContentPane(root);
    }

    private static JPanel fieldBlock(String caption, JTextField field) {
        JPanel block = new JPanel(new BorderLayout(0, 6));
        block.setOpaque(false);
        JLabel label = label(caption, 10.5f, GameTheme.MUTED);
        block.add(label, BorderLayout.NORTH);
        GameTheme.styleField(field);
        block.add(field, BorderLayout.CENTER);
        block.setAlignmentX(LEFT_ALIGNMENT);
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        return block;
    }

    private static JLabel label(String text, float size, java.awt.Color color) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, size));
        label.setForeground(color);
        return label;
    }

    private void submitRegistration() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        char[] password = passwordField.getPassword();
        char[] repeatedPassword = repeatPasswordField.getPassword();
        try {
            if (username.isEmpty() || email.isEmpty() || password.length == 0 || repeatedPassword.length == 0) {
                setMessage("Please complete all fields.");
                return;
            }
            if (!Arrays.equals(password, repeatedPassword)) {
                setMessage("Passwords do not match.");
                return;
            }
            signUpButton.setEnabled(false);
            setMessage("Creating account...");
            controller.register(username, email, password);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(repeatedPassword, '\0');
        }
    }

    public void setMessage(String message) {
        messageLabel.setText(message == null ? " " : message);
    }

    public void setBusy(boolean busy) {
        signUpButton.setEnabled(!busy);
    }
}
