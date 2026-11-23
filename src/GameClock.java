/**
 * 服务端权威对局时钟。
 *
 * <p>只给"轮到走棋"的一方计时。走子时扣时并加秒；状态接口返回的是
 * 当前时刻的剩余毫秒，前端只负责本地倒数插值。</p>
 */
public class GameClock {

    private final long baseMs;
    private final long incrementMs;

    private long firstMs;
    private long secondMs;
    private long turnStartedAt;
    private int turn = 1;
    private boolean running;

    public GameClock(long baseMs, long incrementMs) {
        this.baseMs = Math.max(0, baseMs);
        this.incrementMs = Math.max(0, incrementMs);
        this.firstMs = this.baseMs;
        this.secondMs = this.baseMs;
    }

    public synchronized void start(int firstTurn, long now) {
        firstMs = baseMs;
        secondMs = baseMs;
        turn = firstTurn;
        turnStartedAt = now;
        running = baseMs > 0;
    }

    /** 只重置当前回合的起算点，不退还已经用掉的时间。 */
    public synchronized void resume(int nextTurn, long now) {
        if (baseMs <= 0) {
            return;
        }
        turn = nextTurn;
        turnStartedAt = now;
        running = true;
    }

    public synchronized void onMove(int seat, long now) {
        if (!running || baseMs <= 0) {
            return;
        }
        settle(now);
        if (seat == 1) {
            firstMs += incrementMs;
        } else {
            secondMs += incrementMs;
        }
        turn = seat == 1 ? 2 : 1;
        turnStartedAt = now;
    }

    /** 暂停：先把已用时间结算掉。 */
    public synchronized void pause(long now) {
        if (!running) {
            return;
        }
        settle(now);
        running = false;
    }

    private void settle(long now) {
        long elapsed = Math.max(0, now - turnStartedAt);
        if (turn == 1) {
            firstMs -= elapsed;
        } else {
            secondMs -= elapsed;
        }
    }

    /** 返回超时的一方（1/2），没人超时返回 0。 */
    public synchronized int timeoutSeat(long now) {
        if (!running || baseMs <= 0) {
            return 0;
        }
        long elapsed = Math.max(0, now - turnStartedAt);
        long remaining = turn == 1 ? firstMs - elapsed : secondMs - elapsed;
        return remaining <= 0 ? turn : 0;
    }

    public synchronized int turn() {
        return turn;
    }

    public synchronized boolean running() {
        return running;
    }

    public synchronized String toJson(long now) {
        long first = firstMs;
        long second = secondMs;
        if (running) {
            long elapsed = Math.max(0, now - turnStartedAt);
            if (turn == 1) {
                first = Math.max(0, first - elapsed);
            } else {
                second = Math.max(0, second - elapsed);
            }
        }
        return "{\"firstMs\":" + first
                + ",\"secondMs\":" + second
                + ",\"incrementMs\":" + incrementMs
                + ",\"turn\":" + turn
                + ",\"running\":" + running
                + ",\"serverNow\":" + now + "}";
    }
}