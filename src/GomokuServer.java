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
import java.util.concurrent.Executors;

/**
 * 网页五子棋的后端服务。
 *
 * <p>只用 JDK 自带的 com.sun.net.httpserver.HttpServer，不依赖任何第三方库，
 * 所以不需要 Maven / Gradle，用 javac 编译后直接 java 运行即可。</p>
 *
 * <p>它做两件事：</p>
 * <ul>
 *   <li>把 web 目录下的 HTML / CSS / JS 发给浏览器；</li>
 *   <li>提供 /api/* 接口，负责落子、悔棋、难度切换，并返回最新棋盘。</li>
 * </ul>
 */
public class GomokuServer {

    /** 全局只有一局棋（这是个演示案例，够用了） */
    private static final Game GAME = new Game(15);

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        Path webRoot = Paths.get(System.getProperty("web.root", "web")).toAbsolutePath().normalize();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        // ------------------------- 接口部分 -------------------------
        server.createContext("/api/state", exchange ->
                guard(exchange, "GET", GAME::toJson));

        server.createContext("/api/new", exchange ->
                guard(exchange, "POST", () -> {
                    String body = readBody(exchange);
                    Integer size = Json.getInt(body, "size");
                    int boardSize = (size == null) ? 15 : size;
                    if (boardSize != 9 && boardSize != 13 && boardSize != 15) {
                        throw new IllegalArgumentException("棋盘尺寸只支持 9、13、15");
                    }
                    GAME.newGame(boardSize);
                    return GAME.toJson();
                }));

        server.createContext("/api/move", exchange ->
                guard(exchange, "POST", () -> {
                    String body = readBody(exchange);
                    Integer r = Json.getInt(body, "r");
                    Integer c = Json.getInt(body, "c");
                    if (r == null || c == null) {
                        throw new IllegalArgumentException("缺少落子坐标 r / c");
                    }
                    GAME.playerMove(r, c);
                    return GAME.toJson();
                }));

        server.createContext("/api/undo", exchange ->
                guard(exchange, "POST", () -> {
                    GAME.undo();
                    return GAME.toJson();
                }));

        server.createContext("/api/difficulty", exchange ->
                guard(exchange, "POST", () -> {
                    String body = readBody(exchange);
                    Integer level = Json.getInt(body, "level");
                    if (level == null) {
                        throw new IllegalArgumentException("缺少难度参数 level");
                    }
                    GAME.setDifficulty(level);
                    return GAME.toJson();
                }));

        // ------------------------- 静态页面 -------------------------
        // 注意：HttpServer 会按最长前缀匹配，所以 /api/* 不会被这里抢走
        server.createContext("/", new StaticHandler(webRoot));

        server.setExecutor(Executors.newFixedThreadPool(8));
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
        String run() throws Exception;
    }

    /**
     * 统一入口：处理跨域、校验请求方法、把异常转成 JSON 错误响应。
     * 这样每个接口只需要关心自己的业务逻辑。
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
            respondJson(exchange, 200, call.run());
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
