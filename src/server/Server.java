package server;

import java.io.DataInputStream;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;


public class Server {

    // =========================
    // 💬 CHAT SYSTEM
    // =========================
    public static ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Boolean> allUsers = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Integer> activeCalls = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Integer, Socket> waiting = new ConcurrentHashMap<>();

    private static void handleAudio(Socket inSocket, Socket outSocket) {

        try (InputStream in = inSocket.getInputStream();
             OutputStream out = outSocket.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int read;

            while (!inSocket.isClosed() && !outSocket.isClosed()) {

                if (Thread.currentThread().isInterrupted()) break;

                try {
                    read = in.read(buffer);

                    if (read == -1) break;

                    out.write(buffer, 0, read);
                    out.flush();

                } catch (SocketException e) {
                    break; // 🔥 important
                }
            }

        } catch (Exception e) {
            System.out.println("Audio stopped");
        }
    }
    // 🎤 Audio pairing


    public static void main(String[] args) {

        // =========================
        // 💬 CHAT SERVER (PORT 1234)
        // =========================
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(1234)) {

                System.out.println("💬 Chat Server started on 1234");

                while (true) {
                    Socket socket = serverSocket.accept();
                    new Thread(new ClientHandler(socket)).start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();


        // =========================
        // 🎤 AUDIO SERVER (PORT 5001)
        // =========================
        new Thread(() -> {

            try {

                ServerSocket audioServer = new ServerSocket(5001);

                System.out.println("🎤 Audio Server started on 5001");

                while (true) {

                    Socket socket = audioServer.accept();

                    try {
                        DataInputStream dis = new DataInputStream(socket.getInputStream());
                        int callId = dis.readInt();

                        if (!waiting.containsKey(callId)) {
                            waiting.put(callId, socket);
                        } else {
                            Socket s1 = waiting.remove(callId);
                            Socket s2 = socket;

                            new Thread(() -> forwardAudio(s1, s2)).start();
                            new Thread(() -> forwardAudio(s2, s1)).start();
                        }

                    } catch (Exception e) {
                        try {
                            socket.close();
                        } catch (Exception ignored) {
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }).start();
    }


    // =========================
    // 🔊 AUDIO FORWARD
    // =========================
    private static void forwardAudio(Socket inSocket, Socket outSocket) {

        try (InputStream in = inSocket.getInputStream();
             OutputStream out = outSocket.getOutputStream()) {

            byte[] buffer = new byte[4096];

            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }

        } catch (Exception e) {
            System.out.println("Audio stopped");
        } finally {
            try { inSocket.close(); } catch (Exception ignored) {}
            try { outSocket.close(); } catch (Exception ignored) {}
        }
    }

    // =========================
    // ➕ ADD CLIENT
    // =========================
    public static void addClient(String username, ClientHandler client) {
        clients.put(username, client);
        allUsers.put(username, true);
        broadcastUsers();
    }

    // =========================
    // ➖ REMOVE CLIENT
    // =========================
    public static void removeClient(String username) {
        clients.remove(username);
        broadcastUsers();
    }

    // =========================
    // 📡 BROADCAST USERS
    // =========================
    public static void broadcastUsers() {

        String online = String.join(",", clients.keySet());
        String all = String.join(",", allUsers.keySet());

        String data = "USERS:" + online + "|ALL:" + all;

        System.out.println("📡 " + data);

        for (ClientHandler c : clients.values()) {
            c.send(data);
        }
    }

    // =========================
    // 💬 PRIVATE MESSAGE
    // =========================
    public static void sendPrivate(String user, String msg) {
        ClientHandler c = clients.get(user);
        if (c != null) c.send(msg);
    }
}