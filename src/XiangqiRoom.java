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
        // 对方没有接受悔棋而是直接走棋，视为放弃这次申请。
        undoOffer = 0;

        if (match.status() == XiangqiMatch.FINISHED) {
            status = FINISHED;
            winner = match.winner();
            endReason = match.endReason();
            onMatchFinished();
        } else {
            onMoveDone(seat);
        }
    }

    /**
     * 联机悔棋改为「申请 - 同意」流程：这里只登记申请，真正撤棋在
     * {@link #answerUndo(String, String)} 中由对手接受后执行。
     */
    @Override
    public synchronized void undo(String sessionId) {
        checkTimeout(System.currentTimeMillis());
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
            throw new IllegalArgumentException("只能申请撤回自己刚走的最后一手");
        }
        if (undoOffer == seat) {
            throw new IllegalArgumentException("你已经申请悔棋了，等待对方回应");
        }
        if (undoOffer != 0) {
            throw new IllegalArgumentException("请先处理对方发起的悔棋申请");
        }
        undoOffer = seat;
    }

    /** 对手回应悔棋申请：accept 撤棋，decline 关闭申请。 */
    public synchronized void answerUndo(String sessionId, String action) {
        checkTimeout(System.currentTimeMillis());
        touch();
        int seat = seatOf(sessionId);
        if (seat == 0) {
            throw new IllegalArgumentException("你不在这个房间里");
        }
        if (status != PLAYING) {
            throw new IllegalArgumentException("对局已经结束");
        }
        if (undoOffer == 0) {
            throw new IllegalArgumentException("没有待处理的悔棋申请");
        }
        if (undoOffer == seat) {
            throw new IllegalArgumentException("申请是你发起的，等待对方回应");
        }
        if ("accept".equals(action)) {
            match.undoLast();
            undoOffer = 0;
            onUndo(match.turn());
        } else if ("decline".equals(action)) {
            undoOffer = 0;
        } else {
            throw new IllegalArgumentException("action 只能是 accept / decline");
        }
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
        StringBuilder sb = new StringBuilder(1800);
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
        appendHistory(sb);
        appendCaptured(sb);
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
                .append(",\"captured\":").append(mv.captured)
                .append(",\"check\":").append(mv.check)
                .append(",\"notation\":\"").append(Json.escape(mv.notation)).append('"')
                .append('}');
    }

    private void appendHistory(StringBuilder sb) {
        sb.append(",\"history\":[");
        java.util.List<String> notation = match.notationHistory();
        for (int i = 0; i < notation.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(Json.escape(notation.get(i))).append('"');
        }
        sb.append(']');
    }

    private void appendCaptured(StringBuilder sb) {
        appendPieces(sb, "capturedRed", match.capturedPieces(XiangqiBoard.RED));
        appendPieces(sb, "capturedBlack", match.capturedPieces(XiangqiBoard.BLACK));
        sb.append(",\"materialDiff\":").append(match.materialDiff());
    }

    private void appendPieces(StringBuilder sb, String name, java.util.List<Integer> pieces) {
        sb.append(",\"").append(name).append("\":[");
        for (int i = 0; i < pieces.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(pieces.get(i));
        }
        sb.append(']');
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
        if (undoOffer != 0) {
            return undoOffer == seat
                    ? "已发送悔棋申请，等待对方同意"
                    : "对方申请悔棋，请在右侧选择接受或拒绝";
        }
        if (match.turn() != seat) {
            return "等待对手走棋…";
        }
        return match.isInCheck() ? "轮到你走棋，你正在被将军！" : "轮到你走棋";
    }
}