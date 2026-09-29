import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 JSON 工具。
 *
 * <p>本案例不想引入第三方库（比如 Jackson、Gson），所以只写了两个小方法：
 * 一个把字符串安全地拼进 JSON，一个从请求体里取字段。
 * 它只够应付本案例这种"扁平的小 JSON"，正式项目请使用成熟库。</p>
 */
public final class Json {

    private Json() {
    }

    /** 转义 JSON 字符串里的特殊字符。中文不需要转义，可以直接保留。 */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** 从 JSON 文本里读取一个整数字段，读不到返回 null。 */
    public static Integer getInt(String json, String key) {
        if (json == null) {
            return null;
        }
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)")
                .matcher(json);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** 从 JSON 文本里读取一个字符串字段，读不到返回 null。 */
    public static String getString(String json, String key) {
        if (json == null) {
            return null;
        }
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}
