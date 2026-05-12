package server;

import network.Protocol;
import DAO.MessageDao;
import DAO.UserDao;
import DAO.CallDao;
import DAO.FileDao;
import DAO.ContactDao;
import model.User;

import java.io.*;
import java.net.*;
import java.util.List;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    private MessageDao messageDao = new MessageDao();
    private UserDao    userDao    = new UserDao();
    private CallDao    callDao    = new CallDao();
    private FileDao    fileDao    = new FileDao();
    private ContactDao contactDao = new ContactDao();

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {

                // ================= LOGIN =================
                if (msg.startsWith(Protocol.LOGIN)) {
                    username = msg.split(":", 2)[1];
                    userDao.save(new User(username));
                    Server.addClient(username, this);
                    sendHistory();          // historique messages privés
                    sendGroupList();        // liste des groupes (AVANT l'historique groupe)
                    sendContacts();
                    sendGroupHistory();     // historique groupe APRÈS que le client connaît ses groupes
                }

                // ================= MESSAGE PRIVE =================
                else if (msg.startsWith(Protocol.MSG)) {
                    String[] parts = msg.split(":", 4);
                    if (parts.length < 4) continue;

                    String sender   = parts[1];
                    String receiver = parts[2];
                    String content  = parts[3];

                    if (contactDao.isBlocked(receiver, sender)) {
                        send(Protocol.MSG + ":-1:" + sender + ":" + receiver + ":" + content);
                        continue;
                    }

                    model.Message message = new model.Message(sender, receiver, content);
                    int messageId = messageDao.save(message);

                    String full = Protocol.MSG + ":" + messageId + ":" + sender + ":" + receiver + ":" + content;
                    Server.sendPrivate(receiver, full);
                    Server.sendPrivate(sender, full);
                }

                // ================= FILE =================
                else if (msg.startsWith("FILE:")) {
                    String[] parts = msg.split(":", 5);
                    if (parts.length < 5) continue;

                    String sender   = parts[1];
                    String receiver = parts[2];
                    String fileName = parts[3];
                    String fileData = parts[4];

                    String path = "received_files/" + fileName;
                    fileDao.save(sender, receiver, fileName, path);

                    String full = "FILE:" + sender + ":" + receiver + ":" + fileName + ":" + fileData;
                    Server.sendPrivate(receiver, full);
                    Server.sendPrivate(sender, full);
                }

                // ================= SEEN =================
                else if (msg.startsWith(Protocol.SEEN)) {
                    String[] parts = msg.split(":", 3);
                    if (parts.length < 3) continue;

                    String reader = parts[1];
                    String sender = parts[2];

                    messageDao.markConversationAsSeen(sender, reader);
                    Server.sendPrivate(sender, Protocol.SEEN + ":" + reader);
                }

                // ================= CALL REQUEST =================
                else if (msg.startsWith(Protocol.CALL_REQUEST)) {
                    String[] p = msg.split(":", 4);
                    if (p.length < 4) continue;
                    String caller = p[1];
                    String callee = p[2];
                    String type   = p[3];
                    Server.sendPrivate(callee, Protocol.CALL_REQUEST + ":" + caller + ":" + type);
                }

                // ================= CALL ACCEPT =================
                else if (msg.startsWith(Protocol.CALL_ACCEPT)) {
                    String[] p = msg.split(":", 3);
                    if (p.length < 3) continue;
                    String caller = p[1];
                    String callee = p[2];

                    String key        = caller + "-" + callee;
                    String reverseKey = callee + "-" + caller;

                    int callId;
                    if (Server.activeCalls.containsKey(key)) {
                        callId = Server.activeCalls.get(key);
                    } else if (Server.activeCalls.containsKey(reverseKey)) {
                        callId = Server.activeCalls.get(reverseKey);
                    } else {
                        callId = callDao.startCall(caller, callee, "audio");
                        Server.activeCalls.put(key, callId);
                    }

                    Server.sendPrivate(caller, Protocol.CALL_ACCEPT + ":" + callee + ":" + callId);
                    Server.sendPrivate(callee, Protocol.CALL_ACCEPT + ":" + caller + ":" + callId);
                }

                // ================= CALL REJECT =================
                else if (msg.startsWith(Protocol.CALL_REJECT)) {
                    String[] p = msg.split(":", 3);
                    if (p.length < 3) continue;
                    String caller = p[1];
                    String callee = p[2];
                    Server.sendPrivate(caller, Protocol.CALL_REJECT + ":" + callee);
                }

                // ================= CALL END =================
                else if (msg.startsWith(Protocol.CALL_END)) {
                    String[] p = msg.split(":", 3);
                    if (p.length < 3) continue;
                    String u1 = p[1];
                    String u2 = p[2];

                    String  key    = u1 + "-" + u2;
                    String  revKey = u2 + "-" + u1;
                    Integer callId = Server.activeCalls.remove(key);
                    if (callId == null) callId = Server.activeCalls.remove(revKey);
                    if (callId != null) callDao.endCall(callId);

                    Server.sendPrivate(u1, Protocol.CALL_END + ":" + u2);
                    Server.sendPrivate(u2, Protocol.CALL_END + ":" + u1);
                }

                // ================= ADD CONTACT =================
                else if (msg.startsWith(Protocol.ADD_CONTACT)) {
                    String[] parts = msg.split(":", 3);
                    if (parts.length >= 3) {
                        String owner   = parts[1];
                        String contact = parts[2].trim();
                        if (userDao.userExists(contact)) {
                            userDao.addContact(owner, contact);
                            sendContacts();
                            send("ADD_CONTACT_RESULT:OK:" + contact);
                        } else {
                            send("ADD_CONTACT_RESULT:ERROR:Utilisateur introuvable");
                        }
                    }
                }

                // ================= DELETE CONTACT =================
                else if (msg.startsWith(Protocol.DELETE_CONTACT)) {
                    String[] parts = msg.split(":", 3);
                    if (parts.length >= 3) {
                        String owner   = parts[1];
                        String contact = parts[2].trim();
                        contactDao.deleteContact(owner, contact);
                        sendContacts();
                    }
                }

                // ================= BLOCK CONTACT =================
                else if (msg.startsWith(Protocol.BLOCK_CONTACT)) {
                    String[] parts = msg.split(":", 3);
                    if (parts.length >= 3) {
                        String blocker = parts[1];
                        String blocked = parts[2].trim();
                        contactDao.blockContact(blocker, blocked);
                        contactDao.deleteContact(blocker, blocked);
                        sendContacts();
                        send("BLOCK_RESULT:OK:" + blocked);
                    }
                }

                // ================= UNBLOCK CONTACT =================
                else if (msg.startsWith(Protocol.UNBLOCK_CONTACT)) {
                    String[] parts = msg.split(":", 3);
                    if (parts.length >= 3) {
                        String blocker = parts[1];
                        String blocked = parts[2].trim();
                        contactDao.unblockContact(blocker, blocked);
                        send("UNBLOCK_RESULT:OK:" + blocked);
                    }
                }

                // ================= GROUPES =================

                else if (msg.startsWith(Protocol.GROUP_CREATE)) {
                    String[] parts = msg.split(":", 3);
                    if (parts.length >= 3) {
                        String creator   = parts[1];
                        String groupName = parts[2];
                        Server.createGroup(creator, groupName, this);
                    }
                }

                else if (msg.startsWith(Protocol.GROUP_MSG)) {
                    String[] parts = msg.split(":", 4);
                    if (parts.length >= 4) {
                        try {
                            int    groupId = Integer.parseInt(parts[1]);
                            String sender  = parts[2];
                            String content = parts[3];
                            Server.sendGroupMessage(groupId, sender, content);
                        } catch (NumberFormatException e) { e.printStackTrace(); }
                    }
                }

                else if (msg.startsWith(Protocol.GROUP_ADD_MEMBER)) {
                    String[] parts = msg.split(":", 4);
                    if (parts.length >= 4) {
                        try {
                            int    groupId   = Integer.parseInt(parts[1]);
                            String adder     = parts[2];
                            String newMember = parts[3];
                            Server.addGroupMember(groupId, adder, newMember);
                        } catch (NumberFormatException e) { e.printStackTrace(); }
                    }
                }

                else if (msg.startsWith(Protocol.GROUP_REMOVE_MEMBER)) {
                    String[] parts = msg.split(":", 4);
                    if (parts.length >= 4) {
                        try {
                            int    groupId        = Integer.parseInt(parts[1]);
                            String remover        = parts[2];
                            String memberToRemove = parts[3];
                            Server.removeGroupMember(groupId, remover, memberToRemove);
                        } catch (NumberFormatException e) { e.printStackTrace(); }
                    }
                }

                else if (msg.startsWith(Protocol.GROUP_LIST)) {
                    sendGroupList();
                }

                else if (msg.startsWith(Protocol.GROUP_MEMBERS)) {
                    String[] parts = msg.split(":", 2);
                    if (parts.length >= 2) {
                        try {
                            int    groupId = Integer.parseInt(parts[1]);
                            String members = Server.getGroupMembers(groupId);
                            send("GROUP_MEMBERS:" + groupId + ":" + members);
                        } catch (NumberFormatException e) { e.printStackTrace(); }
                    }
                }

                else if (msg.startsWith(Protocol.GROUP_LEAVE)) {
                    String[] parts = msg.split(":", 3);
                    if (parts.length >= 3) {
                        try {
                            int    groupId = Integer.parseInt(parts[1]);
                            String leaver  = parts[2];
                            Server.leaveGroup(groupId, leaver);
                        } catch (NumberFormatException e) { e.printStackTrace(); }
                    }
                }

                else if (msg.startsWith(Protocol.GROUP_DELETE)) {
                    String[] parts = msg.split(":", 3);
                    if (parts.length >= 3) {
                        try {
                            int    groupId = Integer.parseInt(parts[1]);
                            String deleter = parts[2];
                            Server.deleteGroup(groupId, deleter);
                        } catch (NumberFormatException e) { e.printStackTrace(); }
                    }
                }

                else if (msg.startsWith(Protocol.GROUP_SEEN)) {
                    String[] parts = msg.split(":", 3);
                    if (parts.length >= 3) {
                        try {
                            int    groupId = Integer.parseInt(parts[1]);
                            String reader  = parts[2];
                            // Implémentation future si besoin
                        } catch (NumberFormatException e) { e.printStackTrace(); }
                    }
                }

                else if (msg.startsWith(Protocol.GROUP_CALL_START)) {
                    String[] cp = msg.split(":", 4);
                    if (cp.length >= 4) {
                        try {
                            String callType = cp[1];
                            int    groupId  = Integer.parseInt(cp[2]);
                            String caller   = cp[3];
                            Server.broadcastToGroup(groupId,
                                    Protocol.GROUP_CALL_NOTIFY + ":" + callType + ":" + groupId + ":" + caller, this);
                        } catch (NumberFormatException e) { e.printStackTrace(); }
                    }
                }

                else if (msg.startsWith(Protocol.GROUP_CALL_JOIN)) {
                    String[] cp = msg.split(":", 3);
                    if (cp.length >= 3) {
                        try {
                            int    groupId = Integer.parseInt(cp[1]);
                            String joiner  = cp[2];
                            Server.broadcastToGroup(groupId, "GROUP_CALL_JOINED:" + groupId + ":" + joiner, this);
                        } catch (NumberFormatException e) { e.printStackTrace(); }
                    }
                }

                else if (msg.startsWith(Protocol.GROUP_CALL_LEAVE)) {
                    String[] cp = msg.split(":", 3);
                    if (cp.length >= 3) {
                        try {
                            int    groupId = Integer.parseInt(cp[1]);
                            String leaver  = cp[2];
                            Server.broadcastToGroup(groupId, "GROUP_CALL_LEFT:" + groupId + ":" + leaver, this);
                        } catch (NumberFormatException e) { e.printStackTrace(); }
                    }
                }

                else if (msg.startsWith("GROUP_FILE:")) {
                    String[] fp = msg.split(":", 5);
                    if (fp.length >= 5) {
                        try {
                            int groupId = Integer.parseInt(fp[1]);
                            java.io.File dir = new java.io.File("server_files");
                            if (!dir.exists()) dir.mkdirs();
                            byte[] bytes = java.util.Base64.getDecoder().decode(fp[4]);
                            java.nio.file.Files.write(new java.io.File(dir, fp[3]).toPath(), bytes);
                            Server.broadcastToGroup(groupId, msg, this);
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Client deconnecte: " + username);
        } finally {
            if (username != null) Server.removeClient(username);
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    public void send(String msg) {
        out.println(msg);
    }

    // Historique messages privés
    private void sendHistory() {
        List<model.Message> messages = messageDao.getMessages(username);
        for (model.Message m : messages) {
            send(Protocol.HISTORY + ":" +
                    m.getId() + ":" +
                    m.getSender() + ":" +
                    m.getReceiver() + ":" +
                    m.getContent() + ":" +
                    m.isSeen());
        }
    }

    // *** NOUVEAU : Historique messages de groupe depuis BDD ***
    // Envoye tous les messages de tous les groupes de l'utilisateur
    private void sendGroupHistory() {
        // Recuperer les groupes de l'utilisateur
        String groupsStr = Server.getGroupsForUser(username);
        if (groupsStr == null || groupsStr.isEmpty()) return;

        for (String groupEntry : groupsStr.split(",")) {
            // Format : "groupId|groupName"
            String[] parts = groupEntry.split("\\|");
            if (parts.length < 1) continue;
            try {
                int groupId = Integer.parseInt(parts[0]);
                List<model.GroupMessage> history = Server.getGroupHistory(groupId);
                // Envoyer dans l'ordre chronologique (ASC depuis la BDD)
                for (model.GroupMessage gm : history) {
                    send("GROUP_HISTORY_MSG:" + gm.getGroupId() + ":" + gm.getSender() + ":" + gm.getContent());
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendGroupList() {
        String groups = Server.getGroupsForUser(username);
        send("GROUP_LIST:" + groups);
    }

    private void sendContacts() {
        List<String> contacts = userDao.getContacts(username);
        StringBuilder sb = new StringBuilder();
        for (String c : contacts) {
            if (sb.length() > 0) sb.append(",");
            sb.append(c);
        }
        send(Protocol.CONTACTS + ":" + sb.toString());
    }
}