import java.util.ArrayList;
import java.util.List;

/**
 * 五子棋联机房间。座位 1 = 黑棋，座位 2 = 白棋。
 */
public class GomokuRoom extends Room {

    private final int size;
    private final List<int[]> history = new ArrayList<>();

    private Board board;
    private int turn = 1;
    private int[][] winLine;

    public GomokuRoom(String code, int size, long baseMs, long incrementMs) {
        super(code, "gomoku", baseMs, incrementMs);
        this.size = size;
        resetMatch();
    }

    public synchronized int size() {
        return size;
    }

    @Override
    protected synchronized void resetMatch() {
        board = new Board(size);
        history.clear();
        turn = 1;
        winLine = null;
    }

    @Override
    public synchronized void move(String sessionId, int fr, int fc, int tr, int tc) {
        checkTimeout(System.currentTimeMillis());
        touch();
        if (!ready()) {
            throw new IllegalArgumentException("等待对手加入后再落子");
        }
        if (status != PLAYING) {
            throw new IllegalArgumentException("对局已经结束，请双方都点「再来一盘」");
        }
        int seat = seatOf(sessionId);
        if (seat == 0) {
            throw new IllegalArgumentException("你不在这个房间里");
        }
        if (turn != seat) {
            throw new IllegalArgumentException("还没轮到你落子");
        }
        int r = fr;
        int c = fc;
        if (!board.inBounds(r, c)) {
            throw new IllegalArgumentException("坐标超出棋盘范围");
        }
        if (!board.isEmpty(r, c)) {
            throw new IllegalArgumentException("该位置已经有棋子了");
        }

        board.place(r, c, seat);
        history.add(new int[]{r, c, seat});

        int[][] line = board.winLine(r, c);
        if (line != null) {
            winLine = line;
            status = FINISHED;
            winner = seat;
            endReason = "five";
            onMatchFinished();
            return;
        }
        if (board.isFull()) {
            status = FINISHED;
            winner = 0;
            endReason = "board_full";
            onMatchFinished();
            return;
        }
        turn = other(seat);
        onMoveDone(seat);
    }

    /** 联机悔棋只能撤回自己刚下的最后一手。 */
    @Override
    public synchronized void undo(String sessionId) {
        touch();
        int seat = seatOf(sessionId);
        if (seat == 0) {
            throw new IllegalArgumentException("你不在这个房间里");
        }
        if (status != PLAYING) {
            throw new IllegalArgumentException("对局已经结束，不能悔棋");
        }
        if (!ready()) {
            throw new IllegalArgumentException("等待对手加入后再开始");
        }
        if (history.isEmpty()) {
            throw new IllegalArgumentException("还没有落子，无法悔棋");
        }
        int[] last = history.get(history.size() - 1);
        if (last[2] != seat) {
            throw new IllegalArgumentException("只能撤回自己刚下的最后一手");
        }
        history.remove(history.size() - 1);
        board.clear(last[0], last[1]);
        winLine = null;
        turn = seat;
        onUndo(seat);
    }

    @Override
    public synchronized String stateJson(String sessionId) {
        int seat = seatOf(sessionId);
        StringBuilder sb = new StringBuilder(1024);
        sb.append('{');
        appendCommon(sb, sessionId);

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
        sb.append(",\"turn\":").append(turn);
        sb.append(",\"lastMove\":");
        if (history.isEmpty()) {
            sb.append("null");
        } else {
            int[] last = history.get(history.size() - 1);
            sb.append("{\"r\":").append(last[0]).append(",\"c\":").append(last[1]).append('}');
        }
        sb.append(",\"moves\":").append(history.size());
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
        sb.append(",\"message\":\"").append(Json.escape(message(seat))).append('"');
        sb.append('}');
        return sb.toString();
    }

    private String statusName() {
        if (status == PLAYING) {
            return "playing";
        }
        if (winner == 1) {
            return "black_win";
        }
        if (winner == 2) {
            return "white_win";
        }
        return "draw";
    }

    private String message(int seat) {
        if (!ready()) {
            return "等待对手加入，把房间码发给朋友";
        }
        if (status == FINISHED) {
            if ("opponent_left".equals(endReason)) {
                return "对手已离开房间，等待新对手加入";
            }
            if (winner == 0) {
                return "本局和棋，双方都点「再来一盘」可以继续";
            }
            return (winner == seat ? "你赢了！" : "你输了。")
                    + "双方都点「再来一盘」可以继续";
        }
        if (seat == 0) {
            return "你正在观战";
        }
        return turn == seat ? "轮到你落子" : "等待对手落子…";
    }
}