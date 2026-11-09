package org.jeecg.modules.packagemanagement.application.legacy;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XOR-decodes client risk payload using app secret bytes.
 * <p>
 * 推荐：{@code Base64( XOR( UTF-8 明文 ) )} 写入 UA（或空格后尾部）。<br>
 * 兼容：部分客户端误把 <b>十六进制字串</b>（仅 {@code 0-9a-fA-F}、长度为偶）当作密文写入，此时若再按 Base64 解会得到乱码；
 * 本类会先尝试 Base64，若结果不像合法载荷再尝试按 hex 解码后再 XOR。
 * <p>
 * 明文为 URL 查询串（{@code t=...&f1=...&b=...&abi=...&x=...}）或含相同键的 UTF-8 JSON。
 */
public final class ClientRiskPayloadCodec {

    private ClientRiskPayloadCodec() {
    }

    public static String defaultXorSecret() {
        return "lXaKEclwgjBZVOSeQJKlUMLnVzvczqXk";
    }

    /**
     * @param payloadBase64 Base64-encoded XOR ciphertext; may be null/blank
     * @param secretUtf8      key material (repeated); uses default if blank
     * @return parsed JSON (possibly empty); null if payload missing or decode fails
     */
    public static JSONObject tryDecodeJson(String payloadBase64, String secretUtf8) {
        if (StringUtils.isBlank(payloadBase64)) {
            return null;
        }
        String secret = StringUtils.isNotBlank(secretUtf8) ? secretUtf8 : defaultXorSecret();
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length == 0) {
            key = defaultXorSecret().getBytes(StandardCharsets.UTF_8);
        }
        String text = xorPayloadToPlainUtf8(payloadBase64, key);
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Decodes XOR+Base64 payload into risk parameters {@code t,f1,b,abi,x}.
     * Accepts plaintext query string or JSON object after XOR.
     */
    public static Map<String, String> tryDecodeRiskParams(String payloadBase64, String secretUtf8) {
        Map<String, String> out = new LinkedHashMap<>();
        if (StringUtils.isBlank(payloadBase64)) {
            return out;
        }
        String secret = StringUtils.isNotBlank(secretUtf8) ? secretUtf8 : defaultXorSecret();
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length == 0) {
            key = defaultXorSecret().getBytes(StandardCharsets.UTF_8);
        }
        String text = xorPayloadToPlainUtf8(payloadBase64, key);
        if (text == null || text.isEmpty()) {
            return out;
        }
        if (text.startsWith("{")) {
            try {
                JSONObject jo = JSON.parseObject(text);
                putIfPresent(out, "t", jo.getString("t"));
                putIfPresent(out, "f1", jo.getString("f1"));
                putIfPresent(out, "b", jo.getString("b"));
                putIfPresent(out, "abi", jo.getString("abi"));
                putIfPresent(out, "x", jo.getString("x"));
            } catch (Exception ignored) {
                // leave empty
            }
            return out;
        }
        return parseQueryString(text);
    }

    /**
     * 先 Base64 密文 XOR；若解出内容不像风险载荷再尝试 hex 密文 XOR。
     */
    private static String xorPayloadToPlainUtf8(String payload, byte[] key) {
        String fromB64 = xorBase64ToUtf8String(payload, key);
        if (looksLikeRiskPlaintext(fromB64)) {
            return fromB64;
        }
        String fromHex = xorHexToUtf8String(payload, key);
        if (looksLikeRiskPlaintext(fromHex)) {
            return fromHex;
        }
        if (StringUtils.isNotBlank(fromB64)) {
            return fromB64;
        }
        return fromHex;
    }

    /** 是否为查询串/JSON 形态的载荷，用于区分「Base64 误把 hex 当 Base64 解出的乱码」 */
    private static boolean looksLikeRiskPlaintext(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String t = text.trim();
        if (t.startsWith("{")) {
            return t.contains("\"t\"") || t.contains("\"f1\"");
        }
        return t.contains("t=") || t.contains("f1=");
    }

    private static void putIfPresent(Map<String, String> out, String k, String v) {
        if (v != null && !v.isEmpty()) {
            out.put(k, v);
        }
    }

    private static Map<String, String> parseQueryString(String qs) {
        Map<String, String> m = new LinkedHashMap<>();
        if (StringUtils.isBlank(qs)) {
            return m;
        }
        for (String part : qs.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int i = part.indexOf('=');
            String k;
            String v;
            if (i < 0) {
                k = part;
                v = "";
            } else {
                k = part.substring(0, i);
                v = i < part.length() - 1 ? part.substring(i + 1) : "";
            }
            try {
                v = URLDecoder.decode(v, StandardCharsets.UTF_8.name());
            } catch (Exception ignored) {
                // keep raw
            }
            if (!k.isEmpty()) {
                m.put(k, v);
            }
        }
        return m;
    }

    private static String xorBase64ToUtf8String(String payloadBase64, byte[] key) {
        try {
            byte[] raw = decodeBase64Flexible(payloadBase64);
            byte[] plain = new byte[raw.length];
            for (int i = 0; i < raw.length; i++) {
                plain[i] = (byte) (raw[i] ^ key[i % key.length]);
            }
            return new String(plain, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 依次尝试标准 Base64、URL 安全 Base64（部分客户端使用）、MIME（可忽略空白）。
     * 载荷整段或「空格后尾巴」必须是其中一种可解码形式。
     */
    private static byte[] decodeBase64Flexible(String payloadBase64) {
        String s = StringUtils.trimToEmpty(payloadBase64);
        if (s.isEmpty()) {
            throw new IllegalArgumentException("empty");
        }
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException ignored) {
            // continue
        }
        try {
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException ignored) {
            // continue
        }
        return Base64.getMimeDecoder().decode(s);
    }

    /**
     * 仅当 {@code payload} 为偶数长度且全是十六进制字符时，按 raw bytes 做 XOR 再当 UTF-8；否则返回 {@code null}。
     */
    private static String xorHexToUtf8String(String payloadHex, byte[] key) {
        try {
            String s = StringUtils.trimToEmpty(payloadHex);
            if (s.isEmpty() || (s.length() % 2) != 0) {
                return null;
            }
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                    return null;
                }
            }
            int n = s.length() / 2;
            byte[] raw = new byte[n];
            for (int i = 0; i < n; i++) {
                int hi = Character.digit(s.charAt(i * 2), 16);
                int lo = Character.digit(s.charAt(i * 2 + 1), 16);
                if (hi < 0 || lo < 0) {
                    return null;
                }
                raw[i] = (byte) ((hi << 4) | lo);
            }
            byte[] plain = new byte[raw.length];
            for (int i = 0; i < raw.length; i++) {
                plain[i] = (byte) (raw[i] ^ key[i % key.length]);
            }
            return new String(plain, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return null;
        }
    }
}
