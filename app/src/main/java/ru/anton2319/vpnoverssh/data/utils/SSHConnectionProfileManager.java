package ru.anton2319.vpnoverssh.data.utils;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.anton2319.vpnoverssh.data.SSHConnectionProfile;

public class SSHConnectionProfileManager {
    private static final String TAG = "SSHConnectionProfileManager";
    private static final String FILE_NAME = "ssh_profiles.json";

    private final Context context;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Type listType = new TypeToken<List<SSHConnectionProfile>>() {}.getType();

    public SSHConnectionProfileManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void saveProfiles(List<SSHConnectionProfile> profiles) {
        String json = gson.toJson(profiles);
        try (FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Error saving SSH profiles: " + e.getMessage());
        }
    }

    public synchronized void saveProfile(SSHConnectionProfile profile) {
        if (profile == null) return;
        List<SSHConnectionProfile> existing = loadProfiles();
        boolean replaced = false;
        for (int i = 0; i < existing.size(); i++) {
            if (existing.get(i).uuid != null && existing.get(i).uuid.equals(profile.uuid)) {
                existing.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            existing.add(profile);
        }
        saveProfiles(existing);
    }

    public synchronized SSHConnectionProfile loadProfileByUUID(UUID uuid) {
        if (uuid == null) return null;
        List<SSHConnectionProfile> profiles = loadProfiles();
        for (SSHConnectionProfile profile : profiles) {
            if (profile != null && uuid.equals(profile.uuid)) {
                return profile;
            }
        }
        return null;
    }

    public synchronized List<SSHConnectionProfile> loadProfiles() {
        List<SSHConnectionProfile> profiles = new ArrayList<>();
        try {
            FileInputStream fis = context.openFileInput(FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            String json = sb.toString().trim();
            if (!json.isEmpty()) {
                // Safely deserialize with full TypeToken
                Object parsed = new Gson().fromJson(json, Object.class);
                if (parsed instanceof List) {
                    List<?> rawList = (List<?>) parsed;
                    for (Object item : rawList) {
                        try {
                            SSHConnectionProfile p = SSHConnectionProfile.fromLinkedTreeMap(item);
                            if (p != null) {
                                profiles.add(p);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (profiles.isEmpty()) {
                return seedDefaults();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading SSH profiles: " + e.getMessage());
            return seedDefaults();
        }

        return profiles;
    }

    public synchronized void deleteProfile(UUID uuid) {
        if (uuid == null) return;
        List<SSHConnectionProfile> existing = loadProfiles();
        boolean removed = false;
        for (int i = 0; i < existing.size(); i++) {
            if (existing.get(i).uuid != null && existing.get(i).uuid.equals(uuid)) {
                existing.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            if (existing.isEmpty()) {
                seedDefaults();
            } else {
                saveProfiles(existing);
            }
        }
    }

    public synchronized List<SSHConnectionProfile> seedDefaults() {
        // Do not create fake demo servers or placeholder credentials.
        // A new installation must show an empty server list until the user adds one.
        List<SSHConnectionProfile> profiles = new ArrayList<>();
        saveProfiles(profiles);
        return profiles;
    }
}
