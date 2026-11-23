import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 中国象棋人机对局。
 *
 * <p>玩家可以选红方或黑方；玩家执黑时电脑执红先行。规则判定全部复用
 * {@link XiangqiMatch}，和联机模式完全一致。计时使用服务端权威时钟。</p>
 */
public class XiangqiSolo {

    private final XiangqiAI ai = new XiangqiAI();
    private XiangqiMatch match = new XiangqiMatch();
    private GameClock clock;

    private int playerColor = XiangqiBoard.RED;
    private int difficulty = 2;
    private long aiMillis;
    private String message = "选择执红或执黑，然后开始新对局";
    private boolean started;
    private long baseMs;
    private long incrementMs;
    private String endReason = "";

    /** side: 1 = 红方，2 = 黑方，0 = 随机。baseMs = 0 表示不限时。 */
    public synchronized void newGame(int side, int level, long baseMs, long incrementMs) {
        int chosen = side;
        if (chosen != XiangqiBoard.RED && chosen != XiangqiBoard.BLACK) {
            chosen = ThreadLocalRandom.current().nextBoolean() ? XiangqiBoard.RED : XiangqiBoard.BLACK;
        }
        this.playerColor = chosen;
        this.difficulty = Math.max(1, Math.min(3, level));
        this.baseMs = Math.max(0, baseMs);
        this.incrementMs = Math.max(0, incrementMs);
        this.match = new XiangqiMatch();
        this.clock = this.baseMs > 0 ? new GameClock(this.baseMs, this.incrementMs) : null;
        this.aiMillis = 0;
        this.endReason = "";
        this.started = true;
        if (clock != null) {
            clock.start(XiangqiBoard.RED, System.currentTimeMillis());
        }
        if (playerColor == XiangqiBoard.BLACK) {
            message = "你执黑，电脑执红先行";
            aiMove();
        } else {
            message = "你执红先行";
        }
    }

    public synchronized void setDifficulty(int level) {
        difficulty = Math.max(1, Math.min(3, level));
        message = "电脑难度已切换为「" + difficultyName() + "」";
    }

    public synchronized void move(int fr, int fc, int tr, int tc) {
        requirePlaying();
        tick(System.currentTimeMillis());
        requirePlaying();
        if (match.turn() != playerColor) {
            throw new IllegalArgumentException("还没轮到你走棋");
        }

        match.move(fr, fc, tr, tc);
        if (match.status() == XiangqiMatch.FINISHED) {
            finishInternal();
            return;
        }
        if (clock != null) {
            clock.onMove(playerColor, System.currentTimeMillis());
        }
        message = "电脑思考中…";
        aiMove();
    }

    /** 悔棋：撤回到玩家上一次走棋之前；玩家执黑时如果撤光了，电脑会重新开局。 */
    public synchronized void undo() {
        if (!started) {
            throw new IllegalArgumentException("还没有开始对局");
        }
        if (match.historySize() == 0) {
            throw new IllegalArgumentException("还没有走棋，无法悔棋");
        }
        if (clock != null) {
            clock.pause(System.currentTimeMillis());
        }
        while (match.historySize() > 0) {
            XiangqiMove last = match.lastMove();
            match.undoLast();
            if (last.color == playerColor) {
                break;
            }
        }
        endReason = "";
        if (clock != null) {
            clock.resume(match.turn(), System.currentTimeMillis());
        }
        message = "已悔棋，轮到你走棋";
        if (match.turn() != playerColor) {
            // 玩家执黑且只撤掉了电脑的开局着法：让电脑重新走第一步。
            aiMove();
        }
    }

    public synchronized void resign() {
        requirePlaying();
        match.finishBy(XiangqiBoard.other(playerColor), "resign");
        finishInternal();
    }

    public synchronized void tick(long now) {
        if (!started || match.status() != XiangqiMatch.PLAYING || clock == null) {
            return;
        }
        int timeoutSeat = clock.timeoutSeat(now);
        if (timeoutSeat != 0) {
            match.finishBy(XiangqiBoard.other(timeoutSeat), "timeout");
            clock.pause(now);
            finishInternal();
        }
    }

