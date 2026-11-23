import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 棋类对战平台后端：五子棋 + 中国象棋。
 *
 * <p>仍然只用 JDK 自带的 HttpServer，零第三方依赖。服务端是唯一裁判：
 * 走法生成、胜负、将军、重复局面、计时全部在后端完成，前端只负责渲染。</p>
 */
public class GomokuServer {

    private static final String COOKIE_NAME = "gomoku_sid";
    private static final String ROOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long SESSION_TTL_MILLIS = TimeUnit.HOURS.toMillis(24);

    /** 每个浏览器会话各自的人机棋局。 */
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    /** 双人房间按房间码共享，五子棋和象棋共用一张表。 */
    private static final Map<String, Room> ROOMS = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        Path webRoot = Paths.get(System.getProperty("web.root", "web")).toAbsolutePath().normalize();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        // ------------------------- 平台与大厅 -------------------------
        server.createContext("/api/games", exchange ->
                guard(exchange, "GET", session -> gamesJson()));

        server.createContext("/api/session/state", exchange ->
                guard(exchange, "GET", GomokuServer::stateFor));

        server.createContext("/api/session/game", exchange ->
                guard(exchange, "POST", session -> {
                    String game = Json.getString(readBody(exchange), "game");
                    if (!"lobby".equals(game) && !"gomoku".equals(game) && !"xiangqi".equals(game)) {
                        throw new IllegalArgumentException("game 只能是 lobby / gomoku / xiangqi");
                    }
                    if (!game.equals(session.currentGame)) {
                        leaveCurrentRoom(session);
                    }
                    session.currentGame = game;
                    return stateFor(session);
                }));

        server.createContext("/api/gomoku/mode", exchange ->
                guard(exchange, "POST", session -> {
                    String mode = Json.getString(readBody(exchange), "mode");
                    if (!"solo".equals(mode) && !"pvp".equals(mode)) {
                        throw new IllegalArgumentException("mode 只能是 solo 或 pvp");
                    }
                    leaveCurrentRoom(session);
                    session.currentGame = "gomoku";
                    return "solo".equals(mode) ? session.gomokuSolo.toJson() : roomLobbyJson("gomoku");
                }));

        server.createContext("/api/xiangqi/mode", exchange ->
                guard(exchange, "POST", session -> {
                    String mode = Json.getString(readBody(exchange), "mode");
                    if (!"solo".equals(mode) && !"pvp".equals(mode)) {
                        throw new IllegalArgumentException("mode 只能是 solo 或 pvp");
                    }
                    leaveCurrentRoom(session);
                    session.currentGame = "xiangqi";
                    return "solo".equals(mode) ? session.xiangqiSolo.toJson() : roomLobbyJson("xiangqi");
                }));
        // ------------------------- 五子棋人机 -------------------------
        server.createContext("/api/gomoku/state", exchange ->
                guard(exchange, "GET", session -> session.gomokuSolo.toJson()));

        server.createContext("/api/gomoku/new", exchange ->
                guard(exchange, "POST", session -> {
                    int size = readBoardSize(readBody(exchange));
                    leaveCurrentRoom(session);
                    session.currentGame = "gomoku";
                    session.gomokuSolo.newGame(size);
                    return session.gomokuSolo.toJson();
                }));

        server.createContext("/api/gomoku/move", exchange ->
                guard(exchange, "POST", session -> {
                    int[] move = readGomokuMove(readBody(exchange));
                    session.gomokuSolo.playerMove(move[0], move[1]);
                    return session.gomokuSolo.toJson();
                }));

        server.createContext("/api/gomoku/undo", exchange ->
                guard(exchange, "POST", session -> {
                    session.gomokuSolo.undo();
                    return session.gomokuSolo.toJson();
                }));

        server.createContext("/api/gomoku/difficulty", exchange ->
                guard(exchange, "POST", session -> {
                    Integer level = Json.getInt(readBody(exchange), "level");
                    if (level == null) {
                        throw new IllegalArgumentException("缺少难度参数 level");
                    }
                    session.gomokuSolo.setDifficulty(level);
                    return session.gomokuSolo.toJson();
                }));

        // ------------------------- 象棋人机 -------------------------
        server.createContext("/api/xiangqi/state", exchange ->
                guard(exchange, "GET", session -> session.xiangqiSolo.toJson()));

