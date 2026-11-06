import java.util.ArrayList;
import java.util.List;

/**
 * 一局五子棋的完整状态：棋盘、每一步的历史、胜负结果、难度等。
 *
 * <p>所有方法都加了 synchronized，因为 HTTP 服务是多线程处理请求的，
 * 加上同步可以保证同一时刻只有一个人在修改这一局棋。</p>
 */
public class Game {

    /** 对局状态：进行中 */
    public static final int PLAYING = 0;
    /** 对局状态：黑棋（玩家）胜 */
    public static final int BLACK_WIN = 1;
    /** 对局状态：白棋（电脑）胜 */
    public static final int WHITE_WIN = 2;
    /** 对局状态：平局 */
    public static final int DRAW = 3;

    /** 玩家执黑先行，电脑执白 */
    public static final int PLAYER_COLOR = Board.BLACK;
    public static final int AI_COLOR = Board.WHITE;

    private final GomokuAI ai = new GomokuAI();
    /** 落子历史，每个元素是 {行, 列, 颜色} */
    private final List<int[]> history = new ArrayList<>();

    private Board board;
    private int status = PLAYING;
    private int difficulty = 2;
    private int[][] winLine;
    private long aiMillis;
    private String message = "轮到你落子";

    public Game(int size) {
        this.board = new Board(size);
    }

    /** 开始新对局。 */
    public synchronized void newGame(int size) {
        board = new Board(size);
        history.clear();
        status = PLAYING;
        winLine = null;
        aiMillis = 0;
        message = "新对局开始，你执黑先行";
    }

    /** 切换电脑难度，取值范围 1~3。 */
    public synchronized void setDifficulty(int level) {
        difficulty = Math.max(1, Math.min(3, level));
        message = "难度已切换为「" + difficultyName() + "」";
    }

    /**
     * 玩家落子，随后电脑立刻应对。
     * 坐标非法或该位置已有棋子时抛出 IllegalArgumentException，由上层转成 400 响应。
     */
    public synchronized void playerMove(int r, int c) {
        if (status != PLAYING) {
            throw new IllegalArgumentException("对局已经结束了，请点击「新对局」重新开始");
        }
        if (!board.inBounds(r, c)) {
            throw new IllegalArgumentException("坐标超出棋盘范围");
        }
        if (!board.isEmpty(r, c)) {
            throw new IllegalArgumentException("该位置已经有棋子了");
        }

        place(r, c, PLAYER_COLOR);
        if (judge(r, c, PLAYER_COLOR)) {
            return;
        }

        // 轮到电脑思考
        long start = System.currentTimeMillis();
        int[] move = ai.chooseMove(board, AI_COLOR, difficulty);
        aiMillis = System.currentTimeMillis() - start;

        place(move[0], move[1], AI_COLOR);
        judge(move[0], move[1], AI_COLOR);
    }

    /** 悔棋：同时撤销电脑的应手和玩家自己的上一手。 */
    public synchronized void undo() {
        if (history.isEmpty()) {
            throw new IllegalArgumentException("还没有落子，无法悔棋");
        }
        // 先撤掉电脑的应手
        if (lastColor() == AI_COLOR) {
            removeLast();
        }
        // 再撤掉玩家自己的一手
        if (!history.isEmpty() && lastColor() == PLAYER_COLOR) {
            removeLast();
        }

        status = PLAYING;
        winLine = null;
        message = "已悔棋，轮到你落子";
    }

    /** 把当前对局状态拼成 JSON，交给前端渲染。 */
    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');

        sb.append("\"mode\":\"solo\"");
        sb.append(",\"size\":").append(board.size());

        sb.append(",\"board\":[");
        for (int r = 0; r < board.size(); r++) {
            if (r > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int c = 0; c < board.size(); c++) {
                if (c > 0) {
                    sb.append(',');
                }
                sb.append(board.get(r, c));
            }
            sb.append(']');
        }
        sb.append(']');

        sb.append(",\"status\":\"").append(statusName()).append('"');
        sb.append(",\"playerColor\":").append(PLAYER_COLOR);
        sb.append(",\"aiColor\":").append(AI_COLOR);
        // 因为电脑是同步应手的，只要对局没结束，永远都是"轮到玩家"
        sb.append(",\"turn\":").append(status == PLAYING ? PLAYER_COLOR : 0);

        sb.append(",\"lastMove\":");
        if (history.isEmpty()) {
            sb.append("null");
        } else {
            int[] last = history.get(history.size() - 1);
            sb.append("{\"r\":").append(last[0]).append(",\"c\":").append(last[1]).append('}');
        }

        sb.append(",\"moves\":").append(history.size());
        sb.append(",\"difficulty\":").append(difficulty);
        sb.append(",\"message\":\"").append(Json.escape(message)).append('"');
        sb.append(",\"aiMillis\":").append(aiMillis);

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

    /** 落子并记录历史。 */
    private void place(int r, int c, int color) {
        board.place(r, c, color);
        history.add(new int[]{r, c, color});
    }

    /** 判断刚下的这一手是否结束了对局；结束了就返回 true。 */
    private boolean judge(int r, int c, int color) {
        int[][] line = board.winLine(r, c);
        if (line != null) {
            winLine = line;
            status = (color == PLAYER_COLOR) ? BLACK_WIN : WHITE_WIN;
            message = (color == PLAYER_COLOR) ? "恭喜，你赢了！" : "电脑赢了，再来一局？";
            return true;
        }
        if (board.isFull()) {
            status = DRAW;
            message = "平局，棋盘下满了";
            return true;
        }
        message = "轮到你落子";
        return false;
    }

    private int lastColor() {
        return history.get(history.size() - 1)[2];
    }

    private void removeLast() {
        int[] last = history.remove(history.size() - 1);
        board.clear(last[0], last[1]);
    }

    private String statusName() {
        return switch (status) {
            case BLACK_WIN -> "black_win";
            case WHITE_WIN -> "white_win";
            case DRAW -> "draw";
            default -> "playing";
        };
    }

    private String difficultyName() {
        return switch (difficulty) {
            case 1 -> "简单";
            case 3 -> "困难";
            default -> "普通";
        };
    }
}
