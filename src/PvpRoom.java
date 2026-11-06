import java.util.ArrayList;
import java.util.List;

/**
 * 一盘双人联机棋。
 *
 * <p>Room 负责把两个浏览器会话分到黑、白两方，并保证只能由当前轮次的一方落子。
 * 它和 {@link Game} 的区别是：Game 固定由玩家执黑、电脑执白，这里两边都是真人。</p>
 */
public class PvpRoom {

    public static final int PLAYING = 0;
    public static final int BLACK_WIN = 1;
    public static final int WHITE_WIN = 2;
    public static final int DRAW = 3;

    private final String code;
    private final int size;
    private final List<int[]> history = new ArrayList<>();

    private Board board;
    private String blackId;
    private String whiteId;
    private int status = PLAYING;
    private int turn = Board.BLACK;
    private int[][] winLine;
    private long lastActive = System.currentTimeMillis();

    public PvpRoom(String code, int size) {
        this.code = code;
        this.size = size;
        this.board = new Board(size);
    }

    public synchronized String code() {
        return code;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized int colorOf(String sessionId) {
        if (sessionId == null) {
            return Board.EMPTY;
        }
        if (sessionId.equals(blackId)) {
            return Board.BLACK;
        }
        if (sessionId.equals(whiteId)) {
            return Board.WHITE;
        }
        return Board.EMPTY;
    }

    /**
     * 加入房间。先到的人执黑，后到的人执白。
     *
     * @return true 表示加入成功或本来就在房间里；false 表示房间已经满了
     */
    public synchronized boolean join(String sessionId) {
        touch();
        if (colorOf(sessionId) != Board.EMPTY) {
            return true;
        }
        if (blackId == null) {
            blackId = sessionId;
            return true;
        }
        if (whiteId == null) {
            whiteId = sessionId;
            return true;
        }
        return false;
    }

    /**
     * 离开房间。空出来的位置可以被新玩家补上；
     * 只要走了一个人，这盘棋就作废并重新开始，避免残局被误接。
     */
    public synchronized void leave(String sessionId) {
        boolean left = false;
        if (sessionId != null && sessionId.equals(blackId)) {
            blackId = null;
            left = true;
        } else if (sessionId != null && sessionId.equals(whiteId)) {
            whiteId = null;
            left = true;
        }
        if (left) {
            resetBoard();
            touch();
        }
    }

    public synchronized boolean isEmpty() {
        return blackId == null && whiteId == null;
    }

    public synchronized boolean isReady() {
        return blackId != null && whiteId != null;
    }

    public synchronized long idleMillis() {
        return System.currentTimeMillis() - lastActive;
    }

    /** 联机落子：校验身份、轮次、坐标，然后落子并切换回合。 */
    public synchronized void move(String sessionId, int r, int c) {
        touch();
        if (!isReady()) {
            throw new IllegalArgumentException("等待对手加入后再落子");
        }
        if (status != PLAYING) {
            throw new IllegalArgumentException("对局已经结束，请点「新对局」再来一盘");
        }

        int color = colorOf(sessionId);
        if (color == Board.EMPTY) {
            throw new IllegalArgumentException("你不在这个房间里");
        }
        if (turn != color) {
            throw new IllegalArgumentException("还没轮到你落子");
        }
        if (!board.inBounds(r, c)) {
            throw new IllegalArgumentException("坐标超出棋盘范围");
        }
        if (!board.isEmpty(r, c)) {
            throw new IllegalArgumentException("该位置已经有棋子了");
        }

        board.place(r, c, color);
        history.add(new int[]{r, c, color});

        int[][] line = board.winLine(r, c);
        if (line != null) {
            winLine = line;
            status = (color == Board.BLACK) ? BLACK_WIN : WHITE_WIN;
            return;
        }
        if (board.isFull()) {
            status = DRAW;
            return;
        }
        turn = other(color);
    }

    /**
     * 悔棋只允许撤回“自己刚刚落下的最后一手”。
     * 这样对手已经落子之后，你就不能单方面改掉历史。
     */
    public synchronized void undo(String sessionId) {
        touch();
        int color = colorOf(sessionId);
        if (color == Board.EMPTY) {
            throw new IllegalArgumentException("你不在这个房间里");
        }
        if (history.isEmpty()) {
            throw new IllegalArgumentException("还没有落子，无法悔棋");
        }
        int[] last = history.get(history.size() - 1);
        if (last[2] != color) {
            throw new IllegalArgumentException("只能撤回自己刚刚落下的最后一手");
        }

        history.remove(history.size() - 1);
        board.clear(last[0], last[1]);
        status = PLAYING;
        winLine = null;
        turn = color;
    }

    /** 一局结束后，两名玩家都可以发起再来一盘。 */
    public synchronized void newGame() {
        touch();
        if (status == PLAYING) {
            throw new IllegalArgumentException("对局还在进行中，结束后才能重开");
        }
        resetBoard();
    }

    /** 生成给某个浏览器看的对局状态；myColor 和提示语会因视角不同而不同。 */
    public synchronized String toJson(String sessionId) {
        touch();
        int myColor = colorOf(sessionId);

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"mode\":\"pvp\"");
        sb.append(",\"phase\":\"").append(phase()).append('"');
        sb.append(",\"room\":\"").append(code).append('"');
        sb.append(",\"size\":").append(size);
        sb.append(",\"board\":[");
        for (int r = 0; r < size; r++) {
            if (r > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int c = 0; c < size; c++) {
                if (c > 0) {
                    sb.append(',');
                }
                sb.append(board.get(r, c));
            }
            sb.append(']');
        }
        sb.append(']');

        sb.append(",\"status\":\"").append(statusName()).append('"');
        sb.append(",\"turn\":").append(status == PLAYING ? turn : 0);
        sb.append(",\"myColor\":").append(myColor);
        sb.append(",\"blackPresent\":").append(blackId != null);
        sb.append(",\"whitePresent\":").append(whiteId != null);
        sb.append(",\"moves\":").append(history.size());

        sb.append(",\"lastMove\":");
        if (history.isEmpty()) {
            sb.append("null");
        } else {
            int[] last = history.get(history.size() - 1);
            sb.append("{\"r\":").append(last[0]).append(",\"c\":").append(last[1]).append('}');
        }

        sb.append(",\"message\":\"").append(Json.escape(messageFor(myColor))).append('"');
        sb.append(",\"winLine\":");
        if (winLine == null) {
            sb.append("null");
        } else {
            sb.append('[');
            for (int i = 0; i < winLine.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('[').append(winLine[i][0]).append(',').append(winLine[i][1]).append(']');
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    private void resetBoard() {
        board = new Board(size);
        history.clear();
        status = PLAYING;
        turn = Board.BLACK;
        winLine = null;
    }

    private void touch() {
        lastActive = System.currentTimeMillis();
    }

    private int other(int color) {
        return color == Board.BLACK ? Board.WHITE : Board.BLACK;
    }

    private String phase() {
        if (!isReady()) {
            return "waiting";
        }
        return status == PLAYING ? "playing" : "finished";
    }

    private String messageFor(int myColor) {
        if (!isReady()) {
            return "等待对手加入…";
        }
        if (status == BLACK_WIN) {
            return "黑棋获胜";
        }
        if (status == WHITE_WIN) {
            return "白棋获胜";
        }
        if (status == DRAW) {
            return "平局，棋盘下满了";
        }
        if (myColor == Board.EMPTY) {
            return "你正在观战";
        }
        return (turn == myColor) ? "轮到你落子" : "等待对手落子…";
    }

    private String statusName() {
        return switch (status) {
            case BLACK_WIN -> "black_win";
            case WHITE_WIN -> "white_win";
            case DRAW -> "draw";
            default -> "playing";
        };
    }
}
