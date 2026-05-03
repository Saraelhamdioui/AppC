package Controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.Message;
import DAO.CallDao;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.*;

public class UIController {

    @FXML private VBox messagesBox;
    @FXML private TextField messageField;
    @FXML private ListView<String> contactsList;
    @FXML private Button endCallBtn;

    private Client client;
    private String username;
    private String selectedUser;

    private HBox callBox;
    private Label timerLabel;
    private long startTime;
    private AudioReceiver receiver;
    private volatile boolean audioStopped = false;

    private void drawCallRequest(String caller) {

        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10);
        card.setStyle("-fx-background-color:#1f2c34; -fx-padding:10; -fx-background-radius:12;");

        Label text = new Label("📞 " + caller + " is calling...");
        text.setStyle("-fx-text-fill:white;");

        HBox buttons = new HBox(10);

        Button accept = new Button("Accept");
        Button reject = new Button("Reject");

        accept.setStyle("-fx-background-color:#00a884; -fx-text-fill:white;");
        reject.setStyle("-fx-background-color:red; -fx-text-fill:white;");

        buttons.getChildren().addAll(accept, reject);

        card.getChildren().addAll(text, buttons);
        box.getChildren().add(card);

        messagesBox.getChildren().add(box);

        // ================= handlers =================
        accept.setOnAction(e -> {
            client.acceptCall(username, caller);
            messagesBox.getChildren().remove(box);
            startCallUI(caller);
        });

