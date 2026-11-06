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
 * 网页五子棋的后端服务。
 *
 * <p>只用 JDK 自带的 com.sun.net.httpserver.HttpServer，不依赖任何第三方库，
 * 所以不需要 Maven / Gradle，用 javac 编译后直接 java 运行即可。</p>
 *
 * <p>它做三件事：</p>
 * <ul>
 *   <li>把 web 目录下的 HTML / CSS / JS 发给浏览器；</li>
 *   <li>用 Cookie 区分浏览器，让每个人的人机局互不干扰；</li>
 *   <li>提供房间接口，让两名玩家通过房间码下同一盘棋。</li>
 * </ul>
 */
public class GomokuServer {

    private static final String COOKIE_NAME = "gomoku_sid";
    private static final String ROOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long SESSION_TTL_MILLIS = TimeUnit.HOURS.toMillis(24);

    /** 每个浏览器会话各自有一局人机棋。 */
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    /** 双人房间按房间码共享。 */
    private static final Map<String, PvpRoom> ROOMS = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        Path webRoot = Paths.get(System.getProperty("web.root", "web")).toAbsolutePath().normalize();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        // ------------------------- 会话与模式 -------------------------
        server.createContext("/api/state", exchange ->
                guard(exchange, "GET", GomokuServer::stateFor));

        server.createContext("/api/mode", exchange ->
                guard(exchange, "POST", session -> {
                    String mode = Json.getString(readBody(exchange), "mode");
                    if (!"solo".equals(mode) && !"pvp".equals(mode)) {
                        throw new IllegalArgumentException("mode 只能是 solo 或 pvp");
                    }
                    if ("solo".equals(mode)) {
                        leaveCurrentRoom(session);
                    }
                    session.mode = mode;
                    return stateFor(session);
                }));

        // ------------------------- 人机模式（每个会话独立） -------------------------
        server.createContext("/api/new", exchange ->
                guard(exchange, "POST", session -> {
                    int boardSize = readBoardSize(readBody(exchange));
                    session.mode = "solo";
                    session.solo.newGame(boardSize);
                    return session.solo.toJson();
                }));

        server.createContext("/api/move", exchange ->
                guard(exchange, "POST", session -> {
                    String body = readBody(exchange);
                    int[] move = readMove(body);
                    session.solo.playerMove(move[0], move[1]);
                    return session.solo.toJson();
                }));

        server.createContext("/api/undo", exchange ->
                guard(exchange, "POST", session -> {
                    session.solo.undo();
                    return session.solo.toJson();
                }));

        server.createContext("/api/difficulty", exchange ->
                guard(exchange, "POST", session -> {
                    Integer level = Json.getInt(readBody(exchange), "level");
                    if (level == null) {
                        throw new IllegalArgumentException("缺少难度参数 level");
                    }
                    session.solo.setDifficulty(level);
                    return session.solo.toJson();
                }));

        // ------------------------- 双人模式（房间码） -------------------------
        server.createContext("/api/pvp/create", exchange ->
                guard(exchange, "POST", session -> {
                    int boardSize = readBoardSize(readBody(exchange));
                    leaveCurrentRoom(session);

                    PvpRoom room;
                    String code;
                    do {
                        code = randomRoomCode();
                        room = new PvpRoom(code, boardSize);
                    } while (ROOMS.putIfAbsent(code, room) != null);

                    room.join(session.id);
                    session.roomCode = code;
                    session.mode = "pvp";
                    return room.toJson(session.id);
                }));

        server.createContext("/api/pvp/join", exchange ->
                guard(exchange, "POST", session -> {
                    String code = Json.getString(readBody(exchange), "room");
                    if (code == null || code.isBlank()) {
                        throw new IllegalArgumentException("请输入房间码");
                    }
                    code = code.trim().toUpperCase();
                    PvpRoom room = ROOMS.get(code);
                    if (room == null) {
                        throw new IllegalArgumentException("房间不存在或已经关闭");
                    }

                    if (!code.equals(session.roomCode)) {
                        leaveCurrentRoom(session);
                    }
                    if (!room.join(session.id)) {
                        throw new IllegalArgumentException("房间已满");
                    }
                    session.roomCode = code;
                    session.mode = "pvp";
                    return room.toJson(session.id);
                }));

        server.createContext("/api/pvp/move", exchange ->
                guard(exchange, "POST", session -> {
                    PvpRoom room = requireRoom(session);
                    int[] move = readMove(readBody(exchange));
                    room.move(session.id, move[0], move[1]);
                    return room.toJson(session.id);
                }));

        server.createContext("/api/pvp/undo", exchange ->
                guard(exchange, "POST", session -> {
                    PvpRoom room = requireRoom(session);
                    room.undo(session.id);
                    return room.toJson(session.id);
                }));

        server.createContext("/api/pvp/new", exchange ->
                guard(exchange, "POST", session -> {
                    PvpRoom room = requireRoom(session);
                    room.newGame();
                    return room.toJson(session.id);
                }));

        server.createContext("/api/pvp/leave", exchange ->
                guard(exchange, "POST", session -> {
                    leaveCurrentRoom(session);
                    session.mode = "pvp";
                    return lobbyJson();
                }));

