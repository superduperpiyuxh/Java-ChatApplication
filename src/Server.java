import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {

    private final ServerSocket serverSocket;

    private static final int MAX_CLIENTS    = 20;
    private static final int THREAD_TIMEOUT = 60;

    private final ExecutorService threadPool = new ThreadPoolExecutor(
        2, MAX_CLIENTS,
        THREAD_TIMEOUT, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(MAX_CLIENTS),
        new ThreadPoolExecutor.AbortPolicy()
    );

    // use CHAT_PASSWORD env var, fall back to default if not set
    private static final String RAW_PASSWORD =
        System.getenv("CHAT_PASSWORD") != null ? System.getenv("CHAT_PASSWORD") : "yourpassword123";

    public static final String PASSWORD_HASH = hashPassword(RAW_PASSWORD);
    public static final CopyOnWriteArrayList<WebSocketHandler> webClients = new CopyOnWriteArrayList<>();

    public Server(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public void startServer() {
        System.out.println("Server up on port " + serverSocket.getLocalPort());
        System.out.println("Max clients: " + MAX_CLIENTS);
        System.out.println("Password: " + (System.getenv("CHAT_PASSWORD") != null ? "from env" : "default"));
        System.out.println("Waiting...\n");

        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();

                int total = ClientHandler.clientHandlers.size() + webClients.size();
                if (total >= MAX_CLIENTS) {
                    System.out.println("Full, rejecting " + socket.getInetAddress());
                    socket.close();
                    continue;
                }

                // peek at first 4 bytes to figure out if it's a browser or terminal
                socket.setSoTimeout(3000);
                InputStream in = socket.getInputStream();
                byte[] buf = new byte[4];
                int read = in.read(buf, 0, 4);
                socket.setSoTimeout(30000);

                if (read < 1) { socket.close(); continue; }

                String peek = new String(buf, 0, read, StandardCharsets.UTF_8);
                boolean isBrowser = peek.startsWith("GET ") || peek.startsWith("POST");
                PushedBackSocket wrapped = new PushedBackSocket(socket, buf, read);

                try {
                    if (isBrowser) {
                        System.out.println("Browser: " + socket.getInetAddress());
                        threadPool.execute(new WebSocketHandler(wrapped));
                    } else {
                        System.out.println("Terminal: " + socket.getInetAddress());
                        threadPool.execute(new ClientHandler(wrapped, PASSWORD_HASH));
                    }
                } catch (RejectedExecutionException e) {
                    System.err.println("Thread pool full, dropping " + socket.getInetAddress());
                    socket.close();
                }

            } catch (IOException e) {
                if (!serverSocket.isClosed()) System.err.println("Error: " + e.getMessage());
                break;
            }
        }

        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS))
                threadPool.shutdownNow();
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(5000));

            Server server = new Server(serverSocket);

            // tell everyone the server is going down before killing it
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down...");
                ClientHandler.broadcastToAll("SERVER: Server is shutting down. Goodbye!", null);
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                try { if (!serverSocket.isClosed()) serverSocket.close(); } catch (IOException ignored) {}
            }));

            server.startServer();

        } catch (IOException e) {
            System.err.println("Could not start: " + e.getMessage());
        }
    }
}
