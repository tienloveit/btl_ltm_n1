package tank.server.game;

public final class GameGeometryCheck {
    private GameGeometryCheck() { }

    public static int run() {
        int checks = 0;
        if (tank.server.auth.AccountService.pointsForResult(true, false) != 3
                || tank.server.auth.AccountService.pointsForResult(false, true) != 1
                || tank.server.auth.AccountService.pointsForResult(false, false) != 0) {
            throw new AssertionError("Match rewards must match backend ranking points");
        }
        checks++;
        for (boolean bottom : new boolean[]{false, true}) {
            int y = bottom ? 370 : 10;
            int cannonY = bottom ? y + 47 : y - 6;
            int[][] inside = {
                    {400, y + 20},
                    {431, y + 39}, // Outer tread, right side.
                    {369, y + 2},  // Outer tread, left side.
                    {400, cannonY},
                    {440, y + 20}  // Near the circular edge.
            };
            int[][] outside = {
                    {442, y + 20}, // Just beyond the circular radius.
                    {400, y + 62},
                    {430, y + 60}
            };
            for (int[] point : inside) {
                if (!GameStateManager.hitsTank(point[0], point[1], 400, bottom)) {
                    throw new AssertionError("Tank hitbox must include the body, tracks and cannon");
                }
                checks++;
            }
            for (int[] point : outside) {
                if (GameStateManager.hitsTank(point[0], point[1], 400, bottom)) {
                    throw new AssertionError("Tank hitbox must exclude shots outside the circle");
                }
                checks++;
            }
        }
        return checks;
    }
}
