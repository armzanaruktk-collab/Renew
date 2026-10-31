package ru.anton2319.vpnoverssh.data;

import java.util.UUID;

public class PayloadProfile {
    public String uuid = UUID.randomUUID().toString();
    public String name = "";
    public String description = "";
    public String proxyHost = "";
    public int proxyPort = 0;
    public String payload = "";

    public PayloadProfile() { }
}
