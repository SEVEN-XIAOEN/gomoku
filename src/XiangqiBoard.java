/**
 * 中国象棋棋盘。
 *
 * <p>9 列 × 10 行。棋子用整数编码：颜色 * 10 + 类型。
 * 颜色 1 = 红（先手），2 = 黑。类型 1~7 依次为 帅/仕/相/马/车/炮/兵。</p>
 */
public class XiangqiBoard {

    public static final int ROWS = 10;
    public static final int COLS = 9;

    public static final int EMPTY = 0;
    public static final int RED = 1;
    public static final int BLACK = 2;

    public static final int KING = 1;
    public static final int ADVISOR = 2;
    public static final int ELEPHANT = 3;
    public static final int HORSE = 4;
    public static final int ROOK = 5;
    public static final int CANNON = 6;
    public static final int PAWN = 7;

    private final int[][] grid = new int[ROWS][COLS];

    public XiangqiBoard() {
        reset();
    }

    /** 恢复标准开局。红方在下，黑方在上，红先。 */
    public final void reset() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                grid[r][c] = EMPTY;
            }
        }

        int[] backRank = {ROOK, HORSE, ELEPHANT, ADVISOR, KING, ADVISOR, ELEPHANT, HORSE, ROOK};
        for (int c = 0; c < COLS; c++) {
            grid[0][c] = piece(BLACK, backRank[c]);
            grid[9][c] = piece(RED, backRank[c]);
        }

        grid[2][1] = piece(BLACK, CANNON);
        grid[2][7] = piece(BLACK, CANNON);
        grid[7][1] = piece(RED, CANNON);
        grid[7][7] = piece(RED, CANNON);

        for (int c = 0; c < COLS; c += 2) {
            grid[3][c] = piece(BLACK, PAWN);
            grid[6][c] = piece(RED, PAWN);
        }
    }

    public int get(int r, int c) {
        return grid[r][c];
    }

    public int set(int r, int c, int value) {
        int old = grid[r][c];
        grid[r][c] = value;
        return old;
    }

    public boolean inBounds(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS;
    }

    /** 走子，返回被吃掉的棋子（没有则 0）。调用方负责合法性校验。 */
    public int move(int fr, int fc, int tr, int tc) {
        int captured = grid[tr][tc];
        grid[tr][tc] = grid[fr][fc];
        grid[fr][fc] = EMPTY;
        return captured;
    }

    /** 撤销一步，恢复起点棋子与终点被吃棋子。 */
    public void unmove(int fr, int fc, int tr, int tc, int captured) {
        grid[fr][fc] = grid[tr][tc];
        grid[tr][tc] = captured;
    }

    public static int piece(int color, int type) {
        return color * 10 + type;
    }

    public static int color(int piece) {
        return piece / 10;
    }

    public static int type(int piece) {
        return piece % 10;
    }

    public static int other(int color) {
        return color == RED ? BLACK : RED;
    }
}