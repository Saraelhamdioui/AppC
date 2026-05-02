package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import network.Protocol;

import java.io.DataOutputStream;
import java.net.Socket;
import java.util.*;

public class UIController {

    @FXML private Label statusLabel;
    @FXML private ListView<String> contactsList;
    @FXML private ImageView videoImageView;
    @FXML private VBox messagesBox;
    @FXML private TextField messageField;
    @FXML private Button endCallBtn;

    private Client client;
    private String username;
    private String selectedUser;
    private String serverIp;

    private Map<String, List<String[]>> conversations = new HashMap<>();

    private AudioSender   audioSender;
    private AudioReceiver audioReceiver;
    private VideoSender   videoSender;
    private VideoReceiver videoReceiver;

    private Socket audioSendSocket;
    private Socket audioRecvSocket;
    private Socket videoSendSocket;
    private Socket videoRecvSocket;

    private int     currentCallId   = -1;
    private String  currentCallType = "audio";
    private boolean callActive      = false;

    // ================= INIT =================
    public void init(String serverIp, String username) {
        this.serverIp = serverIp;
        this.username = username;

        client = new Client(serverIp);
        client.login(username);

        contactsList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> {
                    if (selected != null) {
                        selectedUser = selected;
                        displayConversation(selectedUser);
                        client.sendSeen(username, selectedUser);
                    }
                }
        );

