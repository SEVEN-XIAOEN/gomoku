import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋中文记谱。
 *
 * <p>红方用中文数字（九路从红方右侧数起），黑方用阿拉伯数字（从黑方右侧数起）。
 * 同一条线上有多个同类棋子时，用「前 / 中 / 后」消歧。记谱只负责展示，
 * 不参与走法合法性判断。</p>
 */
public final class XiangqiNotation {

    private static final String[] CHINESE = {
            "", "一", "二", "三", "四", "五", "六", "七", "八", "九"
    };

    private static final String[][] NAMES = {
            null,
            {"", "帅", "仕", "相", "马", "车", "炮", "兵"},
            {"", "将", "士", "象", "马", "车", "炮", "卒"}
    };

    private XiangqiNotation() {
    }

    /** 根据走子前的盘面生成记谱，例如「炮二平五」「马8进7」。 */
    public static String of(XiangqiBoard board, int fr, int fc, int tr, int tc) {
        int piece = board.get(fr, fc);
        if (piece == XiangqiBoard.EMPTY) {
            return "";
        }
        int color = XiangqiBoard.color(piece);
        int type = XiangqiBoard.type(piece);
        String name = NAMES[color][type];

        String prefix = name + file(color, fc);
        List<Integer> sameFile = new ArrayList<>();
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            if (board.get(r, fc) == piece) {
                sameFile.add(r);
            }
        }
        if (sameFile.size() > 1) {
            sameFile.sort((a, b) -> color == XiangqiBoard.RED ? Integer.compare(a, b) : Integer.compare(b, a));
            int index = sameFile.indexOf(fr);
            prefix = positionName(sameFile.size(), index) + name;
        }

        String action;
        if (fr == tr) {
            action = "平" + file(color, tc);
        } else {
            boolean forward = color == XiangqiBoard.RED ? tr < fr : tr > fr;
            String verb = forward ? "进" : "退";
            boolean straight = type == XiangqiBoard.KING
                    || type == XiangqiBoard.ROOK
                    || type == XiangqiBoard.CANNON
                    || type == XiangqiBoard.PAWN;
            if (straight) {
                action = verb + digit(color, Math.abs(tr - fr));
            } else {
                action = verb + file(color, tc);
            }
        }
        return prefix + action;
    }

    private static String file(int color, int column) {
        int number = color == XiangqiBoard.RED ? 9 - column : column + 1;
        return digit(color, number);
    }

    private static String digit(int color, int number) {
        if (color == XiangqiBoard.RED && number >= 1 && number <= 9) {
            return CHINESE[number];
        }
        return String.valueOf(number);
    }

    private static String positionName(int count, int index) {
        if (count == 2) {
            return index == 0 ? "前" : "后";
        }
        if (count == 3) {
            if (index == 0) {
                return "前";
            }
            if (index == 1) {
                return "中";
            }
            return "后";
        }
        if (index == 0) {
            return "前";
        }
        if (index == count - 1) {
            return "后";
        }
        return CHINESE[Math.min(index + 1, 9)];
    }
}