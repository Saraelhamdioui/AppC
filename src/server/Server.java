package server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * Architecture vidéo — UN seul port 5002 :
 *
 * Protocole : callId (int 4 octets) + rôle (byte : 'S'=sender, 'R'=receiver)
 *
 * PC1 envoie sa webcam  → connecte en 'S' sur 5002
 * PC1 reçoit la webcam  → connecte en 'R' sur 5002
 * PC2 pareil
 *
 * Bridge : S de PC1 → R de PC2  /  S de PC2 → R de PC1
 */
public class Server {

    public static ConcurrentHashMap<String, ClientHandler> clients     = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Boolean>       allUsers    = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Integer>       activeCalls = new ConcurrentHashMap<>();

    // clé = "callId_S" ou "callId_R"
    private static ConcurrentHashMap<String, Socket> waitingAudio = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Socket> waitingVideo = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("=== ChatApp Server ===");
        printServerIP();

        // CHAT port 1234
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(1234)) {
                System.out.println("Chat demarré port 1234");
                while (true) new Thread(new ClientHandler(ss.accept())).start();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        // AUDIO port 5001
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(5001)) {
                System.out.println("Audio demarré port 5001");
                while (true) {
                    Socket s = ss.accept();
                    new Thread(() -> handleMedia(s, waitingAudio, "audio")).start();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        // VIDEO port 5002
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(5002)) {
                System.out.println("Video demarré port 5002");
                while (true) {
                    Socket s = ss.accept();
                    new Thread(() -> handleMedia(s, waitingVideo, "video")).start();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    /**
     * Protocole :
     *   readInt()  → callId
     *   readByte() → 'S' (sender) ou 'R' (receiver)
     *
     * Matching : un 'S' + un 'R' du même callId → bridge S→R
     */
    private static void handleMedia(Socket socket,
                                    ConcurrentHashMap<String, Socket> waiting,
                                    String label) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            int  callId = dis.readInt();
            char role   = (char) dis.readByte(); // 'S' ou 'R'

            String myKey      = callId + "_" + role;
            String partnerKey = callId + "_" + (role == 'S' ? 'R' : 'S');

            Socket partner = waiting.remove(partnerKey);
            if (partner == null) {
                waiting.put(myKey, socket);
            } else {
                // Bridge unidirectionnel : sender → receiver
                if (role == 'S') {
                    forwardStream(socket,  partner, label + " S→R");
                } else {
                    forwardStream(partner, socket,  label + " S→R");
                }
            }
        } catch (Exception e) {
            System.out.println("[media] erreur : " + e.getMessage());
        }
    }

    private static void forwardStream(Socket inSock, Socket outSock, String label) {
        new Thread(() -> {
            try (InputStream  in  = inSock.getInputStream();
                 OutputStream out = outSock.getOutputStream()) {
                byte[] buf = new byte[16384];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    out.flush();
                }
            } catch (Exception e) {
                System.out.println("[" + label + "] terminé.");
            }
        }).start();
    }

    public static void addClient(String username, ClientHandler client) {
        clients.put(username, client);
        allUsers.put(username, true);
        broadcastUsers();
    }

    public static void removeClient(String username) {
        clients.remove(username);
        broadcastUsers();
    }

    public static void broadcastUsers() {
        String online = String.join(",", clients.keySet());
        String all    = String.join(",", allUsers.keySet());
        for (ClientHandler c : clients.values())
            c.send("USERS:" + online + "|ALL:" + all);
    }

    public static void sendPrivate(String user, String msg) {
        ClientHandler c = clients.get(user);
        if (c != null) c.send(msg);
    }

    private static void printServerIP() {
        try {
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("📡 IP : " + InetAddress.getLocalHost().getHostAddress());
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } catch (Exception e) { System.out.println("IP introuvable"); }
    }
}