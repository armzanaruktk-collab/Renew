package ru.anton2319.vpnoverssh.proxy;

import android.util.Log;

import com.trilead.ssh2.ProxyData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import ru.anton2319.vpnoverssh.utils.ConnectionLogger;
import ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler;

/**
 * Custom ProxyData implementation that connects through an HTTP Proxy using full
 * HTTP Custom payload injection rules (all tags, methods, splits, and response handling).
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
        if (proxyHost.isEmpty() || proxyPort < 1 || proxyPort > 65535) {
            throw new IOException("Invalid proxy host or port: " + proxyHost + ":" + proxyPort);
        }

        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            int timeout = connectTimeout > 0 ? connectTimeout : 15000;
            ConnectionLogger.getInstance().info("กำลังเชื่อมต่อ Remote Proxy: " + proxyHost + ":" + proxyPort + "...");
            socket.connect(new InetSocketAddress(proxyHost, proxyPort), timeout);
            socket.setSoTimeout(timeout);
            ConnectionLogger.getInstance().success("เชื่อมต่อ Proxy สำเร็จ (" + proxyHost + ":" + proxyPort + ")");

            String expandedPayload = HttpCustomPayloadHandler.expandPayload(
                    payloadTemplate, targetHostname, targetPort, proxyHost, proxyPort, proxyAuth
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
            String statusLine = null;
            int statusCode = 0;

            while (true) {
                statusLine = readLine(in);
                if (statusLine == null) {
                    throw new IOException("Proxy ปิดการเชื่อมต่อกะทันหันโดยไม่ได้ส่ง HTTP Response");
                }
                statusLine = statusLine.trim();
                if (statusLine.isEmpty()) {
                    continue;
                }

                String[] parts = statusLine.split(" ", 3);
                if (parts.length >= 2 && parts[0].toUpperCase().startsWith("HTTP/")) {
                    try {
                        statusCode = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        throw new IOException("Proxy ส่ง HTTP Status code ไม่ถูกต้อง: " + statusLine);
                    }
                } else {
                    throw new IOException("Proxy ส่งข้อมูลไม่ถูกต้อง: " + statusLine);
                }

                if (statusCode == 100) {
                    // Consume headers of 100 Continue and read next response
                    consumeHeaders(in);
                    continue;
                }
                break;
            }

            // HTTP 200 Connection established, 2xx, or 101 Switching Protocols
            if ((statusCode >= 200 && statusCode < 300) || statusCode == 101) {
                consumeHeaders(in);
                ConnectionLogger.getInstance().success("Proxy ตอบกลับสำเร็จ: " + statusLine);
                socket.setSoTimeout(0); // Reset for SSH session
                return socket;
            } else {
                consumeHeaders(in);
                String errorMsg = "Proxy ปฏิเสธการเชื่อมต่อ: " + statusLine;
                ConnectionLogger.getInstance().error(errorMsg);
                throw new IOException(errorMsg);
            }

        } catch (IOException e) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private static String consumeHeaders(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = readLine(in)) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }
            sb.append(line).append("\n");
        }
        return sb.toString();
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
}
