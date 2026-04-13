import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {

    public static final CopyOnWriteArrayList<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();

    private final Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String clientUsername;
    private final String requiredPasswordHash;

    // rate limiting
    private long windowStart  = System.currentTimeMillis();
    private int  messageCount = 0;
    private static final int  MAX_MESSAGES = 10;
    private static final long WINDOW_MS    = 5000;

    public ClientHandler(Socket socket, String requiredPasswordHash) {
        this.socket               = socket;
        this.requiredPasswordHash = requiredPasswordHash;
        try {
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Stream error: " + e.getMessage());
            closeEverything();
        }
    }

    @Override
    public void run() {
        try {
            if (!authenticate()) {
                send("AUTH_FAIL");
                System.out.println("Bad auth from " + socket.getInetAddress());
                closeEverything();
                return;
            }
            send("AUTH_OK");

            String rawUsername = bufferedReader.readLine();
            if (rawUsername == null || rawUsername.trim().isEmpty()) {
                closeEverything();
                return;
            }

            clientUsername = rawUsername.replaceAll("[^a-zA-Z0-9_\\-]", "");
            clientUsername = clientUsername.substring(0, Math.min(clientUsername.length(), 20));

            if (clientUsername.isEmpty()) {
                send("ERROR: Username has no valid characters. Use letters, numbers, _ or -");
                closeEverything();
                return;
            }

            clientHandlers.add(this);
            System.out.println(clientUsername + " joined (terminal). Total: " + clientHandlers.size());
            broadcastToAll("SERVER: " + clientUsername + " has joined the chat!", null);

            String message;
            while (socket.isConnected()) {
                message = bufferedReader.readLine();
                if (message == null) { closeEverything(); break; }
                if (message.trim().isEmpty()) continue;

                if (message.length() > 500) {
                    send("SERVER: Message too long (max 500 chars).");
                    continue;
                }

                if (isRateLimited()) {
                    send("SERVER: Slow down!");
                    continue;
                }

                broadcastToAll(message, this);
            }

        } catch (IOException e) {
            closeEverything();
        }
    }

    public static void broadcastToAll(String message, Object sender) {
        for (ClientHandler client : clientHandlers) {
            if (client == sender) continue;
            try {
                client.bufferedWriter.write(message);
                client.bufferedWriter.newLine();
                client.bufferedWriter.flush();
            } catch (IOException e) {
                client.closeEverything();
            }
        }

        for (WebSocketHandler wsh : Server.webClients) {
            if (wsh == sender) continue;
            try {
                wsh.sendFrame(message);
            } catch (IOException e) {
                System.err.println("Failed to send to web client: " + e.getMessage());
            }
        }
    }

    private boolean authenticate() throws IOException {
        String received = bufferedReader.readLine();
        return received != null && requiredPasswordHash.equals(received.trim());
    }

    private boolean isRateLimited() {
        long now = System.currentTimeMillis();
        if (now - windowStart > WINDOW_MS) {
            windowStart  = now;
            messageCount = 0;
        }
        return ++messageCount > MAX_MESSAGES;
    }

    private void send(String message) throws IOException {
        bufferedWriter.write(message);
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    public void removeClientHandler() {
        clientHandlers.remove(this);
        if (clientUsername != null) {
            System.out.println(clientUsername + " left. Total: " + clientHandlers.size());
            broadcastToAll("SERVER: " + clientUsername + " has left the chat.", null);
        }
    }

    public void closeEverything() {
        removeClientHandler();
        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
