package ru.anton2319.vpnoverssh.proxy;

import android.util.Log;

import com.trilead.ssh2.ProxyData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

import ru.anton2319.vpnoverssh.utils.ConnectionLogger;
import ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler;

/**
 * Robust HTTP Custom & HTTP Injector ProxyData implementation.
 * Supports:
 * 1. Remote HTTP/HTTPS Proxy (Squid, Apache, Nginx, Telco proxies).
 * 2. Direct Payload Injection to SSH Server / Bug Host (no remote proxy).
 * 3. Cloudflare CDN WebSocket payloads (101 Switching Protocols).
 * 4. Telco Bug Host Redirects (302 Found, 301 Moved, 307 Redirect).
 * 5. Direct SSH response detection (SSH-2.0- server banner pushback).
 * 6. Packet splits, delays, rotation, and custom variables.
 */
public class HttpCustomProxyData implements ProxyData {
    private static final String TAG = "HttpCustomProxyData";

    private final String proxyHost;
    private final int proxyPort;
    private final String payloadTemplate;
    private final String proxyAuth;

    public HttpCustomProxyData(String proxyHost, int proxyPort, String payloadTemplate, String proxyAuth) {
        this.proxyHost = proxyHost != null ? proxyHost.trim() : "";
        this.proxyPort = proxyPort;
        this.payloadTemplate = payloadTemplate;
        this.proxyAuth = proxyAuth;
    }

