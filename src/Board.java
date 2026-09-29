import java.util.ArrayList;
import java.util.List;

/**
 * 棋盘模型。
 *
 * <p>这个类只负责"棋盘本身"的事情：保存棋子、落子、悔棋、判断五连。
 * 它不知道网络和界面的存在，这样职责单一，也方便单独测试。</p>
 */
public class Board {

    /** 空格 */
    public static final int EMPTY = 0;
    /** 黑棋 */
    public static final int BLACK = 1;
    /** 白棋 */
    public static final int WHITE = 2;

    /** 四个扫描方向：横向、纵向、左上到右下、右上到左下 */
    private static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    private final int size;
    private final int[][] grid;
    private int moves;

    public Board(int size) {
        this.size = size;
        this.grid = new int[size][size];
    }

    public int size() {
        return size;
    }

    public int moves() {
        return moves;
    }

    public int get(int r, int c) {
        return grid[r][c];
    }

    public boolean inBounds(int r, int c) {
        return r >= 0 && r < size && c >= 0 && c < size;
    }

    public boolean isEmpty(int r, int c) {
        return inBounds(r, c) && grid[r][c] == EMPTY;
    }

    /** 落子。成功返回 true；位置越界或已有棋子返回 false。 */
    public boolean place(int r, int c, int color) {
        if (!isEmpty(r, c)) {
            return false;
        }
        grid[r][c] = color;
        moves++;
        return true;
    }

    /**
     * 清空某个位置。
     * 除了悔棋会用到，AI 在推演"如果我下这里会怎样"时也用它来撤销假设的一步。
     */
    public void clear(int r, int c) {
        if (!inBounds(r, c)) {
            return;
        }
        if (grid[r][c] != EMPTY) {
            grid[r][c] = EMPTY;
            moves--;
        }
    }

    public boolean isFull() {
        return moves >= size * size;
    }

    /**
     * 判断"刚刚落在 (r, c) 的这一手"是否形成五连。
     *
     * @return 连成一线的所有坐标（前端用来高亮获胜棋子）；没有五连则返回 null
     */
    public int[][] winLine(int r, int c) {
        if (!inBounds(r, c) || grid[r][c] == EMPTY) {
            return null;
        }
        int color = grid[r][c];

        for (int[] dir : DIRS) {
            List<int[]> line = new ArrayList<>();
            line.add(new int[]{r, c});

            // 沿着一个方向延伸
            for (int step = 1; step < 5; step++) {
                int nr = r + dir[0] * step;
                int nc = c + dir[1] * step;
                if (!inBounds(nr, nc) || grid[nr][nc] != color) {
                    break;
                }
                line.add(new int[]{nr, nc});
            }
            // 再朝反方向延伸
            for (int step = 1; step < 5; step++) {
                int nr = r - dir[0] * step;
                int nc = c - dir[1] * step;
                if (!inBounds(nr, nc) || grid[nr][nc] != color) {
                    break;
                }
                line.add(new int[]{nr, nc});
            }

            if (line.size() >= 5) {
                return line.toArray(new int[0][]);
            }
        }
        return null;
    }
}
