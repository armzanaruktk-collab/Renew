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
            String payloadUuid = intent.getStringExtra("payloadUuid");
            PayloadProfile selected = findPayload(payloadUuid);
            if (selected == null) {
                ConnectionLogger.getInstance().error("ไม่พบ Payload ที่เลือก กรุณาตรวจสอบในหน้าจัดการ Payload");
                throw new IOException("Selected payload was not found. Open the SSH profile and select a saved payload.");
            }
            if (selected.proxyHost == null || selected.proxyHost.trim().isEmpty()
                    || selected.proxyPort < 1 || selected.proxyPort > 65535) {
                ConnectionLogger.getInstance().error("Proxy Host หรือ Port ของ Payload ไม่ถูกต้อง (" + selected.proxyHost + ":" + selected.proxyPort + ")");
                throw new IOException("Payload proxy host or port is invalid");
            }
            ConnectionLogger.getInstance().info("กำลังเตรียม HTTP Custom Payload Proxy: " + selected.proxyHost + ":" + selected.proxyPort);
            conn.setProxyData(new HttpCustomProxyData(selected.proxyHost.trim(), selected.proxyPort, selected.payload, null));
            Log.i(TAG, "Connecting SSH through HTTP Custom payload proxy " + selected.proxyHost + ":" + selected.proxyPort);
        } else {
            ConnectionLogger.getInstance().info("โหมดเชื่อมต่อ: Direct SSH (ไม่ผ่าน Proxy)");
            Log.i(TAG, "Connecting directly to SSH server " + host + ":" + port);
        }

        ConnectionLogger.getInstance().info("กำลังเริ่ม TCP Connection และเจรจา Handshake...");
        conn.connect();
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
    static String[] parseConnectPayload(String template, String sshHost, int sshPort) throws IOException {
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
            try {
                System.setProperty("user.home", getFilesDir().getAbsolutePath());
                initiateSSH(intent);
                while (!Thread.currentThread().isInterrupted() && conn != null && conn.isAuthenticationComplete()) {
                    try { Thread.sleep(500); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "SSH tunnel failed: " + e.getMessage(), e);
                ConnectionLogger.getInstance().error("SSH ล้มเหลว: " + e.getMessage());
                ConnectionLogger.getInstance().warn("แนวทางแก้ไข: ตรวจสอบว่าโฮสต์ SSH ออนไลน์, พอร์ตไม่ถูกบล็อก, และข้อมูลรหัสผ่าน/คีย์ถูกต้อง");
                closeConnection();
                stopSelf();
            } finally {
                if (Thread.currentThread().isInterrupted()) {
                    ConnectionLogger.getInstance().info("อุโมงค์ SSH ถูกยกเลิกตามคำสั่งผู้ใช้ (Disconnected)");
                    closeConnection();
                    stopSelf();
                }
            }
        }, "SSH-Tunnel");
    }
}