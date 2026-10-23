package tank.client.ui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

final class PlayerTableActions {
    private PlayerTableActions() { }

    static void install(JTable table, int nameColumn, int actionColumn, Consumer<String> onDetails) {
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 1 && row >= 0
                        && column >= 0 && table.convertColumnIndexToModel(column) != actionColumn) {
                    open(row);
                }
            }
            private void open(int row) {
                onDetails.accept(String.valueOf(table.getModel().getValueAt(
                        table.convertRowIndexToModel(row), nameColumn)));
            }
        });
        table.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                int column = table.columnAtPoint(event.getPoint());
                boolean clickable = table.rowAtPoint(event.getPoint()) >= 0 && column >= 0
                        && table.convertColumnIndexToModel(column) != actionColumn;
                table.setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "view-player-details");
        table.getActionMap().put("view-player-details", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                int row = table.getSelectedRow();
                if (row >= 0 && !table.isEditing()) {
                    onDetails.accept(String.valueOf(table.getModel().getValueAt(
                            table.convertRowIndexToModel(row), nameColumn)));
                }
            }
        });
    }
}