        reject.setOnAction(e -> {
            client.rejectCall(username, caller);
            messagesBox.getChildren().remove(box);
        });
    }
    // =========================
    // CALL ACTIVE UI
    // =========================
    private void startCallUI(String otherUser) {
        if (callBox != null) {
            messagesBox.getChildren().remove(callBox);
            callBox = null;
        }
        callBox = new HBox(10);
        callBox.setAlignment(Pos.CENTER);

        Label label = new Label("📞 In call with " + otherUser);
        label.setStyle("-fx-text-fill:white;");

        timerLabel = new Label("00:00");
        timerLabel.setStyle("-fx-text-fill:#00ffcc;");

        Button end = new Button("End");
        end.setStyle("-fx-background-color:red; -fx-text-fill:white;");

        callBox.getChildren().addAll(label, timerLabel, end);

        messagesBox.getChildren().add(callBox);

        startTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                while (callBox != null) {

                    long sec = (System.currentTimeMillis() - startTime) / 1000;
                    String time = String.format("%02d:%02d", sec / 60, sec % 60);

                    Platform.runLater(() -> timerLabel.setText(time));

                    Thread.sleep(1000);
                }
            } catch (Exception ignored) {}
        }).start();

        end.setOnAction(e -> {
            client.endCall(username, otherUser);
            stopCallUI();
        });
    }

    // =========================
    // STOP CALL UI
    // =========================
    private void stopCallUI() {
        if (callBox != null) {
            messagesBox.getChildren().remove(callBox);
            callBox = null;
        }
    }

    // 🎤 Audio
    private AudioSender sender;
    private Thread receiverThread;

    // ⏱ Call info
    private long callStartTime;
    private int currentCallId;

    private CallDao callDao = new CallDao();

    // 💬 data
    private Map<String, List<Message>> conversations = new HashMap<>();
    private Map<String, Integer> unreadCount = new HashMap<>();

    private List<String> allUsersUI = new ArrayList<>();
    private Set<String> onlineUsers = new HashSet<>();

    // ================= INIT =================
    public void setUsername(String username) {

        this.username = username;

        contactsList.setOnMouseClicked(e -> {

            int index = contactsList.getSelectionModel().getSelectedIndex();
            if (index == -1) return;

            selectedUser = allUsersUI.get(index);

            messagesBox.getChildren().clear();

            conversations.putIfAbsent(selectedUser, new ArrayList<>());

            for (Message m : conversations.get(selectedUser)) {
                drawMessage(m);
            }

            unreadCount.put(selectedUser, 0);
            updateUsersListUI();

            client.sendSeen(username, selectedUser);
            markSeen();
        });

        client = new Client();
        client.login(username);

        client.listen(msg -> {
            Platform.runLater(() -> {

                // USERS
                if (msg.startsWith("USERS:")) {
                    updateUsers(msg);

                    // HISTORY
                } else if (msg.startsWith("HISTORY:")) {
                    addMessage(msg.replace("HISTORY:", ""));

                    // SEEN
                } else if (msg.startsWith("SEEN:")) {
                    markSeen();

                    // ================= CALL REQUEST =================
                } else if (msg.startsWith("CALL_REQUEST")) {

                    String[] p = msg.split(":");
                    String caller = p[1];
                    String type = p[2];

                    drawCallRequest(caller);


                    // ================= CALL ACCEPT =================
                } else if (msg.startsWith("CALL_ACCEPT")) {

                    String[] p = msg.split(":");

                    String otherUser = p[1];

                    if (p.length >= 3) {
                        try {
                            currentCallId = Integer.parseInt(p[2]);
                        } catch (Exception e) {
                            currentCallId = -1;
                        }
                    }

                    // 🔥 1. reset old UI first
                    stopCallUI();

                    // 🔥 2. set state
                    selectedUser = otherUser;
                    callStartTime = System.currentTimeMillis();

                    // 🔥 3. UI first
                    startCallUI(otherUser);
                    endCallBtn.setVisible(true);

                    // 🔥 4. audio last (important)
                    startAudioCallSession();

                    // ================= CALL END =================
                } else if (msg.startsWith("CALL_END")) {

                    String[] p = msg.split(":");
                    String other = p.length > 1 ? p[1] : "User";

                    // 🔥 stop audio
                    stopAudioCall();

                    // 🔥 stop UI call (VERY IMPORTANT)
                    stopCallUI();

                    // ❌ ما تديرش DB هنا (server هو المسؤول)
                    endCallBtn.setVisible(false);

                    long duration = (System.currentTimeMillis() - callStartTime) / 1000;

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setHeaderText(
                            "Call ended with " + other +
                                    "\nDuration: " + duration + " sec"
                    );
                    alert.showAndWait();

                    // CHAT
                } else {
                    addMessage(msg);
                }
            });
        });
    }

    // ================= CHAT =================
    @FXML
    public void sendMessage() {

        String msg = messageField.getText();

        if (msg.isEmpty() || selectedUser == null) return;

        client.sendMessage(username, selectedUser, msg);
        messageField.clear();
    }

    // ================= CALL BUTTONS =================
    @FXML
    public void startAudioCall() {

        if (selectedUser == null) return;

        client.sendCallRequest(username, selectedUser, "audio");
    }

    @FXML
    public void startVideoCall() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Video Call");
        alert.setHeaderText(null);
        alert.setContentText("Video call is not implemented yet,please wait till next update, thank youuuu!.");
        alert.showAndWait();
    }

    @FXML
    public void endCall() {
        if (selectedUser != null) {
            client.endCall(username, selectedUser);
            stopAudioCall();
            endCallBtn.setVisible(false);
        }
    }

    // ================= AUDIO CALL =================
    private void startAudioCallSession() {
        audioStopped = false;
        try {
            Socket audioSocket = new Socket("localhost", 5001);

            // 🔥 send callId FIRST
            DataOutputStream dos = new DataOutputStream(audioSocket.getOutputStream());
            dos.writeInt(currentCallId);
            dos.flush();

            // 🎤 start sending audio
            sender = new AudioSender(audioSocket);
            new Thread(() -> sender.start()).start();

            // 🔊 start receiving audio
            receiver = new AudioReceiver(audioSocket);
            receiverThread = new Thread(receiver);
            receiverThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopAudioCall() {

        if (audioStopped) return;
        audioStopped = true;

        try {
            if (sender != null) sender.stop();
            if (receiver != null) receiver.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= MESSAGES =================
    private void addMessage(String msg) {

        String[] parts = msg.split(":", 3);

        String sender = parts[0];
        String receiver = parts[1];
        String content = parts[2];

        String otherUser = sender.equals(username) ? receiver : sender;

        conversations.putIfAbsent(otherUser, new ArrayList<>());

        Message m = new Message(sender, receiver, content);
        conversations.get(otherUser).add(m);

        if (!sender.equals(username) && !otherUser.equals(selectedUser)) {
            unreadCount.put(otherUser,
                    unreadCount.getOrDefault(otherUser, 0) + 1);
            updateUsersListUI();
        }

        if (!sender.equals(username) && otherUser.equals(selectedUser)) {
            client.sendSeen(username, sender);
            m.setSeen(true);
        }

        if (otherUser.equals(selectedUser)) {
            drawMessage(m);
        }
    }

    private void drawMessage(Message m) {

        boolean isMe = m.getSender().equals(username);

        HBox box = new HBox();
        Label label = new Label();

        if (isMe) {
            String ticks = m.isSeen() ? " ✔✔" : " ✔";
            label.setText(m.getSender() + ": " + m.getContent() + ticks);
        } else {
            label.setText(m.getSender() + ": " + m.getContent());
        }

        label.setWrapText(true);
        label.setMaxWidth(300);

        label.setStyle(
                "-fx-padding:10;" +
                        "-fx-background-radius:15;" +
                        (isMe
                                ? "-fx-background-color:#00a884; -fx-text-fill:white;"
                                : "-fx-background-color:#202c33; -fx-text-fill:white;")
        );

        box.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        box.getChildren().add(label);

        messagesBox.getChildren().add(box);
    }

    private void markSeen() {

        if (selectedUser == null) return;

        List<Message> msgs = conversations.get(selectedUser);
        if (msgs == null) return;

        for (Message m : msgs) {
            if (m.getSender().equals(username)) {
                m.setSeen(true);
            }
        }

        messagesBox.getChildren().clear();

        for (Message m : msgs) {
            drawMessage(m);
        }
    }

    // ================= USERS =================
    private void updateUsers(String msg) {

        String[] parts = msg.split("\\|");

        String onlinePart = parts[0].replace("USERS:", "");
        String allPart = parts[1].replace("ALL:", "");

        onlineUsers.clear();
        allUsersUI.clear();

        if (!allPart.isEmpty()) {
            allUsersUI.addAll(Arrays.asList(allPart.split(",")));
        }

        if (!onlinePart.isEmpty()) {
            onlineUsers.addAll(Arrays.asList(onlinePart.split(",")));
        }

        allUsersUI.remove(username);

        updateUsersListUI();
    }

    private void updateUsersListUI() {

        contactsList.getItems().clear();

        for (String user : allUsersUI) {

            int count = unreadCount.getOrDefault(user, 0);

            String display = "● " + user;

            if (count > 0) display += " (" + count + ")";

            contactsList.getItems().add(display);
        }

        contactsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(item);

                String usernameOnly = item.replaceAll("^● ", "")
                        .replaceAll("\\(\\d+\\)", "")
                        .trim();

                if (onlineUsers.contains(usernameOnly)) {
                    setStyle("-fx-text-fill: #27ae60;");
                } else {
                    setStyle("-fx-text-fill: #888888;");
                }
            }
        });
    }
}