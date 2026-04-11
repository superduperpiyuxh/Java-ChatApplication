import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {

    public static final CopyOnWriteArrayList<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String clientUsername;
    private String requiredPasswordHash;

    // Rate limiting — max 10 messages per 5 seconds
    private long windowStart = System.currentTimeMillis();
    private int messageCount = 0;
    static final int MAX_MESSAGES = 10;
    static final long WINDOW_MS   = 5000;

    public ClientHandler(Socket socket, String requiredPasswordHash) {
        this.socket = socket;
        this.requiredPasswordHash = requiredPasswordHash;
        try {
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Stream setup error: " + e.getMessage());
            closeEverything();
        }
    }

    @Override
    public void run() {
        try {
            if (!authenticate()) {
                send("AUTH_FAIL");
                System.out.println("Failed auth from: " + socket.getInetAddress());
                closeEverything();
                return;
            }
            send("AUTH_OK");

            clientUsername = bufferedReader.readLine();
            if (clientUsername == null || clientUsername.trim().isEmpty()) {
                closeEverything();
                return;
            }

            clientUsername = clientUsername
                .replaceAll("[^a-zA-Z0-9_\\-]", "")
                .substring(0, Math.min(clientUsername.length(), 20));

            clientHandlers.add(this);
            System.out.println(clientUsername + " joined via terminal. Total: " + clientHandlers.size());
            broadcastToAll("SERVER: " + clientUsername + " has joined the chat!", null);

            String message;
            while (socket.isConnected()) {
                message = bufferedReader.readLine();

                if (message == null) { closeEverything(); break; }
                if (message.trim().isEmpty() || message.length() > 500) continue;

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

    // Broadcasts to all terminal clients and all web clients.
    // Pass `sender` to skip echoing back to whoever sent the message (pass null to send to everyone).
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
                System.err.println("Failed sending to web client.");
            }
        }
    }

    private boolean authenticate() throws IOException {
        String received = bufferedReader.readLine();
        return requiredPasswordHash.equals(received);
    }

    private boolean isRateLimited() {
        long now = System.currentTimeMillis();
        if (now - windowStart > WINDOW_MS) {
            windowStart = now;
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
            System.out.println(clientUsername + " disconnected. Total: " + clientHandlers.size());
            broadcastToAll("SERVER: " + clientUsername + " has left the chat.", null);
        }
    }

    public void closeEverything() {
        removeClientHandler();
        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection.");
        }
    }
}
