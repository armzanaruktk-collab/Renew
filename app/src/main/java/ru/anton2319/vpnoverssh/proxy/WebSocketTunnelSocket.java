package ru.anton2319.vpnoverssh.proxy;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Adapts a WebSocket binary-message connection to the byte-stream Socket API
 * expected by the SSH transport. Client frames are masked as required by RFC 6455.
 */
public final class WebSocketTunnelSocket extends Socket {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_FRAME_SIZE = 16 * 1024 * 1024;

    private final Socket delegate;
    private final InputStream networkIn;
    private final OutputStream networkOut;
    private final InputStream tunnelIn = new TunnelInput();
    private final OutputStream tunnelOut = new TunnelOutput();
    private final SecureRandom random = new SecureRandom();
    private volatile boolean closed;

    public WebSocketTunnelSocket(Socket delegate, InputStream networkIn) throws IOException {
        this.delegate = delegate;
        this.networkIn = networkIn;
        this.networkOut = delegate.getOutputStream();
    }

    public static boolean isValidAccept(String key, String accept) {
        try {
            String expected = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1")
                            .digest((key.trim() + GUID).getBytes(StandardCharsets.ISO_8859_1)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    accept.trim().getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }

    @Override public InputStream getInputStream() { return tunnelIn; }
    @Override public OutputStream getOutputStream() { return tunnelOut; }

    private synchronized void sendFrame(int opcode, byte[] data, int offset, int length) throws IOException {
        if (closed) throw new SocketException("WebSocket tunnel is closed");
        if (length < 0) throw new IllegalArgumentException("negative frame length");
        networkOut.write(0x80 | (opcode & 0x0f));
        if (length <= 125) {
            networkOut.write(0x80 | length);
        } else if (length <= 65535) {
            networkOut.write(0x80 | 126);
            networkOut.write((length >>> 8) & 0xff);
            networkOut.write(length & 0xff);
        } else {
            networkOut.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) networkOut.write((int)(((long)length >>> (8 * i)) & 0xff));
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        networkOut.write(mask);
        for (int i = 0; i < length; i++) networkOut.write(data[offset + i] ^ mask[i & 3]);
        networkOut.flush();
    }

    private final class TunnelOutput extends OutputStream {
        @Override public void write(int value) throws IOException {
            byte[] one = {(byte)value};
            sendFrame(2, one, 0, 1);
        }
        @Override public void write(byte[] data, int offset, int length) throws IOException {
            if (data == null) throw new NullPointerException("data");
            if (offset < 0 || length < 0 || length > data.length - offset) throw new IndexOutOfBoundsException();
            if (length == 0) return;
            int pos = offset;
            while (pos < offset + length) {
                int n = Math.min(16 * 1024, offset + length - pos);
                sendFrame(2, data, pos, n);
                pos += n;
            }
        }
        @Override public void flush() throws IOException { networkOut.flush(); }
        @Override public void close() throws IOException { WebSocketTunnelSocket.this.close(); }
    }

    private final class TunnelInput extends InputStream {
        private byte[] current = new byte[0];
        private int position;
        private boolean ended;

        private int readNetworkByte() throws IOException {
            int value = networkIn.read();
            if (value < 0) throw new EOFException("WebSocket peer closed the TCP connection");
            return value;
        }

        private long readLength(int marker) throws IOException {
            if (marker < 126) return marker;
            if (marker == 126) return ((long)readNetworkByte() << 8) | readNetworkByte();
            long value = 0;
            for (int i = 0; i < 8; i++) {
                int b = readNetworkByte();
                if (i == 0 && (b & 0x80) != 0) throw new IOException("Invalid WebSocket frame length");
                value = (value << 8) | b;
            }
            return value;
        }

        private boolean nextDataFrame() throws IOException {
            while (!ended) {
                int first = readNetworkByte();
                int second = readNetworkByte();
                boolean fin = (first & 0x80) != 0;
                int opcode = first & 0x0f;
                boolean masked = (second & 0x80) != 0;
                long length = readLength(second & 0x7f);
                if (length > MAX_FRAME_SIZE) throw new IOException("WebSocket frame exceeds safety limit");
                byte[] mask = masked ? new byte[]{(byte)readNetworkByte(), (byte)readNetworkByte(),
                        (byte)readNetworkByte(), (byte)readNetworkByte()} : null;
                byte[] payload = new byte[(int)length];
                int read = 0;
                while (read < payload.length) {
                    int n = networkIn.read(payload, read, payload.length - read);
                    if (n < 0) throw new EOFException("Truncated WebSocket frame");
                    read += n;
                }
                if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];

                if (opcode == 8) {
                    ended = true;
                    try { sendFrame(8, payload, 0, payload.length); } catch (IOException ignored) { }
                    return false;
                }
                if (opcode == 9) { sendFrame(10, payload, 0, payload.length); continue; }
                if (opcode == 10) continue;
                if (opcode == 1) throw new IOException("WebSocket text frames are not valid SSH transport data");
                if (opcode != 0 && opcode != 2) throw new IOException("Unsupported WebSocket opcode: " + opcode);
                if (!fin && opcode == 2) throw new IOException("Fragmented WebSocket messages are not supported by this transport");
                if (opcode == 0) throw new IOException("Unexpected WebSocket continuation frame");
                current = payload;
                position = 0;
                if (current.length > 0) return true;
            }
            return false;
        }

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override public int read(byte[] data, int offset, int length) throws IOException {
            if (data == null) throw new NullPointerException("data");
            if (offset < 0 || length < 0 || length > data.length - offset) throw new IndexOutOfBoundsException();
            if (length == 0) return 0;
            while (position >= current.length) if (!nextDataFrame()) return -1;
            int n = Math.min(length, current.length - position);
            System.arraycopy(current, position, data, offset, n);
            position += n;
            return n;
        }
    }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            byte[] code = ByteBuffer.allocate(2).putShort((short)1000).array();
            // Close frame is sent before the local closed flag is observed by sendFrame.
            networkOut.write(0x88);
            networkOut.write(0x82);
            byte[] mask = new byte[4]; random.nextBytes(mask);
            networkOut.write(mask);
            networkOut.write(code[0] ^ mask[0]);
            networkOut.write(code[1] ^ mask[1]);
            networkOut.flush();
        } catch (Exception ignored) { }
        delegate.close();
    }

    @Override public boolean isConnected() { return delegate.isConnected(); }
    @Override public boolean isClosed() { return closed || delegate.isClosed(); }
    @Override public boolean isBound() { return delegate.isBound(); }
    @Override public boolean isInputShutdown() { return delegate.isInputShutdown(); }
    @Override public boolean isOutputShutdown() { return delegate.isOutputShutdown(); }
    @Override public void setTcpNoDelay(boolean on) throws SocketException { delegate.setTcpNoDelay(on); }
    @Override public boolean getTcpNoDelay() throws SocketException { return delegate.getTcpNoDelay(); }
    @Override public void setSoTimeout(int timeout) throws SocketException { delegate.setSoTimeout(timeout); }
    @Override public int getSoTimeout() throws SocketException { return delegate.getSoTimeout(); }
    @Override public void setKeepAlive(boolean on) throws SocketException { delegate.setKeepAlive(on); }
    @Override public boolean getKeepAlive() throws SocketException { return delegate.getKeepAlive(); }
    @Override public java.net.InetAddress getInetAddress() { return delegate.getInetAddress(); }
    @Override public int getPort() { return delegate.getPort(); }
    @Override public SocketAddress getRemoteSocketAddress() { return delegate.getRemoteSocketAddress(); }
    @Override public SocketAddress getLocalSocketAddress() { return delegate.getLocalSocketAddress(); }
}
