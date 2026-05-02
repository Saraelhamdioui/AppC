package server;

import network.Protocol;
import dao.MessageDao;
import dao.UserDao;
import dao.CallDao;
import model.User;
import model.Message;

import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    private MessageDao messageDao = new MessageDao();
    private UserDao    userDao    = new UserDao();
    private CallDao    callDao    = new CallDao();

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
                    sendHistory();
                }

                // ================= MESSAGE =================
                else if (msg.startsWith(Protocol.MSG)) {
                    String[] parts = msg.split(":", 4);
                    if (parts.length < 4) continue;
                    String sender   = parts[1];
                    String receiver = parts[2];
                    String content  = parts[3];

                    messageDao.save(new Message(sender, receiver, content));

                    String full = Protocol.MSG + ":" + sender + ":" + receiver + ":" + content;
                    Server.sendPrivate(receiver, full);
                    Server.sendPrivate(sender,   full);
                }

                // ================= SEEN =================
                else if (msg.startsWith(Protocol.SEEN)) {
                    String[] parts = msg.split(":", 3);
                    if (parts.length < 3) continue;
                    String sender   = parts[1];
                    String receiver = parts[2];
                    messageDao.markAsSeen(receiver, sender);
                    Server.sendPrivate(receiver, Protocol.SEEN + ":" + sender);
                }

                // ================= CALL REQUEST =================
                else if (msg.startsWith(Protocol.CALL_REQUEST)) {
                    String[] p = msg.split(":", 4);
                    if (p.length < 4) continue;
                    String caller = p[1];
                    String callee = p[2];
                    String type   = p[3];
                    Server.sendPrivate(callee,
                            Protocol.CALL_REQUEST + ":" + caller + ":" + type);
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

                    // Les deux reçoivent CALL_ACCEPT avec l'ID pour se connecter aux ports media
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

                    String  key       = u1 + "-" + u2;
                    String  revKey    = u2 + "-" + u1;
                    Integer callId    = Server.activeCalls.remove(key);
                    if (callId == null) callId = Server.activeCalls.remove(revKey);
                    if (callId != null) callDao.endCall(callId);

                    Server.sendPrivate(u1, Protocol.CALL_END + ":" + u2);
                    Server.sendPrivate(u2, Protocol.CALL_END + ":" + u1);
                }
            }
        } catch (Exception e) {
            System.out.println("Client déconnecté: " + username);
        } finally {
            if (username != null) Server.removeClient(username);
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    public void send(String msg) {
        out.println(msg);
    }

    private void sendHistory() {
        for (Message m : messageDao.getMessages(username)) {
            send(Protocol.HISTORY + ":" + m.getSender() + ":" + m.getReceiver() + ":" + m.getContent());
        }
    }
}
