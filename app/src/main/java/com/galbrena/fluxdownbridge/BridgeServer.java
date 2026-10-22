package com.galbrena.fluxdownbridge;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class BridgeServer {
    private static final String APP_VERSION = BuildConfig.VERSION_NAME;
    static final int PORT = 17800;
    private static final int MAX_HEADER = 32 * 1024;
    private static final int MAX_BODY = 2 * 1024 * 1024;

    private final Context context;
    private final String token;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService clients = Executors.newFixedThreadPool(4);
    private ServerSocket serverSocket;
    private Thread acceptThread;

    BridgeServer(Context context, String token) {
        this.context = context.getApplicationContext();
        this.token = token;
    }

    void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 50);
        acceptThread = new Thread(this::acceptLoop, "fluxdown-bridge");
        acceptThread.start();
    }

    void stop() {
        running.set(false);
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        clients.shutdownNow();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                clients.execute(() -> handle(socket));
            } catch (IOException e) {
                if (running.get()) e.printStackTrace();
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(10_000);
            HttpRequest request = readRequest(s.getInputStream());
            if (request == null) return;
            HttpResponse response;
            String path = request.path;
            if ("OPTIONS".equals(request.method)) {
                response = new HttpResponse(204, "{}");
            } else if ("GET".equals(request.method) && "/ping".equals(path)) {
                JSONObject json = new JSONObject();
                json.put("success", true);
                json.put("message", "pong");
                json.put("app", "FluxDown Android Bridge");
                json.put("version", APP_VERSION);
                response = new HttpResponse(200, json.toString());
            } else if ("GET".equals(request.method) && "/api/v1/info".equals(path)) {
                response = authorized(request)
                        ? ok("ready")
                        : new HttpResponse(401, error("remote_auth_failed"));
            } else if ("POST".equals(request.method) && "/download".equals(path)) {
                response = handleOne(request);
            } else if ("POST".equals(request.method) && "/download/batch".equals(path)) {
                response = handleBatch(request);
            } else {
                response = new HttpResponse(404, error("not found"));
            }
            writeResponse(s.getOutputStream(), response, request.origin);
        } catch (Exception e) {
            try {
                writeResponse(socket.getOutputStream(), new HttpResponse(400, error(e.getMessage())), null);
            } catch (IOException ignored) {}
        }
    }

    private HttpResponse handleOne(HttpRequest request) {
        if (!authorized(request)) return new HttpResponse(401, error("remote_auth_failed"));
        try {
            JSONObject item = new JSONObject(request.body);
            launch(item);
            return ok("sent to FluxDown");
        } catch (Exception e) {
            return new HttpResponse(400, error(e.getMessage()));
        }
    }

    private HttpResponse handleBatch(HttpRequest request) {
        if (!authorized(request)) return new HttpResponse(401, error("remote_auth_failed"));
        try {
            JSONObject root = new JSONObject(request.body);
            JSONArray items = root.optJSONArray("items");
            if (items == null || items.length() == 0) throw new IllegalArgumentException("items array required");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) throw new IllegalArgumentException("item " + i + " is not an object");
                launch(item);
                if (i + 1 < items.length()) Thread.sleep(250);
            }
            return ok(items.length() + " item(s) sent to FluxDown");
        } catch (Exception e) {
            return new HttpResponse(400, error(e.getMessage()));
        }
    }

    private void launch(JSONObject item) throws Exception {
        String url = item.optString("url", "").trim();
        if (url.isEmpty()) throw new IllegalArgumentException("missing url");
        String scheme = "";
        try {
            scheme = new URI(url).getScheme();
        } catch (Exception ignored) {}
        scheme = scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https") || scheme.equals("ftp")
                || scheme.equals("magnet") || scheme.equals("ed2k"))) {
            throw new IllegalArgumentException("unsupported URL scheme: " + scheme);
        }
        if (!item.optString("audioUrl", "").isEmpty()) {
            throw new IllegalArgumentException("separate audioUrl is not supported by protocol handoff");
        }

        StringBuilder uri = new StringBuilder("fluxdown://download?url=");
        uri.append(enc(url));
        append(uri, "filename", item.optString("filename", ""));
        append(uri, "cookies", item.optString("cookies", ""));
        append(uri, "referrer", item.optString("referrer", ""));
        JSONObject headers = item.optJSONObject("headers");
        if (headers != null && headers.length() > 0) append(uri, "headers", headers.toString());

        RootLauncher.Result result = RootLauncher.openFluxDown(uri.toString());
        if (!result.ok()) throw new IllegalStateException("root launch failed: " + result.output);
    }

    private static void append(StringBuilder uri, String key, String value) {
        if (value == null || value.isEmpty()) return;
        uri.append('&').append(key).append('=').append(enc(value));
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception impossible) {
            throw new RuntimeException(impossible);
        }
    }

    private boolean authorized(HttpRequest request) {
        String supplied = request.headers.get("x-fluxdown-token");
        if (supplied == null) return false;
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpResponse ok(String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("success", true);
            json.put("message", message);
        } catch (Exception ignored) {}
        return new HttpResponse(200, json.toString());
    }

    private static String error(String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("success", false);
            json.put("message", message == null ? "error" : message);
        } catch (Exception ignored) {}
        return json.toString();
    }

    private static HttpRequest readRequest(InputStream in) throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int state = 0;
        while (headerBytes.size() < MAX_HEADER) {
            int b = in.read();
            if (b < 0) throw new IOException("unexpected EOF");
            headerBytes.write(b);
            if ((state == 0 || state == 2) && b == '\r') state++;
            else if ((state == 1 || state == 3) && b == '\n') {
                state++;
                if (state == 4) break;
            } else state = b == '\r' ? 1 : 0;
        }
        String raw = headerBytes.toString(StandardCharsets.ISO_8859_1.name());
        String[] lines = raw.split("\r\n");
        if (lines.length == 0) return null;
        String[] first = lines[0].split(" ");
        if (first.length < 3) return null;
        HttpRequest request = new HttpRequest();
        request.method = first[0];
        request.path = first[1].split("\\?", 2)[0];
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            request.headers.put(
                    lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                    lines[i].substring(colon + 1).trim());
        }
        request.origin = request.headers.get("origin");
        int length = 0;
        try {
            length = Integer.parseInt(request.headers.getOrDefault("content-length", "0"));
        } catch (NumberFormatException ignored) {}
        if (length < 0 || length > MAX_BODY) throw new IOException("bad request size");
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(body, offset, length - offset);
            if (n < 0) throw new IOException("unexpected EOF");
            offset += n;
        }
        request.body = new String(body, StandardCharsets.UTF_8);
        return request;
    }

    private static void writeResponse(OutputStream out, HttpResponse response, String origin) throws IOException {
        byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(response.status).append(' ').append(reason(response.status)).append("\r\n");
        head.append("Content-Type: application/json; charset=utf-8\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Cache-Control: no-store\r\n");
        head.append("Connection: close\r\n");
        head.append("Access-Control-Allow-Origin: ").append(origin == null ? "*" : origin).append("\r\n");
        head.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
        head.append("Access-Control-Allow-Headers: Content-Type, X-FluxDown-Token, X-FluxDown-Client\r\n");
        head.append("Vary: Origin\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 404: return "Not Found";
            default: return "Response";
        }
    }

    private static final class HttpRequest {
        String method;
        String path;
        String origin;
        String body = "";
        final Map<String, String> headers = new HashMap<>();
    }

    private static final class HttpResponse {
        final int status;
        final String body;

        HttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
