package ru.anton2319.vpnoverssh.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.DynamicPortForwarder;
import com.trilead.ssh2.HTTPProxyData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ru.anton2319.vpnoverssh.data.PayloadProfile;
import ru.anton2319.vpnoverssh.data.singleton.PortForward;
import ru.anton2319.vpnoverssh.data.singleton.StatusInfo;
import ru.anton2319.vpnoverssh.data.utils.PayloadProfileManager;
import ru.anton2319.vpnoverssh.proxy.HttpCustomProxyData;
import ru.anton2319.vpnoverssh.utils.ConnectionLogger;
import ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler;

public class SshService extends Service {
    private static final String TAG = "SshService";
    private Thread sshThread;
    private volatile Connection conn;
    private volatile DynamicPortForwarder forwarder;
    private SharedPreferences sharedPreferences;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Thread previous = PortForward.getInstance().getSshThread();
        if (previous != null) previous.interrupt();
        sshThread = newSshThread(intent);
        PortForward.getInstance().setSshThread(sshThread);
        sshThread.start();
        return START_NOT_STICKY;
    }

    public void initiateSSH(Intent intent) throws IOException {
        String user = intent.getStringExtra("user");
        String host = intent.getStringExtra("hostname");
        String password = intent.getStringExtra("password");
        String privateKey = intent.getStringExtra("privateKey");
        int port;
        try { port = Integer.parseInt(intent.getStringExtra("port")); }
        catch (Exception e) { port = 22; }
        if (host == null || host.trim().isEmpty() || user == null || user.trim().isEmpty()
                || port < 1 || port > 65535) {
            ConnectionLogger.getInstance().error("การตั้งค่า SSH ไม่ถูกต้อง (ตรวจสอบ Host, Port หรือ Username)");
            throw new IOException("Invalid SSH server settings");
        }

        ConnectionLogger.getInstance().info("กำลังเตรียมการเชื่อมต่อ SSH: " + host + ":" + port + " (" + user + ")");

        conn = new Connection(host, port);
        String mode = intent.getStringExtra("connectionMode");
        if ("PAYLOAD".equals(mode)) {
            String payloadBody = intent.getStringExtra("payloadBody");
            String proxyHost = intent.getStringExtra("payloadProxyHost");
            int proxyPort = intent.getIntExtra("payloadProxyPort", 0);

            if (payloadBody == null || payloadBody.trim().isEmpty()) {
                String payloadUuid = intent.getStringExtra("payloadUuid");
                PayloadProfile selected = findPayload(payloadUuid);
                if (selected != null) {
                    payloadBody = selected.payload;
                    proxyHost = selected.proxyHost;
                    proxyPort = selected.proxyPort;
                }
            }

            if (payloadBody == null || payloadBody.trim().isEmpty()) {
                payloadBody = "CONNECT [host_port] [protocol][crlf]Host: [host][crlf]User-Agent: [ua][crlf][keep][crlf][crlf]";
            }

            if (proxyHost != null && (proxyHost.contains("example.com") || proxyHost.equalsIgnoreCase("direct") || proxyHost.equalsIgnoreCase("none"))) {
                proxyHost = "";
            }

            if (proxyHost != null && !proxyHost.trim().isEmpty()) {
                ConnectionLogger.getInstance().info("โหมด: HTTP Custom Payload ผ่าน Remote Proxy (" + proxyHost + ":" + (proxyPort > 0 ? proxyPort : 8080) + ")");
            } else {
                ConnectionLogger.getInstance().info("โหมด: HTTP Custom Payload ฉีดตรงไปยัง SSH Server (" + host + ":" + port + ")");
            }

            conn.setProxyData(new HttpCustomProxyData(proxyHost, proxyPort, payloadBody, null));
            Log.i(TAG, "Connecting SSH with HTTP Custom payload (Proxy: " + proxyHost + ":" + proxyPort + ")");
        } else {
            ConnectionLogger.getInstance().info("โหมดเชื่อมต่อ: Direct SSH (ไม่ผ่าน Proxy)");
            Log.i(TAG, "Connecting directly to SSH server " + host + ":" + port);
        }

        // Pre-flight DNS resolution check for clear diagnostic logging
        ConnectionLogger.getInstance().info("กำลังตรวจสอบ DNS และค้นหา IP ของ " + host + "...");
        try {
            java.net.InetAddress[] ips = java.net.InetAddress.getAllByName(host);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ips.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(ips[i].getHostAddress());
            }
            ConnectionLogger.getInstance().info("DNS สำเร็จ: IP เซิร์ฟเวอร์คือ [" + sb + "]");
        } catch (java.net.UnknownHostException uhe) {
            ConnectionLogger.getInstance().error("DNS Lookup ล้มเหลว: ไม่พบโฮสต์ \"" + host + "\" ในระบบเครือข่ายอินเทอร์เน็ต");
            ConnectionLogger.getInstance().warn("ข้อเสนอแนะ: โฮสต์ '" + host + "' ไม่มีอยู่จริงหรือโดเมนหมดอายุ กรุณาเปลี่ยนเป็น IP หรือโดเมนเซิร์ฟเวอร์จริงของคุณ");
            throw new IOException("Cannot resolve SSH host '" + host + "': UnknownHostException", uhe);
        }

        ConnectionLogger.getInstance().info("กำลังเริ่ม TCP Connection และเจรจา Handshake (timeout: 15s)...");
        conn.connect(null, 15000, 15000);
        ConnectionLogger.getInstance().success("SSH Handshake เชื่อมต่อสำเร็จ!");
        PortForward.getInstance().setConn(conn);

        boolean authenticated = false;
        ConnectionLogger.getInstance().info("กำลังยืนยันตัวตนผู้ใช้: " + user + (privateKey != null && !privateKey.trim().isEmpty() ? " ด้วย Private Key" : " ด้วยรหัสผ่าน"));
        if (privateKey != null && !privateKey.trim().isEmpty()) {
            authenticated = conn.authenticateWithPublicKey(user, privateKey.toCharArray(), password);
        } else if (password != null && !password.isEmpty()) {
            authenticated = conn.authenticateWithPassword(user, password);
        } else {
            authenticated = conn.authenticateWithNone(user);
        }
        if (!authenticated) {
            ConnectionLogger.getInstance().error("การยืนยันตัวตนล้มเหลว (SSH Auth Failed) ตรวจสอบ Username, Password หรือ Private Key");
            throw new IOException("SSH authentication failed. Check username, password or private key.");
        }
        ConnectionLogger.getInstance().success("ยืนยันตัวตนสำเร็จแล้ว (Authenticated as " + user + ")");

        int localPort;
        try { localPort = Integer.parseInt(sharedPreferences.getString("forwarder_port", "1080")); }
        catch (Exception e) { localPort = 1080; }
        if (localPort < 1 || localPort > 65535) localPort = 1080;
        ConnectionLogger.getInstance().info("กำลังสร้าง Local SOCKS5 Dynamic Forwarder พอร์ต " + localPort + "...");
        forwarder = conn.createDynamicPortForwarder(localPort);
        PortForward.getInstance().setForwarder(forwarder);
        ConnectionLogger.getInstance().success("Dynamic Forwarder เปิดทำงานแล้วบน 127.0.0.1:" + localPort);
        Log.i(TAG, "SSH authenticated; local SOCKS5 forwarder listening on 127.0.0.1:" + localPort);

        // Start the VPN only after the local SOCKS5 endpoint is listening.
        Intent vpnIntent = StatusInfo.getInstance().getVpnIntent();
        if (vpnIntent == null) {
            throw new IOException("VPN service intent is not initialized");
        }
        ConnectionLogger.getInstance().info("SSH พร้อมแล้ว กำลังเริ่ม VPN ผ่าน SOCKS5...");
        startService(vpnIntent);
    }

    private PayloadProfile findPayload(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) return null;
        for (PayloadProfile profile : new PayloadProfileManager(this).loadProfiles()) {
            if (profile != null && uuid.equals(profile.uuid)) return profile;
        }
        return null;
    }

    /*
     * Parses payload headers using HttpCustomPayloadHandler for backwards compatibility.
     */
    public static String[] parseConnectPayload(String template, String sshHost, int sshPort) throws IOException {
        if (template == null || template.trim().isEmpty()) throw new IOException("Payload is empty");
        String expanded = HttpCustomPayloadHandler.expandPayload(template, sshHost, sshPort, "", 0, null);
        String normalized = expanded.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n");
        List<String> headers = new ArrayList<>();
        int firstHeader = 0;
        if (lines.length > 0 && lines[0].trim().length() > 0) {
            String request = lines[0].trim();
            if (request.startsWith("CONNECT ") || request.startsWith("HTTP/") || request.startsWith("GET ")
                    || request.startsWith("POST ") || request.startsWith("HEAD ") || request.startsWith("PUT ")) {
                firstHeader = 1;
            }
        }
        for (int i = firstHeader; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.regionMatches(true, 0, "Host:", 0, 5)) continue;
            if (line.indexOf(':') <= 0) continue;
            headers.add(line);
        }
        return headers.toArray(new String[0]);
    }

    @Override public void onDestroy() {
        Log.d(TAG, "Shutting down SSH service");
        Thread current = PortForward.getInstance().getSshThread();
        if (current != null) current.interrupt();
        closeConnection();
        super.onDestroy();
    }

    private void closeConnection() {
        try { if (forwarder != null) forwarder.close(); } catch (Exception ignored) { }
        try { if (conn != null) conn.close(); } catch (Exception ignored) { }
        forwarder = null;
        conn = null;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Thread newSshThread(Intent intent) {
        return new Thread(() -> {
            String targetHost = intent != null ? intent.getStringExtra("hostname") : "unknown";
            String targetPort = intent != null ? intent.getStringExtra("port") : "22";
            try {
                System.setProperty("user.home", getFilesDir().getAbsolutePath());
                initiateSSH(intent);
                while (!Thread.currentThread().isInterrupted() && conn != null && conn.isAuthenticationComplete()) {
                    try { Thread.sleep(500); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            } catch (IOException | RuntimeException e) {
                Throwable root = e;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                String rootMsg = root.getMessage() != null ? root.getMessage() : root.toString();
                Log.e(TAG, "SSH tunnel failed: " + rootMsg, e);
                StatusInfo.getInstance().setActive(false);

                if (root instanceof java.net.UnknownHostException || rootMsg.contains("UnknownHost")
                        || rootMsg.contains("Name or service not known") || rootMsg.contains("Cannot resolve")) {
                    ConnectionLogger.getInstance().error("SSH ขัดข้อง (DNS): ไม่พบชื่อโฮสต์ \"" + targetHost + "\"");
                    ConnectionLogger.getInstance().warn("แนวทางแก้ไข: โฮสต์นี้ไม่มีอยู่จริง กรุณาแก้ไขเซิร์ฟเวอร์โดยเปลี่ยนเป็น IP หรือโดเมน SSH จริงของคุณ");
                } else if (root instanceof java.net.SocketTimeoutException || rootMsg.contains("timed out")
                        || rootMsg.contains("timeout")) {
                    ConnectionLogger.getInstance().error("SSH ขัดข้อง (Timeout): ไม่สามารถเชื่อมต่อกับ " + targetHost + ":" + targetPort + " ได้ภายใน 15 วินาที");
                    ConnectionLogger.getInstance().warn("แนวทางแก้ไข: เซิร์ฟเวอร์ไม่ตอบสนอง หรือพอร์ต " + targetPort + " ถูกเครือข่าย/ไฟร์วอลล์บล็อก");
                } else if (root instanceof java.net.ConnectException || rootMsg.contains("Connection refused")) {
                    ConnectionLogger.getInstance().error("SSH ขัดข้อง (Connection Refused): เซิร์ฟเวอร์ปฏิเสธการเชื่อมต่อบนพอร์ต " + targetPort);
                    ConnectionLogger.getInstance().warn("แนวทางแก้ไข: ตรวจสอบว่าพอร์ต " + targetPort + " เปิดให้บริการ SSH หรือไม่");
                } else {
                    ConnectionLogger.getInstance().error("SSH ล้มเหลว: " + rootMsg);
                    ConnectionLogger.getInstance().warn("แนวทางแก้ไข: ตรวจสอบข้อมูลเซิร์ฟเวอร์ SSH พอร์ต และความถูกต้องของข้อมูลรับรอง");
                }

                // Stop and clean up VPN interface if active so device network is not blocked
                try {
                    Thread vpnThread = ru.anton2319.vpnoverssh.data.singleton.SocksPersistent.getInstance().getVpnThread();
                    if (vpnThread != null) {
                        vpnThread.interrupt();
                    }
                    Intent vpnIntent = StatusInfo.getInstance().getVpnIntent();
                    if (vpnIntent != null) {
                        stopService(vpnIntent);
                    }
                    android.os.ParcelFileDescriptor pfd = ru.anton2319.vpnoverssh.data.singleton.SocksPersistent.getInstance().getVpnInterface();
                    if (pfd != null) {
                        pfd.close();
                        ru.anton2319.vpnoverssh.data.singleton.SocksPersistent.getInstance().setVpnInterface(null);
                    }
                } catch (Exception ex) {
                    Log.w(TAG, "Failed to clean up VPN interface on SSH failure", ex);
                }

                closeConnection();
                stopSelf();
            } finally {
                if (Thread.currentThread().isInterrupted()) {
                    StatusInfo.getInstance().setActive(false);
                    ConnectionLogger.getInstance().info("อุโมงค์ SSH ถูกยกเลิกตามคำสั่งผู้ใช้ (Disconnected)");
                    closeConnection();
                    stopSelf();
                }
            }
        }, "SSH-Tunnel");
    }
}