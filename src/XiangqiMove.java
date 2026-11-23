/** 一条象棋着法记录。 */
public class XiangqiMove {

    public final int fr;
    public final int fc;
    public final int tr;
    public final int tc;
    public final int color;
    public final int captured;
    /** 这步是否将军 */
    public final boolean check;
    /** 这步是否属于"捉" */
    public final boolean chase;
    /** 中文棋谱记法，例如「炮二平五」。 */
    public final String notation;

    public XiangqiMove(int fr, int fc, int tr, int tc, int color, int captured,
                       boolean check, boolean chase, String notation) {
        this.fr = fr;
        this.fc = fc;
        this.tr = tr;
        this.tc = tc;
        this.color = color;
        this.captured = captured;
        this.check = check;
        this.chase = chase;
        this.notation = notation == null ? "" : notation;
    }

    public XiangqiMove(int fr, int fc, int tr, int tc, int color, int captured,
                       boolean check, boolean chase) {
        this(fr, fc, tr, tc, color, captured, check, chase, "");
    }
}