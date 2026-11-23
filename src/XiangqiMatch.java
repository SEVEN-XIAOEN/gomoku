import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一盘象棋的规则状态机：盘面、轮次、着法历史、终局判定、重复局面裁决。
 *
 * <p>人机局和联机局共用这个类，保证两边规则完全一致。</p>
 */
public class XiangqiMatch {

    public static final int PLAYING = 0;
    public static final int FINISHED = 1;

    private final XiangqiBoard board = new XiangqiBoard();
    private final List<XiangqiMove> history = new ArrayList<>();
    private final List<String> positionKeys = new ArrayList<>();
    private final Map<String, Integer> positionCounts = new HashMap<>();

    private int turn = XiangqiBoard.RED;
    private int status = PLAYING;
    /** 0 = 和棋/未结束，1 = 红胜，2 = 黑胜 */
    private int winner;
    private String endReason = "";
    private String repetitionNote = "";
    private int noCapturePlies;

    public XiangqiMatch() {
        reset();
    }

    public final void reset() {
        board.reset();
        history.clear();
        positionKeys.clear();
        positionCounts.clear();
        turn = XiangqiBoard.RED;
        status = PLAYING;
        winner = 0;
        endReason = "";
        repetitionNote = "";
        noCapturePlies = 0;
        positionCounts.put(key(), 1);
    }

    public XiangqiBoard board() {
        return board;
    }

    public int turn() {
        return turn;
    }

    public int status() {
        return status;
    }

    public int winner() {
        return winner;
    }

    public String endReason() {
        return endReason;
    }

    public String repetitionNote() {
        return repetitionNote;
    }

    public int historySize() {
        return history.size();
    }

    public XiangqiMove lastMove() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    /** 当前完整中文棋谱。 */
    public List<String> notationHistory() {
        List<String> list = new ArrayList<>(history.size());
        for (XiangqiMove mv : history) {
            list.add(mv.notation);
        }
        return list;
    }

    /** 被吃掉的某一方棋子（按被吃先后排序）。 */
    public List<Integer> capturedPieces(int color) {
        List<Integer> list = new ArrayList<>();
        for (XiangqiMove mv : history) {
            if (mv.captured != 0 && XiangqiBoard.color(mv.captured) == color) {
                list.add(mv.captured);
            }
        }
        return list;
    }

    /** 子力差：正数表示红方占优，负数表示黑方占优。 */
    public int materialDiff() {
        int redLost = 0;
        int blackLost = 0;
        for (XiangqiMove mv : history) {
            if (mv.captured == 0) {
                continue;
            }
            if (XiangqiBoard.color(mv.captured) == XiangqiBoard.RED) {
                redLost += pieceValue(mv.captured);
            } else {
                blackLost += pieceValue(mv.captured);
            }
        }
        return blackLost - redLost;
    }

    private static int pieceValue(int piece) {
        return switch (XiangqiBoard.type(piece)) {
            case XiangqiBoard.KING -> 0;
            case XiangqiBoard.ROOK -> 9;
            case XiangqiBoard.CANNON -> 4;
            case XiangqiBoard.HORSE -> 4;
            case XiangqiBoard.ELEPHANT, XiangqiBoard.ADVISOR -> 2;
            case XiangqiBoard.PAWN -> 1;
            default -> 0;
        };
    }

    public XiangqiMove moveAt(int index) {
        return history.get(index);
    }

    public boolean isInCheck() {
        return XiangqiRules.inCheck(board, turn);
    }

    /**
     * 走一步棋。坐标非法、不是本方棋子、不是本方回合、走后自将都会抛错。
     */
    public synchronized void move(int fr, int fc, int tr, int tc) {
        if (status != PLAYING) {
            throw new IllegalArgumentException("对局已经结束，请开始新对局");
        }
        if (!board.inBounds(fr, fc) || !board.inBounds(tr, tc)) {
            throw new IllegalArgumentException("坐标超出棋盘范围");
        }
        int piece = board.get(fr, fc);
        if (piece == XiangqiBoard.EMPTY) {
            throw new IllegalArgumentException("起点没有棋子");
        }
        if (XiangqiBoard.color(piece) != turn) {
            throw new IllegalArgumentException(turn == XiangqiBoard.RED ? "还没轮到红方走棋" : "还没轮到黑方走棋");
        }
        if (!XiangqiRules.isLegalMove(board, fr, fc, tr, tc)) {
            throw new IllegalArgumentException("该走法不合法");
        }

        int mover = turn;
        String notation = XiangqiNotation.of(board, fr, fc, tr, tc);
        int captured = board.move(fr, fc, tr, tc);
        turn = XiangqiBoard.other(mover);

        boolean gaveCheck = XiangqiRules.inCheck(board, turn);
        boolean chase = !gaveCheck && XiangqiRules.isChase(board, mover, tr, tc);

        XiangqiMove mv = new XiangqiMove(fr, fc, tr, tc, mover, captured, gaveCheck, chase,
                notation + (gaveCheck ? "将" : ""));
        history.add(mv);
        noCapturePlies = captured == 0 ? noCapturePlies + 1 : 0;

        String k = key();
        positionKeys.add(k);
        int count = positionCounts.merge(k, 1, Integer::sum);

        // 立即将死/困毙优先于重复局面与自然限着：最后一手直接把对方将死，就是胜局。
        if (!XiangqiRules.hasLegalMove(board, turn)) {
            status = FINISHED;
            winner = mover;
            endReason = gaveCheck ? "checkmate" : "stalemate";
            return;
        }

        if (count >= 3) {
            adjudicateRepetition(k);
            return;
        }

        if (noCapturePlies >= 120) {
            finishDraw("natural_limit");
            return;
        }

        if (XiangqiRules.insufficientMaterial(board)) {
            finishDraw("insufficient");
        }
    }

