package controller;

import network.Protocol;
import java.io.*;
import java.net.*;

public class Client {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String serverIp;

    public static final int CHAT_PORT  = 1234;
    public static final int AUDIO_PORT = 5001;
    public static final int VIDEO_PORT = 5002; // un seul port vidéo

    public Client(String serverIp) {
        this.serverIp = serverIp;
        try {
            socket = new Socket(serverIp, CHAT_PORT);
            in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out    = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getServerIp() { return serverIp; }

    public void login(String username) {
        out.println(Protocol.LOGIN + ":" + username);
    }

    public void sendMessage(String sender, String receiver, String msg) {
        out.println(Protocol.MSG + ":" + sender + ":" + receiver + ":" + msg);
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
}