import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋规则引擎：走法生成、将军判定、合法性过滤、"捉"的识别。
 *
 * <p>所有走法都由本类统一生成，服务端是唯一裁判。前端只展示和提交坐标，
 * 不允许自己判断规则，避免前后端各有一套规则。</p>
 */
public final class XiangqiRules {

    private static final int[][] ORTHO = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAG = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private XiangqiRules() {
    }

    public static boolean inPalace(int r, int c, int color) {
        if (c < 3 || c > 5) {
            return false;
        }
        return color == XiangqiBoard.RED ? (r >= 7 && r <= 9) : (r >= 0 && r <= 2);
    }

    private static boolean onOwnSide(int r, int color) {
        return color == XiangqiBoard.RED ? r >= 5 : r <= 4;
    }

    private static boolean canLand(XiangqiBoard board, int r, int c, int color) {
        if (!board.inBounds(r, c)) {
            return false;
        }
        int target = board.get(r, c);
        return target == XiangqiBoard.EMPTY || XiangqiBoard.color(target) != color;
    }

    /** 生成某一枚棋子的伪合法走法（不检查走后是否被将军）。 */
    public static void addPseudoMoves(XiangqiBoard board, int r, int c, List<int[]> out) {
        int piece = board.get(r, c);
        if (piece == XiangqiBoard.EMPTY) {
            return;
        }
        int color = XiangqiBoard.color(piece);
        switch (XiangqiBoard.type(piece)) {
            case XiangqiBoard.KING -> addKing(board, r, c, color, out);
            case XiangqiBoard.ADVISOR -> addAdvisor(board, r, c, color, out);
            case XiangqiBoard.ELEPHANT -> addElephant(board, r, c, color, out);
            case XiangqiBoard.HORSE -> addHorse(board, r, c, color, out);
            case XiangqiBoard.ROOK -> addRook(board, r, c, color, out);
            case XiangqiBoard.CANNON -> addCannon(board, r, c, color, out);
            case XiangqiBoard.PAWN -> addPawn(board, r, c, color, out);
            default -> {
            }
        }
    }

    private static void addKing(XiangqiBoard b, int r, int c, int color, List<int[]> out) {
        for (int[] d : ORTHO) {
            int nr = r + d[0];
            int nc = c + d[1];
            if (inPalace(nr, nc, color) && canLand(b, nr, nc, color)) {
                out.add(new int[]{nr, nc});
            }
        }
    }

    private static void addAdvisor(XiangqiBoard b, int r, int c, int color, List<int[]> out) {
        for (int[] d : DIAG) {
            int nr = r + d[0];
            int nc = c + d[1];
            if (inPalace(nr, nc, color) && canLand(b, nr, nc, color)) {
                out.add(new int[]{nr, nc});
            }
        }
    }

    private static void addElephant(XiangqiBoard b, int r, int c, int color, List<int[]> out) {
        for (int[] d : DIAG) {
            int nr = r + d[0] * 2;
            int nc = c + d[1] * 2;
            if (!b.inBounds(nr, nc) || !onOwnSide(nr, color) || !canLand(b, nr, nc, color)) {
                continue;
            }
            int eyeR = r + d[0];
            int eyeC = c + d[1];
            if (b.get(eyeR, eyeC) == XiangqiBoard.EMPTY) {
                out.add(new int[]{nr, nc});
            }
        }
    }

    private static void addHorse(XiangqiBoard b, int r, int c, int color, List<int[]> out) {
        int[][] jumps = {
                {-2, -1, -1, 0}, {-2, 1, -1, 0},
                {2, -1, 1, 0}, {2, 1, 1, 0},
                {-1, -2, 0, -1}, {1, -2, 0, -1},
                {-1, 2, 0, 1}, {1, 2, 0, 1}
        };
        for (int[] j : jumps) {
            int nr = r + j[0];
            int nc = c + j[1];
            if (!canLand(b, nr, nc, color)) {
                continue;
            }
            int legR = r + j[2];
            int legC = c + j[3];
            if (b.get(legR, legC) == XiangqiBoard.EMPTY) {
                out.add(new int[]{nr, nc});
            }
        }
    }

