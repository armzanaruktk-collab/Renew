package ru.anton2319.vpnoverssh.utils;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles HTTP Custom and HTTP Injector payload variables, tags, and split-packet rules.
 * Supports:
 * - [host], [port], [host_port], [ssh], [real_host], [real_port]
 * - [ip], [ip_port]
 * - [proxy], [proxy_host], [proxy_port]
 * - [protocol] (HTTP/1.1)
 * - [netData], [raw], [real_raw]
 * - [ua], [user_agent]
 * - [keep], [close]
 * - [crlf], [cr], [lf], [lfcr], [tab]
 * - [auth]
 * - [rotate=A;B;C]
 * - [split], [delay_split], [delay_split=N], [instant_split], [reverse_split]
 */
public class HttpCustomPayloadHandler {

    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final AtomicInteger ROTATE_COUNTER = new AtomicInteger(0);

    public static class PayloadStep {
        public final byte[] data;
        public final int delayMs;
        public final boolean flush;

        public PayloadStep(byte[] data, int delayMs, boolean flush) {
            this.data = data;
            this.delayMs = delayMs;
            this.flush = flush;
        }
    }

    /**
     * Replaces all HTTP Custom variables inside the payload template.
     */
    public static String expandPayload(String template, String sshHost, int sshPort, String proxyHost, int proxyPort, String proxyAuth) {
        if (template == null || template.trim().isEmpty()) {
            return "CONNECT " + sshHost + ":" + sshPort + " HTTP/1.1\r\nHost: " + sshHost + ":" + sshPort + "\r\n\r\n";
        }

        String result = template;
        String hostPort = sshHost + ":" + sshPort;
        String ip = resolveHostIp(sshHost);
        String ipPort = ip + ":" + sshPort;

        String pHost = proxyHost != null ? proxyHost.trim() : "";
        String pPort = proxyPort > 0 ? String.valueOf(proxyPort) : "";
        String pCombined = pHost + (proxyPort > 0 ? (":" + proxyPort) : "");

        // 1. Process rotation tags first: [rotate=val1;val2;val3] or [rotate=val1|val2]
        Pattern rotatePattern = Pattern.compile("\\[rotate=([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
        Matcher rotateMatcher = rotatePattern.matcher(result);
        StringBuffer sbRotate = new StringBuffer();
        while (rotateMatcher.find()) {
            String optionsStr = rotateMatcher.group(1);
            String[] options = optionsStr.split("[;|,]");
            if (options.length > 0) {
                int idx = Math.abs(ROTATE_COUNTER.getAndIncrement() % options.length);
                rotateMatcher.appendReplacement(sbRotate, Matcher.quoteReplacement(options[idx].trim()));
            } else {
                rotateMatcher.appendReplacement(sbRotate, "");
            }
        }
        rotateMatcher.appendTail(sbRotate);
        result = sbRotate.toString();

        // 2. Network Data & Raw templates
        result = replaceIgnoreCase(result, "\\[netData\\]", "CONNECT " + hostPort + " HTTP/1.1\r\n");
        result = replaceIgnoreCase(result, "\\[real_raw\\]", "CONNECT " + hostPort + " HTTP/1.1\r\n\r\n");
        result = replaceIgnoreCase(result, "\\[raw\\]", "CONNECT " + hostPort + " HTTP/1.1\r\n\r\n");

        // 3. Host and port variations
        result = replaceIgnoreCase(result, "\\[host_port\\]", hostPort);
        result = replaceIgnoreCase(result, "\\[ssh\\]", hostPort);
        result = replaceIgnoreCase(result, "\\[ip_port\\]", ipPort);
        result = replaceIgnoreCase(result, "\\[ip\\]", ip);
        result = replaceIgnoreCase(result, "\\[real_host\\]", sshHost);
        result = replaceIgnoreCase(result, "\\[real_port\\]", String.valueOf(sshPort));
        result = replaceIgnoreCase(result, "\\[host\\]", sshHost);
        result = replaceIgnoreCase(result, "\\[port\\]", String.valueOf(sshPort));

        // 4. Proxy variations
        result = replaceIgnoreCase(result, "\\[proxy_host\\]", pHost);
        result = replaceIgnoreCase(result, "\\[proxy_port\\]", pPort);
        result = replaceIgnoreCase(result, "\\[proxy\\]", pCombined);

        // 5. Protocol & User Agent & Headers
        result = replaceIgnoreCase(result, "\\[protocol\\]", "HTTP/1.1");
        result = replaceIgnoreCase(result, "\\[user_agent\\]", DEFAULT_UA);
        result = replaceIgnoreCase(result, "\\[ua\\]", DEFAULT_UA);
        result = replaceIgnoreCase(result, "\\[keep\\]", "Connection: Keep-Alive");
        result = replaceIgnoreCase(result, "\\[close\\]", "Connection: Close");

        // 6. Proxy Authentication
        if (proxyAuth != null && !proxyAuth.trim().isEmpty()) {
            String encodedAuth = java.util.Base64.getEncoder().encodeToString(proxyAuth.getBytes(StandardCharsets.UTF_8));
            result = replaceIgnoreCase(result, "\\[auth\\]", "Proxy-Authorization: Basic " + encodedAuth);
        } else {
            result = replaceIgnoreCase(result, "\\[auth\\]", "");
        }

        // 7. Line breaks & control characters
        result = replaceIgnoreCase(result, "\\[lfcr\\]", "\n\r");
        result = replaceIgnoreCase(result, "\\[crlf\\]", "\r\n");
        result = replaceIgnoreCase(result, "\\[cr\\]", "\r");
        result = replaceIgnoreCase(result, "\\[lf\\]", "\n");
        result = replaceIgnoreCase(result, "\\[tab\\]", "\t");
        result = replaceIgnoreCase(result, "\\[custom\\]", "");

        // 8. Normalize literal newlines into \r\n (if any standalone \n or \r without \r\n)
        result = normalizeNewlines(result);

        return result;
    }

    /**
     * Parses the expanded payload into executable transmission steps for the socket,
     * honoring [reverse_split], [delay_split], [instant_split], and [split].
     */
    public static List<PayloadStep> parsePayloadSteps(String expandedPayload) {
        List<PayloadStep> steps = new ArrayList<>();
        if (expandedPayload == null || expandedPayload.isEmpty()) {
            return steps;
        }

        // Handle reverse split first: sends part 2 then part 1
        Pattern revPattern = Pattern.compile("\\[reverse_split\\]", Pattern.CASE_INSENSITIVE);
        String[] revParts = revPattern.split(expandedPayload, 2);
        if (revParts.length == 2) {
            String part1 = revParts[0];
            String part2 = revParts[1];
            // Send part 2 first, then part 1
            steps.addAll(parseSplitChunks(part2));
            steps.addAll(parseSplitChunks(part1));
            return steps;
        }

        return parseSplitChunks(expandedPayload);
    }

    private static List<PayloadStep> parseSplitChunks(String content) {
        List<PayloadStep> steps = new ArrayList<>();
        // Split regex matching [delay_split=N], [delay_split], [instant_split], [split]
        Pattern splitPattern = Pattern.compile("(\\[delay_split=\\d+\\]|\\[delay_split\\]|\\[instant_split\\]|\\[split\\])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = splitPattern.matcher(content);

        int lastIndex = 0;
        while (matcher.find()) {
            String chunk = content.substring(lastIndex, matcher.start());
            String tag = matcher.group(1).toLowerCase();

            int delay = 50;
            if (tag.startsWith("[delay_split=")) {
                try {
                    String num = tag.substring(13, tag.length() - 1);
                    delay = Integer.parseInt(num);
                } catch (Exception e) {
                    delay = 1000;
                }
            } else if (tag.equals("[delay_split]")) {
                delay = 1000;
            } else if (tag.equals("[instant_split]")) {
                delay = 0;
            } else if (tag.equals("[split]")) {
                delay = 50;
            }

            if (!chunk.isEmpty()) {
                steps.add(new PayloadStep(chunk.getBytes(StandardCharsets.ISO_8859_1), delay, true));
            }
            lastIndex = matcher.end();
        }

        if (lastIndex < content.length()) {
            String remaining = content.substring(lastIndex);
            if (!remaining.isEmpty()) {
                steps.add(new PayloadStep(remaining.getBytes(StandardCharsets.ISO_8859_1), 0, true));
            }
        }

        if (steps.isEmpty()) {
            steps.add(new PayloadStep(content.getBytes(StandardCharsets.ISO_8859_1), 0, true));
        }

        return steps;
    }

    private static String replaceIgnoreCase(String source, String regexTag, String replacement) {
        return Pattern.compile(regexTag, Pattern.CASE_INSENSITIVE)
                .matcher(source)
                .replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String resolveHostIp(String host) {
        if (host == null || host.trim().isEmpty()) return "";
        try {
            return InetAddress.getByName(host.trim()).getHostAddress();
        } catch (Exception e) {
            return host.trim();
        }
    }

    private static String normalizeNewlines(String input) {
        // Replace CRLF temporarily to protect already valid endings
        String temp = input.replace("\r\n", "\n");
        // Replace standalone \r with \n
        temp = temp.replace("\r", "\n");
        // Re-expand all \n to \r\n
        return temp.replace("\n", "\r\n");
    }
}
