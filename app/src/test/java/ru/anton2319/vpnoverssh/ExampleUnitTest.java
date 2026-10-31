package ru.anton2319.vpnoverssh;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testSshProfileCopyAsNew() {
        ru.anton2319.vpnoverssh.data.SSHConnectionProfile original = new ru.anton2319.vpnoverssh.data.SSHConnectionProfile(
                "128.199.200.1", 22, "root", ru.anton2319.vpnoverssh.data.SSHConnectionProfile.AuthenticationType.PASSWORD
        );
        original.setProfileName("Main-Server");
        original.setPassword("secret123");
        original.setConnectionMode("PAYLOAD");
        original.setPayloadUuid("payload-abc");

        ru.anton2319.vpnoverssh.data.SSHConnectionProfile copied = original.copyAsNew("New-Server-Backup");

        assertNotNull(copied.uuid);
        assertNotEquals(original.uuid, copied.uuid);
        assertEquals("New-Server-Backup", copied.getProfileName());
        assertEquals("128.199.200.1", copied.getServerIP());
        assertEquals(22, copied.getServerPort());
        assertEquals("root", copied.getUsername());
        assertEquals("secret123", copied.getPassword());
        assertEquals("PAYLOAD", copied.getConnectionMode());
        assertEquals("payload-abc", copied.getPayloadUuid());
    }

    @Test
    public void testSshProfileDefaultName() {
        ru.anton2319.vpnoverssh.data.SSHConnectionProfile p = new ru.anton2319.vpnoverssh.data.SSHConnectionProfile();
        p.setServerIP("1.2.3.4");
        p.setProfileName("");
        assertEquals("1.2.3.4", p.getProfileName());
    }

    @Test
    public void testHttpCustomVariablesExpansion() {
        String template = "CONNECT [host_port] [protocol][crlf]Host: [host][crlf]Port: [port][crlf]User-Agent: [ua][crlf][keep][crlf][crlf]";
        String expanded = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                template, "sg.server.com", 22, "proxy.local", 8080, null
        );

        assertTrue(expanded.contains("CONNECT sg.server.com:22 HTTP/1.1\r\n"));
        assertTrue(expanded.contains("Host: sg.server.com\r\n"));
        assertTrue(expanded.contains("Port: 22\r\n"));
        assertTrue(expanded.contains("User-Agent: Mozilla/5.0"));
        assertTrue(expanded.contains("Connection: Keep-Alive\r\n"));
        assertTrue(expanded.endsWith("\r\n\r\n"));
    }

    @Test
    public void testHttpCustomCaseInsensitivity() {
        String template = "CONNECT [HOST_PORT] [PROTOCOL][CRLF]Host: [HOST][CRLF]Port: [PORT][CRLF][KEEP][CRLF][CRLF]";
        String expanded = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                template, "sg2.server.com", 443, "1.2.3.4", 80, null
        );

        assertTrue(expanded.contains("CONNECT sg2.server.com:443 HTTP/1.1\r\n"));
        assertTrue(expanded.contains("Host: sg2.server.com\r\n"));
        assertTrue(expanded.contains("Port: 443\r\n"));
        assertTrue(expanded.contains("Connection: Keep-Alive\r\n"));
    }

    @Test
    public void testHttpCustomNetDataAndRaw() {
        String templateNet = "[netData]Host: [host][crlf][crlf]";
        String expandedNet = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                templateNet, "ssh.net", 22, null, 0, null
        );
        assertTrue(expandedNet.startsWith("CONNECT ssh.net:22 HTTP/1.1\r\nHost: ssh.net\r\n\r\n"));

        String templateRaw = "[raw]";
        String expandedRaw = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                templateRaw, "ssh.net", 22, null, 0, null
        );
        assertEquals("CONNECT ssh.net:22 HTTP/1.1\r\n\r\n", expandedRaw);
    }

    @Test
    public void testHttpCustomSplitSteps() {
        String template = "GET / [protocol][crlf]Host: bug.com[crlf][split]CONNECT [host_port] [protocol][crlf]Host: [host_port][crlf][crlf]";
        String expanded = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                template, "128.199.1.1", 22, "proxy.isp.com", 8080, null
        );

        java.util.List<ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.PayloadStep> steps =
                ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.parsePayloadSteps(expanded);

        assertEquals(2, steps.size());
        assertEquals(50, steps.get(0).delayMs);
        assertTrue(steps.get(0).flush);
        assertEquals(0, steps.get(1).delayMs);
        assertTrue(steps.get(1).flush);
    }

    @Test
    public void testHttpCustomDelaySplit() {
        String template = "[raw][crlf]Host: [host][crlf][delay_split]Connection: Keep-Alive[crlf][crlf]";
        String expanded = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                template, "ssh.test", 22, "proxy.test", 8080, null
        );

        java.util.List<ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.PayloadStep> steps =
                ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.parsePayloadSteps(expanded);

        assertEquals(2, steps.size());
        assertEquals(1000, steps.get(0).delayMs);

        String customDelayTemplate = "[raw][crlf][delay_split=400]Host: [host][crlf][crlf]";
        String expandedCustom = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                customDelayTemplate, "ssh.test", 22, "proxy.test", 8080, null
        );
        java.util.List<ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.PayloadStep> stepsCustom =
                ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.parsePayloadSteps(expandedCustom);

        assertEquals(2, stepsCustom.size());
        assertEquals(400, stepsCustom.get(0).delayMs);
    }

    @Test
    public void testHttpCustomReverseSplit() {
        String template = "PART_A[reverse_split]PART_B";
        String expanded = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                template, "ssh.test", 22, null, 0, null
        );

        java.util.List<ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.PayloadStep> steps =
                ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.parsePayloadSteps(expanded);

        assertEquals(2, steps.size());
        String firstSent = new String(steps.get(0).data, java.nio.charset.StandardCharsets.ISO_8859_1);
        String secondSent = new String(steps.get(1).data, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertEquals("PART_B", firstSent);
        assertEquals("PART_A", secondSent);
    }

    @Test
    public void testHttpCustomAuthAndProxyVariables() {
        String template = "Proxy: [proxy][crlf]Host: [proxy_host][crlf]Port: [proxy_port][crlf][auth][crlf][crlf]";
        String expanded = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                template, "ssh.test", 22, "proxy.server", 3128, "admin:secret"
        );

        assertTrue(expanded.contains("Proxy: proxy.server:3128\r\n"));
        assertTrue(expanded.contains("Host: proxy.server\r\n"));
        assertTrue(expanded.contains("Port: 3128\r\n"));
        assertTrue(expanded.contains("Proxy-Authorization: Basic "));
    }

    @Test
    public void testHttpCustomRotation() {
        String template = "Host: [rotate=host1.com;host2.com;host3.com][crlf][crlf]";
        String first = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                template, "ssh.test", 22, null, 0, null
        );
        String second = ru.anton2319.vpnoverssh.utils.HttpCustomPayloadHandler.expandPayload(
                template, "ssh.test", 22, null, 0, null
        );
        assertNotNull(first);
        assertNotNull(second);
    }
}