        // ------------------------- 静态页面 -------------------------
        // 注意：HttpServer 会按最长前缀匹配，所以 /api/* 不会被这里抢走
        server.createContext("/", new StaticHandler(webRoot));

        server.setExecutor(Executors.newFixedThreadPool(8));

        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "gomoku-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        cleaner.scheduleAtFixedRate(GomokuServer::cleanup, 10, 10, TimeUnit.MINUTES);

        server.start();

        System.out.println("=========================================");
        System.out.println(" 网页五子棋已启动");
        System.out.println(" 浏览器打开： http://localhost:" + port);
        System.out.println(" 静态文件目录：" + webRoot);
        System.out.println(" 按 Ctrl + C 停止服务");
        System.out.println("=========================================");
        if (!Files.isDirectory(webRoot)) {
            System.out.println("警告：找不到 web 目录，页面会返回 404。请确认是在项目根目录下运行。");
        }
    }

    /**
     * 端口来源的优先级：命令行参数 &gt; 环境变量 PORT &gt; 默认 8080。
     * 云平台（Render、Railway 等）通常通过 PORT 环境变量指定端口。
     */
    private static int resolvePort(String[] args) {
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {
                // 参数写得不对就继续往下找
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

    /** 一个可能抛异常的业务处理动作，用来配合 guard 统一处理错误。 */
    @FunctionalInterface
    private interface ApiCall {
        String run(Session session) throws Exception;
    }

    /**
     * 统一入口：识别浏览器会话、处理跨域、校验请求方法、把异常转成 JSON。
     * Cookie 让同一个浏览器的多次请求找到同一局人机棋。
     */
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
            // 参数不合法：属于客户端的问题，返回 400
            respondJson(exchange, 400, "{\"error\":\"" + Json.escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            e.printStackTrace();
            respondJson(exchange, 500, "{\"error\":\"服务器内部错误：" + Json.escape(String.valueOf(e.getMessage())) + "\"}");
        } finally {
            exchange.close();
        }
    }

    /** 当前会话应该看到的页面状态：人机局，或它所在的双人房间。 */
    private static String stateFor(Session session) {
        if (!"pvp".equals(session.mode)) {
            return session.solo.toJson();
        }
        PvpRoom room = session.roomCode == null ? null : ROOMS.get(session.roomCode);
        if (room == null) {
            session.roomCode = null;
            return lobbyJson();
        }
        return room.toJson(session.id);
    }

    private static String lobbyJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"mode\":\"pvp\"");
        sb.append(",\"phase\":\"lobby\"");
        sb.append(",\"room\":null");
        sb.append(",\"size\":15");
        sb.append(",\"board\":");
        appendEmptyBoard(sb, 15);
        sb.append(",\"status\":\"playing\"");
        sb.append(",\"turn\":0");
        sb.append(",\"myColor\":0");
        sb.append(",\"blackPresent\":false");
        sb.append(",\"whitePresent\":false");
        sb.append(",\"moves\":0");
        sb.append(",\"lastMove\":null");
        sb.append(",\"message\":\"创建或加入一个房间\"");
        sb.append(",\"winLine\":null");
        sb.append('}');
        return sb.toString();
    }

    private static void appendEmptyBoard(StringBuilder sb, int size) {
        sb.append('[');
        for (int r = 0; r < size; r++) {
            if (r > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int c = 0; c < size; c++) {
                if (c > 0) {
                    sb.append(',');
                }
                sb.append(Board.EMPTY);
            }
            sb.append(']');
        }
        sb.append(']');
    }

    private static int readBoardSize(String body) {
        Integer size = Json.getInt(body, "size");
        int boardSize = (size == null) ? 15 : size;
        if (boardSize != 9 && boardSize != 13 && boardSize != 15) {
            throw new IllegalArgumentException("棋盘尺寸只支持 9、13、15");
        }
        return boardSize;
    }

    private static int[] readMove(String body) {
        Integer r = Json.getInt(body, "r");
        Integer c = Json.getInt(body, "c");
        if (r == null || c == null) {
            throw new IllegalArgumentException("缺少落子坐标 r / c");
        }
        return new int[]{r, c};
    }

    private static PvpRoom requireRoom(Session session) {
        PvpRoom room = session.roomCode == null ? null : ROOMS.get(session.roomCode);
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
        PvpRoom room = ROOMS.get(code);
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

    /**
     * 从 Cookie 中找回会话；没有 Cookie 就发一个新的。
     * Cookie 只保存随机 ID，真正的棋局在服务器的 map 里。
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

    private static void cleanup() {
        long cutoff = System.currentTimeMillis() - SESSION_TTL_MILLIS;
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().lastSeen < cutoff);
        ROOMS.entrySet().removeIf(entry -> entry.getValue().idleMillis() > SESSION_TTL_MILLIS);
    }

    private static void respondJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
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

    /** 一个浏览器身份对应的数据。solo 是它的私人棋局。 */
    private static final class Session {
        private final String id;
        private final Game solo = new Game(15);
        private volatile String mode = "solo";
        private volatile String roomCode;
        private volatile long lastSeen = System.currentTimeMillis();

        private Session(String id) {
            this.id = id;
        }
    }

    /** 负责把 web 目录下的静态文件发送给浏览器。 */
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
                // 防止有人用 "../" 读到 web 目录之外的文件
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
