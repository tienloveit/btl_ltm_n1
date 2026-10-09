package tank.client.protocol;

public final class Protocol {
    public static final String REGISTER_REQUEST = "REGISTER_REQUEST";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String REGISTER_FAIL = "REGISTER_FAIL";
    public static final String LOGIN_REQUEST = "LOGIN_REQUEST";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String PLAYER_LIST = "PLAYER_LIST";
    public static final String LOGOUT_REQUEST = "LOGOUT_REQUEST";
    public static final String LOGOUT_SUCCESS = "LOGOUT_SUCCESS";
    public static final String SERVER_ERROR = "SERVER_ERROR";
    public static final String CHALLENGE = "CHALLENGE";
    public static final String CHALLENGE_PENDING = "CHALLENGE_PENDING";
    public static final String CHALLENGE_RECEIVED = "CHALLENGE_RECEIVED";
    public static final String CHALLENGE_CANCEL = "CHALLENGE_CANCEL";
    public static final String CHALLENGE_CLOSED = "CHALLENGE_CLOSED";
    public static final String ROOM_STATE = "ROOM_STATE";
    public static final String ROOM_LEAVE = "ROOM_LEAVE";
    public static final String ROOM_CLOSED = "ROOM_CLOSED";
    public static final String LOBBY_ERROR = "LOBBY_ERROR";
    public static final String ACCEPT = "ACCEPT";
    public static final String REJECT = "REJECT";
    public static final String MATCH_START = "MATCH_START";
    public static final String MOVE_LEFT = "MOVE_LEFT";
    public static final String MOVE_RIGHT = "MOVE_RIGHT";
    public static final String SHOOT = "SHOOT";
    public static final String GAME_STATE = "GAME_STATE";
    public static final String GAME_OVER = "GAME_OVER";
    public static final String PLAY_AGAIN = "PLAY_AGAIN";
    public static final String EXIT = "EXIT";

    private Protocol() {
    }
}
