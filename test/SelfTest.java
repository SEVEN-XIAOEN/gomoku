/**
 * 自测程序：不需要任何测试框架，直接 java 运行即可。
 *
 * <p>编译运行（在项目根目录执行）：</p>
 * <pre>
 *   javac -encoding UTF-8 -d out-test src/*.java test/SelfTest.java
 *   java -cp out-test SelfTest
 * </pre>
 */
public class SelfTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testHorizontalWin();
        testVerticalWin();
        testDiagonalWin();
        testNoWinWithFour();
        testCannotPlaceOnOccupied();
        testOutOfBounds();
        testUndoRestoresBoard();
        testAiTakesImmediateWin();
        testAiBlocksFourInARow();
        testAiMovesNextToExistingStone();
        testPvpAssignsColors();
        testPvpRejectsOutOfTurn();
        testPvpDetectsWin();
        testPvpUndoOwnLastMove();
        testPvpRejectsThirdPlayer();

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ 棋盘逻辑

    private static void testHorizontalWin() {
        Board board = new Board(15);
        for (int c = 3; c <= 7; c++) {
            board.place(5, c, Board.BLACK);
        }
        check("横向五连能判定获胜", board.winLine(5, 5) != null);
    }

    private static void testVerticalWin() {
        Board board = new Board(15);
        for (int r = 2; r <= 6; r++) {
            board.place(r, 9, Board.WHITE);
        }
        check("纵向五连能判定获胜", board.winLine(4, 9) != null);
    }

    private static void testDiagonalWin() {
        Board board = new Board(15);
        for (int i = 0; i < 5; i++) {
            board.place(3 + i, 3 + i, Board.BLACK);
        }
        check("斜向五连能判定获胜", board.winLine(5, 5) != null);
    }

    private static void testNoWinWithFour() {
        Board board = new Board(15);
        for (int c = 3; c <= 6; c++) {
            board.place(5, c, Board.BLACK);
        }
        check("四连不算获胜", board.winLine(5, 5) == null);
    }

    private static void testCannotPlaceOnOccupied() {
        Board board = new Board(15);
        board.place(0, 0, Board.BLACK);
        check("同一位置不能重复落子", !board.place(0, 0, Board.WHITE));
    }

    private static void testOutOfBounds() {
        Board board = new Board(9);
        check("越界落子会被拒绝", !board.place(9, 9, Board.BLACK) && !board.inBounds(-1, 0));
    }

    private static void testUndoRestoresBoard() {
        Board board = new Board(15);
        board.place(7, 7, Board.BLACK);
        board.clear(7, 7);
        check("悔棋后该位置重新变空", board.isEmpty(7, 7) && board.moves() == 0);
    }

    // ------------------------------------------------------------------ AI 行为

    private static void testAiTakesImmediateWin() {
        Board board = new Board(15);
        // 白棋（电脑）已有四连，两端都能成五，AI 必须直接获胜
        for (int c = 4; c <= 7; c++) {
            board.place(7, c, Board.WHITE);
        }
        board.place(6, 6, Board.BLACK);

        int[] move = new GomokuAI().chooseMove(board, Board.WHITE, 2);
        boolean wins = (move[0] == 7 && (move[1] == 3 || move[1] == 8));
        check("AI 能抓住一步获胜的机会", wins);
    }

    private static void testAiBlocksFourInARow() {
        Board board = new Board(15);
        // 黑棋（玩家）四连，AI 必须去堵
        for (int c = 4; c <= 7; c++) {
            board.place(7, c, Board.BLACK);
        }
        board.place(6, 6, Board.WHITE);

        int[] move = new GomokuAI().chooseMove(board, Board.WHITE, 2);
        boolean blocked = (move[0] == 7 && (move[1] == 3 || move[1] == 8));
        check("AI 会封堵玩家的四连", blocked);
    }

    private static void testAiMovesNextToExistingStone() {
        Board board = new Board(15);
        board.place(7, 7, Board.BLACK);

        int[] move = new GomokuAI().chooseMove(board, Board.WHITE, 2);
        int distance = Math.max(Math.abs(move[0] - 7), Math.abs(move[1] - 7));
        check("AI 落子贴近已有棋子（切比雪夫距离 <= 2）", board.isEmpty(move[0], move[1]) && distance <= 2);
    }

    // ------------------------------------------------------------------ 双人联机

    private static void testPvpAssignsColors() {
        PvpRoom room = new PvpRoom("ABC123", 15);
        room.join("black-player");
        room.join("white-player");
        check("先加入房间的人执黑，后加入的人执白",
                room.colorOf("black-player") == Board.BLACK
                        && room.colorOf("white-player") == Board.WHITE);
    }

    private static void testPvpRejectsOutOfTurn() {
        PvpRoom room = new PvpRoom("ABC123", 15);
        room.join("black-player");
        room.join("white-player");
        check("双人模式不能替对手落子",
                throwsIllegalArgument(() -> room.move("white-player", 7, 7)));
    }

    private static void testPvpDetectsWin() {
        PvpRoom room = new PvpRoom("ABC123", 15);
        room.join("black-player");
        room.join("white-player");
        for (int c = 3; c <= 6; c++) {
            room.move("black-player", 7, c);
            room.move("white-player", 0, c - 3);
        }
        room.move("black-player", 7, 7);
        String json = room.toJson("black-player");
        check("双人模式能判定五连获胜", json.contains("\"status\":\"black_win\""));
    }

    private static void testPvpUndoOwnLastMove() {
        PvpRoom room = new PvpRoom("ABC123", 15);
        room.join("black-player");
        room.join("white-player");
        room.move("black-player", 7, 7);
        room.undo("black-player");
        String json = room.toJson("black-player");
        check("双人模式可以撤回自己刚下的最后一手",
                json.contains("\"moves\":0") && json.contains("\"turn\":1"));
    }

    private static void testPvpRejectsThirdPlayer() {
        PvpRoom room = new PvpRoom("ABC123", 15);
        room.join("black-player");
        room.join("white-player");
        check("双人房间满员后拒绝第三名玩家", !room.join("third-player"));
    }

    // ------------------------------------------------------------------ 小工具

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("[通过] " + name);
        } else {
            failed++;
            System.out.println("[失败] " + name);
        }
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
