import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {

    private ServerSocket serverSocket;

    // Max 20 clients total — prevents thread explosion crashing your system
    private static final int MAX_CLIENTS    = 20;
    private static final int THREAD_TIMEOUT = 60; // seconds before idle thread is released

    // Fixed thread pool — will never spawn more than MAX_CLIENTS threads
    private final ExecutorService threadPool = new ThreadPoolExecutor(
        2,                                       // keep 2 threads alive always
        MAX_CLIENTS,                             // never exceed this
        THREAD_TIMEOUT, TimeUnit.SECONDS,        // idle threads die after 60s
        new LinkedBlockingQueue<>(MAX_CLIENTS),  // queue up to MAX_CLIENTS waiting
        new ThreadPoolExecutor.AbortPolicy()     // reject if full — no silent crashes
    );

    public static final String PASSWORD_HASH = hashPassword("yourpassword123");
    public static final CopyOnWriteArrayList<WebSocketHandler> webClients = new CopyOnWriteArrayList<>();

    public Server(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public void startServer() {
        System.out.println("Server started on port " + serverSocket.getLocalPort());
        System.out.println("Max clients: " + MAX_CLIENTS);
        System.out.println("Password protection: ON");
        System.out.println("Waiting for connections...\n");

        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();

                // Check if we're already at capacity before doing anything
                int total = ClientHandler.clientHandlers.size() + webClients.size();
                if (total >= MAX_CLIENTS) {
                    System.out.println("Max clients reached. Rejecting: " + socket.getInetAddress());
                    socket.close();
                    continue;
                }

                // Set a timeout so dead connections don't hang forever
                socket.setSoTimeout(3000);
                InputStream in = socket.getInputStream();
                byte[] buf = new byte[4];
                int read = in.read(buf, 0, 4);
                socket.setSoTimeout(30000); // 30s timeout for normal operation

                if (read < 1) { socket.close(); continue; }

                String peek = new String(buf, 0, read, StandardCharsets.UTF_8);
                boolean isBrowser = peek.startsWith("GET ") || peek.startsWith("POST");

                PushedBackSocket wrapped = new PushedBackSocket(socket, buf, read);

                try {
                    if (isBrowser) {
                        System.out.println("Browser connecting: " + socket.getInetAddress());
                        threadPool.execute(new WebSocketHandler(wrapped));
                    } else {
                        System.out.println("Terminal connecting: " + socket.getInetAddress());
                        threadPool.execute(new ClientHandler(wrapped, PASSWORD_HASH));
                    }
                } catch (RejectedExecutionException e) {
                    // Thread pool is full — tell client and close cleanly
                    System.err.println("Thread pool full. Rejecting connection.");
                    socket.close();
                }

            } catch (IOException e) {
                if (serverSocket.isClosed()) {
                    System.out.println("Server shut down.");
                } else {
                    System.err.println("Connection error: " + e.getMessage());
                }
                break;
            }
        }

        // Shut down thread pool cleanly on exit
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
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
            throw new RuntimeException("Hashing failed", e);
        }
    }

    public static void main(String[] args) {
        try {
            // FIX: setReuseAddress MUST be called before bind, not after.
            // Doing it after new ServerSocket(5000) is too late — the socket is already bound.
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(5000));

            Server server = new Server(serverSocket);
            server.startServer();
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }
}
