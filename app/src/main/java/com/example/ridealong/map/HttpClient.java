package com.example.ridealong.map;

import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.utils.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Minimal blocking HTTP client (call from a background thread). No extra dependency needed. */
public final class HttpClient {
    private HttpClient() { }

    public static String get(String url, Map<String, String> headers) throws IOException {
        return request("GET", url, null, headers);
    }

    public static String post(String url, String jsonBody, Map<String, String> headers) throws IOException {
        return request("POST", url, jsonBody, headers);
    }

    private static String request(String method, String url, String body, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        int timeout = AppConfig.get().httpTimeoutMs;
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", Constants.USER_AGENT);
        c.setRequestProperty("Accept", "application/json");
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        try {
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String text = read(in);
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + truncate(text));
            return text;
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String truncate(String s) { return s == null ? "" : s.length() > 200 ? s.substring(0, 200) : s; }
}