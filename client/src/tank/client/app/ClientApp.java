package tank.client.app;

import tank.client.controller.ClientController;

import javax.swing.SwingUtilities;

public final class ClientApp {
    private ClientApp() {
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : environmentOrDefault("TANK_SERVER_HOST", "127.0.0.1");
        String portValue = args.length > 1 ? args[1] : environmentOrDefault("TANK_SERVER_PORT", "5000");
        int port;
        try {
            port = Integer.parseInt(portValue);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            System.err.println("Server port must be between 1 and 65535.");
            return;
        }
        SwingUtilities.invokeLater(() -> new ClientController(host, port).showLogin());
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}