/**
 * 联机房间的公共部分：座位、准备状态、时钟、认输、提和、再来一盘、超时。
 *
 * <p>五子棋和象棋各自继承本类，只实现自己棋盘上的落子与状态序列化。
 * 座位 1 永远是先手（五子棋黑、象棋红）。</p>
 */
public abstract class Room {

    public static final int PLAYING = 0;
    public static final int FINISHED = 1;
    /** 超过这个时间没有轮询，就视为对手暂时离线。 */
    protected static final long ONLINE_WINDOW_MS = 6000;

    protected final String code;
    protected final String game;
    protected final GameClock clock;

    protected String firstId;
    protected String secondId;
    protected int status = PLAYING;
    /** 0 = 和棋/未结束，1 = 座位 1 胜，2 = 座位 2 胜 */
    protected int winner;
    protected String endReason = "";
    /** 0 = 无人提和，1/2 = 对应座位提和 */
    protected int drawOffer;
    /** 0 = 没有悔棋申请，1/2 = 对应座位发起申请 */
    protected int undoOffer;
    protected final boolean[] rematch = new boolean[3];
    /** 每个座位最后一次被看到的时间，用于判断对手是否在线。 */
    protected final long[] seatSeenAt = new long[3];
    protected long lastActive = System.currentTimeMillis();

    protected Room(String code, String game, long baseMs, long incrementMs) {
        this.code = code;
        this.game = game;
        this.clock = baseMs > 0 ? new GameClock(baseMs, incrementMs) : null;
    }

    public synchronized String code() {
        return code;
    }

    public synchronized String game() {
        return game;
    }

    public synchronized int seatOf(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        if (sessionId.equals(firstId)) {
            return 1;
        }
        if (sessionId.equals(secondId)) {
            return 2;
        }
        return 0;
    }

    public synchronized boolean ready() {
        return firstId != null && secondId != null;
    }

    public synchronized boolean isEmpty() {
        return firstId == null && secondId == null;
    }

    public synchronized int status() {
        return status;
    }

    public synchronized int winner() {
        return winner;
    }

    public synchronized String endReason() {
        return endReason;
    }

    public synchronized int drawOffer() {
        return drawOffer;
    }

    public synchronized int undoOffer() {
        return undoOffer;
    }

    public synchronized boolean hasVotedRematch(int seat) {
        return seat >= 1 && seat <= 2 && rematch[seat];
    }

    public synchronized long idleMillis() {
        return System.currentTimeMillis() - lastActive;
    }

    /** 记录某个座位刚刚活跃过。每次状态查询和操作都会调用。 */
    public synchronized void seen(String sessionId) {
        int seat = seatOf(sessionId);
        if (seat != 0) {
            seatSeenAt[seat] = System.currentTimeMillis();
        }
    }

    protected void touch() {
        lastActive = System.currentTimeMillis();
    }

    public synchronized boolean join(String sessionId, int preferredSeat) {
        touch();
        int existing = seatOf(sessionId);
        if (existing != 0) {
            seatSeenAt[existing] = System.currentTimeMillis();
            return true;
        }
        boolean wasReady = ready();
        int seat = 0;
        if (preferredSeat == 1 && firstId == null) {
            seat = 1;
        } else if (preferredSeat == 2 && secondId == null) {
            seat = 2;
        } else if (firstId == null) {
            seat = 1;
        } else if (secondId == null) {
            seat = 2;
        } else {
            return false;
        }
        if (seat == 1) {
            firstId = sessionId;
        } else {
            secondId = sessionId;
        }
        seatSeenAt[seat] = System.currentTimeMillis();
        if (!wasReady && ready()) {
            beginNewMatch();
        }
        return true;
    }

    public synchronized void leave(String sessionId) {
        int seat = seatOf(sessionId);
        if (seat == 0) {
            return;
        }
        if (ready() && status == PLAYING) {
            status = FINISHED;
            winner = other(seat);
            endReason = "opponent_left";
            if (clock != null) {
                clock.pause(System.currentTimeMillis());
            }
        }
        if (seat == 1) {
            firstId = null;
        } else {
            secondId = null;
        }
        seatSeenAt[seat] = 0;
        if (undoOffer == seat) {
            undoOffer = 0;
        }
        touch();
    }

    public synchronized void resign(String sessionId) {
        checkTimeout(System.currentTimeMillis());
        touch();
        int seat = seatOf(sessionId);
        if (seat == 0) {
            throw new IllegalArgumentException("你不在这个房间里");
        }
        if (status != PLAYING) {
            throw new IllegalArgumentException("对局已经结束");
        }
        status = FINISHED;
        winner = other(seat);
        endReason = "resign";
        drawOffer = 0;
        undoOffer = 0;
        if (clock != null) {
            clock.pause(System.currentTimeMillis());
        }
    }

    public synchronized void draw(String sessionId, String action) {
        checkTimeout(System.currentTimeMillis());
        touch();
        int seat = seatOf(sessionId);
        if (seat == 0) {
            throw new IllegalArgumentException("你不在这个房间里");
        }
        if (status != PLAYING) {
            throw new IllegalArgumentException("对局已经结束");
        }
        if (undoOffer != 0) {
            throw new IllegalArgumentException("请先处理悔棋申请");
        }
        if ("offer".equals(action)) {
            if (drawOffer == seat) {
                throw new IllegalArgumentException("你已经提和了，等待对方回应");
            }
            drawOffer = seat;
        } else if ("accept".equals(action)) {
            if (drawOffer == 0 || drawOffer == seat) {
                throw new IllegalArgumentException("没有待接受的提和");
            }
            status = FINISHED;
            winner = 0;
            endReason = "draw_agreed";
            drawOffer = 0;
            undoOffer = 0;
            if (clock != null) {
                clock.pause(System.currentTimeMillis());
            }
        } else if ("decline".equals(action)) {
            if (drawOffer == 0 || drawOffer == seat) {
                throw new IllegalArgumentException("没有待拒绝的提和");
            }
            drawOffer = 0;
        } else {
            throw new IllegalArgumentException("action 只能是 offer / accept / decline");
        }
    }