    private static void addRook(XiangqiBoard b, int r, int c, int color, List<int[]> out) {
        for (int[] d : ORTHO) {
            int nr = r + d[0];
            int nc = c + d[1];
            while (b.inBounds(nr, nc)) {
                int target = b.get(nr, nc);
                if (target == XiangqiBoard.EMPTY) {
                    out.add(new int[]{nr, nc});
                } else {
                    if (XiangqiBoard.color(target) != color) {
                        out.add(new int[]{nr, nc});
                    }
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
    }

    private static void addCannon(XiangqiBoard b, int r, int c, int color, List<int[]> out) {
        for (int[] d : ORTHO) {
            int nr = r + d[0];
            int nc = c + d[1];
            boolean screen = false;
            while (b.inBounds(nr, nc)) {
                int target = b.get(nr, nc);
                if (!screen) {
                    if (target == XiangqiBoard.EMPTY) {
                        out.add(new int[]{nr, nc});
                    } else {
                        screen = true;
                    }
                } else if (target != XiangqiBoard.EMPTY) {
                    if (XiangqiBoard.color(target) != color) {
                        out.add(new int[]{nr, nc});
                    }
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
    }

    private static void addPawn(XiangqiBoard b, int r, int c, int color, List<int[]> out) {
        int forward = color == XiangqiBoard.RED ? -1 : 1;
        int nr = r + forward;
        if (canLand(b, nr, c, color)) {
            out.add(new int[]{nr, c});
        }
        boolean crossed = color == XiangqiBoard.RED ? r <= 4 : r >= 5;
        if (crossed) {
            if (canLand(b, r, c - 1, color)) {
                out.add(new int[]{r, c - 1});
            }
            if (canLand(b, r, c + 1, color)) {
                out.add(new int[]{r, c + 1});
            }
        }
    }

    /** 找到将/帅，返回 {r, c}；找不到返回 null。 */
    public static int[] findKing(XiangqiBoard board, int color) {
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                int p = board.get(r, c);
                if (p != 0 && XiangqiBoard.color(p) == color && XiangqiBoard.type(p) == XiangqiBoard.KING) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    /** 两将同列且中间无子（飞将）。 */
    public static boolean kingsFacing(XiangqiBoard board) {
        int[] red = findKing(board, XiangqiBoard.RED);
        int[] black = findKing(board, XiangqiBoard.BLACK);
        if (red == null || black == null || red[1] != black[1]) {
            return false;
        }
        int from = Math.min(red[0], black[0]) + 1;
        int to = Math.max(red[0], black[0]);
        for (int r = from; r < to; r++) {
            if (board.get(r, red[1]) != XiangqiBoard.EMPTY) {
                return false;
            }
        }
        return true;
    }

    /** 目标格是否被 byColor 一方攻击（只考虑能威胁到将/帅的兵种）。 */
    public static boolean squareAttacked(XiangqiBoard b, int r, int c, int byColor) {
        // 车、将：四个方向第一枚棋子
        for (int[] d : ORTHO) {
            int nr = r + d[0];
            int nc = c + d[1];
            int dist = 1;
            while (b.inBounds(nr, nc) && b.get(nr, nc) == XiangqiBoard.EMPTY) {
                nr += d[0];
                nc += d[1];
                dist++;
            }
            if (b.inBounds(nr, nc)) {
                int p = b.get(nr, nc);
                if (XiangqiBoard.color(p) == byColor) {
                    int t = XiangqiBoard.type(p);
                    if (t == XiangqiBoard.ROOK || (t == XiangqiBoard.KING && dist == 1)) {
                        return true;
                    }
                }
                // 炮：越过第一个炮架后，下一枚棋子是炮
                nr += d[0];
                nc += d[1];
                while (b.inBounds(nr, nc) && b.get(nr, nc) == XiangqiBoard.EMPTY) {
                    nr += d[0];
                    nc += d[1];
                }
                if (b.inBounds(nr, nc)) {
                    int q = b.get(nr, nc);
                    if (XiangqiBoard.color(q) == byColor && XiangqiBoard.type(q) == XiangqiBoard.CANNON) {
                        return true;
                    }
                }
            }
        }

        // 马：从目标格反推马的位置，并检查马腿
        int[][] horseFrom = {
                {2, 1, -1, 0}, {2, -1, -1, 0},
                {-2, 1, 1, 0}, {-2, -1, 1, 0},
                {1, 2, 0, -1}, {1, -2, 0, 1},
                {-1, 2, 0, -1}, {-1, -2, 0, 1}
        };
        for (int[] h : horseFrom) {
            int hr = r + h[0];
            int hc = c + h[1];
            if (!b.inBounds(hr, hc)) {
                continue;
            }
            int p = b.get(hr, hc);
            if (XiangqiBoard.color(p) == byColor && XiangqiBoard.type(p) == XiangqiBoard.HORSE) {
                int legR = hr + h[2];
                int legC = hc + h[3];
                if (b.inBounds(legR, legC) && b.get(legR, legC) == XiangqiBoard.EMPTY) {
                    return true;
                }
            }
        }

        // 士/仕：目标格斜一格
        for (int[] d : DIAG) {
            int ar = r + d[0];
            int ac = c + d[1];
            if (!b.inBounds(ar, ac)) {
                continue;
            }
            int p = b.get(ar, ac);
            if (XiangqiBoard.color(p) == byColor && XiangqiBoard.type(p) == XiangqiBoard.ADVISOR) {
                return true;
            }
        }

        // 象/相：目标格斜两格，塞象眼则不算
        for (int[] d : DIAG) {
            int er = r + d[0] * 2;
            int ec = c + d[1] * 2;
            if (!b.inBounds(er, ec)) {
                continue;
            }
            int p = b.get(er, ec);
            if (XiangqiBoard.color(p) == byColor && XiangqiBoard.type(p) == XiangqiBoard.ELEPHANT) {
                if (b.get(r + d[0], c + d[1]) == XiangqiBoard.EMPTY) {
                    return true;
                }
            }
        }

        // 兵/卒
        if (byColor == XiangqiBoard.RED) {
            if (b.inBounds(r + 1, c)) {
                int p = b.get(r + 1, c);
                if (XiangqiBoard.color(p) == XiangqiBoard.RED && XiangqiBoard.type(p) == XiangqiBoard.PAWN) {
                    return true;
                }
            }
            if (r <= 4) {
                for (int dc : new int[]{-1, 1}) {
                    if (b.inBounds(r, c + dc)) {
                        int p = b.get(r, c + dc);
                        if (XiangqiBoard.color(p) == XiangqiBoard.RED && XiangqiBoard.type(p) == XiangqiBoard.PAWN) {
                            return true;
                        }
                    }
                }
            }
        } else {
            if (b.inBounds(r - 1, c)) {
                int p = b.get(r - 1, c);
                if (XiangqiBoard.color(p) == XiangqiBoard.BLACK && XiangqiBoard.type(p) == XiangqiBoard.PAWN) {
                    return true;
                }
            }
            if (r >= 5) {
                for (int dc : new int[]{-1, 1}) {
                    if (b.inBounds(r, c + dc)) {
                        int p = b.get(r, c + dc);
                        if (XiangqiBoard.color(p) == XiangqiBoard.BLACK && XiangqiBoard.type(p) == XiangqiBoard.PAWN) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** 某一方是否正被将军（包含飞将）。 */
    public static boolean inCheck(XiangqiBoard board, int color) {
        int[] king = findKing(board, color);
        if (king == null) {
            return true;
        }
        if (kingsFacing(board)) {
            return true;
        }
        return squareAttacked(board, king[0], king[1], XiangqiBoard.other(color));
    }

    /** 某一枚棋子的全部合法走法（已过滤自将）。 */
    public static List<int[]> legalMoves(XiangqiBoard board, int r, int c) {
        List<int[]> result = new ArrayList<>();
        int piece = board.get(r, c);
        if (piece == XiangqiBoard.EMPTY) {
            return result;
        }
        int color = XiangqiBoard.color(piece);
        List<int[]> pseudo = new ArrayList<>();
        addPseudoMoves(board, r, c, pseudo);
        for (int[] m : pseudo) {
            int captured = board.get(m[0], m[1]);
            board.move(r, c, m[0], m[1]);
            boolean safe = !inCheck(board, color);
            board.unmove(r, c, m[0], m[1], captured);
            if (safe) {
                result.add(m);
            }
        }
        return result;
    }

    public static boolean isLegalMove(XiangqiBoard board, int fr, int fc, int tr, int tc) {
        if (!board.inBounds(fr, fc) || !board.inBounds(tr, tc)) {
            return false;
        }
        int piece = board.get(fr, fc);
        if (piece == XiangqiBoard.EMPTY) {
            return false;
        }
        int color = XiangqiBoard.color(piece);
        int target = board.get(tr, tc);
        if (target != XiangqiBoard.EMPTY && XiangqiBoard.color(target) == color) {
            return false;
        }
        List<int[]> pseudo = new ArrayList<>();
        addPseudoMoves(board, fr, fc, pseudo);
        boolean ok = false;
        for (int[] m : pseudo) {
            if (m[0] == tr && m[1] == tc) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            return false;
        }
        int captured = board.move(fr, fc, tr, tc);
        boolean safe = !inCheck(board, color);
        board.unmove(fr, fc, tr, tc, captured);
        return safe;
    }

    public static List<int[]> allLegalMoves(XiangqiBoard board, int color) {
        List<int[]> result = new ArrayList<>();
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                int p = board.get(r, c);
                if (p != 0 && XiangqiBoard.color(p) == color) {
                    for (int[] m : legalMoves(board, r, c)) {
                        result.add(new int[]{r, c, m[0], m[1]});
                    }
                }
            }
        }
        return result;
    }

    public static boolean hasLegalMove(XiangqiBoard board, int color) {
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                int p = board.get(r, c);
                if (p != 0 && XiangqiBoard.color(p) == color && !legalMoves(board, r, c).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int value(int piece) {
        return switch (XiangqiBoard.type(piece)) {
            case XiangqiBoard.KING -> 10000;
            case XiangqiBoard.ROOK -> 900;
            case XiangqiBoard.CANNON -> 450;
            case XiangqiBoard.HORSE -> 400;
            case XiangqiBoard.ELEPHANT, XiangqiBoard.ADVISOR -> 200;
            case XiangqiBoard.PAWN -> 100;
            default -> 0;
        };
    }

    /**
     * 判断刚落子的这枚棋子是否在"捉"对方棋子。
     *
     * <p>这是一个工程化的近似判定：移动后的棋子能吃到对方某个子，且该子没有保护，
     * 或者价值高于进攻子。将/帅被攻击不算"捉"，那是"将"。</p>
     */
    public static boolean isChase(XiangqiBoard board, int color, int tr, int tc) {
        int piece = board.get(tr, tc);
        if (piece == XiangqiBoard.EMPTY || XiangqiBoard.color(piece) != color) {
            return false;
        }
        List<int[]> attacks = new ArrayList<>();
        addPseudoMoves(board, tr, tc, attacks);
        int attackerValue = value(piece);
        for (int[] m : attacks) {
            int target = board.get(m[0], m[1]);
            if (target == XiangqiBoard.EMPTY || XiangqiBoard.color(target) == color) {
                continue;
            }
            if (XiangqiBoard.type(target) == XiangqiBoard.KING) {
                continue;
            }
            boolean defended = squareAttacked(board, m[0], m[1], XiangqiBoard.color(target));
            if (!defended || value(target) > attackerValue) {
                return true;
            }
        }
        return false;
    }

    /** 双方都没有车、马、炮、兵，认定没有取胜可能。 */
    public static boolean insufficientMaterial(XiangqiBoard board) {
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                int t = XiangqiBoard.type(board.get(r, c));
                if (t == XiangqiBoard.ROOK || t == XiangqiBoard.HORSE
                        || t == XiangqiBoard.CANNON || t == XiangqiBoard.PAWN) {
                    return false;
                }
            }
        }
        return true;
    }
}