        server.createContext("/api/xiangqi/new", exchange ->
                guard(exchange, "POST", session -> {
                    String body = readBody(exchange);
                    int side = intOr(body, "side", 0);
                    int level = intOr(body, "level", 2);
                    int minutes = intOr(body, "minutes", 10);
                    int increment = intOr(body, "increment", 5);
                    validateTime(minutes, increment);
                    leaveCurrentRoom(session);
                    session.currentGame = "xiangqi";
                    session.xiangqiSolo.newGame(side, level, minutes * 60_000L, increment * 1000L);
                    return session.xiangqiSolo.toJson();
                }));

        server.createContext("/api/xiangqi/move", exchange ->
                guard(exchange, "POST", session -> {
                    int[] move = readXiangqiMove(readBody(exchange));
                    session.xiangqiSolo.move(move[0], move[1], move[2], move[3]);
                    return session.xiangqiSolo.toJson();
                }));

        server.createContext("/api/xiangqi/undo", exchange ->
                guard(exchange, "POST", session -> {
                    session.xiangqiSolo.undo();
                    return session.xiangqiSolo.toJson();
                }));

        server.createContext("/api/xiangqi/difficulty", exchange ->
                guard(exchange, "POST", session -> {
                    Integer level = Json.getInt(readBody(exchange), "level");
                    if (level == null) {
                        throw new IllegalArgumentException("缺少难度参数 level");
                    }
                    session.xiangqiSolo.setDifficulty(level);
                    return session.xiangqiSolo.toJson();
                }));

        server.createContext("/api/xiangqi/legal", exchange ->
                guard(exchange, "POST", session -> {
                    String body = readBody(exchange);
                    Integer r = Json.getInt(body, "r");
                    Integer c = Json.getInt(body, "c");
                    if (r == null || c == null) {
                        throw new IllegalArgumentException("缺少坐标 r / c");
                    }
                    return session.xiangqiSolo.legalJson(r, c);
                }));

        server.createContext("/api/xiangqi/resign", exchange ->
                guard(exchange, "POST", session -> {
                    session.xiangqiSolo.resign();
                    return session.xiangqiSolo.toJson();
                }));

        // ------------------------- 联机房间 -------------------------
        server.createContext("/api/rooms/create", exchange ->
                guard(exchange, "POST", session -> {
                    String body = readBody(exchange);
                    String game = Json.getString(body, "game");
                    if (!"gomoku".equals(game) && !"xiangqi".equals(game)) {
                        throw new IllegalArgumentException("game 只能是 gomoku 或 xiangqi");
                    }
                    int requestedSeat = intOr(body, "seat", 0);
                    if (requestedSeat < 0 || requestedSeat > 2) {
                        throw new IllegalArgumentException("seat 只能是 0 / 1 / 2");
                    }
                    int seat = requestedSeat == 0
                            ? (ThreadLocalRandom.current().nextBoolean() ? 1 : 2)
                            : requestedSeat;
                    int minutes = intOr(body, "minutes", 0);
                    int increment = intOr(body, "increment", 0);
                    validateTime(minutes, increment);
                    long baseMs = minutes * 60_000L;
                    long incMs = increment * 1000L;

                    leaveCurrentRoom(session);

                    Room room;
                    String code;
                    do {
                        code = randomRoomCode();
                        room = "gomoku".equals(game)
                                ? new GomokuRoom(code, readBoardSize(body), baseMs, incMs)
                                : new XiangqiRoom(code, baseMs, incMs);
                    } while (ROOMS.putIfAbsent(code, room) != null);

                    room.join(session.id, seat);
                    session.roomCode = code;
                    session.currentGame = game;
                    return room.stateJson(session.id);
                }));

        server.createContext("/api/rooms/join", exchange ->
                guard(exchange, "POST", session -> {
                    String body = readBody(exchange);
                    String code = Json.getString(body, "room");
                    if (code == null || code.isBlank()) {
                        throw new IllegalArgumentException("请输入房间码");
                    }
                    code = code.trim().toUpperCase();
                    Room room = ROOMS.get(code);
                    if (room == null) {
                        throw new IllegalArgumentException("房间不存在或已经关闭");
                    }

                    if (!code.equals(session.roomCode)) {
                        leaveCurrentRoom(session);
                    }
                    if (!room.join(session.id, intOr(body, "seat", 0))) {
                        throw new IllegalArgumentException("房间已满");
                    }
                    session.roomCode = code;
                    session.currentGame = room.game();
                    return room.stateJson(session.id);
                }));

