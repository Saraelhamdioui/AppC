package server;
import java.util.List;
import network.Protocol;
import DAO.MessageDao;
import DAO.UserDao;
import DAO.CallDao;
import model.User;
import model.Message;
import DAO.ContactDao;
import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    private MessageDao messageDao = new MessageDao();
    private UserDao userDao = new UserDao();
    private CallDao callDao = new CallDao();

    public ClientHandler(Socket socket) {
        this.socket = socket;

        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private ContactDao contactDao = new ContactDao();
    @Override
    public void run() {

        try {
            String msg;

            while ((msg = in.readLine()) != null) {

                // ================= LOGIN =================
                if (msg.startsWith(Protocol.LOGIN)) {

                    username = msg.split(":")[1];

                    userDao.save(new User(username));
                    Server.addClient(username, this);

                    sendContactsTo(username);// ✔️ خليه
                    sendHistory();
                }

                // ================= MESSAGE =================
                else if (msg.startsWith(Protocol.MSG)) {

                    String[] parts = msg.split(":", 4);

                    String sender = parts[1];
                    String receiver = parts[2];
                    String content = parts[3];

                    messageDao.save(new Message(sender, receiver, content));


                    String full = sender + ":" + receiver + ":" + content;

                    Server.sendPrivate(receiver, full);
                    Server.sendPrivate(sender, full);
                }

                // ================= SEEN =================
                else if (msg.startsWith(Protocol.SEEN)) {

                    String[] parts = msg.split(":", 3);

                    String sender = parts[1];
                    String receiver = parts[2];

                    messageDao.markAsSeen(receiver, sender);

                    Server.sendPrivate(receiver, Protocol.SEEN + ":" + sender);
                }

                // ================= CALL REQUEST =================
                else if (msg.startsWith(Protocol.CALL_REQUEST)) {

                    String[] p = msg.split(":");

                    String caller = p[1];
                    String callee = p[2];
                    String type = p[3];

                    Server.sendPrivate(
                            callee,
                            Protocol.CALL_REQUEST + ":" + caller + ":" + type
                    );
                }

                // ================= CALL ACCEPT =================
                else if (msg.startsWith(Protocol.CALL_ACCEPT)) {

                    String[] p = msg.split(":");

                    String caller = p[1];
                    String callee = p[2];

                    String key = caller + "-" + callee;
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

                    Server.sendPrivate(caller,
                            Protocol.CALL_ACCEPT + ":" + callee + ":" + callId
                    );

                    Server.sendPrivate(callee,
                            Protocol.CALL_ACCEPT + ":" + caller + ":" + callId
                    );
                } else if (msg.startsWith(Protocol.CALL_REJECT)) {

                    String[] p = msg.split(":");
                    if (p.length < 3) continue;

                    String caller = p[1];
                    String callee = p[2];

                    Server.sendPrivate(caller, Protocol.CALL_REJECT + ":" + callee);
                }
                // ================= CALL END =================
                else if (msg.startsWith(Protocol.CALL_END)) {

                    String[] p = msg.split(":");

                    String u1 = p[1];
                    String u2 = p[2];

                    String key = u1 + "-" + u2;
                    String reverseKey = u2 + "-" + u1;

                    Integer callId = Server.activeCalls.remove(key);

                    if (callId == null) {
                        callId = Server.activeCalls.remove(reverseKey);
                    }

                    if (callId != null) {
                        callDao.endCall(callId);
                    }

                    Server.sendPrivate(u1, Protocol.CALL_END + ":" + u2);
                    Server.sendPrivate(u2, Protocol.CALL_END + ":" + u1);
                } else if (msg.startsWith(Protocol.ADD_CONTACT)) {

                    String[] p = msg.split(":");

                    String u1 = p[1];
                    String u2 = p[2];

                    if (!u1.equals(u2)) {
                        contactDao.addContact(u1, u2);


                        sendContactsTo(u1);

                    }
                }
                else if (msg.startsWith(Protocol.DELETE_CONTACT)) {

                    String[] p = msg.split(":");

                    String u1 = p[1];
                    String u2 = p[2];

                    contactDao.deleteContact(u1, u2);

                    sendContactsTo(u1); // refresh غير عندو
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (username != null) {
                Server.removeClient(username);
            }
        }
    }
    private void sendContactsTo(String targetUser) {

        List<String> contacts = contactDao.getContacts(targetUser);

        String data = Protocol.CONTACTS + ":" + String.join(",", contacts);

        Server.sendPrivate(targetUser, data); // 🔥 هنا الفرق الكبير
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