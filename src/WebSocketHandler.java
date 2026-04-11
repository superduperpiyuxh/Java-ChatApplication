import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WebSocketHandler implements Runnable {

    private Socket socket;
    private InputStream in;
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
            if (!doHandshake()) {
                socket.close();
                return;
            }

            // First frame must be the password hash
            String passwordHash = readFrame();
            if (passwordHash == null || !Server.PASSWORD_HASH.equals(passwordHash.trim())) {
                sendFrame("AUTH_FAIL");
                System.out.println("Browser client failed auth: " + socket.getInetAddress());
                socket.close();
                return;
            }
            sendFrame("AUTH_OK");

            // Second frame is the username
            username = readFrame();
            if (username == null || username.trim().isEmpty()) {
                socket.close();
                return;
            }

            // FIX: clean the username first, THEN take substring of the cleaned result.
            // Before, substring used username.length() (pre-clean length) which could
            // exceed the cleaned string's length and throw StringIndexOutOfBoundsException.
            username = username.replaceAll("[^a-zA-Z0-9_\\-]", "");
            username = username.substring(0, Math.min(username.length(), 20));
            username = "[WEB] " + username;

            Server.webClients.add(this);
            System.out.println(username + " joined via browser. Web clients: " + Server.webClients.size());
            ClientHandler.broadcastToAll("SERVER: " + username + " has joined the chat!", null);

            // Listen for messages
            String message;
            while (!socket.isClosed()) {
                message = readFrame();
                if (message == null) break;
                if (message.trim().isEmpty() || message.length() > 500) continue;

                ClientHandler.broadcastToAll(username + ": " + message, this);
            }

        } catch (IOException e) {
            // client disconnected normally
        } catch (java.security.NoSuchAlgorithmException e) {
            System.err.println("SHA-1 not available: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    public void sendFrame(String message) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();

        frame.write(0x81); // FIN bit + text opcode

        if (data.length <= 125) {
            frame.write(data.length);
        } else if (data.length <= 65535) {
            frame.write(126);
            frame.write((data.length >> 8) & 0xFF);
            frame.write(data.length & 0xFF);
        } else {
            frame.write(127);
            for (int i = 7; i >= 0; i--) {
                frame.write((data.length >> (i * 8)) & 0xFF);
            }
        }

        frame.write(data);
        out.write(frame.toByteArray());
        out.flush();
    }

    private String readFrame() throws IOException {
        int b1 = in.read();
        if (b1 == -1) return null;

        int b2 = in.read();
        if (b2 == -1) return null;

        // Opcode 8 = close frame
        if ((b1 & 0x0F) == 8) return null;

        boolean masked = (b2 & 0x80) != 0;
        int payloadLen = b2 & 0x7F;

        if (payloadLen == 126) {
            payloadLen = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        } else if (payloadLen == 127) {
            for (int i = 0; i < 8; i++) in.read();
            return null; // too large, ignore
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
            for (int i = 0; i < payloadLen; i++) {
                payload[i] ^= mask[i % 4];
            }
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    // Reads the HTTP upgrade request and sends back the WebSocket handshake response.
    // Uses raw byte reading to avoid BufferedReader consuming bytes past the headers.
    private boolean doHandshake() throws IOException, java.security.NoSuchAlgorithmException {
        StringBuilder headers = new StringBuilder();

        // Read until we hit the blank line (\r\n\r\n) that ends HTTP headers
        while (true) {
            int curr = in.read();
            if (curr == -1) return false;
            headers.append((char) curr);

            String tail = headers.length() >= 4
                ? headers.substring(headers.length() - 4)
                : headers.toString();

            if (tail.equals("\r\n\r\n")) break;
        }

        String wsKey = null;
        for (String line : headers.toString().split("\r\n")) {
            if (line.startsWith("Sec-WebSocket-Key:")) {
                wsKey = line.substring(line.indexOf(':') + 1).trim();
            }
        }

        if (wsKey == null) return false;

        String acceptKey = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                .getBytes(StandardCharsets.UTF_8))
        );

        String response =
            "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
        return true;
    }

    private void disconnect() {
        Server.webClients.remove(this);
        if (username != null) {
            System.out.println(username + " disconnected.");
            ClientHandler.broadcastToAll("SERVER: " + username + " has left the chat.", null);
        }
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
