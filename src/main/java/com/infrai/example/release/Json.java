package com.infrai.example.release;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON reader/writer: enough to build a request body and walk the response envelope. */
public final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWhitespace();
        Object value = p.readValue();
        return value;
    }

    /** Reads a nested path, e.g. get(env, "data", "job_id"); returns null when any hop is absent. */
    @SuppressWarnings("unchecked")
    public static Object get(Object node, String... path) {
        Object current = node;
        for (String key : path) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(key);
        }
        return current;
    }

    public static String text(Object node, String... path) {
        Object value = get(node, path);
        return value == null ? null : String.valueOf(value);
    }

    public static String object(Map<String, Object> fields) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(string(entry.getKey())).append(':').append(write(entry.getValue()));
        }
        return out.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static String write(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map) {
            return object((Map<String, Object>) value);
        }
        if (value instanceof List) {
            StringBuilder out = new StringBuilder("[");
            List<Object> items = (List<Object>) value;
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(write(items.get(i)));
            }
            return out.append(']').toString();
        }
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        return string(String.valueOf(value));
    }

    public static String string(String raw) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    private Object readValue() {
        char c = src.charAt(pos);
        if (c == '{') {
            return readObject();
        }
        if (c == '[') {
            return readArray();
        }
        if (c == '"') {
            return readString();
        }
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        return readNumber();
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        pos++;
        skipWhitespace();
        if (src.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            pos++; // ':'
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = src.charAt(pos++);
            if (c == '}') {
                return map;
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<Object>();
        pos++;
        skipWhitespace();
        if (src.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = src.charAt(pos++);
            if (c == ']') {
                return list;
            }
        }
    }

    private String readString() {
        StringBuilder out = new StringBuilder();
        pos++;
        while (true) {
            char c = src.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                char esc = src.charAt(pos++);
                switch (esc) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'u':
                        out.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default: out.append(esc);
                }
            } else {
                out.append(c);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        while (pos < src.length() && "-+.eE0123456789".indexOf(src.charAt(pos)) >= 0) {
            pos++;
        }
        String raw = src.substring(start, pos);
        if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
            return Double.valueOf(raw);
        }
        return Long.valueOf(raw);
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }
}