    @Override
    public Socket openConnection(String targetHostname, int targetPort, int connectTimeout) throws IOException {
        boolean isDirect = (proxyHost == null || proxyHost.trim().isEmpty()
                || proxyHost.equalsIgnoreCase("direct")
                || proxyHost.equalsIgnoreCase("none")
                || proxyHost.contains("example.com")
                || proxyHost.equalsIgnoreCase(targetHostname));

        String connectHost = isDirect ? targetHostname : proxyHost.trim();
        int connectPort = isDirect ? targetPort : (proxyPort > 0 && proxyPort <= 65535 ? proxyPort : 8080);

        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            int timeout = connectTimeout > 0 ? connectTimeout : 15000;
            if (isDirect) {
                ConnectionLogger.getInstance().info("กำลังเชื่อมต่อไปยัง SSH Server (Direct Payload): " + connectHost + ":" + connectPort + "...");
            } else {
                ConnectionLogger.getInstance().info("กำลังเชื่อมต่อ Remote Proxy: " + connectHost + ":" + connectPort + "...");
            }

            InetSocketAddress endpoint = new InetSocketAddress(connectHost, connectPort);
            if (endpoint.isUnresolved()) {
                throw new java.net.UnknownHostException("ไม่สามารถค้นหา IP ของโฮสต์ " + connectHost + " (Unresolved host)");
            }
            socket.connect(endpoint, timeout);
            socket.setSoTimeout(timeout);
            ConnectionLogger.getInstance().success("TCP เชื่อมต่อสำเร็จ (" + connectHost + ":" + connectPort + ")");

            String effectivePayload = (payloadTemplate != null && !payloadTemplate.trim().isEmpty())
                    ? payloadTemplate
                    : "CONNECT [host_port] HTTP/1.1[crlf]Host: [host][crlf]User-Agent: [ua][crlf][keep][crlf][crlf]";

            String expandedPayload = HttpCustomPayloadHandler.expandPayload(
                    effectivePayload, targetHostname, targetPort, connectHost, connectPort, proxyAuth
            );

            // Ensure proper HTTP header termination if missing
            if (!expandedPayload.endsWith("\r\n\r\n")) {
                if (expandedPayload.endsWith("\r\n")) {
                    expandedPayload = expandedPayload + "\r\n";
                } else {
                    expandedPayload = expandedPayload + "\r\n\r\n";
                }
            }

            List<HttpCustomPayloadHandler.PayloadStep> steps = HttpCustomPayloadHandler.parsePayloadSteps(expandedPayload);
            ConnectionLogger.getInstance().info("ส่ง HTTP Custom Payload (" + steps.size() + " segments)...");

            OutputStream out = socket.getOutputStream();
            for (int i = 0; i < steps.size(); i++) {
                HttpCustomPayloadHandler.PayloadStep step = steps.get(i);
                if (step.data != null && step.data.length > 0) {
                    out.write(step.data);
                }
                if (step.flush) {
                    out.flush();
                }
                if (step.delayMs > 0) {
                    ConnectionLogger.getInstance().info("Split delay: " + step.delayMs + "ms");
                    try {
                        Thread.sleep(step.delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Payload injection split was interrupted", e);
                    }
                }
            }

            InputStream in = socket.getInputStream();
            String statusLine = readLine(in);
            if (statusLine == null) {
                throw new IOException("ไม่ได้รับ HTTP response จาก Remote Proxy; ไม่สามารถยืนยัน SSH tunnel ได้");
            }

            statusLine = statusLine.trim();
            ConnectionLogger.getInstance().info("ตอบกลับ: " + statusLine);

            // A direct SSH banner is valid and must be pushed back for the SSH library.
            if (statusLine.startsWith("SSH-")) {
                ConnectionLogger.getInstance().success("เซิร์ฟเวอร์ส่ง SSH Banner สำเร็จ (" + statusLine + ")");
                socket.setSoTimeout(0);
                byte[] pushedBytes = (statusLine + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
                return new DelegatingSocket(socket, new SequenceInputStream(new ByteArrayInputStream(pushedBytes), in));
            }

            if (!statusLine.toUpperCase(Locale.ROOT).startsWith("HTTP/")) {
                // Preserve non-HTTP bytes; let SSH validate the actual protocol banner.
                socket.setSoTimeout(0);
                byte[] pushedBytes = (statusLine + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
                return new DelegatingSocket(socket, new SequenceInputStream(new ByteArrayInputStream(pushedBytes), in));
            }

            int statusCode = parseStatusCode(statusLine);
            Map<String, String> headers = readHeaders(in);

            // Handle interim 100 Continue responses without treating them as a tunnel.
            while (statusCode == 100) {
                statusLine = readLine(in);
                if (statusLine == null) {
                    throw new IOException("Proxy ส่ง HTTP 100 Continue แต่ไม่มี response สุดท้าย");
                }
                statusLine = statusLine.trim();
                ConnectionLogger.getInstance().info("ตอบกลับ: " + statusLine);
                if (!statusLine.toUpperCase(Locale.ROOT).startsWith("HTTP/")) {
                    throw new IOException("รูปแบบ HTTP response หลัง 100 Continue ไม่ถูกต้อง");
                }
                statusCode = parseStatusCode(statusLine);
                headers = readHeaders(in);
            }

            String firstLine = expandedPayload.trim();
            int lineEnd = firstLine.indexOf('\n');
            if (lineEnd >= 0) firstLine = firstLine.substring(0, lineEnd).trim();
            String method = firstLine.isEmpty() ? "" : firstLine.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
            boolean isConnectRequest = "CONNECT".equals(method);

            if (isConnectRequest) {
                if (statusCode >= 200 && statusCode < 300) {
                    ConnectionLogger.getInstance().success("HTTP CONNECT tunnel สำเร็จ (" + statusLine + ")");
                    socket.setSoTimeout(0);
                    return socket;
                }
                if (statusCode >= 300 && statusCode < 400) {
                    String location = headers.get("location");
                    throw new IOException("HTTP CONNECT ถูก Redirect (" + statusLine + ")"
                            + (location != null ? " Location: " + location : "")
                            + "; Redirect ไม่ใช่ SSH tunnel");
                }
                throw new IOException("HTTP CONNECT ไม่สำเร็จ (" + statusLine + ")"
                        + (statusCode == 407 ? " — Proxy ต้องการการยืนยันตัวตน (407)" : ""));
            }

            // A non-CONNECT payload is accepted only for an explicit WebSocket upgrade.
            boolean requestedWebSocket = expandedPayload.toLowerCase(Locale.ROOT).contains("upgrade: websocket")
                    && expandedPayload.toLowerCase(Locale.ROOT).contains("connection: upgrade");
            boolean responseWebSocket = "websocket".equalsIgnoreCase(headers.get("upgrade"))
                    && headers.getOrDefault("connection", "").toLowerCase(Locale.ROOT).contains("upgrade");
            if (statusCode == 101 && requestedWebSocket && responseWebSocket) {
                ConnectionLogger.getInstance().success("WebSocket Upgrade สำเร็จ (" + statusLine + ")");
                socket.setSoTimeout(0);
                return socket;
            }

            if (statusCode >= 300 && statusCode < 400) {
                String location = headers.get("location");
                throw new IOException("HTTP Payload ถูก Redirect (" + statusLine + ")"
                        + (location != null ? " Location: " + location : "")
                        + "; ปลายทางยังไม่ได้สร้าง SSH tunnel");
            }
            if (statusCode == 101) {
                throw new IOException("ได้รับ HTTP 101 แต่ไม่พบ WebSocket Upgrade ที่ตรงกับ Payload; ปฏิเสธการส่งข้อมูลเข้า SSH");
            }
            throw new IOException("HTTP response (" + statusLine + ") ไม่ได้ยืนยัน SSH tunnel; "
                    + "Payload แบบ " + (method.isEmpty() ? "ไม่ทราบ method" : method)
                    + " ต้องใช้ปลายทางที่รองรับ tunnel จริง");
        } catch (IOException e) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private static Map<String, String> readHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(in)) != null) {
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                headers.merge(name, value, (oldValue, newValue) -> oldValue + ", " + newValue);
            }
        }
        return headers;
    }

    private static int parseStatusCode(String statusLine) throws IOException {
        String[] parts = statusLine.trim().split("\\s+", 3);
        if (parts.length < 2 || !parts[0].toUpperCase(Locale.ROOT).startsWith("HTTP/")) {
            throw new IOException("HTTP status line ไม่ถูกต้อง: " + statusLine);
        }
        try {
            int code = Integer.parseInt(parts[1]);
            if (code < 100 || code > 599) throw new NumberFormatException("out of range");
            return code;
        } catch (NumberFormatException e) {
            throw new IOException("ไม่สามารถอ่าน HTTP status code: " + statusLine, e);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                baos.write(b);
            }
        }
        if (b == -1 && baos.size() == 0) {
            return null;
        }
        return baos.toString("ISO-8859-1");
    }

    /**
     * Socket wrapper that delegates all operations to the underlying socket,
     * but provides a customized InputStream (e.g. with pushed-back bytes).
     */
    public static class DelegatingSocket extends Socket {
        private final Socket delegate;
        private final InputStream customIn;

        public DelegatingSocket(Socket delegate, InputStream customIn) {
            this.delegate = delegate;
            this.customIn = customIn;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return customIn != null ? customIn : delegate.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return delegate.getOutputStream();
        }

        @Override
        public void close() throws IOException {
            try {
                if (customIn != null) customIn.close();
            } catch (Exception ignored) {
            }
            delegate.close();
        }

        @Override
        public boolean isConnected() {
            return delegate.isConnected();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public boolean isBound() {
            return delegate.isBound();
        }

        @Override
        public boolean isInputShutdown() {
            return delegate.isInputShutdown();
        }

        @Override
        public boolean isOutputShutdown() {
            return delegate.isOutputShutdown();
        }

        @Override
        public void setTcpNoDelay(boolean on) throws SocketException {
            delegate.setTcpNoDelay(on);
        }

        @Override
        public boolean getTcpNoDelay() throws SocketException {
            return delegate.getTcpNoDelay();
        }

        @Override
        public void setSoTimeout(int timeout) throws SocketException {
            delegate.setSoTimeout(timeout);
        }

        @Override
        public int getSoTimeout() throws SocketException {
            return delegate.getSoTimeout();
        }

        @Override
        public void setKeepAlive(boolean on) throws SocketException {
            delegate.setKeepAlive(on);
        }

        @Override
        public boolean getKeepAlive() throws SocketException {
            return delegate.getKeepAlive();
        }

        @Override
        public InetAddress getInetAddress() {
            return delegate.getInetAddress();
        }

        @Override
        public int getPort() {
            return delegate.getPort();
        }

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return delegate.getRemoteSocketAddress();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return delegate.getLocalSocketAddress();
        }

        @Override
        public SocketChannel getChannel() {
            return delegate.getChannel();
        }
    }
}