        client.listen(msg -> Platform.runLater(() -> handleIncoming(msg)));
    }

    // ================= INCOMING MESSAGES =================
    private void handleIncoming(String msg) {

        // --- USERS ---
        if (msg.startsWith("USERS:")) {
            String[] split = msg.substring(6).split("\\|ALL:");
            String[] online = split[0].isEmpty() ? new String[0] : split[0].split(",");
            String[] all    = split.length > 1 && !split[1].isEmpty()
                    ? split[1].split(",") : new String[0];

            Set<String> allSet = new LinkedHashSet<>();
            for (String u : all)    if (!u.equals(username)) allSet.add(u);
            for (String u : online) if (!u.equals(username)) allSet.add(u);
            contactsList.getItems().setAll(new ArrayList<>(allSet));

            // --- MSG / HISTORY ---
        } else if (msg.startsWith(Protocol.MSG + ":") ||
                msg.startsWith(Protocol.HISTORY + ":")) {

            String[] p = msg.split(":", 4);
            if (p.length < 4) return;
            String sender  = p[1];
            String receiver = p[2];
            String content  = p[3];

            String key = conversationKey(sender, receiver);
            conversations.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new String[]{sender, content});

            String peer = sender.equals(username) ? receiver : sender;
            if (peer.equals(selectedUser)) {
                addMessageBubble(sender, content);
            }

            // --- CALL_REQUEST : quelqu'un nous appelle ---
            // Format serveur : CALL_REQUEST:caller:type
        } else if (msg.startsWith(Protocol.CALL_REQUEST + ":")) {
            String[] p = msg.split(":", 3);
            if (p.length < 3) return;
            String caller    = p[1];
            String type      = p[2]; // "audio" ou "video"
            currentCallType  = type; // ← on sauvegarde ici pour le receveur

            System.out.println("[APPEL ENTRANT] caller=" + caller + " type=" + type);

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Appel entrant");
            alert.setHeaderText(caller + " vous appelle (" + type + ")");
            alert.setContentText("Accepter ?");

            ButtonType acceptBtn = new ButtonType("Accepter");
            ButtonType rejectBtn = new ButtonType("Refuser", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(acceptBtn, rejectBtn);

            alert.showAndWait().ifPresent(result -> {
                if (result == acceptBtn) {
                    client.acceptCall(username, caller);
                    setStatus("Appel en cours avec " + caller + "...");
                } else {
                    client.rejectCall(username, caller);
                    currentCallType = "audio"; // reset
                }
            });

            // --- CALL_ACCEPT : appel accepté → démarrer la session media ---
            // Format serveur : CALL_ACCEPT:autreUser:callId
        } else if (msg.startsWith(Protocol.CALL_ACCEPT + ":")) {
            String[] p = msg.split(":", 3);
            if (p.length < 3) return;
            String otherUser = p[1];
            try {
                currentCallId = Integer.parseInt(p[2].trim());
            } catch (NumberFormatException e) {
                currentCallId = 0;
            }
            callActive = true;

            System.out.println("[CALL_ACCEPT] otherUser=" + otherUser
                    + " callId=" + currentCallId + " type=" + currentCallType);

            setStatus("Appel en cours avec " + otherUser);
            Platform.runLater(() -> {
                if (endCallBtn != null) endCallBtn.setVisible(true);
            });

            // Toujours démarrer l'audio
            startAudioCallSession();

            // Démarrer la vidéo seulement si c'est un appel vidéo
            if ("video".equals(currentCallType)) {
                System.out.println("[VIDEO] Démarrage session vidéo...");
                startVideoCallSession();
            } else {
                System.out.println("[AUDIO] Appel audio uniquement.");
            }

            // --- CALL_REJECT ---
        } else if (msg.startsWith(Protocol.CALL_REJECT + ":")) {
            String[] p   = msg.split(":", 2);
            String who   = p.length > 1 ? p[1] : "L'utilisateur";
            currentCallType = "audio";
            setStatus(who + " a refusé l'appel.");
            Alert info = new Alert(Alert.AlertType.INFORMATION,
                    who + " a refusé votre appel.", ButtonType.OK);
            info.show();

            // --- CALL_END ---
        } else if (msg.startsWith(Protocol.CALL_END + ":")) {
            if (!callActive) return;
            callActive      = false;
            currentCallType = "audio";
            stopAudioCall();
            stopVideoCall();
            currentCallId = -1;
            Platform.runLater(() -> {
                if (endCallBtn != null) endCallBtn.setVisible(false);
            });
            setStatus("Appel terminé");
        }
    }

    // ================= AFFICHAGE MESSAGES =================
    private void displayConversation(String peer) {
        messagesBox.getChildren().clear();
        String key = conversationKey(username, peer);
        List<String[]> msgs = conversations.getOrDefault(key, Collections.emptyList());
        List<String[]> copy = new ArrayList<>(msgs);
        for (String[] m : copy) {
            addMessageBubbleNow(m[0], m[1]);
        }
    }

    private void addMessageBubble(String sender, String content) {
        // Vérifier que le message appartient à la conversation affichée
        String peer = sender.equals(username) ? selectedUser : sender;
        if (peer == null || !peer.equals(selectedUser)) return;
        addMessageBubbleNow(sender, content);
    }

    private void addMessageBubbleNow(String sender, String content) {
        boolean isMe = sender.equals(username);

        Label bubble = new Label(content);
        bubble.setWrapText(true);
        bubble.setMaxWidth(350);
        bubble.setPadding(new Insets(8));
        bubble.setFont(Font.font(14));
        bubble.setStyle(isMe
                ? "-fx-background-color:#005c4b; -fx-background-radius:12; -fx-text-fill:white;"
                : "-fx-background-color:#202c33; -fx-background-radius:12; -fx-text-fill:white;");

        HBox row = new HBox(bubble);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 8, 2, 8));
        messagesBox.getChildren().add(row);
    }

    // ================= ACTIONS =================
    @FXML
    public void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || selectedUser == null) return;
        client.sendMessage(username, selectedUser, text);
        messageField.clear();
    }

    @FXML
    public void startAudioCall() {
        if (selectedUser == null) return;
        currentCallType = "audio";
        System.out.println("[ACTION] Appel audio vers " + selectedUser);
        client.sendCallRequest(username, selectedUser, "audio");
        setStatus("Appel audio vers " + selectedUser + "...");
    }

    @FXML
    public void startVideoCall() {
        if (selectedUser == null) return;
        currentCallType = "video";
        System.out.println("[ACTION] Appel vidéo vers " + selectedUser);
        client.sendCallRequest(username, selectedUser, "video");
        setStatus("Appel vidéo vers " + selectedUser + "...");
    }

    @FXML
    public void endCall() {
        if (selectedUser == null || !callActive) return;
        callActive      = false;
        currentCallType = "audio";
        client.endCall(username, selectedUser);
        stopAudioCall();
        stopVideoCall();
        currentCallId = -1;
        endCallBtn.setVisible(false);
        setStatus("Appel terminé");
    }

    // ================= AUDIO =================
    private void startAudioCallSession() {
        new Thread(() -> {
            try {
                System.out.println("[AUDIO] Connexion socket send...");
                audioSendSocket = new Socket(serverIp, Client.AUDIO_PORT);
                DataOutputStream dosSend = new DataOutputStream(audioSendSocket.getOutputStream());
                dosSend.writeInt(currentCallId);
                dosSend.writeByte('S');
                dosSend.flush();

                System.out.println("[AUDIO] Connexion socket recv...");
                audioRecvSocket = new Socket(serverIp, Client.AUDIO_PORT);
                DataOutputStream dosRecv = new DataOutputStream(audioRecvSocket.getOutputStream());
                dosRecv.writeInt(currentCallId);
                dosRecv.writeByte('R');
                dosRecv.flush();

                audioSender   = new AudioSender(audioSendSocket);
                audioReceiver = new AudioReceiver(audioRecvSocket);

                new Thread(audioSender::start).start();
                new Thread(audioReceiver).start();
                System.out.println("[AUDIO] Session audio démarrée ✅");

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> setStatus("Erreur audio : " + e.getMessage()));
            }
        }).start();
    }

    private void stopAudioCall() {
        if (audioSender   != null) { audioSender.stop();   audioSender   = null; }
        if (audioReceiver != null) { audioReceiver.stop(); audioReceiver = null; }
    }

    // ================= VIDEO =================
    private void startVideoCallSession() {
        new Thread(() -> {
            try {
                System.out.println("[VIDEO] Connexion socket send (S)...");
                videoSendSocket = new Socket(serverIp, Client.VIDEO_PORT);
                DataOutputStream dosSend = new DataOutputStream(videoSendSocket.getOutputStream());
                dosSend.writeInt(currentCallId);
                dosSend.writeByte('S');
                dosSend.flush();

                System.out.println("[VIDEO] Connexion socket recv (R)...");
                videoRecvSocket = new Socket(serverIp, Client.VIDEO_PORT);
                DataOutputStream dosRecv = new DataOutputStream(videoRecvSocket.getOutputStream());
                dosRecv.writeInt(currentCallId);
                dosRecv.writeByte('R');
                dosRecv.flush();

                videoSender   = new VideoSender(videoSendSocket);
                videoReceiver = new VideoReceiver(videoRecvSocket, videoImageView);

                new Thread(videoSender).start();
                new Thread(videoReceiver).start();
                System.out.println("[VIDEO] Session vidéo démarrée ✅");

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> setStatus("Erreur vidéo : " + e.getMessage()));
            }
        }).start();
    }

    private void stopVideoCall() {
        if (videoSender   != null) { videoSender.stop();   videoSender   = null; }
        if (videoReceiver != null) { videoReceiver.stop(); videoReceiver = null; }
        Platform.runLater(() -> {
            if (videoImageView != null) videoImageView.setImage(null);
        });
    }

    // ================= UTILS =================
    private String conversationKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private void setStatus(String text) {
        Platform.runLater(() -> {
            if (statusLabel != null) statusLabel.setText(text);
        });
    }
}