    public synchronized void rematch(String sessionId) {
        touch();
        int seat = seatOf(sessionId);
        if (seat == 0) {
            throw new IllegalArgumentException("你不在这个房间里");
        }
        if (!ready()) {
            throw new IllegalArgumentException("等待对手加入后再开始");
        }
        if (status != FINISHED) {
            throw new IllegalArgumentException("对局还在进行中，结束后才能再来一盘");
        }
        rematch[seat] = true;
        if (rematch[1] && rematch[2]) {
            String tmp = firstId;
            firstId = secondId;
            secondId = tmp;
            seatSeenAt[1] = System.currentTimeMillis();
            seatSeenAt[2] = System.currentTimeMillis();
            beginNewMatch();
        }
    }

    /** 结算当前时钟；定时器与走子入口都会调用，避免超时后仍能落子。 */
    protected synchronized void checkTimeout(long now) {
        if (status == PLAYING && ready() && clock != null) {
            int timeoutSeat = clock.timeoutSeat(now);
            if (timeoutSeat != 0) {
                status = FINISHED;
                winner = other(timeoutSeat);
                endReason = "timeout";
                drawOffer = 0;
                undoOffer = 0;
                clock.pause(now);
            }
        }
    }

    /** 定时器每 200ms 调用一次，负责判超时。 */
    public synchronized void tick(long now) {
        checkTimeout(now);
    }

    protected void beginNewMatch() {
        status = PLAYING;
        winner = 0;
        endReason = "";
        drawOffer = 0;
        undoOffer = 0;
        rematch[1] = false;
        rematch[2] = false;
        resetMatch();
        if (clock != null) {
            clock.start(1, System.currentTimeMillis());
        }
        touch();
    }

    protected void onMoveDone(int seat) {
        if (clock != null) {
            clock.onMove(seat, System.currentTimeMillis());
        }
        touch();
    }

    protected void onMatchFinished() {
        if (clock != null) {
            clock.pause(System.currentTimeMillis());
        }
        touch();
    }

    protected void onUndo(int nextTurn) {
        if (clock != null) {
            clock.resume(nextTurn, System.currentTimeMillis());
        }
        touch();
    }

    /** 拼出五子棋/象棋共用的房间字段。调用方需要先写入 "{"。 */
    protected synchronized void appendCommon(StringBuilder sb, String sessionId) {
        checkTimeout(System.currentTimeMillis());
        int seat = seatOf(sessionId);
        seen(sessionId);
        long now = System.currentTimeMillis();
        int otherSeat = seat == 0 ? 0 : other(seat);
        String otherId = otherSeat == 1 ? firstId : (otherSeat == 2 ? secondId : null);
        long otherSeen = otherSeat == 0 ? 0 : seatSeenAt[otherSeat];
        boolean opponentOnline = otherId != null && otherSeen > 0 && now - otherSeen <= ONLINE_WINDOW_MS;
        long opponentLastSeenMs = (otherId == null || otherSeen <= 0) ? -1 : Math.max(0, now - otherSeen);

        sb.append("\"game\":\"").append(Json.escape(game)).append('"');
        sb.append(",\"mode\":\"pvp\"");
        sb.append(",\"phase\":\"").append(phase()).append('"');
        sb.append(",\"room\":\"").append(Json.escape(code)).append('"');
        sb.append(",\"myColor\":").append(seat);
        sb.append(",\"mySeat\":").append(seat);
        sb.append(",\"firstPresent\":").append(firstId != null);
        sb.append(",\"secondPresent\":").append(secondId != null);
        sb.append(",\"opponentOnline\":").append(opponentOnline);
        sb.append(",\"opponentLastSeenMs\":").append(opponentLastSeenMs);
        sb.append(",\"drawOffer\":").append(drawOffer);
        sb.append(",\"undoOffer\":").append(undoOffer);
        sb.append(",\"undoRequestSeat\":").append(undoOffer);
        sb.append(",\"rematchVotes\":[").append(rematch[1]).append(',').append(rematch[2]).append(']');
        sb.append(",\"clock\":").append(clockJson());
        sb.append(",\"serverNow\":").append(System.currentTimeMillis());
    }

    protected String clockJson() {
        return clock == null ? "null" : clock.toJson(System.currentTimeMillis());
    }

    protected String phase() {
        if (!ready()) {
            return "waiting";
        }
        return status == PLAYING ? "playing" : "finished";
    }

    protected static int other(int seat) {
        return seat == 1 ? 2 : 1;
    }

    protected String seatName(int seat) {
        if ("xiangqi".equals(game)) {
            return seat == 1 ? "红方" : "黑方";
        }
        return seat == 1 ? "黑棋" : "白棋";
    }

    public abstract void move(String sessionId, int fr, int fc, int tr, int tc);

    public abstract void undo(String sessionId);

    public abstract String stateJson(String sessionId);

    protected abstract void resetMatch();
}