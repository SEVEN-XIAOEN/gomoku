/**
 * 中国象棋联机房间。座位 1 = 红方（先手），座位 2 = 黑方。
 */
public class XiangqiRoom extends Room {

    private XiangqiMatch match = new XiangqiMatch();

    public XiangqiRoom(String code, long baseMs, long incrementMs) {
        super(code, "xiangqi", baseMs, incrementMs);
        resetMatch();
    }

    @Override
    protected synchronized void resetMatch() {
        match = new XiangqiMatch();
    }

    @Override
    public synchronized void move(String sessionId, int fr, int fc, int tr, int tc) {
        checkTimeout(System.currentTimeMillis());
        touch();
        if (!ready()) {
            throw new IllegalArgumentException("等待对手加入后再走棋");
        }
        if (status != PLAYING) {
            throw new IllegalArgumentException("对局已经结束，请双方都点「再来一盘」");
        }
        int seat = seatOf(sessionId);
        if (seat == 0) {
            throw new IllegalArgumentException("你不在这个房间里");
        }
        if (match.turn() != seat) {
            throw new IllegalArgumentException(seat == 1 ? "还没轮到红方走棋" : "还没轮到黑方走棋");
        }

        match.move(fr, fc, tr, tc);

        if (match.status() == XiangqiMatch.FINISHED) {
            status = FINISHED;
            winner = match.winner();
            endReason = match.endReason();
            onMatchFinished();
        } else {
            onMoveDone(seat);
        }
    }

    /** 联机悔棋只能撤回自己刚走的一手。 */
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
        if (match.historySize() == 0) {
            throw new IllegalArgumentException("还没有走棋，无法悔棋");
        }
        XiangqiMove last = match.lastMove();
        if (last.color != seat) {
            throw new IllegalArgumentException("只能撤回自己刚走的最后一手");
        }
        match.undoLast();
        onUndo(match.turn());
    }

    /** 返回某枚棋子的合法落点，供前端高亮；规则仍由服务端生成。 */
    public synchronized String legalJson(String sessionId, int r, int c) {
        checkTimeout(System.currentTimeMillis());
        int seat = seatOf(sessionId);
        if (seat == 0) {
            throw new IllegalArgumentException("你不在这个房间里");
        }
        if (status != PLAYING) {
            throw new IllegalArgumentException("对局已经结束");
        }
        if (!ready()) {
            throw new IllegalArgumentException("等待对手加入后再开始");
        }
        if (match.turn() != seat) {
            throw new IllegalArgumentException("还没轮到你走棋");
        }
        if (!match.board().inBounds(r, c)) {
            throw new IllegalArgumentException("坐标超出棋盘范围");
        }
        int piece = match.board().get(r, c);
        if (piece == XiangqiBoard.EMPTY || XiangqiBoard.color(piece) != seat) {
            throw new IllegalArgumentException("请选择你自己的棋子");
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"moves\":[");
        java.util.List<int[]> moves = XiangqiRules.legalMoves(match.board(), r, c);
        for (int i = 0; i < moves.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[').append(moves.get(i)[0]).append(',').append(moves.get(i)[1]).append(']');
        }
        sb.append("]}");
        return sb.toString();
    }
    @Override
    public synchronized String stateJson(String sessionId) {
        int seat = seatOf(sessionId);
        StringBuilder sb = new StringBuilder(1200);
        sb.append('{');
        appendCommon(sb, sessionId);

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
        sb.append(",\"status\":\"").append(statusName()).append('"');
        sb.append(",\"endReason\":\"").append(Json.escape(endReason)).append('"');
        sb.append(",\"check\":").append(status == PLAYING && match.isInCheck());
        sb.append(",\"lastMove\":");
        appendMove(sb, match.lastMove());
        sb.append(",\"moves\":").append(match.historySize());
        sb.append(",\"winLine\":null");
        sb.append(",\"message\":\"").append(Json.escape(message(seat))).append('"');
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

    private String statusName() {
        if (status == PLAYING) {
            return "playing";
        }
        if (winner == XiangqiBoard.RED) {
            return "red_win";
        }
        if (winner == XiangqiBoard.BLACK) {
            return "black_win";
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
            String reason = XiangqiMatch.reasonText(endReason);
            if (winner == 0) {
                return (reason.isEmpty() ? "本局和棋" : reason + "，本局和棋")
                        + "；双方都点「再来一盘」可以继续";
            }
            String side = winner == XiangqiBoard.RED ? "红方" : "黑方";
            String result = winner == seat ? "你赢了！" : "你输了。";
            return reason + "，" + side + "胜。" + result + "双方都点「再来一盘」可以继续";
        }
        if (seat == 0) {
            return "你正在观战";
        }
        if (match.turn() != seat) {
            return "等待对手走棋…";
        }
        return match.isInCheck() ? "轮到你走棋，你正在被将军！" : "轮到你走棋";
    }
}