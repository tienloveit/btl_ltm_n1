package tank.client.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class GameTheme {
    static final int FORM_COLUMN_WIDTH = 340;
    static final Color BACKGROUND = new Color(12, 19, 27);
    static final Color BACKGROUND_END = new Color(19, 31, 40);
    static final Color SURFACE = new Color(25, 37, 47);
    static final Color SURFACE_LIGHT = new Color(34, 48, 58);
    static final Color FIELD = new Color(15, 25, 33);
    static final Color ACCENT = new Color(166, 232, 91);
    static final Color ACCENT_HOVER = new Color(190, 246, 116);
    static final Color CYAN = new Color(83, 214, 213);
    static final Color TEXT = new Color(238, 244, 238);
    static final Color MUTED = new Color(157, 174, 177);
    static final Color BORDER = new Color(64, 83, 89);

    private GameTheme() {
    }

    static JPanel background(LayoutManager layout) {
        return new BackgroundPanel(layout);
    }

    static JPanel card(LayoutManager layout) {
        return new CardPanel(layout);
    }

    static void styleField(JTextComponent field) {
        field.setFont(field.getFont().deriveFont(15f));
        field.setForeground(TEXT);
        field.setCaretColor(ACCENT);
        field.setSelectionColor(new Color(73, 105, 72));
        field.setBackground(FIELD);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(11, 12, 11, 12)));
    }

    static JButton button(String text, boolean primary) {
        return new GameButton(text, primary);
    }

    static JButton dangerButton(String text) {
        return new GameButton(text, false, new Color(162, 48, 49));
    }

    static void styleWindow(javax.swing.JFrame frame) {
        frame.getRootPane().setBorder(BorderFactory.createLineBorder(BORDER));
    }

    private static final class BackgroundPanel extends JPanel {
        private BackgroundPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, BACKGROUND, getWidth(), getHeight(), BACKGROUND_END));
            g.fillRect(0, 0, getWidth(), getHeight());

            g.setColor(new Color(255, 255, 255, 13));
            for (int x = 18; x < getWidth(); x += 34) {
                for (int y = 16; y < getHeight(); y += 34) {
                    g.fillOval(x, y, 2, 2);
                }
            }
            g.setColor(new Color(166, 232, 91, 28));
            g.fillOval(getWidth() - 170, -100, 260, 260);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class CardPanel extends JPanel {
        private CardPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, 70));
            g.fillRoundRect(5, 7, getWidth() - 10, getHeight() - 10, 24, 24);
            g.setColor(SURFACE);
            g.fillRoundRect(1, 1, getWidth() - 8, getHeight() - 9, 24, 24);
            g.setColor(BORDER);
            g.drawRoundRect(1, 1, getWidth() - 9, getHeight() - 10, 24, 24);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class GameButton extends JButton {
        private final boolean primary;
        private final Color customFill;
        private boolean hovered;

        private GameButton(String text, boolean primary) {
            this(text, primary, null);
        }

        private GameButton(String text, boolean primary, Color customFill) {
            super(text);
            this.primary = primary;
            this.customFill = customFill;
            setFont(getFont().deriveFont(13f).deriveFont(java.awt.Font.BOLD));
            setForeground(primary ? BACKGROUND : TEXT);
            setBorder(BorderFactory.createEmptyBorder(13, 20, 13, 20));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = primary ? (hovered ? ACCENT_HOVER : ACCENT)
                    : (hovered ? SURFACE_LIGHT : SURFACE);
            if (customFill != null) { fill = hovered ? customFill.brighter() : customFill; }
            if (!isEnabled()) {
                fill = primary ? new Color(105, 136, 74) : SURFACE;
            }
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight() - 1, 14, 14);
            if (!primary) {
                g.setColor(BORDER);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 2, 14, 14);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
