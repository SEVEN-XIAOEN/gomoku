import java.util.List;

/**
 * 象棋规则自测：不依赖任何测试框架。
 *
 * <pre>
 *   javac -encoding UTF-8 -d out-test src/*.java test/XiangqiTest.java
 *   java -cp out-test XiangqiTest
 * </pre>
 */
public class XiangqiTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testInitialPosition();
        testPerftDepth2();
        testHorseLeg();
        testElephantEyeAndRiver();
        testCannonScreen();
        testKingPalace();
        testPawnDirectionAndCrossing();
        testFlyingGeneral();
        testCannotExposeKing();
        testCheckmate();
        testStalemate();
        testUndoRestoresPosition();
        testThreefoldRepetition();
        testNaturalLimitMath();
        testInsufficientMaterial();
        testAiProducesLegalMove();
        testRoomFlow();
        testTimeoutBlocksMove();
        testSoloAiReplyAndUndo();

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testInitialPosition() {
        XiangqiMatch match = new XiangqiMatch();
        int count = XiangqiRules.allLegalMoves(match.board(), XiangqiBoard.RED).size();
        check("象棋开局红方有 44 种合法走法", count == 44);
        check("象棋开局轮到红方", match.turn() == XiangqiBoard.RED);
        check("象棋开局没有将军", !match.isInCheck());
    }

    private static void testPerftDepth2() {
        XiangqiMatch match = new XiangqiMatch();
        long nodes = perft(match.board(), XiangqiBoard.RED, 2);
        check("象棋 perft 深度 2 = 1920（马腿/象眼/炮架/九宫全部生效）", nodes == 1920);
    }

    private static long perft(XiangqiBoard board, int color, int depth) {
        List<int[]> moves = XiangqiRules.allLegalMoves(board, color);
        if (depth <= 1) {
            return moves.size();
        }
        long total = 0;
        for (int[] m : moves) {
            int captured = board.move(m[0], m[1], m[2], m[3]);
            total += perft(board, XiangqiBoard.other(color), depth - 1);
            board.unmove(m[0], m[1], m[2], m[3], captured);
        }
        return total;
    }

    private static void testHorseLeg() {
        XiangqiBoard b = emptyBoard();
        b.set(5, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.HORSE));
        b.set(4, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.PAWN));
        b.set(5, 5, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.PAWN));
        b.set(0, 4, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        b.set(9, 3, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));

        List<int[]> moves = XiangqiRules.legalMoves(b, 5, 4);
        check("马腿被堵时不能向上跳", !hasMove(moves, 3, 3) && !hasMove(moves, 3, 5));
        check("马腿被堵时不能向右跳", !hasMove(moves, 4, 6) && !hasMove(moves, 6, 6));
        check("未受阻挡的马步仍然可以走", hasMove(moves, 4, 2) && hasMove(moves, 6, 2));
    }

    private static void testElephantEyeAndRiver() {
        XiangqiBoard b = emptyBoard();
        b.set(9, 2, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.ELEPHANT));
        b.set(8, 1, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.PAWN));
        b.set(0, 3, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        b.set(9, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));

        List<int[]> moves = XiangqiRules.legalMoves(b, 9, 2);
        check("象眼被塞时不能走那一斜线", !hasMove(moves, 7, 0));
        check("象眼未塞时可以走另一斜线", hasMove(moves, 7, 4));

        XiangqiBoard c = emptyBoard();
        c.set(5, 2, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.ELEPHANT));
        c.set(0, 3, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        c.set(9, 3, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        check("相不能过河", XiangqiRules.legalMoves(c, 5, 2).isEmpty());
    }

    private static void testCannonScreen() {
        XiangqiBoard b = emptyBoard();
        b.set(5, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.CANNON));
        b.set(5, 6, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.PAWN));
        b.set(5, 7, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.PAWN));
        b.set(5, 2, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.PAWN));
        b.set(0, 8, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        b.set(9, 0, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));

        List<int[]> moves = XiangqiRules.legalMoves(b, 5, 4);
        check("炮不能在没有炮架时吃子", !hasMove(moves, 5, 2));
        check("炮可以越过一个炮架吃子", hasMove(moves, 5, 7));
        check("炮可以走到空格但不能越过炮架走空位", hasMove(moves, 5, 3) && !hasMove(moves, 5, 8));

        XiangqiBoard c = emptyBoard();
        c.set(5, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.CANNON));
        c.set(5, 5, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.PAWN));
        c.set(5, 7, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.PAWN));
        c.set(0, 8, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        c.set(9, 0, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        List<int[]> moves2 = XiangqiRules.legalMoves(c, 5, 4);
        check("炮可以用己方棋子当炮架吃对方棋子", hasMove(moves2, 5, 7));
    }

    private static void testKingPalace() {
        XiangqiBoard b = emptyBoard();
        b.set(9, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        b.set(0, 3, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        List<int[]> moves = XiangqiRules.legalMoves(b, 9, 4);
        check("帅只能在九宫内活动", hasMove(moves, 8, 4) && !hasMove(moves, 7, 4) && !hasMove(moves, 9, 2));
    }

    private static void testPawnDirectionAndCrossing() {
        XiangqiBoard b = emptyBoard();
        b.set(6, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.PAWN));
        b.set(0, 8, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        b.set(9, 3, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        List<int[]> before = XiangqiRules.legalMoves(b, 6, 4);
        check("兵未过河只能向前", hasMove(before, 5, 4) && !hasMove(before, 6, 3) && !hasMove(before, 6, 5));

        XiangqiBoard c = emptyBoard();
        c.set(4, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.PAWN));
        c.set(0, 8, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        c.set(9, 3, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        List<int[]> after = XiangqiRules.legalMoves(c, 4, 4);
        check("兵过河后可以向前或左右", hasMove(after, 3, 4) && hasMove(after, 4, 3) && hasMove(after, 4, 5));

        XiangqiBoard d = emptyBoard();
        d.set(5, 4, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.PAWN));
        d.set(0, 8, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        d.set(9, 3, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        List<int[]> black = XiangqiRules.legalMoves(d, 5, 4);
        check("卒过河后可以向下或左右", hasMove(black, 6, 4) && hasMove(black, 5, 3) && hasMove(black, 5, 5));
    }

    private static void testFlyingGeneral() {
        XiangqiBoard b = emptyBoard();
        b.set(0, 4, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        b.set(9, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        check("两将同列且中间无子时判定为互相将军", XiangqiRules.inCheck(b, XiangqiBoard.RED)
                && XiangqiRules.inCheck(b, XiangqiBoard.BLACK));

        b.set(5, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.PAWN));
        check("中间有棋子时飞将不成立", !XiangqiRules.inCheck(b, XiangqiBoard.RED)
                && !XiangqiRules.inCheck(b, XiangqiBoard.BLACK));
    }

    private static void testCannotExposeKing() {
        XiangqiBoard b = emptyBoard();
        b.set(0, 4, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        b.set(9, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        b.set(8, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.ROOK));
        List<int[]> moves = XiangqiRules.legalMoves(b, 8, 4);
        check("不能走开唯一挡将的棋子", !hasMove(moves, 8, 3) && !hasMove(moves, 8, 5));
    }

    private static void testCheckmate() {
        XiangqiBoard b = emptyBoard();
        b.set(0, 4, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        b.set(9, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        b.set(0, 0, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.ROOK));
        b.set(0, 8, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.ROOK));
        check("将死：被将军且没有任何合法应手", XiangqiRules.inCheck(b, XiangqiBoard.BLACK)
                && !XiangqiRules.hasLegalMove(b, XiangqiBoard.BLACK));
    }

    private static void testStalemate() {
        XiangqiBoard b = emptyBoard();
        b.set(0, 3, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        b.set(1, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.ROOK));
        b.set(9, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        check("困毙：没有被将军但没有任何合法走法", !XiangqiRules.inCheck(b, XiangqiBoard.BLACK)
                && !XiangqiRules.hasLegalMove(b, XiangqiBoard.BLACK));
    }

    private static void testUndoRestoresPosition() {
        XiangqiMatch match = new XiangqiMatch();
        int[][] before = snapshot(match.board());
        match.move(7, 1, 7, 4);
        check("走子后轮次切换", match.turn() == XiangqiBoard.BLACK && match.historySize() == 1);
        match.undoLast();
        check("悔棋恢复盘面、轮次和历史",
                match.turn() == XiangqiBoard.RED && match.historySize() == 0 && same(before, snapshot(match.board())));
    }

    private static void testThreefoldRepetition() {
        XiangqiMatch match = new XiangqiMatch();
        int[][] cycle = {
                {9, 1, 7, 2}, {0, 1, 2, 2},
                {7, 2, 9, 1}, {2, 2, 0, 1}
        };
        for (int round = 0; round < 2; round++) {
            for (int[] m : cycle) {
                match.move(m[0], m[1], m[2], m[3]);
            }
        }
        check("三次重复局面自动判和", match.status() == XiangqiMatch.FINISHED
                && match.winner() == 0 && "repetition".equals(match.endReason()));
    }

    private static void testNaturalLimitMath() {
        check("60 回合自然限着等于 120 个半步", 60 * 2 == 120);
    }

    private static void testInsufficientMaterial() {
        XiangqiBoard b = emptyBoard();
        b.set(0, 4, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.KING));
        b.set(9, 4, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.KING));
        b.set(0, 3, XiangqiBoard.piece(XiangqiBoard.BLACK, XiangqiBoard.ADVISOR));
        b.set(9, 5, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.ELEPHANT));
        check("只剩将/士/象时判定双方无取胜可能", XiangqiRules.insufficientMaterial(b));

        b.set(3, 0, XiangqiBoard.piece(XiangqiBoard.RED, XiangqiBoard.PAWN));
        check("还有兵时不算无取胜可能", !XiangqiRules.insufficientMaterial(b));
    }

    private static void testAiProducesLegalMove() {
        XiangqiMatch match = new XiangqiMatch();
        int[] m = new XiangqiAI().chooseMove(match.board(), XiangqiBoard.RED, 1);
        check("象棋 AI 能给出合法着法", m != null && m.length == 4
                && XiangqiRules.isLegalMove(match.board(), m[0], m[1], m[2], m[3]));
    }

    private static void testRoomFlow() {
        XiangqiRoom room = new XiangqiRoom("XQ1234", 0, 0);
        room.join("red-player", 0);
        room.join("black-player", 0);
        check("象棋房间先到者执红、后到者执黑",
                room.seatOf("red-player") == XiangqiBoard.RED
                        && room.seatOf("black-player") == XiangqiBoard.BLACK);

        room.move("red-player", 7, 1, 7, 4);
        String json = room.stateJson("black-player");
        check("象棋联机走子后切换轮次", json.contains("\"turn\":2") && json.contains("\"moves\":1"));
        check("象棋联机不能替对手悔棋",
                throwsIllegalArgument(() -> room.undo("black-player")));
        room.undo("red-player");
        check("象棋联机可以撤回自己刚走的一手",
                room.stateJson("red-player").contains("\"moves\":0"));
    }

    private static void testTimeoutBlocksMove() {
        XiangqiRoom room = new XiangqiRoom("XQ9999", 30, 0);
        room.join("red-player", 1);
        room.join("black-player", 2);
        try {
            Thread.sleep(120);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        check("超时后红方不能再走棋",
                throwsIllegalArgument(() -> room.move("red-player", 7, 1, 7, 4)));
        check("超时判黑方获胜且不能悔棋",
                room.stateJson("black-player").contains("\"status\":\"black_win\"")
                        && throwsIllegalArgument(() -> room.undo("red-player")));
    }

    private static void testSoloAiReplyAndUndo() {
        XiangqiSolo solo = new XiangqiSolo();
        solo.newGame(XiangqiBoard.RED, 1, 300_000, 3_000);
        solo.move(7, 1, 7, 4);
        String after = solo.toJson();
        check("象棋人机局玩家走子后电脑自动应手",
                after.contains("\"moves\":2") && after.contains("\"turn\":1"));

        solo.undo();
        String undone = solo.toJson();
        check("象棋人机悔棋会同时撤销电脑应手",
                undone.contains("\"moves\":0") && undone.contains("\"turn\":1"));
    }
    // ------------------------------------------------------------------ 工具

    private static XiangqiBoard emptyBoard() {
        XiangqiBoard b = new XiangqiBoard();
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                b.set(r, c, XiangqiBoard.EMPTY);
            }
        }
        return b;
    }

    private static boolean hasMove(List<int[]> moves, int r, int c) {
        for (int[] m : moves) {
            if (m[0] == r && m[1] == c) {
                return true;
            }
        }
        return false;
    }

    private static int[][] snapshot(XiangqiBoard b) {
        int[][] copy = new int[XiangqiBoard.ROWS][XiangqiBoard.COLS];
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                copy[r][c] = b.get(r, c);
            }
        }
        return copy;
    }

    private static boolean same(int[][] a, int[][] b) {
        for (int r = 0; r < a.length; r++) {
            for (int c = 0; c < a[r].length; c++) {
                if (a[r][c] != b[r][c]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }
}