        server.createContext("/api/rooms/state", exchange ->
                guard(exchange, "GET", session -> requireRoom(session).stateJson(session.id)));

        server.createContext("/api/rooms/move", exchange ->
                guard(exchange, "POST", session -> {
                    Room room = requireRoom(session);
                    String body = readBody(exchange);
                    if ("gomoku".equals(room.game())) {
                        int[] move = readGomokuMove(body);
                        room.move(session.id, move[0], move[1], 0, 0);
                    } else {
                        int[] move = readXiangqiMove(body);
                        room.move(session.id, move[0], move[1], move[2], move[3]);
                    }
                    return room.stateJson(session.id);
                }));

        server.createContext("/api/rooms/undo", exchange ->
                guard(exchange, "POST", session -> {
                    Room room = requireRoom(session);
                    room.undo(session.id);
                    return room.stateJson(session.id);
                }));

        server.createContext("/api/rooms/resign", exchange ->
                guard(exchange, "POST", session -> {
                    Room room = requireRoom(session);
                    room.resign(session.id);
                    return room.stateJson(session.id);
                }));

        server.createContext("/api/rooms/draw", exchange ->
                guard(exchange, "POST", session -> {
                    Room room = requireRoom(session);
                    String action = Json.getString(readBody(exchange), "action");
                    if (action == null) {
                        throw new IllegalArgumentException("缺少 action");
                    }
                    room.draw(session.id, action);
                    return room.stateJson(session.id);
                }));

        server.createContext("/api/rooms/rematch", exchange ->
                guard(exchange, "POST", session -> {
                    Room room = requireRoom(session);
                    room.rematch(session.id);
                    return room.stateJson(session.id);
                }));

        server.createContext("/api/rooms/legal", exchange ->
                guard(exchange, "POST", session -> {
                    Room room = requireRoom(session);
                    if (!(room instanceof XiangqiRoom xiangqi)) {
                        throw new IllegalArgumentException("只有象棋需要请求合法落点");
                    }
                    String body = readBody(exchange);
                    Integer r = Json.getInt(body, "r");
                    Integer c = Json.getInt(body, "c");
                    if (r == null || c == null) {
                        throw new IllegalArgumentException("缺少坐标 r / c");
                    }
                    return xiangqi.legalJson(session.id, r, c);
                }));
        server.createContext("/api/rooms/leave", exchange ->
                guard(exchange, "POST", session -> {
                    leaveCurrentRoom(session);
                    return stateFor(session);
                }));

        // ------------------------- 静态页面 -------------------------
        server.createContext("/", new StaticHandler(webRoot));

