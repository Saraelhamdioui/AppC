package server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;
import DAO.GroupDao;
import DAO.GroupMessageDao;
import model.GroupMember;

public class Server {

    public static ConcurrentHashMap<String, ClientHandler> clients     = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Boolean>       allUsers    = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Integer>       activeCalls = new ConcurrentHashMap<>();

    private static ConcurrentHashMap<String, Socket> waitingAudio = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Socket> waitingVideo = new ConcurrentHashMap<>();

    // Cache mémoire : groupId -> [name, creator]
    // Les membres et messages sont TOUJOURS lus depuis la BDD
    private static Map<Integer, String[]> groupCache = new ConcurrentHashMap<>();

    private static final GroupDao        groupDao        = new GroupDao();
    private static final GroupMessageDao groupMessageDao = new GroupMessageDao();

    public static void main(String[] args) {
        System.out.println("=== ChatApp Server ===");
        printServerIP();
        groupDao.initTables();
        loadGroupsFromDB();

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

    // ========== MEDIA ==========

    private static void handleMedia(Socket socket,
                                    ConcurrentHashMap<String, Socket> waiting,
                                    String label) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            int  callId = dis.readInt();
            char role   = (char) dis.readByte();

            String myKey      = callId + "_" + role;
            String partnerKey = callId + "_" + (role == 'S' ? 'R' : 'S');

            Socket partner = waiting.remove(partnerKey);
            if (partner == null) {
                waiting.put(myKey, socket);
            } else {
                if (role == 'S') forwardStream(socket, partner, label + " S->R");
                else             forwardStream(partner, socket, label + " S->R");
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
                System.out.println("[" + label + "] termine.");
            }
        }).start();
    }

    // ========== CLIENTS ==========

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
            System.out.println("IP : " + InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) { System.out.println("IP introuvable"); }
    }

    // ========== GROUPES (100% BDD) ==========

    private static void loadGroupsFromDB() {
        try {
            List<model.Group> dbGroups = groupDao.getAllGroups();
            groupCache.clear();
            for (model.Group g : dbGroups) {
                groupCache.put(g.getId(), new String[]{g.getName(), g.getCreatedBy()});
            }
            System.out.println("[BDD] " + dbGroups.size() + " groupe(s) charge(s)");
        } catch (Exception e) {
            System.out.println("[BDD] Erreur chargement groupes: " + e.getMessage());
        }
    }

    public static void broadcastGroupList() {
        for (ClientHandler client : clients.values()) {
            client.sendGroupList();
        }
    }

    public static void createGroup(String creator, String groupName, ClientHandler creatorHandler) {
        try {
            System.out.println("[GROUPE] Tentative creation: '" + groupName + "' par " + creator);
            model.Group dbGroup = new model.Group(groupName, creator);
            int groupId = groupDao.createGroup(dbGroup);

            if (groupId == -1) {
                System.out.println("[GROUPE] ECHEC creation groupe '" + groupName + "' - voir erreur SQL ci-dessus");
                creatorHandler.send("ERROR:Impossible de creer le groupe");
                return;
            }

            groupCache.put(groupId, new String[]{groupName, creator});
            System.out.println("[GROUPE] Cree: " + groupName + " (ID=" + groupId + ") par " + creator);

            creatorHandler.send("GROUP_CREATE:" + groupId + ":" + groupName);
            broadcastGroupList();

        } catch (Exception e) {
            e.printStackTrace();
            creatorHandler.send("ERROR:Erreur creation groupe");
        }
    }

    public static void sendGroupMessage(int groupId, String sender, String content) {
        try {
            List<GroupMember> members = groupDao.getGroupMembers(groupId);

            if (members.isEmpty()) {
                System.out.println("[GROUP_MSG] Aucun membre pour groupe " + groupId);
                return;
            }

            // 1. Sauvegarder en BDD (les membres deconnectes recevront a la reconnexion)
            groupMessageDao.saveMessage(new model.GroupMessage(groupId, sender, content));

            // 2. Diffuser aux membres connectes maintenant
            String message = "GROUP_MSG:" + groupId + ":" + sender + ":" + content;
            for (GroupMember member : members) {
                ClientHandler mc = clients.get(member.getUsername());
                if (mc != null) {
                    mc.send(message);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addGroupMember(int groupId, String adder, String newMember) {
        try {
            String[] groupInfo = groupCache.get(groupId);
            if (groupInfo == null) {
                System.out.println("[ADD_MEMBER] Groupe " + groupId + " introuvable");
                return;
            }
            if (!groupInfo[1].equals(adder)) {
                System.out.println("[ADD_MEMBER] " + adder + " n'est pas le createur");
                return;
            }

            groupDao.addMember(groupId, newMember, "MEMBER");
            System.out.println("[ADD_MEMBER] " + newMember + " ajoute au groupe " + groupInfo[0]);

            List<GroupMember> members = groupDao.getGroupMembers(groupId);
            for (GroupMember member : members) {
                ClientHandler mc = clients.get(member.getUsername());
                if (mc != null) {
                    mc.send("GROUP_ADD_MEMBER:" + groupId + ":" + newMember);
                    mc.sendGroupList();
                }
            }

            ClientHandler newClient = clients.get(newMember);
            if (newClient != null) {
                newClient.send("GROUP_ADD_MEMBER:" + groupId + ":" + newMember);
                newClient.sendGroupList();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void removeGroupMember(int groupId, String remover, String memberToRemove) {
        String[] groupInfo = groupCache.get(groupId);
        if (groupInfo == null || !groupInfo[1].equals(remover)) return;

        groupDao.removeMember(groupId, memberToRemove);

        List<GroupMember> members = groupDao.getGroupMembers(groupId);
        for (GroupMember member : members) {
            ClientHandler mc = clients.get(member.getUsername());
            if (mc != null) {
                mc.send("GROUP_REMOVE_MEMBER:" + groupId + ":" + memberToRemove);
                mc.sendGroupList();
            }
        }
        ClientHandler removedClient = clients.get(memberToRemove);
        if (removedClient != null) {
            removedClient.send("GROUP_REMOVE_MEMBER:" + groupId + ":" + memberToRemove);
            removedClient.sendGroupList();
        }
    }

    public static String getGroupMembers(int groupId) {
        List<GroupMember> members = groupDao.getGroupMembers(groupId);
        StringBuilder sb = new StringBuilder();
        for (GroupMember m : members) {
            if (sb.length() > 0) sb.append(",");
            sb.append(m.getUsername());
        }
        return sb.toString();
    }

    public static void leaveGroup(int groupId, String leaver) {
        groupDao.removeMember(groupId, leaver);

        List<GroupMember> remaining = groupDao.getGroupMembers(groupId);
        if (remaining.isEmpty()) {
            groupCache.remove(groupId);
            groupDao.deleteGroup(groupId);
            System.out.println("[GROUPE] Groupe " + groupId + " supprime (vide)");
        }
        broadcastGroupList();
    }

    public static void deleteGroup(int groupId, String deleter) {
        String[] groupInfo = groupCache.get(groupId);
        if (groupInfo == null || !groupInfo[1].equals(deleter)) return;

        // Notifier AVANT suppression
        broadcastToGroup(groupId, "GROUP_DELETE:" + groupId, null);

        groupCache.remove(groupId);
        groupDao.deleteGroup(groupId);
        broadcastGroupList();
    }

    public static String getGroupsForUser(String username) {
        List<model.Group> userGroups = groupDao.getUserGroups(username);
        StringBuilder sb = new StringBuilder();
        for (model.Group g : userGroups) {
            if (sb.length() > 0) sb.append(",");
            sb.append(g.getId()).append("|").append(g.getName());
        }
        return sb.toString();
    }

    // Retourne l'historique des messages d'un groupe depuis la BDD
    public static List<model.GroupMessage> getGroupHistory(int groupId) {
        return groupMessageDao.getGroupMessages(groupId, 200);
    }

    public static void broadcastToGroup(int groupId, String message, ClientHandler exclude) {
        List<GroupMember> members = groupDao.getGroupMembers(groupId);
        for (GroupMember m : members) {
            ClientHandler mc = clients.get(m.getUsername());
            if (mc != null && mc != exclude) mc.send(message);
        }
    }
}