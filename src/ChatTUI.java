import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatTUI {

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String username;

    static final String GREEN     = "\033[32m";
    static final String BGREEN    = "\033[92m";
    static final String DIM_GREEN = "\033[2;32m";
    static final String RED       = "\033[31m";
    static final String RESET     = "\033[0m";
    static final String BOLD      = "\033[1m";
    static final String CLEAR     = "\033[2J\033[H";

    static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ChatTUI(Socket socket, String username) {
        try {
            this.socket = socket;
            this.username = username;
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    public boolean authenticate(String password) {
        try {
            String hash = Server.hashPassword(password);
            bufferedWriter.write(hash);
            bufferedWriter.newLine();
            bufferedWriter.flush();

            String response = bufferedReader.readLine();
            return "AUTH_OK".equals(response);

        } catch (IOException e) {
            return false;
        }
    }

    public void start() {
        clearScreen();
        printBanner();

        try {
            bufferedWriter.write(username);
            bufferedWriter.newLine();
            bufferedWriter.flush();
        } catch (IOException e) {
            printSystem("Failed to register username.");
            return;
        }

        listenForMessages();

        Scanner scanner = new Scanner(System.in);

        // Keep looping as long as the socket is open
        while (!socket.isClosed()) {
            printPrompt();

            // nextLine() blocks — if socket drops mid-wait, user needs to
            // press Enter once before the loop condition is re-checked
            String input;
            try {
                input = scanner.nextLine();
            } catch (IllegalStateException e) {
                // Scanner closed (stdin ended)
                break;
            }

            if (input.equalsIgnoreCase("/quit")) {
                printSystem("Disconnecting...");
                closeEverything();
                break;
            }

            if (input.trim().isEmpty()) continue;

            try {
                bufferedWriter.write(username + ": " + input);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            } catch (IOException e) {
                printSystem("Failed to send message. Connection lost.");
                closeEverything();
                break;
            }
        }
    }

    private void listenForMessages() {
        new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    String message = bufferedReader.readLine();
                    if (message == null) {
                        printSystem("Server closed the connection.");
                        closeEverything();
                        break;
                    }
                    printIncoming(message);
                    printPrompt();
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        printSystem("Lost connection to server.");
                    }
                    closeEverything();
                    break;
                }
            }
        }).start();
    }

    private void clearScreen() {
        System.out.print(CLEAR);
        System.out.flush();
    }

    private void printBanner() {
        String line = "─".repeat(60);
        System.out.println(BGREEN + BOLD);
        System.out.println("  ██████╗██╗  ██╗ █████╗ ████████╗");
        System.out.println(" ██╔════╝██║  ██║██╔══██╗╚══██╔══╝");
        System.out.println(" ██║     ███████║███████║   ██║   ");
        System.out.println(" ██║     ██╔══██║██╔══██║   ██║   ");
        System.out.println(" ╚██████╗██║  ██║██║  ██║   ██║   ");
        System.out.println("  ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝  ");
        System.out.println(RESET);
        System.out.println(GREEN + "  " + line);
        System.out.println("  Connected as: " + BGREEN + BOLD + username + RESET + GREEN);
        System.out.println("  Type /quit to exit");
        System.out.println("  " + line + RESET);
        System.out.println();
        System.out.println(DIM_GREEN + "  [ Session started at " + now() + " ]" + RESET);
        System.out.println();
    }

    private void printIncoming(String message) {
        if (message.startsWith("SERVER:")) {
            System.out.println("\r" + DIM_GREEN + "  ⚡ " + message + RESET);
        } else {
            System.out.println("\r" + GREEN + "  [" + now() + "] " + BGREEN + message + RESET);
        }
    }

    private void printSystem(String message) {
        System.out.println(DIM_GREEN + "  >> " + message + RESET);
    }

    private void printPrompt() {
        System.out.print(BGREEN + BOLD + "  [" + username + "] > " + RESET + GREEN);
        System.out.flush();
    }

    private String now() {
        return LocalTime.now().format(TIME_FMT);
    }

    public void closeEverything() {
        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection.");
        }
    }

    // Accepts optional args: java ChatTUI <ip> <port>
    // If not provided, prompts the user interactively.
    public static void main(String[] args) {
        System.out.print(CLEAR);
        Scanner scanner = new Scanner(System.in);

        System.out.println(BGREEN + BOLD + "  CHAT CLIENT" + RESET);
        System.out.println(GREEN + "  ────────────────────────────" + RESET);

        String ip   = args.length > 0 ? args[0] : null;
        String port = args.length > 1 ? args[1] : null;

        if (ip == null || ip.isEmpty()) {
            System.out.print(BGREEN + "  Server IP  > " + RESET + GREEN);
            ip = scanner.nextLine().trim();
            if (ip.isEmpty()) ip = "localhost";
        }

        int portNum = 5000;
        if (port != null) {
            try {
                portNum = Integer.parseInt(port);
            } catch (NumberFormatException e) {
                System.err.println(RED + "  Invalid port: " + port + RESET);
                return;
            }
        } else {
            System.out.print(BGREEN + "  Port       > " + RESET + GREEN);
            String portInput = scanner.nextLine().trim();
            if (!portInput.isEmpty()) {
                try {
                    portNum = Integer.parseInt(portInput);
                } catch (NumberFormatException e) {
                    System.err.println(RED + "  Invalid port." + RESET);
                    return;
                }
            }
        }

        System.out.print(BGREEN + "  Password   > " + RESET + GREEN);
        String password = scanner.nextLine();

        System.out.print(BGREEN + "  Username   > " + RESET + GREEN);
        String username = scanner.nextLine().trim();
        System.out.print(RESET);

        if (username.isEmpty()) {
            System.err.println(RED + "  Username cannot be empty." + RESET);
            return;
        }

        try {
            System.out.println(DIM_GREEN + "  Connecting to " + ip + ":" + portNum + "..." + RESET);
            Socket socket = new Socket(ip, portNum);
            ChatTUI tui = new ChatTUI(socket, username);

            if (!tui.authenticate(password)) {
                System.out.println(RED + "  Wrong password. Access denied." + RESET);
                socket.close();
                return;
            }

            System.out.println(BGREEN + "  Authenticated!" + RESET);
            Thread.sleep(400);
            tui.start();

        } catch (IOException e) {
            System.err.println(RED + "  Could not connect: " + e.getMessage() + RESET);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
