import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 电脑对手。
 *
 * <p>核心思路是"给每个空位打分，挑最高分的下"：</p>
 * <ol>
 *   <li>先看自己能不能一步赢，能赢就直接下；</li>
 *   <li>再看对手是不是下一步就要赢，是就赶紧堵；</li>
 *   <li>否则按"我下这里的价值 + 对手下这里的价值"排序，综合进攻和防守。</li>
 * </ol>
 *
 * <p>难度从 1 到 3：简单模式会乱下，普通模式用上面这套打分，困难模式再多推演一层。</p>
 */
public class GomokuAI {

    private static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    /** 五格窗口里全是自己的棋子，也就是直接获胜 */
    private static final int SCORE_WIN = 10_000_000;
    /** 五格窗口里 4 个自己的棋子 + 1 个空位 */
    private static final int SCORE_FOUR = 10_000;
    private static final int SCORE_THREE = 1_000;
    private static final int SCORE_TWO = 100;
    private static final int SCORE_ONE = 10;

    private final Random random = new Random();

    /**
     * 选出电脑的下一手。
     *
     * @return 长度 2 的数组 {行, 列}
     */
    public int[] chooseMove(Board board, int aiColor, int level) {
        int playerColor = (aiColor == Board.BLACK) ? Board.WHITE : Board.BLACK;

        List<int[]> candidates = candidates(board);
        if (candidates.isEmpty()) {
            // 空棋盘：下正中间
            int center = board.size() / 2;
            return new int[]{center, center};
        }

        // 第一步：自己能赢，立刻赢
        for (int[] p : candidates) {
            if (gain(board, p[0], p[1], aiColor) >= SCORE_WIN) {
                return p;
            }
        }

        // 第二步：对手下一步能赢，必须堵。
        // 简单模式有几率看漏，这样玩家才有赢的机会。
        boolean willBlock = (level >= 2) || random.nextDouble() < 0.6;
        if (willBlock) {
            for (int[] p : candidates) {
                if (gain(board, p[0], p[1], playerColor) >= SCORE_WIN) {
                    return p;
                }
            }
        }

        // 简单模式：随便挑一个相邻的空位
        if (level == 1) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        // 第三步：综合评分。
        //   attack  —— 我下这里的收益
        //   defend  —— 对手下这里的收益（我占了这里，对手就占不到了）
        //   compact —— 贴棋奖励：离已有棋子越近越优先，避免跑到远处下孤棋
        List<int[]> scored = new ArrayList<>();
        for (int[] p : candidates) {
            int attack = gain(board, p[0], p[1], aiColor);
            int defend = gain(board, p[0], p[1], playerColor);
            int compact = countOccupied(board, p[0], p[1], 1) * 30
                        + countOccupied(board, p[0], p[1], 2) * 5;
            scored.add(new int[]{p[0], p[1], attack + (int) (defend * 0.9) + compact});
        }
        scored.sort((a, b) -> b[2] - a[2]);

        if (level == 2) {
            return new int[]{scored.get(0)[0], scored.get(0)[1]};
        }

        // 困难模式：在评分最高的前几名里，再推演一层"下了这步之后对手最强能做什么"
        int limit = Math.min(10, scored.size());
        int[] bestMove = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < limit; i++) {
            int[] cand = scored.get(i);
            board.place(cand[0], cand[1], aiColor);

            int opponentBest = 0;
            for (int[] q : candidates(board)) {
                int value = gain(board, q[0], q[1], playerColor);
                if (value > opponentBest) {
                    opponentBest = value;
                }
            }

            board.clear(cand[0], cand[1]);

            int finalScore = cand[2] - (int) (opponentBest * 0.8);
            if (finalScore > bestScore) {
                bestScore = finalScore;
                bestMove = new int[]{cand[0], cand[1]};
            }
        }
        return (bestMove != null) ? bestMove : new int[]{scored.get(0)[0], scored.get(0)[1]};
    }

    /**
     * 收集候选落点：只考虑离已有棋子两格以内的空位。
     * 这样既贴近下棋常识，又能把搜索范围从 225 个点压到几十个点。
     */
    private List<int[]> candidates(Board board) {
        List<int[]> list = new ArrayList<>();
        int n = board.size();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (board.isEmpty(r, c) && hasNeighbor(board, r, c, 2)) {
                    list.add(new int[]{r, c});
                }
            }
        }
        return list;
    }

    private boolean hasNeighbor(Board board, int r, int c, int distance) {
        for (int dr = -distance; dr <= distance; dr++) {
            for (int dc = -distance; dc <= distance; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int nr = r + dr;
                int nc = c + dc;
                if (board.inBounds(nr, nc) && board.get(nr, nc) != Board.EMPTY) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 统计 (r, c) 周围 distance 格以内有多少个已有棋子，用来衡量这手棋贴不贴棋。 */
    private int countOccupied(Board board, int r, int c, int distance) {
        int count = 0;
        for (int dr = -distance; dr <= distance; dr++) {
            for (int dc = -distance; dc <= distance; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int nr = r + dr;
                int nc = c + dc;
                if (board.inBounds(nr, nc) && board.get(nr, nc) != Board.EMPTY) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 假设某颜色在 (r, c) 落一子，这一手值多少分。
     * 内部是"落子 → 计算 → 撤销"，所以调用前后棋盘完全不变。
     */
    private int gain(Board board, int r, int c, int color) {
        if (!board.isEmpty(r, c)) {
            return 0;
        }
        board.place(r, c, color);
        int score = 0;
        for (int[] dir : DIRS) {
            score += lineScore(board, r, c, color, dir);
        }
        board.clear(r, c);
        return score;
    }

    /**
     * 沿某个方向，把所有"包含 (r, c) 的长度为 5 的窗口"都检查一遍。
     * 窗口里只要混进对方棋子就不算威胁；否则自己棋子越多、剩余空位越多，分数越高。
     *
     * <p>窗口越过棋盘边界时按"被挡住"处理，所以边上的棋力自然比中间弱，符合五子棋常识。</p>
     */
    private int lineScore(Board board, int r, int c, int color, int[] dir) {
        int score = 0;
        // start 取 -4 到 0，正好覆盖所有包含 (r, c) 的窗口
        for (int start = -4; start <= 0; start++) {
            int own = 0;
            int blocked = 0;

            for (int k = 0; k < 5; k++) {
                int nr = r + dir[0] * (start + k);
                int nc = c + dir[1] * (start + k);
                if (!board.inBounds(nr, nc)) {
                    blocked++;
                    continue;
                }
                int value = board.get(nr, nc);
                if (value == color) {
                    own++;
                } else if (value != Board.EMPTY) {
                    blocked++;
                }
            }

            if (blocked > 0) {
                continue;
            }
            if (own == 5) {
                score += SCORE_WIN;
            } else if (own == 4) {
                score += SCORE_FOUR;
            } else if (own == 3) {
                score += SCORE_THREE;
            } else if (own == 2) {
                score += SCORE_TWO;
            } else if (own == 1) {
                score += SCORE_ONE;
            }
        }
        return score;
    }
}
