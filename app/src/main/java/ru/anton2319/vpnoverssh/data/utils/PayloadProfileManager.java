package ru.anton2319.vpnoverssh.data.utils;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import ru.anton2319.vpnoverssh.data.PayloadProfile;

public class PayloadProfileManager {
    private static final String TAG = "PayloadProfileManager";
    private final boolean netmodMode;
    private final AtomicFile file;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<List<PayloadProfile>>() {}.getType();

    public PayloadProfileManager(Context context) {
        this(context, false);
    }

    public PayloadProfileManager(Context context, boolean netmodMode) {
        this.netmodMode = netmodMode;
        String fileName = netmodMode ? "netmod_payload_profiles.json" : "payload_profiles.json";
        file = new AtomicFile(new File(context.getFilesDir(), fileName));
    }

    public synchronized List<PayloadProfile> loadProfiles() {
        if (!file.getBaseFile().exists()) return netmodMode ? new ArrayList<>() : seedDefaults();
        try (InputStream in = file.openRead();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            List<PayloadProfile> profiles = gson.fromJson(
                    new String(out.toByteArray(), StandardCharsets.UTF_8), listType);
            if (profiles == null || profiles.isEmpty()) {
                return netmodMode ? new ArrayList<>() : seedDefaults();
            }
            // Sanitize any dummy hostnames from earlier versions
            boolean changed = false;
            for (PayloadProfile p : profiles) {
                if (p != null && p.proxyHost != null && (p.proxyHost.contains("example.com")
                        || p.proxyHost.equals("th-proxy.com") || p.proxyHost.equals("bestproxy.net"))) {
                    p.proxyHost = "";
                    changed = true;
                }
            }
            if (changed) {
                saveProfiles(profiles);
            }
            return profiles;
        } catch (Exception e) {
            Log.e(TAG, "Unable to read payload profiles", e);
            return netmodMode ? new ArrayList<>() : seedDefaults();
        }
    }

    private synchronized List<PayloadProfile> seedDefaults() {
        List<PayloadProfile> defaults = new ArrayList<>();
        PayloadProfile p1 = new PayloadProfile();
        p1.uuid = "default-mypayload-uuid";
        p1.name = "HTTP Custom Direct";
        p1.description = "เชื่อมต่อตรงพร้อมฉีด Payload (Direct + Payload)";
        p1.proxyHost = "";
        p1.proxyPort = 80;
        p1.payload = "CONNECT [host_port] [protocol][crlf]Host: [host][crlf]X-Online-Host: [host][crlf]User-Agent: [ua][crlf][keep][crlf][crlf]";
        defaults.add(p1);

        PayloadProfile p2 = new PayloadProfile();
        p2.uuid = "default-thailand01-uuid";
        p2.name = "Cloudflare / CDN WebSocket";
        p2.description = "WebSocket Payload สำหรับ CDN หรือ Cloudflare";
        p2.proxyHost = "";
        p2.proxyPort = 80;
        p2.payload = "GET / [protocol][crlf]Host: [host][crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]";
        defaults.add(p2);

        PayloadProfile p3 = new PayloadProfile();
        p3.uuid = "default-bestspeed-uuid";
        p3.name = "HTTP Custom Split Proxy";
        p3.description = "แยกแพ็กเก็ต [split] (เชื่อมตรงหรือผ่าน Remote Proxy)";
        p3.proxyHost = "";
        p3.proxyPort = 8080;
        p3.payload = "GET / [protocol][crlf]Host: [host][crlf][split]CONNECT [host_port] [protocol][crlf]Host: [host][crlf][crlf]";
        defaults.add(p3);

        saveProfiles(defaults);
        return defaults;
    }

    public synchronized boolean saveProfiles(List<PayloadProfile> profiles) {
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(gson.toJson(profiles).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
            return true;
        } catch (Exception e) {
            if (out != null) file.failWrite(out);
            Log.e(TAG, "Unable to save payload profiles", e);
            return false;
        }
    }

    public synchronized boolean saveProfile(PayloadProfile profile) {
        if (profile == null || profile.uuid == null) return false;
        List<PayloadProfile> profiles = loadProfiles();
        boolean replaced = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profile.uuid.equals(profiles.get(i).uuid)) {
                profiles.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) profiles.add(profile);
        return saveProfiles(profiles);
    }

    public synchronized boolean deleteProfile(String uuid) {
        List<PayloadProfile> profiles = loadProfiles();
        boolean changed = profiles.removeIf(p -> p != null && uuid.equals(p.uuid));
        return !changed || saveProfiles(profiles);
    }
}
