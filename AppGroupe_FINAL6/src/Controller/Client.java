package Controller;

import network.Protocol;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Base64;

public class Client {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String serverIp;

    public static final int CHAT_PORT  = 1234;
    public static final int AUDIO_PORT = 5001;
    public static final int VIDEO_PORT = 5002;

    public Client(String serverIp) {
        this.serverIp = serverIp;
        try {
            socket = new Socket(serverIp, CHAT_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getServerIp() {
        return serverIp;
    }

    public void login(String username) {
        out.println(Protocol.LOGIN + ":" + username);
    }

    public void sendMessage(String sender, String receiver, String msg) {
        out.println(Protocol.MSG + ":" + sender + ":" + receiver + ":" + msg);
    }

    public void sendFile(String sender, String receiver, File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String encoded = Base64.getEncoder().encodeToString(bytes);

            out.println("FILE:" + sender + ":" + receiver + ":" + file.getName() + ":" + encoded);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendSeen(String sender, String receiver) {
        out.println(Protocol.SEEN + ":" + sender + ":" + receiver);
    }

    public void sendCallRequest(String caller, String callee, String type) {
        out.println(Protocol.CALL_REQUEST + ":" + caller + ":" + callee + ":" + type);
    }

    public void acceptCall(String me, String caller) {
        out.println(Protocol.CALL_ACCEPT + ":" + caller + ":" + me);
    }

    public void rejectCall(String me, String caller) {
        out.println(Protocol.CALL_REJECT + ":" + caller + ":" + me);
    }

    public void endCall(String me, String other) {
        out.println(Protocol.CALL_END + ":" + me + ":" + other);
    }

    public void listen(MessageListener listener) {
        new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    listener.onMessage(msg);
                }
            } catch (Exception e) {
                System.out.println("Connexion au serveur perdue.");
            }
        }).start();
    }

    //******************Modifications ajouter

    // Méthodes pour les groupes
    public void createGroup(String creator, String groupName) {
        out.println(Protocol.GROUP_CREATE + ":" + creator + ":" + groupName);
    }

    public void sendGroupMessage(int groupId, String sender, String content) {
        out.println(Protocol.GROUP_MSG + ":" + groupId + ":" + sender + ":" + content);
    }

    public void addGroupMember(int groupId, String adder, String newMember) {
        out.println(Protocol.GROUP_ADD_MEMBER + ":" + groupId + ":" + adder + ":" + newMember);
    }

    public void removeGroupMember(int groupId, String remover, String memberToRemove) {
        out.println(Protocol.GROUP_REMOVE_MEMBER + ":" + groupId + ":" + remover + ":" + memberToRemove);
    }

    public void getGroupList(String username) {
        out.println(Protocol.GROUP_LIST + ":" + username);
    }

    public void getGroupMembers(int groupId) {
        out.println(Protocol.GROUP_MEMBERS + ":" + groupId);
    }

    public void leaveGroup(int groupId, String username) {
        out.println(Protocol.GROUP_LEAVE + ":" + groupId + ":" + username);
    }

    public void deleteGroup(int groupId, String username) {
        out.println(Protocol.GROUP_DELETE + ":" + groupId + ":" + username);
    }

    public void sendGroupSeen(int groupId, String username) {
        out.println(Protocol.GROUP_SEEN + ":" + groupId + ":" + username);
    }

    public void addContact(String owner, String contact) {
        out.println(Protocol.ADD_CONTACT + ":" + owner + ":" + contact);
    }

    public void deleteContact(String owner, String contact) {
        out.println(Protocol.DELETE_CONTACT + ":" + owner + ":" + contact);
    }

    public void blockContact(String blocker, String blocked) {
        out.println(Protocol.BLOCK_CONTACT + ":" + blocker + ":" + blocked);
    }

    public void unblockContact(String blocker, String blocked) {
        out.println(Protocol.UNBLOCK_CONTACT + ":" + blocker + ":" + blocked);
    }
    public void sendRaw(String raw) {
        out.println(raw);
    }

}