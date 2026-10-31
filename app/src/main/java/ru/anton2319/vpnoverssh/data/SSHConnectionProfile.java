package ru.anton2319.vpnoverssh.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.UUID;

public class SSHConnectionProfile {
    private String serverIP;
    private int serverPort;
    private String username;
    private AuthenticationType authenticationType;
    private String password;
    private String privateKey;
    private String profileName = "bd_ready_01";
    private String connectionMode = "DIRECT";
    private String payloadUuid;

    public enum AuthenticationType { PASSWORD, PRIVATE_KEY }
    public UUID uuid = UUID.randomUUID();
    public SSHConnectionProfile() {}
    public SSHConnectionProfile(String serverIP, int serverPort, String username, AuthenticationType authenticationType) {
        this.serverIP = serverIP; this.serverPort = serverPort; this.username = username; this.authenticationType = authenticationType;
    }
    public static SSHConnectionProfile fromLinkedTreeMap(Object value) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(gson.toJson(value), SSHConnectionProfile.class);
    }
    public String getServerIP() { return serverIP; }
    public void setServerIP(String value) { serverIP = value; }
    public int getServerPort() { return serverPort; }
    public void setServerPort(int value) { serverPort = value; }
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public AuthenticationType getAuthenticationType() { return authenticationType; }
    public void setAuthenticationType(AuthenticationType value) { authenticationType = value; }
    public void setPassword(String value) { password = value; }
    public void setPrivateKey(String value) { privateKey = value; }
    public String getPassword() { return password; }
    public String getPrivateKey() { return privateKey; }
    public String getConnectionMode() { return connectionMode == null ? "DIRECT" : connectionMode; }
    public void setConnectionMode(String value) { connectionMode = value; }
    public String getPayloadUuid() { return payloadUuid; }
    public void setPayloadUuid(String value) { payloadUuid = value; }
    public String getProfileName() { return (profileName == null || profileName.isEmpty()) ? (serverIP != null ? serverIP : "bd_ready_01") : profileName; }
    public void setProfileName(String value) { profileName = value; }

    public SSHConnectionProfile copyAsNew(String newName) {
        SSHConnectionProfile copy = new SSHConnectionProfile();
        copy.uuid = UUID.randomUUID();
        copy.setProfileName((newName != null && !newName.trim().isEmpty()) ? newName : (this.getProfileName() + " (สำเนา)"));
        copy.setServerIP(this.getServerIP());
        copy.setServerPort(this.getServerPort());
        copy.setUsername(this.getUsername());
        copy.setPassword(this.getPassword());
        copy.setPrivateKey(this.getPrivateKey());
        copy.setAuthenticationType(this.getAuthenticationType());
        copy.setConnectionMode(this.getConnectionMode());
        copy.setPayloadUuid(this.getPayloadUuid());
        return copy;
    }
}