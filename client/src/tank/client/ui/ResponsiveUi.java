package tank.client.ui;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.border.AbstractBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Window;

/** Applies one stable, window-size-based scale to an authenticated Swing screen. */
final class ResponsiveUi {
    private static final String METRICS_KEY = "tank.ui.baseMetrics";
    private static final String LAYOUT_KEY = "tank.ui.baseLayoutMetrics";
    private static final int BASE_WIDTH = 1000;
    private static final int BASE_HEIGHT = 620;

    private ResponsiveUi() { }

    static float scaleFor(Window window) {
        Dimension size = window.getSize();
        return scaleFor(size.width, size.height);
    }

    static float scaleFor(int width, int height) {
        float scale = Math.min(width / (float) BASE_WIDTH, height / (float) BASE_HEIGHT);
        return Math.max(1f, Math.min(1.75f, scale));
    }

    static Dimension scaled(Dimension size, float scale) {
        return new Dimension(Math.max(1, Math.round(size.width * scale)),
                Math.max(1, Math.round(size.height * scale)));
    }

    static void apply(Component component, float scale) {
        // Gameplay owns its compact HUD and world transform; lobby scaling must not resize them.
        if (component instanceof GamePlayPanel) { return; }
        if (component instanceof JComponent swing) {
            ComponentMetrics metrics = (ComponentMetrics) swing.getClientProperty(METRICS_KEY);
            if (metrics == null) {
                metrics = new ComponentMetrics(swing);
                swing.putClientProperty(METRICS_KEY, metrics);
            }
            if (metrics.font != null) {
                swing.setFont(metrics.font.deriveFont(Math.max(9f, metrics.font.getSize2D() * scale)));
            }
            if (metrics.border != null) {
                swing.setBorder(scale == 1f ? metrics.border : new ScaledBorder(metrics.border, scale));
            }
            if (metrics.preferredSize != null) { swing.setPreferredSize(scaled(metrics.preferredSize, scale)); }
            if (metrics.minimumSize != null) { swing.setMinimumSize(scaled(metrics.minimumSize, scale)); }
            if (metrics.maximumSize != null) { swing.setMaximumSize(scaled(metrics.maximumSize, scale)); }
            if (swing instanceof JTable table && metrics.rowHeight > 0) {
                table.setRowHeight(Math.max(18, Math.round(metrics.rowHeight * scale)));
                int count = Math.min(metrics.tableColumnWidths.length, table.getColumnModel().getColumnCount());
                for (int i = 0; i < count; i++) {
                    table.getColumnModel().getColumn(i).setPreferredWidth(
                            Math.max(24, Math.round(metrics.tableColumnWidths[i] * scale)));
                }
            }
            scaleLayout(swing, scale);
        } else if (component instanceof Container container) {
            scaleLayout(container, scale);
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) { apply(child, scale); }
        }
    }

    private static void scaleLayout(Container container, float scale) {
        LayoutManager layout = container.getLayout();
        if (!(container instanceof JComponent swing) || layout == null) { return; }
        LayoutMetrics metrics = (LayoutMetrics) swing.getClientProperty(LAYOUT_KEY);
        if (metrics == null || metrics.layout != layout) {
            metrics = new LayoutMetrics(layout);
            swing.putClientProperty(LAYOUT_KEY, metrics);
        }
        if (layout instanceof BorderLayout border) {
            border.setHgap(Math.round(metrics.horizontalGap * scale));
            border.setVgap(Math.round(metrics.verticalGap * scale));
        } else if (layout instanceof GridLayout grid) {
            grid.setHgap(Math.round(metrics.horizontalGap * scale));
            grid.setVgap(Math.round(metrics.verticalGap * scale));
        } else if (layout instanceof FlowLayout flow) {
            flow.setHgap(Math.round(metrics.horizontalGap * scale));
            flow.setVgap(Math.round(metrics.verticalGap * scale));
        }
    }

    private static final class ComponentMetrics {
        final Font font;
        final Border border;
        final Dimension preferredSize;
        final Dimension minimumSize;
        final Dimension maximumSize;
        final int rowHeight;
        final int[] tableColumnWidths;

        ComponentMetrics(JComponent component) {
            font = component.getFont();
            border = component.getBorder();
            preferredSize = component.isPreferredSizeSet() ? new Dimension(component.getPreferredSize()) : null;
            minimumSize = component.isMinimumSizeSet() ? new Dimension(component.getMinimumSize()) : null;
            maximumSize = component.isMaximumSizeSet() ? new Dimension(component.getMaximumSize()) : null;
            rowHeight = component instanceof JTable table ? table.getRowHeight() : -1;
            if (component instanceof JTable table) {
                tableColumnWidths = new int[table.getColumnModel().getColumnCount()];
                for (int i = 0; i < tableColumnWidths.length; i++) {
                    tableColumnWidths[i] = table.getColumnModel().getColumn(i).getPreferredWidth();
                }
            } else {
                tableColumnWidths = new int[0];
            }
        }
    }

    private static final class LayoutMetrics {
        final LayoutManager layout;
        final int horizontalGap;
        final int verticalGap;

        LayoutMetrics(LayoutManager layout) {
            this.layout = layout;
            if (layout instanceof BorderLayout border) {
                horizontalGap = border.getHgap();
                verticalGap = border.getVgap();
            } else if (layout instanceof GridLayout grid) {
                horizontalGap = grid.getHgap();
                verticalGap = grid.getVgap();
            } else if (layout instanceof FlowLayout flow) {
                horizontalGap = flow.getHgap();
                verticalGap = flow.getVgap();
            } else {
                horizontalGap = 0;
                verticalGap = 0;
            }
        }
    }

    private static final class ScaledBorder extends AbstractBorder {
        private final Border delegate;
        private final float scale;

        ScaledBorder(Border delegate, float scale) {
            this.delegate = delegate;
            this.scale = scale;
        }

        @Override public Insets getBorderInsets(Component component) {
            Insets base = delegate.getBorderInsets(component);
            return new Insets(Math.round(base.top * scale), Math.round(base.left * scale),
                    Math.round(base.bottom * scale), Math.round(base.right * scale));
        }

        @Override public Insets getBorderInsets(Component component, Insets insets) {
            Insets scaled = getBorderInsets(component);
            insets.set(scaled.top, scaled.left, scaled.bottom, scaled.right);
            return insets;
        }

        @Override public boolean isBorderOpaque() { return delegate.isBorderOpaque(); }

        @Override public void paintBorder(Component component, java.awt.Graphics graphics,
                                          int x, int y, int width, int height) {
            delegate.paintBorder(component, graphics, x, y, width, height);
        }
    }
}
