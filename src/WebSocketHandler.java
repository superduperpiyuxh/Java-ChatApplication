import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WebSocketHandler implements Runnable {

    private final Socket socket;
    private InputStream  in;
    private OutputStream out;
    private String username;

    public WebSocketHandler(Socket socket) {
        this.socket = socket;
        try {
            this.in  = socket.getInputStream();
            this.out = socket.getOutputStream();
        } catch (IOException e) {
            System.err.println("WebSocket setup error: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            if (!doHandshake()) { socket.close(); return; }

            String passwordHash = readFrame();
            if (passwordHash == null || !Server.PASSWORD_HASH.equals(passwordHash.trim())) {
                sendFrame("AUTH_FAIL");
                System.out.println("Bad auth from " + socket.getInetAddress());
                socket.close();
                return;
            }
            sendFrame("AUTH_OK");

            String rawUsername = readFrame();
            if (rawUsername == null || rawUsername.trim().isEmpty()) {
                sendFrame("ERROR: Username cannot be empty.");
                socket.close();
                return;
            }

            username = rawUsername.replaceAll("[^a-zA-Z0-9_\\-]", "");
            username = username.substring(0, Math.min(username.length(), 20));

            if (username.isEmpty()) {
                sendFrame("ERROR: Username has no valid characters. Use letters, numbers, _ or -");
                socket.close();
                return;
            }

            username = "[WEB] " + username;

            Server.webClients.add(this);
            System.out.println(username + " joined (browser). Total web: " + Server.webClients.size());
            ClientHandler.broadcastToAll("SERVER: " + username + " has joined the chat!", null);

            String message;
            while (!socket.isClosed()) {
                message = readFrame();
                if (message == null) break;
                if (message.trim().isEmpty()) continue;

                if (message.length() > 500) {
                    sendFrame("SERVER: Message too long (max 500 chars).");
                    continue;
                }

                ClientHandler.broadcastToAll(username + ": " + message, this);
            }

        } catch (IOException e) {
            // disconnected, nothing to do
        } catch (java.security.NoSuchAlgorithmException e) {
            System.err.println("SHA-1 missing: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    public void sendFrame(String message) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81);

        if (data.length <= 125) {
            frame.write(data.length);
        } else if (data.length <= 65535) {
            frame.write(126);
            frame.write((data.length >> 8) & 0xFF);
            frame.write(data.length & 0xFF);
        } else {
            frame.write(127);
            for (int i = 7; i >= 0; i--) frame.write((data.length >> (i * 8)) & 0xFF);
        }

        frame.write(data);
        out.write(frame.toByteArray());
        out.flush();
    }

    private String readFrame() throws IOException {
        int b1 = in.read(); if (b1 == -1) return null;
        int b2 = in.read(); if (b2 == -1) return null;
        if ((b1 & 0x0F) == 8) return null; // close frame

        boolean masked     = (b2 & 0x80) != 0;
        int     payloadLen =  b2 & 0x7F;

        if (payloadLen == 126) {
            payloadLen = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        } else if (payloadLen == 127) {
            for (int i = 0; i < 8; i++) in.read();
            return null; // too big, ignore
        }

        byte[] mask = new byte[4];
        if (masked) {
            int got = 0;
            while (got < 4) got += in.read(mask, got, 4 - got);
        }

        byte[] payload = new byte[payloadLen];
        int read = 0;
        while (read < payloadLen) {
            int r = in.read(payload, read, payloadLen - read);
            if (r == -1) return null;
            read += r;
        }

        if (masked) {
            for (int i = 0; i < payloadLen; i++) payload[i] ^= mask[i % 4];
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    private boolean doHandshake() throws IOException, java.security.NoSuchAlgorithmException {
        StringBuilder headers = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c == -1) return false;
            headers.append((char) c);
            if (headers.length() >= 4 && headers.substring(headers.length() - 4).equals("\r\n\r\n")) break;
        }

        String wsKey = null;
        for (String line : headers.toString().split("\r\n")) {
            if (line.startsWith("Sec-WebSocket-Key:"))
                wsKey = line.substring(line.indexOf(':') + 1).trim();
        }
        if (wsKey == null) return false;

        String acceptKey = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8))
        );

        out.write((
            "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n"
        ).getBytes(StandardCharsets.UTF_8));
        out.flush();
        return true;
    }

    private void disconnect() {
        Server.webClients.remove(this);
        if (username != null) {
            System.out.println(username + " disconnected.");
            ClientHandler.broadcastToAll("SERVER: " + username + " has left the chat.", null);
        }
        try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }
}
