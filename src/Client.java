import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String username;

    public Client(Socket socket, String username) {
        try {
            this.socket = socket;
            this.username = username;
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Error connecting: " + e.getMessage());
            closeEverything();
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
            System.err.println("Auth error: " + e.getMessage());
            return false;
        }
    }

    public void sendMessage() {
        try {
            bufferedWriter.write(username);
            bufferedWriter.newLine();
            bufferedWriter.flush();

            Scanner scanner = new Scanner(System.in);

            while (!socket.isClosed()) {
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("/quit")) {
                    closeEverything();
                    break;
                }

                if (message.trim().isEmpty()) continue;

                bufferedWriter.write(username + ": " + message);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            }

        } catch (IOException e) {
            System.err.println("Connection lost: " + e.getMessage());
            closeEverything();
        }
    }

    public void listenForMessage() {
        new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    String message = bufferedReader.readLine();

                    if (message == null) {
                        System.out.println("Disconnected from server.");
                        closeEverything();
                        break;
                    }

                    System.out.println(message);

                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        System.err.println("Lost connection to server.");
                    }
                    closeEverything();
                    break;
                }
            }
        }).start();
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

    // Accepts optional args: java Client <ip> <port>
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String ip   = args.length > 0 ? args[0] : null;
        String port = args.length > 1 ? args[1] : null;

        if (ip == null || ip.isEmpty()) {
            System.out.print("\033[92m  Enter server IP > \033[0m");
            ip = scanner.nextLine().trim();
            if (ip.isEmpty()) ip = "localhost";
        }

        int portNum = 5000;
        if (port != null) {
            try { portNum = Integer.parseInt(port); }
            catch (NumberFormatException e) {
                System.err.println("Invalid port: " + port);
                return;
            }
        }

        System.out.print("\033[92m  Enter password  > \033[0m");
        String password = scanner.nextLine();

        System.out.print("\033[92m  Enter username  > \033[0m");
        String username = scanner.nextLine().trim();

        if (username.isEmpty()) {
            System.err.println("Username cannot be empty.");
            return;
        }

        try {
            Socket socket = new Socket(ip, portNum);
            Client client = new Client(socket, username);

            if (!client.authenticate(password)) {
                System.out.println("\033[31m  Wrong password. Connection rejected.\033[0m");
                socket.close();
                return;
            }

            System.out.println("\033[92m  Connected! Type /quit to exit.\033[0m");
            client.listenForMessage();
            client.sendMessage();

        } catch (IOException e) {
            System.err.println("\033[31m  Could not connect: " + e.getMessage() + "\033[0m");
        }
    }
}
