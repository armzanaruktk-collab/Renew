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
    private static final String FILE_NAME = "payload_profiles.json";
    private final AtomicFile file;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<List<PayloadProfile>>() {}.getType();

    public PayloadProfileManager(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    public synchronized List<PayloadProfile> loadProfiles() {
        if (!file.getBaseFile().exists()) return seedDefaults();
        try (InputStream in = file.openRead();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            List<PayloadProfile> profiles = gson.fromJson(
                    new String(out.toByteArray(), StandardCharsets.UTF_8), listType);
            if (profiles == null || profiles.isEmpty()) {
                return seedDefaults();
            }
            return profiles;
        } catch (Exception e) {
            Log.e(TAG, "Unable to read payload profiles", e);
            return seedDefaults();
        }
    }

    private synchronized List<PayloadProfile> seedDefaults() {
        List<PayloadProfile> defaults = new ArrayList<>();
        PayloadProfile p1 = new PayloadProfile();
        p1.uuid = "default-mypayload-uuid";
        p1.name = "MyPayload";
        p1.description = "Default HTTP CONNECT payload";
        p1.proxyHost = "proxy.example.com";
        p1.proxyPort = 8080;
        p1.payload = "CONNECT [host_port] HTTP/1.1[crlf]Host: [host_port][crlf]User-Agent: [ua][crlf]Connection: keep-alive[crlf][crlf]";
        defaults.add(p1);

        PayloadProfile p2 = new PayloadProfile();
        p2.uuid = "default-thailand01-uuid";
        p2.name = "Thailand-01";
        p2.description = "Thailand High Speed Proxy";
        p2.proxyHost = "th-proxy.com";
        p2.proxyPort = 8080;
        p2.payload = "CONNECT [host_port] HTTP/1.1[crlf]Host: [host][crlf]Proxy-Connection: Keep-Alive[crlf][crlf]";
        defaults.add(p2);

        PayloadProfile p3 = new PayloadProfile();
        p3.uuid = "default-bestspeed-uuid";
        p3.name = "BestSpeed";
        p3.description = "High Speed Cloud Proxy";
        p3.proxyHost = "bestproxy.net";
        p3.proxyPort = 80;
        p3.payload = "CONNECT [host_port] HTTP/1.1[crlf]Host: [host_port][crlf]User-Agent: [ua][crlf][crlf]";
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
