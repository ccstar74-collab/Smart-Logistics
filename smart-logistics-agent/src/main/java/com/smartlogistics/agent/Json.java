package com.smartlogistics.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON codec used to keep this Java 8 MVP dependency-free. */
final class Json {
    private Json() {}

    static Object parse(String source) {
        Parser parser = new Parser(source == null ? "" : source);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("JSON 末尾存在多余内容");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(String source) {
        Object value = parse(source);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("请求体必须是 JSON 对象");
        }
        return (Map<String, Object>) value;
    }

    static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            quote((String) value, out);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Object item : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
                if (!first) out.append(',');
                quote(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
                first = false;
            }
            out.append('}');
        } else if (value instanceof Iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) out.append(',');
                write(item, out);
                first = false;
            }
            out.append(']');
        } else {
            quote(String.valueOf(value), out);
        }
    }

    private static void quote(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String source;
        private int index;

        Parser(String source) { this.source = source; }
        boolean atEnd() { return index >= source.length(); }
        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(source.charAt(index))) index++;
        }

        Object readValue() {
            skipWhitespace();
            if (atEnd()) throw error("JSON 不能为空");
            char c = source.charAt(index);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't') return literal("true", Boolean.TRUE);
            if (c == 'f') return literal("false", Boolean.FALSE);
            if (c == 'n') return literal("null", null);
            if (c == '-' || Character.isDigit(c)) return readNumber();
            throw error("无法识别的 JSON 值");
        }

        private Map<String, Object> readObject() {
            LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
            index++;
            skipWhitespace();
            if (consume('}')) return result;
            while (true) {
                skipWhitespace();
                if (atEnd() || source.charAt(index) != '"') throw error("对象键必须是字符串");
                String key = readString();
                skipWhitespace();
                expect(':');
                result.put(key, readValue());
                skipWhitespace();
                if (consume('}')) return result;
                expect(',');
            }
        }

        private List<Object> readArray() {
            ArrayList<Object> result = new ArrayList<Object>();
            index++;
            skipWhitespace();
            if (consume(']')) return result;
            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (consume(']')) return result;
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!atEnd()) {
                char c = source.charAt(index++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (atEnd()) throw error("字符串转义不完整");
                char escape = source.charAt(index++);
                switch (escape) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (index + 4 > source.length()) throw error("Unicode 转义不完整");
                        out.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                        break;
                    default: throw error("非法字符串转义");
                }
            }
            throw error("字符串没有结束引号");
        }

        private Object readNumber() {
            int start = index;
            if (source.charAt(index) == '-') index++;
            while (!atEnd() && Character.isDigit(source.charAt(index))) index++;
            if (!atEnd() && source.charAt(index) == '.') {
                index++;
                while (!atEnd() && Character.isDigit(source.charAt(index))) index++;
            }
            if (!atEnd() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                index++;
                if (!atEnd() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
                while (!atEnd() && Character.isDigit(source.charAt(index))) index++;
            }
            String number = source.substring(start, index);
            try {
                return number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0
                        ? Double.valueOf(number) : Long.valueOf(number);
            } catch (NumberFormatException e) {
                throw error("非法数字");
            }
        }

        private Object literal(String literal, Object value) {
            if (!source.startsWith(literal, index)) throw error("非法 JSON 字面量");
            index += literal.length();
            return value;
        }

        private boolean consume(char expected) {
            if (!atEnd() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) throw error("缺少字符 " + expected);
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + "（位置 " + index + "）");
        }
    }
}