        server.setExecutor(Executors.newFixedThreadPool(8));

        ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "game-ticker");
            thread.setDaemon(true);
            return thread;
        });
        ticker.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (Room room : ROOMS.values()) {
                try {
                    room.tick(now);
                } catch (RuntimeException ignored) {
                    // 单房间异常不能拖垮整个定时器
                }
            }
            for (Session session : SESSIONS.values()) {
                try {
                    session.xiangqiSolo.tick(now);
                } catch (RuntimeException ignored) {
                    // 同上
                }
            }
        }, 200, 200, TimeUnit.MILLISECONDS);

        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "gomoku-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        cleaner.scheduleAtFixedRate(GomokuServer::cleanup, 10, 10, TimeUnit.MINUTES);

        server.start();

        System.out.println("=========================================");
        System.out.println(" 棋类对战平台已启动：五子棋 + 中国象棋");
        System.out.println(" 浏览器打开： http://localhost:" + port);
        System.out.println(" 静态文件目录：" + webRoot);
        System.out.println(" 按 Ctrl + C 停止服务");
        System.out.println("=========================================");
        if (!Files.isDirectory(webRoot)) {
            System.out.println("警告：找不到 web 目录，页面会返回 404。请确认是在项目根目录下运行。");
        }
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {
                // 继续读环境变量
            }
        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
                // 忽略
            }
        }
        return 8080;
    }

    @FunctionalInterface
    private interface ApiCall {
        String run(Session session) throws Exception;
    }

    private static void guard(HttpExchange exchange, String method, ApiCall call) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
                respondJson(exchange, 405, "{\"error\":\"该接口只支持 " + method + " 请求\"}");
                return;
            }
            Session session = sessionOf(exchange);
            exchange.getResponseHeaders().add("Set-Cookie",
                    COOKIE_NAME + "=" + session.id + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + (30 * 24 * 60 * 60));
            respondJson(exchange, 200, call.run(session));
        } catch (IllegalArgumentException e) {
            respondJson(exchange, 400, "{\"error\":\"" + Json.escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            e.printStackTrace();
            respondJson(exchange, 500, "{\"error\":\"服务器内部错误：" + Json.escape(String.valueOf(e.getMessage())) + "\"}");
        } finally {
            exchange.close();
        }
    }

    /** 当前会话应该看到的页面状态：所在房间 &gt; 当前游戏人机局 &gt; 大厅。 */
    private static String stateFor(Session session) {
        if (session.roomCode != null) {
            Room room = ROOMS.get(session.roomCode);
            if (room != null) {
                return room.stateJson(session.id);
            }
            session.roomCode = null;
        }
        if ("gomoku".equals(session.currentGame)) {
            return session.gomokuSolo.toJson();
        }
        if ("xiangqi".equals(session.currentGame)) {
            return session.xiangqiSolo.toJson();
        }
        return lobbyJson();
    }

    private static String lobbyJson() {
        return "{\"game\":\"lobby\",\"mode\":\"lobby\",\"phase\":\"lobby\","
                + "\"message\":\"请选择要玩的游戏\",\"room\":null}";
    }

    private static String gamesJson() {
        return "{\"games\":["
                + "{\"id\":\"gomoku\",\"name\":\"五子棋\",\"players\":\"1-2 人\","
                + "\"tagline\":\"先连成五子者获胜，支持人机与联机\"},"
                + "{\"id\":\"xiangqi\",\"name\":\"中国象棋\",\"players\":\"1-2 人\","
                + "\"tagline\":\"完整走子规则、将军判定、计时与联机对战\"}"
                + "]}";
    }

    /** 进入双人模式但还没建房时的空棋盘状态。 */
    private static String roomLobbyJson(String game) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"game\":\"").append(game).append('"');
        sb.append(",\"mode\":\"pvp\",\"phase\":\"lobby\",\"room\":null");
        sb.append(",\"myColor\":0,\"mySeat\":0,\"firstPresent\":false,\"secondPresent\":false");
        sb.append(",\"status\":\"playing\",\"turn\":0,\"moves\":0,\"lastMove\":null,\"winLine\":null");
        sb.append(",\"clock\":null,\"drawOffer\":0,\"rematchVotes\":[false,false]");
        sb.append(",\"message\":\"创建或加入一个房间\"");
        if ("xiangqi".equals(game)) {
            sb.append(",\"rows\":10,\"cols\":9,\"check\":false,\"endReason\":\"\",\"board\":[");
            for (int r = 0; r < XiangqiBoard.ROWS; r++) {
                if (r > 0) {
                    sb.append(',');
                }
                sb.append('[');
                for (int c = 0; c < XiangqiBoard.COLS; c++) {
                    if (c > 0) {
                        sb.append(',');
                    }
                    sb.append(0);
                }
                sb.append(']');
            }
            sb.append(']');
        } else {
            sb.append(",\"size\":15,\"board\":[");
            for (int r = 0; r < 15; r++) {
                if (r > 0) {
                    sb.append(',');
                }
                sb.append('[');
                for (int c = 0; c < 15; c++) {
                    if (c > 0) {
                        sb.append(',');
                    }
                    sb.append(0);
                }
                sb.append(']');
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }
    private static int readBoardSize(String body) {
        Integer size = Json.getInt(body, "size");
        int boardSize = (size == null) ? 15 : size;
        if (boardSize != 9 && boardSize != 13 && boardSize != 15) {
            throw new IllegalArgumentException("棋盘尺寸只支持 9、13、15");
        }
        return boardSize;
    }

    private static int[] readGomokuMove(String body) {
        Integer r = Json.getInt(body, "r");
        Integer c = Json.getInt(body, "c");
        if (r == null || c == null) {
            throw new IllegalArgumentException("缺少落子坐标 r / c");
        }
        return new int[]{r, c};
    }

    private static int[] readXiangqiMove(String body) {
        Integer fr = Json.getInt(body, "fr");
        Integer fc = Json.getInt(body, "fc");
        Integer tr = Json.getInt(body, "tr");
        Integer tc = Json.getInt(body, "tc");
        if (fr == null || fc == null || tr == null || tc == null) {
            throw new IllegalArgumentException("缺少走子坐标 fr / fc / tr / tc");
        }
        return new int[]{fr, fc, tr, tc};
    }

    private static int intOr(String body, String key, int fallback) {
        Integer value = Json.getInt(body, key);
        return value == null ? fallback : value;
    }

    private static void validateTime(int minutes, int increment) {
        if (minutes < 0 || minutes > 60) {
            throw new IllegalArgumentException("时限只支持 0~60 分钟（0 = 不限时）");
        }
        if (increment < 0 || increment > 60) {
            throw new IllegalArgumentException("加秒只支持 0~60 秒");
        }
    }

    private static Room requireRoom(Session session) {
        Room room = session.roomCode == null ? null : ROOMS.get(session.roomCode);
        if (room == null) {
            session.roomCode = null;
            throw new IllegalArgumentException("你还没有加入房间");
        }
        return room;
    }

    private static void leaveCurrentRoom(Session session) {
        String code = session.roomCode;
        session.roomCode = null;
        if (code == null) {
            return;
        }
        Room room = ROOMS.get(code);
        if (room != null) {
            room.leave(session.id);
            if (room.isEmpty()) {
                ROOMS.remove(code, room);
            }
        }
    }

    private static String randomRoomCode() {
        StringBuilder code = new StringBuilder(6);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            code.append(ROOM_ALPHABET.charAt(random.nextInt(ROOM_ALPHABET.length())));
        }
        return code.toString();
    }

    private static void cleanup() {
        long cutoff = System.currentTimeMillis() - SESSION_TTL_MILLIS;
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().lastSeen < cutoff);
        ROOMS.entrySet().removeIf(entry -> entry.getValue().idleMillis() > SESSION_TTL_MILLIS);
    }

    private static void respondJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        addCors(exchange);
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 从 Cookie 中找回会话；没有 Cookie 就发一个新的。
     * Cookie 名字沿用 gomoku_sid，保证老用户升级后不会丢失身份。
     */
    private static Session sessionOf(HttpExchange exchange) {
        String id = cookieValue(exchange, COOKIE_NAME);
        if (id == null || !id.matches("[A-Za-z0-9-]{8,64}")) {
            id = UUID.randomUUID().toString();
        }
        Session session = SESSIONS.computeIfAbsent(id, Session::new);
        session.lastSeen = System.currentTimeMillis();
        return session;
    }

    private static String cookieValue(HttpExchange exchange, String name) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null || header.isBlank()) {
            return null;
        }
        for (String part : header.split(";")) {
            String item = part.trim();
            int equals = item.indexOf('=');
            if (equals > 0 && item.substring(0, equals).equals(name)) {
                return item.substring(equals + 1);
            }
        }
        return null;
    }

    /** 一个浏览器身份对应的数据。 */
    private static final class Session {
        private final String id;
        private final Game gomokuSolo = new Game(15);
        private final XiangqiSolo xiangqiSolo = new XiangqiSolo();
        private volatile String currentGame = "lobby";
        private volatile String roomCode;
        private volatile long lastSeen = System.currentTimeMillis();

        private Session(String id) {
            this.id = id;
        }
    }

    private static class StaticHandler implements HttpHandler {

        private final Path root;

        StaticHandler(Path root) {
            this.root = root;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.isEmpty() || path.equals("/")) {
                    path = "/index.html";
                }

                Path file = root.resolve(path.substring(1)).normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                    byte[] body = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(404, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                    return;
                }

                byte[] body = Files.readAllBytes(file);
                exchange.getResponseHeaders().set("Content-Type", contentType(file));
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } finally {
                exchange.close();
            }
        }

        private String contentType(Path file) {
            String name = file.getFileName().toString().toLowerCase();
            if (name.endsWith(".html")) return "text/html; charset=utf-8";
            if (name.endsWith(".css"))  return "text/css; charset=utf-8";
            if (name.endsWith(".js"))   return "application/javascript; charset=utf-8";
            if (name.endsWith(".json")) return "application/json; charset=utf-8";
            if (name.endsWith(".png"))  return "image/png";
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
            if (name.endsWith(".svg"))  return "image/svg+xml";
            if (name.endsWith(".ico"))  return "image/x-icon";
            return "application/octet-stream";
        }
    }
}