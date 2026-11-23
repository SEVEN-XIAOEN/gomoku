import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 象棋电脑对手：Alpha-Beta 极小化极大搜索。
 *
 * <p>难度 1 = 随机为主；2 = 搜 2 层；3 = 搜 3 层并带时间上限。
 * 评估函数 = 子力价值 + 简单位置分。</p>
 */
public class XiangqiAI {

    private static final int INF = 1_000_000_000;
    private static final int MATE = 500_000;

    /** 兵过河后的位置奖励 */
    private static final int[][] PAWN_BONUS = new int[10][9];

    static {
        for (int c = 0; c < 9; c++) {
            PAWN_BONUS[4][c] = 30;
            PAWN_BONUS[3][c] = 50;
            PAWN_BONUS[2][c] = 70;
            PAWN_BONUS[1][c] = 80;
            PAWN_BONUS[0][c] = 90;
        }
    }

    private final Random random = new Random();

    public int[] chooseMove(XiangqiBoard board, int color, int level) {
        List<int[]> moves = XiangqiRules.allLegalMoves(board, color);
        if (moves.isEmpty()) {
            return null;
        }
        if (level <= 1 && random.nextDouble() < 0.55) {
            return moves.get(random.nextInt(moves.size()));
        }

        int depth = level >= 3 ? 3 : 2;
        long budget = level >= 3 ? 1500L : 700L;
        long deadline = System.currentTimeMillis() + budget;

        orderMoves(board, moves);
        int[] best = moves.get(0);
        int bestScore = Integer.MIN_VALUE;
        int alpha = -INF;

        for (int[] m : moves) {
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            int captured = board.move(m[0], m[1], m[2], m[3]);
            int score = -negamax(board, XiangqiBoard.other(color), depth - 1, -INF, -alpha, deadline, 1);
            board.unmove(m[0], m[1], m[2], m[3], captured);

            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
            if (score > alpha) {
                alpha = score;
            }
        }
        return best;
    }

    private int negamax(XiangqiBoard board, int color, int depth, int alpha, int beta, long deadline, int ply) {
        if (depth <= 0 || System.currentTimeMillis() > deadline) {
            return evaluate(board, color);
        }
        List<int[]> moves = XiangqiRules.allLegalMoves(board, color);
        if (moves.isEmpty()) {
            return -MATE + ply;
        }
        orderMoves(board, moves);
        int best = -INF;
        for (int[] m : moves) {
            int captured = board.move(m[0], m[1], m[2], m[3]);
            int score = -negamax(board, XiangqiBoard.other(color), depth - 1, -beta, -alpha, deadline, ply + 1);
            board.unmove(m[0], m[1], m[2], m[3], captured);
            if (score > best) {
                best = score;
            }
            if (best > alpha) {
                alpha = best;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    private void orderMoves(XiangqiBoard board, List<int[]> moves) {
        moves.sort((a, b) -> Integer.compare(orderScore(board, b), orderScore(board, a)));
    }

    private int orderScore(XiangqiBoard board, int[] m) {
        int target = board.get(m[2], m[3]);
        if (target == XiangqiBoard.EMPTY) {
            return 0;
        }
        int attacker = board.get(m[0], m[1]);
        return 1000 + XiangqiRules.value(target) - XiangqiRules.value(attacker);
    }

    /** 从 color 视角评估局面。 */
    private int evaluate(XiangqiBoard board, int color) {
        int score = 0;
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                int p = board.get(r, c);
                if (p == XiangqiBoard.EMPTY) {
                    continue;
                }
                int v = XiangqiRules.value(p);
                int t = XiangqiBoard.type(p);
                if (t == XiangqiBoard.PAWN) {
                    if (XiangqiBoard.color(p) == XiangqiBoard.RED) {
                        v += PAWN_BONUS[r][c];
                    } else {
                        v += PAWN_BONUS[9 - r][c];
                    }
                }
                if (t == XiangqiBoard.HORSE || t == XiangqiBoard.CANNON) {
                    v += centerBonus(r, c);
                }
                score += XiangqiBoard.color(p) == color ? v : -v;
            }
        }
        return score;
    }

    private int centerBonus(int r, int c) {
        int dr = Math.abs(r - 4);
        int dc = Math.abs(c - 4);
        return Math.max(0, 20 - dr * 3 - dc * 3);
    }
}