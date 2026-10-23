package tank.client.ui;

import tank.client.audio.SoundPlayer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.function.Consumer;

final class SoundControls {
    private SoundControls() { }

    static JButton button(SoundPlayer audio) { return button(audio, ignored -> { }); }

    static JButton button(SoundPlayer audio, Consumer<Boolean> openChanged) {
        JButton button = GameTheme.button("Cài đặt", false);
        button.setName("audio-settings");
        button.setFocusable(false);
        button.setToolTipText("Điều chỉnh âm lượng");

        JPanel body = new JPanel(new GridLayout(0, 1, 0, 12));
        body.setBackground(GameTheme.SURFACE);
        body.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        body.setPreferredSize(new Dimension(340, 230));
        JLabel title = label("Cài đặt âm thanh");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        body.add(title);
        JSlider music = new JSlider(0, 100, audio.getMusicVolume());
        JSlider effects = new JSlider(0, 100, audio.getEffectsVolume());
        music.setName("music-volume");
        effects.setName("effects-volume");
        JLabel musicValue = label("");
        JLabel effectsValue = label("");
        body.add(row("Nhạc nền", music, musicValue));
        body.add(row("Hiệu ứng âm thanh", effects, effectsValue));

        JDialog[] dialogRef = {null};

        boolean[] updating = {false}, active = {false};
        Runnable refresh = () -> {
            updating[0] = true;
            try {
                music.setValue(audio.getMusicVolume());
                effects.setValue(audio.getEffectsVolume());
                musicValue.setText(music.getValue() + "%");
                effectsValue.setText(effects.getValue() + "%");
            } finally { updating[0] = false; }
        };
        Runnable listener = () -> {
            if (SwingUtilities.isEventDispatchThread()) { refresh.run(); }
            else { SwingUtilities.invokeLater(() -> { if (active[0]) { refresh.run(); } }); }
        };
        music.addChangeListener(event -> {
            if (!updating[0]) {
                audio.setMusicVolume(music.getValue());
                musicValue.setText(music.getValue() + "%");
                if (!music.getValueIsAdjusting()) { audio.saveSettings(); }
            }
        });
        effects.addChangeListener(event -> {
            if (!updating[0]) {
                audio.setEffectsVolume(effects.getValue());
                effectsValue.setText(effects.getValue() + "%");
                if (!effects.getValueIsAdjusting()) { audio.saveSettings(); }
            }
        });
        Runnable close = () -> {
            if (active[0]) {
                active[0] = false;
                audio.removeStateListener(listener);
                audio.saveSettings();
                openChanged.accept(false);
            }
            if (dialogRef[0] != null && dialogRef[0].isVisible()) { dialogRef[0].setVisible(false); }
        };
        button.addActionListener(event -> {
            if (dialogRef[0] != null && dialogRef[0].isVisible()) { close.run(); return; }
            if (dialogRef[0] == null) {
                Window owner = SwingUtilities.getWindowAncestor(button);
                JDialog dialog = new JDialog(owner, "Cài đặt âm thanh", Dialog.ModalityType.MODELESS);
                dialog.setName("audio-settings-window");
                dialog.setResizable(false);
                dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
                dialog.getContentPane().setBackground(GameTheme.SURFACE);
                dialog.setContentPane(body);
                dialog.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosing(WindowEvent event) { close.run(); }
                    @Override public void windowClosed(WindowEvent event) { close.run(); }
                    @Override public void windowOpened(WindowEvent event) { refresh.run(); }
                });
                dialog.addWindowFocusListener(new WindowFocusListener() {
                    @Override public void windowGainedFocus(WindowEvent event) { }
                    @Override public void windowLostFocus(WindowEvent event) { close.run(); }
                });
                dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                        .put(KeyStroke.getKeyStroke("ESCAPE"), "close-settings");
                dialog.getRootPane().getActionMap().put("close-settings", new AbstractAction() {
                    @Override public void actionPerformed(ActionEvent event) { close.run(); }
                });
                dialogRef[0] = dialog;
                button.putClientProperty("audio-settings-window", dialog);
            }
            Window owner = dialogRef[0].getOwner();
            float scale = ResponsiveUi.scaleFor(owner);
            ResponsiveUi.apply(body, scale);
            dialogRef[0].pack();
            dialogRef[0].setLocationRelativeTo(owner);
            refresh.run();
            active[0] = true;
            audio.addStateListener(listener);
            openChanged.accept(true);
            dialogRef[0].setVisible(true);
            SwingUtilities.invokeLater(() -> { if (dialogRef[0].isVisible()) { music.requestFocusInWindow(); } });
        });
        button.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !button.isShowing()) {
                close.run();
            }
        });
        refresh.run();
        return button;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(GameTheme.TEXT);
        label.setFont(label.getFont().deriveFont(14f));
        return label;
    }

    private static JPanel row(String text, JSlider slider, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 4));
        row.setOpaque(false);
        JPanel captions = new JPanel(new BorderLayout());
        captions.setOpaque(false);
        JLabel caption = label(text);
        caption.setHorizontalAlignment(SwingConstants.LEADING);
        captions.add(caption, BorderLayout.WEST);
        captions.add(value, BorderLayout.EAST);
        row.add(captions, BorderLayout.NORTH);
        slider.setOpaque(false);
        slider.setForeground(GameTheme.ACCENT);
        slider.getAccessibleContext().setAccessibleName(text);
        row.add(slider, BorderLayout.CENTER);
        return row;
    }
}