    public synchronized String legalJson(int r, int c) {
        requirePlaying();
        if (match.turn() != playerColor) {
            throw new IllegalArgumentException("还没轮到你走棋");
        }
        if (!match.board().inBounds(r, c)) {
            throw new IllegalArgumentException("坐标超出棋盘范围");
        }
        int piece = match.board().get(r, c);
        if (piece == XiangqiBoard.EMPTY || XiangqiBoard.color(piece) != playerColor) {
            throw new IllegalArgumentException("请选择你自己的棋子");
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"moves\":[");
        List<int[]> moves = XiangqiRules.legalMoves(match.board(), r, c);
        for (int i = 0; i < moves.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[').append(moves.get(i)[0]).append(',').append(moves.get(i)[1]).append(']');
        }
        sb.append("]}");
        return sb.toString();
    }

    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder(1600);
        sb.append('{');
        sb.append("\"game\":\"xiangqi\"");
        sb.append(",\"mode\":\"solo\"");
        sb.append(",\"phase\":\"").append(phase()).append('"');
        sb.append(",\"room\":null");
        sb.append(",\"rows\":").append(XiangqiBoard.ROWS);
        sb.append(",\"cols\":").append(XiangqiBoard.COLS);
        sb.append(",\"board\":[");
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            if (r > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                if (c > 0) {
                    sb.append(',');
                }
                sb.append(match.board().get(r, c));
            }
            sb.append(']');
        }
        sb.append(']');
        sb.append(",\"turn\":").append(match.turn());
        sb.append(",\"myColor\":").append(started ? playerColor : 0);
        sb.append(",\"playerColor\":").append(started ? playerColor : 0);
        sb.append(",\"status\":\"").append(match.statusName()).append('"');
        sb.append(",\"endReason\":\"").append(Json.escape(endReason)).append('"');
        sb.append(",\"check\":").append(match.status() == XiangqiMatch.PLAYING && match.isInCheck());
        sb.append(",\"lastMove\":");
        appendMove(sb, match.lastMove());
        sb.append(",\"moves\":").append(match.historySize());
        sb.append(",\"difficulty\":").append(difficulty);
        sb.append(",\"aiMillis\":").append(aiMillis);
        sb.append(",\"message\":\"").append(Json.escape(message)).append('"');
        sb.append(",\"clock\":").append(clock == null ? "null" : clock.toJson(System.currentTimeMillis()));
        sb.append(",\"drawOffer\":0");
        sb.append(",\"rematchVotes\":[false,false]");
        sb.append(",\"winLine\":null");
        sb.append('}');
        return sb.toString();
    }

    private void appendMove(StringBuilder sb, XiangqiMove mv) {
        if (mv == null) {
            sb.append("null");
            return;
        }
        sb.append("{\"fr\":").append(mv.fr)
                .append(",\"fc\":").append(mv.fc)
                .append(",\"tr\":").append(mv.tr)
                .append(",\"tc\":").append(mv.tc)
                .append(",\"color\":").append(mv.color)
                .append(",\"check\":").append(mv.check).append('}');
    }

    private void aiMove() {
        if (match.status() != XiangqiMatch.PLAYING) {
            finishInternal();
            return;
        }
        long start = System.currentTimeMillis();
        int[] m = ai.chooseMove(match.board(), match.turn(), difficulty);
        aiMillis = System.currentTimeMillis() - start;
        if (m == null) {
            match.finishBy(playerColor, "checkmate");
            finishInternal();
            return;
        }
        int aiColor = match.turn();
        match.move(m[0], m[1], m[2], m[3]);
        if (match.status() == XiangqiMatch.FINISHED) {
            finishInternal();
            return;
        }
        if (clock != null) {
            clock.onMove(aiColor, System.currentTimeMillis());
        }
        message = match.isInCheck() ? "轮到你走棋，你正在被将军！" : "轮到你走棋";
    }

    private void finishInternal() {
        endReason = match.endReason();
        if (clock != null) {
            clock.pause(System.currentTimeMillis());
        }
        String reason = XiangqiMatch.reasonText(endReason);
        if (match.winner() == 0) {
            message = reason.isEmpty() ? "本局和棋" : reason + "，本局和棋";
        } else if (match.winner() == playerColor) {
            message = reason + "，你赢了！";
        } else {
            message = reason + "，你输了。";
        }
    }

    private void requirePlaying() {
        if (!started) {
            throw new IllegalArgumentException("还没有开始对局，请先点「新对局」");
        }
        if (match.status() != XiangqiMatch.PLAYING) {
            throw new IllegalArgumentException("对局已经结束，请点「新对局」再来一盘");
        }
    }

    private String phase() {
        if (!started) {
            return "ready";
        }
        return match.status() == XiangqiMatch.PLAYING ? "playing" : "finished";
    }

    private String difficultyName() {
        return switch (difficulty) {
            case 1 -> "简单";
            case 3 -> "困难";
            default -> "普通";
        };
    }
}