    /** 由认输、超时、双方同意和棋等外部原因结束对局。 */
    public synchronized void finishBy(int winnerColor, String reason) {
        if (status != PLAYING) {
            return;
        }
        status = FINISHED;
        winner = winnerColor;
        endReason = reason == null ? "" : reason;
        repetitionNote = "";
    }

    /** 撤销最后一步。返回 false 表示没有可撤的棋。 */
    public synchronized boolean undoLast() {
        if (history.isEmpty()) {
            return false;
        }
        XiangqiMove last = history.remove(history.size() - 1);
        board.unmove(last.fr, last.fc, last.tr, last.tc, last.captured);
        turn = last.color;
        status = PLAYING;
        winner = 0;
        endReason = "";
        repetitionNote = "";

        if (!positionKeys.isEmpty()) {
            String removed = positionKeys.remove(positionKeys.size() - 1);
            positionCounts.computeIfPresent(removed, (k, v) -> v > 1 ? v - 1 : null);
        }
        recomputeNoCapture();
        return true;
    }

    private void recomputeNoCapture() {
        int count = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).captured != 0) {
                break;
            }
            count++;
        }
        noCapturePlies = count;
    }

    private void finishDraw(String reason) {
        status = FINISHED;
        winner = 0;
        endReason = reason;
    }

    private String key() {
        StringBuilder sb = new StringBuilder(91);
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                sb.append((char) ('a' + board.get(r, c)));
            }
        }
        sb.append((char) ('0' + turn));
        return sb.toString();
    }

    /**
     * 三次重复局面裁决。
     *
     * <p>把最近一个重复循环里的着法按"将 / 捉 / 普通"分类：
     * 单方长将或长捉判负；双方都长将或都长捉判和；其余不变作和。</p>
     */
    private void adjudicateRepetition(String currentKey) {
        int previous = -1;
        for (int i = positionKeys.size() - 2; i >= 0; i--) {
            if (positionKeys.get(i).equals(currentKey)) {
                previous = i;
                break;
            }
        }
        if (previous < 0) {
            finishDraw("repetition");
            repetitionNote = "三次重复局面，判和";
            return;
        }

        boolean redMoved = false;
        boolean blackMoved = false;
        boolean redAllCheck = true;
        boolean blackAllCheck = true;
        boolean redAllChase = true;
        boolean blackAllChase = true;

        for (int i = previous + 1; i < history.size(); i++) {
            XiangqiMove mv = history.get(i);
            if (mv.color == XiangqiBoard.RED) {
                redMoved = true;
                redAllCheck &= mv.check;
                redAllChase &= mv.chase;
            } else {
                blackMoved = true;
                blackAllCheck &= mv.check;
                blackAllChase &= mv.chase;
            }
        }

        boolean redCheck = redMoved && redAllCheck;
        boolean blackCheck = blackMoved && blackAllCheck;
        boolean redChase = redMoved && !redCheck && redAllChase;
        boolean blackChase = blackMoved && !blackCheck && blackAllChase;

        status = FINISHED;
        endReason = "repetition";
        if (redCheck && blackCheck) {
            winner = 0;
            repetitionNote = "双方长将，判和";
        } else if (redCheck) {
            winner = XiangqiBoard.BLACK;
            repetitionNote = "红方长将判负";
        } else if (blackCheck) {
            winner = XiangqiBoard.RED;
            repetitionNote = "黑方长将判负";
        } else if (redChase && blackChase) {
            winner = 0;
            repetitionNote = "双方长捉，判和";
        } else if (redChase) {
            winner = XiangqiBoard.BLACK;
            repetitionNote = "红方长捉判负";
        } else if (blackChase) {
            winner = XiangqiBoard.RED;
            repetitionNote = "黑方长捉判负";
        } else {
            winner = 0;
            repetitionNote = "三次重复局面，判和";
        }
    }

    public String statusName() {
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

    public static String reasonText(String reason) {
        return switch (reason) {
            case "checkmate" -> "将死";
            case "stalemate" -> "困毙";
            case "natural_limit" -> "60 回合自然限着";
            case "repetition" -> "重复局面裁决";
            case "insufficient" -> "双方均无取胜可能";
            case "timeout" -> "超时";
            case "resign" -> "认输";
            case "draw_agreed" -> "双方同意和棋";
            case "opponent_left" -> "对手离开";
            default -> "";
        };
    }
}