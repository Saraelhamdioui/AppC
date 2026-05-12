
package Controller;

import DAO.GroupDao;
import DAO.GroupMessageDao;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import model.Group;
import model.GroupMember;
import model.GroupMessage;
import network.Protocol;

import java.io.DataOutputStream;
import java.io.File;
import java.net.Socket;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class UIController {

    @FXML private Label statusLabel;
    @FXML private Label chatUserLabel;
    @FXML private Label chatStatusLabel;

    @FXML private ListView<String> contactsList;
    @FXML private ImageView videoImageView;
    @FXML private VBox messagesBox;
    @FXML private TextField messageField;
    @FXML private Button endCallBtn;
    @FXML private Button emojiBtn;
    @FXML private MenuButton contactMenuBtn;
    @FXML private MenuItem deleteContactMenu;
    @FXML private MenuItem blockContactMenu;
    @FXML private MenuItem unblockContactMenu;

    private final Set<String> blockedContacts = new HashSet<>();

    // Groupes
    @FXML private ListView<String> groupListView;
    @FXML private VBox groupMessagesBox;
    @FXML private TextField groupMessageField;
    @FXML private TextField groupNameField;
    @FXML private TextField addMemberField;
    @FXML private Label selectedGroupLabel;
    @FXML private TabPane mainTabPane;

    // ── Nouveaux éléments groupe ──────────────────────────────────────────
    @FXML private Label       groupMembersPreviewLabel;
    @FXML private TextField   groupSearchField;
    @FXML private VBox        createGroupPanel;
    @FXML private VBox        addMemberPanel;
    @FXML private VBox        removeMemberPanel;
    @FXML private TextField   removeMemberField;
    @FXML private MenuButton  groupMenuBtn;
    @FXML private Button      groupAudioCallBtn;
    @FXML private Button      groupVideoCallBtn;
    @FXML private Button      joinGroupCallBtn;
    @FXML private Button      leaveGroupCallBtn;
    @FXML private Button      groupSendFileBtn;
    private boolean           inGroupCall      = false;
    private boolean           inGroupAudioCall = false;
    private boolean           inGroupVideoCall = false;

    private Client client;
    private String username;
    private String selectedUser;
    private String serverIp;
    private boolean suppressSelectionEvent = false;

    private final Map<String, List<String[]>> conversations = new HashMap<>();
    private final Set<String> onlineUsers = new HashSet<>();
    private final Set<String> seenConversations = new HashSet<>();

    private AudioSender audioSender;
    private AudioReceiver audioReceiver;
    private VideoSender videoSender;
    private VideoReceiver videoReceiver;

    private Socket audioSendSocket;
    private Socket audioRecvSocket;
    private Socket videoSendSocket;
    private Socket videoRecvSocket;

    private int currentCallId = -1;
    private String currentCallType = "audio";
    private boolean callActive = false;
    private final Map<String, Integer> unreadCounts = new HashMap<>();

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    // Groupes - champs supplémentaires
    private GroupController groupController;
    private Map<Integer, Group> userGroups = new HashMap<>();
    private int currentGroupId = -1;
    private Map<Integer, List<GroupMessage>> groupConversations = new HashMap<>();
    private Map<Integer, Integer> unreadGroupCounts = new HashMap<>();

    public void init(String serverIp, String username) {
        this.serverIp = serverIp;
        this.username = username;

        setupContactCellFactory();
        initGroups();

        if (videoImageView != null) videoImageView.setVisible(false);
        if (endCallBtn != null) endCallBtn.setVisible(false);

        client = new Client(serverIp);

        // IMPORTANT : démarrer le listener AVANT le login
        // sinon les messages HISTORY envoyés immédiatement par le serveur
        // arrivent avant que le listener soit actif → perdus définitivement
        client.listen(msg -> Platform.runLater(() -> handleIncoming(msg)));

        contactsList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> {
                    if (suppressSelectionEvent) return;
                    if (selected != null) {
                        selectedUser = selected;

                        unreadCounts.remove(selectedUser);
                        contactsList.refresh();

                        if (chatUserLabel != null) chatUserLabel.setText(selectedUser);

                        if (chatStatusLabel != null) {
                            chatStatusLabel.setText(
                                    onlineUsers.contains(selectedUser) ? "en ligne" : "hors ligne"
                            );
                        }

                        // Afficher le menu ⋮ et ajuster Block/Unblock selon l'état
                        boolean isBlocked = blockedContacts.contains(selectedUser);
                        if (contactMenuBtn    != null) contactMenuBtn.setVisible(true);
                        if (blockContactMenu  != null) blockContactMenu.setVisible(!isBlocked);
                        if (unblockContactMenu != null) unblockContactMenu.setVisible(isBlocked);

                        displayConversation(selectedUser);
                        client.sendSeen(username, selectedUser);
                    }
                }
        );

        client.login(username); // Login EN DERNIER, après que le listener est prêt
    }

    private void initGroups() {
        groupController = new GroupController();

        if (groupListView != null) {
            groupListView.getSelectionModel().selectedItemProperty().addListener(
                    (obs, old, selected) -> {
                        if (selected != null) {
                            // Supprimer le suffixe " (N)" des messages non-lus avant comparaison
                            String pureName = selected.replaceAll(" \\(\\d+\\)$", "");
                            for (Group g : userGroups.values()) {
                                if (g.getName().equals(pureName)) {
                                    currentGroupId = g.getId();
                                    break;
                                }
                            }

                            if (selectedGroupLabel != null) {
                                selectedGroupLabel.setText(selected);
                            }

                            loadGroupMessages(currentGroupId);
                            client.sendGroupSeen(currentGroupId, username);
                            // loadGroupMembers retiré : ne plus afficher la liste à chaque sélection

                            // ── Afficher les boutons d'action ─────────────
                            if (groupMenuBtn       != null) groupMenuBtn.setVisible(true);
                            if (groupAudioCallBtn  != null) groupAudioCallBtn.setVisible(true);
                            if (groupVideoCallBtn  != null) groupVideoCallBtn.setVisible(true);
                            if (groupSendFileBtn   != null) groupSendFileBtn.setVisible(true);
                            // Réinitialiser l'état des boutons d'appel
                            if (joinGroupCallBtn   != null) joinGroupCallBtn.setVisible(false);
                            if (leaveGroupCallBtn  != null) leaveGroupCallBtn.setVisible(false);
                            inGroupCall = false; inGroupAudioCall = false; inGroupVideoCall = false;
                        }
                    }
            );
        }

        loadUserGroups();
    }

    private void initGroupControllers() {
        // Initialisation des contrôleurs de groupe
    }

    private void loadUserGroups() {
        if (client != null) {
            client.getGroupList(username);
        }
    }

    private void loadGroupMessages(int groupId) {
        if (groupController != null) {
            unreadGroupCounts.remove(groupId);
            List<GroupMessage> messages = groupController.getGroupHistory(groupId, 100);
            groupConversations.put(groupId, new ArrayList<>(messages));
            displayGroupMessages(groupId);
            updateGroupListView();
        }
    }

    private void displayGroupMessages(int groupId) {
        Platform.runLater(() -> {
            if (groupMessagesBox != null) {
                groupMessagesBox.getChildren().clear();

                List<GroupMessage> messages = groupConversations.get(groupId);
                if (messages != null) {
                    for (GroupMessage msg : messages) {
                        boolean isMine = msg.getSender().equals(username);
                        addGroupMessageBubbleUI(msg.getSender(), msg.getContent(), isMine);
                    }
                }
            }
        });
    }



    // Dans UIController.java - Ajoutez ces méthodes publiques

    // Appels audio/vidéo
    @FXML
    public void startAudioCall() {
        handleStartAudioCall();
    }

    @FXML
    public void startVideoCall() {
        handleStartVideoCall();
    }

    @FXML
    public void endCall() {
        handleEndCall();
    }

    // Envoi de messages
    @FXML
    public void sendMessage() {
        handleSendMessage();
    }

    @FXML
    public void sendFile() {
        handleSendFile();
    }







    private void addGroupMessageBubble(String sender, String content, boolean isMine) {
        Platform.runLater(() -> addGroupMessageBubbleUI(sender, content, isMine));
    }

    private void addGroupMessageBubbleUI(String sender, String content, boolean isMine) {
        GroupHubble bubble = new GroupHubble(sender, content, isMine);
        groupMessagesBox.getChildren().add(bubble);
        groupMessagesBox.layout();
    }

    private void loadGroupMembers(int groupId) {
        if (client != null && groupId != -1) {
            client.getGroupMembers(groupId);
        }
    }

    private void updateGroupListView() {
        Platform.runLater(() -> {
            if (groupListView != null) {
                groupListView.getItems().clear();
                for (Group g : userGroups.values()) {
                    String displayName = g.getName();
                    int unread = unreadGroupCounts.getOrDefault(g.getId(), 0);
                    if (unread > 0) {
                        displayName += " (" + unread + ")";
                    }
                    groupListView.getItems().add(displayName);
                }
            }
        });
    }

    private void showGroupMembers(String[] members) {
        Platform.runLater(() -> {
            ListView<String> memberList = new ListView<>();
            memberList.getItems().addAll(members);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Membres du groupe");
            alert.setHeaderText("Liste des membres");
            alert.getDialogPane().setContent(memberList);
            alert.setResizable(true);
            alert.show();
        });
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(message);
            alert.showAndWait();
        });
    }

    private void handleIncoming(String msg) {

        System.out.println("[CLIENT-IN] " + msg);

        if (msg.startsWith("USERS:")) {
            String[] split = msg.substring(6).split("\\|ALL:");
            String[] online = split[0].isEmpty() ? new String[0] : split[0].split(",");

            onlineUsers.clear();

            for (String u : online) {
                u = u.trim();
                if (!u.isEmpty() && !u.equals(username)) onlineUsers.add(u);
            }

            // Rafraîchir uniquement le statut en ligne dans la liste des contacts
            contactsList.refresh();

            if (selectedUser != null && chatStatusLabel != null) {
                chatStatusLabel.setText(
                        onlineUsers.contains(selectedUser) ? "en ligne" : "hors ligne"
                );
            }

        } else if (msg.startsWith(Protocol.CONTACTS + ":")) {
            // Mise à jour de la liste des contacts personnels
            String data = msg.substring(Protocol.CONTACTS.length() + 1);
            System.out.println("[CONTACTS] data brut = '" + data + "'");
            System.out.println("[CONTACTS] conversations actuelles = " + conversations.keySet());
            // Utiliser un LinkedHashSet pour préserver l'ordre et éviter les doublons
            Set<String> merged = new LinkedHashSet<>();

            // 1. Ajouter les contacts explicitement ajoutés (via bouton ➕)
            if (!data.isEmpty()) {
                for (String u : data.split(",")) {
                    u = u.trim();
                    if (!u.isEmpty() && !u.equals(username)) merged.add(u);
                }
            }

            // 2. Ajouter aussi les peers avec qui on a déjà des messages (HISTORY)
            for (String key : conversations.keySet()) {
                for (String part : key.split("_")) {
                    if (!part.equals(username)) merged.add(part);
                }
            }

            System.out.println("[CONTACTS] merged final = " + merged);

            // Sauvegarder la sélection courante avant setAll (qui reset la ListView)
            String previousSelection = selectedUser;
            suppressSelectionEvent = true;
            contactsList.getItems().setAll(merged);
            // Restaurer la sélection pour ne pas effacer la conversation affichée
            if (previousSelection != null && merged.contains(previousSelection)) {
                contactsList.getSelectionModel().select(previousSelection);
            }
            suppressSelectionEvent = false;
            contactsList.refresh();
            System.out.println("[CONTACTS] contactsList finale = " + contactsList.getItems());

        } else if (msg.startsWith("ADD_CONTACT_RESULT:")) {
            String[] parts = msg.split(":", 3);
            if (parts.length >= 3 && "ERROR".equals(parts[1])) {
                showAlert("Erreur", parts[2]);
            }

        } else if (msg.startsWith("BLOCK_RESULT:OK:")) {
            String blocked = msg.split(":", 3)[2];
            // Retirer le contact bloqué de la liste et effacer la conversation affichée
            contactsList.getItems().remove(blocked);
            if (blocked.equals(selectedUser)) {
                selectedUser = null;
                if (chatUserLabel != null) chatUserLabel.setText("Choisissez un contact");
                if (messagesBox != null) messagesBox.getChildren().clear();
            }
            showAlert("Bloqué", blocked + " a été bloqué. Il ne peut plus vous envoyer de messages.");

        } else if (msg.startsWith("UNBLOCK_RESULT:OK:")) {
            String unblocked = msg.split(":", 3)[2];
            showAlert("Débloqué", unblocked + " a été débloqué.");

        } else if (msg.startsWith(Protocol.MSG + ":") ||
                msg.startsWith(Protocol.HISTORY + ":")) {

            String[] p = msg.split(":", 6);
            if (p.length < 5) return;

            int messageId;

            try {
                messageId = Integer.parseInt(p[1]);
            } catch (NumberFormatException e) {
                messageId = -1;
            }

            String sender = p[2];
            String receiver = p[3];
            String content = p[4];

            boolean seen = false;
            if (p.length >= 6) {
                seen = Boolean.parseBoolean(p[5]);
            }

            String key = conversationKey(sender, receiver);

            conversations.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new String[]{
                            String.valueOf(messageId),
                            sender,
                            content,
                            String.valueOf(seen)
                    });

            String peer = sender.equals(username) ? receiver : sender;

            if (!contactsList.getItems().contains(peer)) {
                contactsList.getItems().add(peer);
                contactsList.refresh();
            }

            if (peer.equals(selectedUser)) {
                addMessageBubbleNow(sender, content, seen);
                client.sendSeen(username, peer);
            } else if (!sender.equals(username) && msg.startsWith(Protocol.MSG + ":")) {
                unreadCounts.put(peer, unreadCounts.getOrDefault(peer, 0) + 1);
                contactsList.refresh();
            }

        } else if (msg.startsWith("FILE:")) {
            String[] p = msg.split(":", 5);
            if (p.length < 5) return;

            String sender = p[1];
            String receiver = p[2];
            String fileName = p[3];
            String fileData = p[4];

            try {
                File dir = new File("received_files");
                if (!dir.exists()) dir.mkdirs();

                byte[] bytes = Base64.getDecoder().decode(fileData);
                File receivedFile = new File(dir, fileName);
                Files.write(receivedFile.toPath(), bytes);

                String text = sender.equals(username)
                        ? "📎 Fichier envoyé : " + fileName
                        : "📎 Fichier reçu : " + fileName;

                String key = conversationKey(sender, receiver);

                conversations.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new String[]{
                                "-1",
                                sender,
                                text
                        });

                String peer = sender.equals(username) ? receiver : sender;

                if (!contactsList.getItems().contains(peer)) {
                    contactsList.getItems().add(peer);
                    contactsList.refresh();
                }

                if (peer.equals(selectedUser)) {
                    addMessageBubble(sender, text);
                    client.sendSeen(username, peer);
                } else if (!sender.equals(username)) {
                    unreadCounts.put(peer, unreadCounts.getOrDefault(peer, 0) + 1);
                    contactsList.refresh();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if (msg.startsWith(Protocol.SEEN + ":")) {
            String[] p = msg.split(":", 2);
            if (p.length < 2) return;

            String peer = p[1];
            String key = conversationKey(username, peer);

            List<String[]> msgs = conversations.get(key);

            if (msgs != null) {
                for (String[] m : msgs) {
                    String sender = m[1];

                    if (sender.equals(username) && m.length >= 4) {
                        m[3] = "true";
                    }
                }
            }

            if (selectedUser != null && selectedUser.equals(peer)) {
                displayConversation(selectedUser);
            }

            contactsList.refresh();
        }
        else if (msg.startsWith(Protocol.CALL_REQUEST + ":")) {
            String[] p = msg.split(":", 3);
            if (p.length < 3) return;

            String caller = p[1];
            String type = p[2];
            currentCallType = type;

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Appel entrant");
            alert.setHeaderText(caller + " vous appelle");
            alert.setContentText(type.equals("video") ? "Appel vidéo entrant" : "Appel audio entrant");

            ButtonType acceptBtn = new ButtonType("Accepter");
            ButtonType rejectBtn = new ButtonType("Refuser", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(acceptBtn, rejectBtn);

            alert.showAndWait().ifPresent(result -> {
                if (result == acceptBtn) {
                    selectedUser = caller;
                    contactsList.getSelectionModel().select(caller);

                    if (chatUserLabel != null) chatUserLabel.setText(caller);
                    if (chatStatusLabel != null) chatStatusLabel.setText("en appel");

                    client.acceptCall(username, caller);
                    setStatus("Appel en cours avec " + caller);
                } else {
                    client.rejectCall(username, caller);
                    currentCallType = "audio";
                }
            });

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

            if (chatStatusLabel != null) chatStatusLabel.setText("en appel");
            if (endCallBtn != null) endCallBtn.setVisible(true);

            setStatus("Appel en cours avec " + otherUser);

            startAudioCallSession();

            if ("video".equals(currentCallType)) startVideoCallSession();

        } else if (msg.startsWith(Protocol.CALL_REJECT + ":")) {
            String[] p = msg.split(":", 2);
            String who = p.length > 1 ? p[1] : "L'utilisateur";

            currentCallType = "audio";
            callActive = false;

            if (endCallBtn != null) endCallBtn.setVisible(false);

            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Appel refusé");
            info.setHeaderText(who + " a refusé votre appel.");
            info.show();

        } else if (msg.startsWith(Protocol.CALL_END + ":")) {
            if (!callActive) return;

            callActive = false;
            currentCallType = "audio";

            stopAudioCall();
            stopVideoCall();

            currentCallId = -1;

            if (endCallBtn != null) endCallBtn.setVisible(false);

            if (chatStatusLabel != null && selectedUser != null) {
                chatStatusLabel.setText(
                        onlineUsers.contains(selectedUser) ? "en ligne" : "hors ligne"
                );
            }

            setStatus("Appel terminé");
        }
        // Gestion des messages de groupe
        else if (msg.startsWith("GROUP_HISTORY_MSG:") ||
                msg.startsWith(Protocol.GROUP_CREATE) ||
                msg.startsWith(Protocol.GROUP_MSG) ||
                msg.startsWith(Protocol.GROUP_LIST) ||
                msg.startsWith(Protocol.GROUP_ADD_MEMBER) ||
                msg.startsWith(Protocol.GROUP_REMOVE_MEMBER) ||
                msg.startsWith(Protocol.GROUP_MEMBERS) ||
                msg.startsWith(Protocol.GROUP_SEEN)) {
            handleGroupIncoming(msg);
        }
    }

    private void handleGroupIncoming(String msg) {
        if (msg.startsWith("GROUP_HISTORY_MSG:")) {
            // Historique reçu à la connexion pour les membres déconnectés
            String[] parts = msg.split(":", 4);
            if (parts.length >= 4) {
                try {
                    int groupId = Integer.parseInt(parts[1]);
                    String sender = parts[2];
                    String content = parts[3];
                    GroupMessage groupMsg = new GroupMessage(groupId, sender, content);
                    groupConversations.computeIfAbsent(groupId, k -> new ArrayList<>()).add(groupMsg);
                    // Si ce groupe est actuellement ouvert, afficher immédiatement
                    if (currentGroupId == groupId) {
                        addGroupMessageBubble(sender, content, sender.equals(username));
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }

        } else if (msg.startsWith(Protocol.GROUP_CREATE + ":")) {
            String[] parts = msg.split(":", 3);
            if (parts.length >= 3) {
                int groupId = Integer.parseInt(parts[1]);
                String groupName = parts[2];

                showAlert("Groupe créé", "Groupe '" + groupName + "' créé avec succès !");
                loadUserGroups();
            }

        } else if (msg.startsWith(Protocol.GROUP_MSG + ":")) {
            String[] parts = msg.split(":", 4);
            if (parts.length >= 4) {
                int groupId = Integer.parseInt(parts[1]);
                String sender = parts[2];
                String content = parts[3];

                GroupMessage groupMsg = new GroupMessage(groupId, sender, content);
                groupConversations.computeIfAbsent(groupId, k -> new ArrayList<>()).add(groupMsg);

                if (currentGroupId == groupId) {
                    addGroupMessageBubble(sender, content, sender.equals(username));
                    client.sendGroupSeen(groupId, username);
                } else {
                    unreadGroupCounts.put(groupId, unreadGroupCounts.getOrDefault(groupId, 0) + 1);
                    updateGroupListView();
                }
            }

        } else if (msg.startsWith(Protocol.GROUP_LIST + ":")) {
            String data = msg.substring(Protocol.GROUP_LIST.length() + 1);
            userGroups.clear();
            if (!data.isEmpty()) {
                String[] groupsData = data.split(",");

                for (String gd : groupsData) {
                    if (!gd.isEmpty()) {
                        String[] parts = gd.split("\\|");
                        if (parts.length == 2) {
                            Group g = new Group();
                            g.setId(Integer.parseInt(parts[0]));
                            g.setName(parts[1]);
                            userGroups.put(g.getId(), g);
                        }
                    }
                }
            }
            updateGroupListView();

        } else if (msg.startsWith(Protocol.GROUP_ADD_MEMBER + ":")) {
            String[] parts = msg.split(":", 4);
            if (parts.length >= 3) {
                String newMember = parts[2];
                showAlert("Nouveau membre", newMember + " a rejoint le groupe !");
            }

        } else if (msg.startsWith(Protocol.GROUP_REMOVE_MEMBER + ":")) {
            String[] parts = msg.split(":", 4);
            if (parts.length >= 3) {
                String removedMember = parts[2];
                if (removedMember.equals(username)) {
                    showAlert("Groupe", "Vous avez été retiré du groupe");
                    currentGroupId = -1;
                    loadUserGroups();
                } else {
                    showAlert("Membre retiré", removedMember + " a été retiré du groupe !");
                }
            }

        } else if (msg.startsWith(Protocol.GROUP_MEMBERS + ":")) {
            String[] parts = msg.split(":", 3);
            if (parts.length >= 3) {
                String membersStr = parts[2];
                String[] members = membersStr.isEmpty() ? new String[0] : membersStr.split(",");
                showGroupMembers(members);
            }

        } else if (msg.startsWith(Protocol.GROUP_SEEN + ":")) {
            String[] parts = msg.split(":", 3);
            if (parts.length >= 3) {
                int groupId = Integer.parseInt(parts[1]);
                if (currentGroupId == groupId) {
                    loadGroupMessages(groupId);
                }
            }

        } else if (msg.startsWith("GROUP_CALL_NOTIFY:")) {
            // FORMAT: GROUP_CALL_NOTIFY:callType:groupId:caller
            String[] p = msg.split(":", 4);
            if (p.length >= 4) {
                String callType = p[1];
                int gid = Integer.parseInt(p[2]);
                String caller = p[3];
                if (currentGroupId == gid && !inGroupCall) {
                    Platform.runLater(() -> {
                        if (joinGroupCallBtn != null) joinGroupCallBtn.setVisible(true);
                        String icon = "video".equals(callType) ? "📹" : "📞";
                        showAlert(icon + " Appel de groupe",
                                caller + " a lancé un appel " + callType + ". Cliquez 'Rejoindre' !");
                    });
                }
            }

        } else if (msg.startsWith("GROUP_FILE:")) {
            // FORMAT: GROUP_FILE:groupId:sender:fileName:base64data
            String[] p = msg.split(":", 5);
            if (p.length >= 5) {
                try {
                    int gid = Integer.parseInt(p[1]);
                    String sender = p[2];
                    String fileName = p[3];
                    byte[] bytes = java.util.Base64.getDecoder().decode(p[4]);
                    java.io.File dir = new java.io.File("received_files");
                    if (!dir.exists()) dir.mkdirs();
                    java.nio.file.Files.write(new java.io.File(dir, fileName).toPath(), bytes);
                    String label = sender.equals(username)
                            ? "📎 Fichier envoyé : " + fileName
                            : "📎 " + sender + " a envoyé : " + fileName;
                    if (currentGroupId == gid) {
                        addGroupMessageBubble(sender, label, sender.equals(username));
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    private void setupContactCellFactory() {
        contactsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String user, boolean empty) {
                super.updateItem(user, empty);

                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: white;");
                    return;
                }

                boolean isOnline = onlineUsers.contains(user);
                int unread = unreadCounts.getOrDefault(user, 0);

                Label avatar = new Label(user.substring(0, 1).toUpperCase());
                avatar.setAlignment(Pos.CENTER);
                avatar.setStyle(
                        "-fx-background-color: #dfe5e7;" +
                                "-fx-background-radius: 50;" +
                                "-fx-min-width: 42;" +
                                "-fx-min-height: 42;" +
                                "-fx-max-width: 42;" +
                                "-fx-max-height: 42;" +
                                "-fx-font-size: 16px;" +
                                "-fx-font-weight: bold;" +
                                "-fx-text-fill: #54656f;"
                );

                Label name = new Label(user);
                name.setStyle(
                        "-fx-text-fill: #111b21;" +
                                "-fx-font-size: 14px;" +
                                "-fx-font-weight: bold;"
                );

                Label status = new Label(isOnline ? "en ligne" : "hors ligne");
                status.setStyle(isOnline
                        ? "-fx-text-fill: #25d366; -fx-font-size: 12px;"
                        : "-fx-text-fill: #667781; -fx-font-size: 12px;");

                Label dot = new Label("●");
                dot.setStyle(isOnline
                        ? "-fx-text-fill: #25d366; -fx-font-size: 12px;"
                        : "-fx-text-fill: #b0b7bc; -fx-font-size: 12px;");

                HBox statusRow = new HBox(5, dot, status);
                statusRow.setAlignment(Pos.CENTER_LEFT);

                VBox textBox = new VBox(3, name, statusRow);
                textBox.setAlignment(Pos.CENTER_LEFT);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Label badge = new Label(String.valueOf(unread));
                badge.setAlignment(Pos.CENTER);
                badge.setVisible(unread > 0);
                badge.setManaged(unread > 0);
                badge.setStyle(
                        "-fx-background-color: #25d366;" +
                                "-fx-text-fill: white;" +
                                "-fx-background-radius: 50;" +
                                "-fx-min-width: 22;" +
                                "-fx-min-height: 22;" +
                                "-fx-max-width: 22;" +
                                "-fx-max-height: 22;" +
                                "-fx-font-size: 11px;" +
                                "-fx-font-weight: bold;"
                );

                HBox row = new HBox(12, avatar, textBox, spacer, badge);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(10, 14, 10, 14));

                setText(null);
                setGraphic(row);

                setStyle(isSelected()
                        ? "-fx-background-color: #f0f2f5;"
                        : "-fx-background-color: white;");

                // ===== MENU CONTEXTUEL CLIC DROIT =====
                if (user != null) {
                    ContextMenu menu = new ContextMenu();

                    MenuItem deleteItem = new MenuItem("🗑️ Supprimer le contact");
                    deleteItem.setStyle("-fx-text-fill: #e74c3c;");
                    deleteItem.setOnAction(e -> {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Supprimer le contact");
                        confirm.setHeaderText("Supprimer " + user + " de vos contacts ?");
                        confirm.showAndWait().ifPresent(r -> {
                            if (r == ButtonType.OK) {
                                client.deleteContact(username, user);
                            }
                        });
                    });

                    MenuItem blockItem = new MenuItem("🚫 Bloquer ce contact");
                    blockItem.setStyle("-fx-text-fill: #e67e22;");
                    blockItem.setOnAction(e -> {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Bloquer le contact");
                        confirm.setHeaderText("Bloquer " + user + " ? Il ne pourra plus vous envoyer de messages.");
                        confirm.showAndWait().ifPresent(r -> {
                            if (r == ButtonType.OK) {
                                client.blockContact(username, user);
                            }
                        });
                    });

                    menu.getItems().addAll(deleteItem, new SeparatorMenuItem(), blockItem);
                    setContextMenu(menu);
                } else {
                    setContextMenu(null);
                }
            }
        });
    }

    private void displayConversation(String peer) {
        messagesBox.getChildren().clear();

        String key = conversationKey(username, peer);
        System.out.println("[DISPLAY] peer=" + peer + " key=" + key + " conversations=" + conversations.keySet());
        List<String[]> msgs = conversations.get(key);
        System.out.println("[DISPLAY] msgs trouvés = " + (msgs == null ? "NULL" : msgs.size()));

        if (msgs != null) {
            for (String[] m : msgs) {
                String sender = m[1];
                String content = m[2];
                boolean seen = false;
                if (m.length >= 4) {
                    seen = Boolean.parseBoolean(m[3]);
                }

                boolean isMe = sender.equals(username);
                addMessageBubble(sender, content, false, seen);
            }
        }
    }

    private void addMessageBubbleNow(String sender, String content, boolean seen) {
        addMessageBubble(sender, content, true, seen);
    }

    private void addMessageBubble(String sender, String content) {
        addMessageBubble(sender, content, true, false);
    }

    private void addMessageBubble(String sender, String content, boolean isNew, boolean seen) {
        Platform.runLater(() -> {
            MessageBubble bubble = new MessageBubble(sender, content, sender.equals(username), seen);
            messagesBox.getChildren().add(bubble);
            messagesBox.layout();
        });
    }

    public void handleSendMessage() {
        String content = messageField.getText().trim();
        if (content.isEmpty() || selectedUser == null) return;

        client.sendMessage(username, selectedUser, content);
        messageField.clear();

        // NE PAS afficher ici : le serveur renvoie le message en écho,
        // ce qui déclenchera l'affichage via handleIncoming (évite le doublon)
    }

    @FXML
    public void handleEmojiPicker() {
        // Popup principal
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);

        VBox container = new VBox(0);
        container.setStyle(
                "-fx-background-color: white;" +
                        "-fx-border-color: #ddd;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 12;" +
                        "-fx-background-radius: 12;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.18), 12, 0, 0, 4);" +
                        "-fx-padding: 8;"
        );
        container.setPrefWidth(340);

        // Barre de recherche
        TextField searchField = new TextField();
        searchField.setPromptText("🔍  Rechercher un emoji...");
        searchField.setStyle(
                "-fx-background-color: #f0f2f5;" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-width: 0;" +
                        "-fx-padding: 8 14;" +
                        "-fx-font-size: 13px;"
        );
        VBox.setMargin(searchField, new Insets(0, 0, 8, 0));

        // Catégories d'emojis
        java.util.LinkedHashMap<String, String[]> categories = new java.util.LinkedHashMap<>();
        categories.put("😀 Visages", new String[]{
                "😀","😁","😂","🤣","😃","😄","😅","😆","😇","😈","😉","😊",
                "😋","😌","😍","🥰","😎","😏","😐","😑","😒","😓","😔","😕",
                "😖","😗","😘","😙","😚","😛","😜","😝","😞","😟","😠","😡",
                "😢","😣","😤","😥","😦","😧","😨","😩","😪","😫","😬","😭",
                "😮","😯","😰","😱","😲","😳","😴","😵","😶","😷","🤒","🤔",
                "🤕","🤗","🤠","🤡","🤢","🤣","🤤","🤥","🤧","🤨","🤩","🤪"
        });
        categories.put("❤️ Cœurs", new String[]{
                "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕",
                "💞","💓","💗","💖","💘","💝","💟","♥️","💋","😘","🥰","😍"
        });
        categories.put("👋 Gestes", new String[]{
                "👋","🤚","🖐️","✋","🖖","👌","🤌","🤏","✌️","🤞","🤟","🤘",
                "🤙","👈","👉","👆","🖕","👇","☝️","👍","👎","✊","👊","🤛",
                "🤜","👏","🙌","👐","🤲","🤝","🙏","✍️","💪","🦾","🦿"
        });
        categories.put("🎉 Fête", new String[]{
                "🎉","🎊","🎈","🎁","🎀","🎗️","🎟️","🎫","🏆","🥇","🥈","🥉",
                "🎖️","🏅","🎪","🎭","🎨","🎬","🎤","🎧","🎵","🎶","🎸","🎹"
        });
        categories.put("🐶 Animaux", new String[]{
                "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮",
                "🐷","🐸","🐵","🐔","🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺"
        });
        categories.put("🍕 Nourriture", new String[]{
                "🍕","🍔","🍟","🌭","🍿","🧂","🥓","🥚","🍳","🥞","🧇","🥐",
                "🥖","🥨","🥯","🧀","🥗","🥙","🥪","🌮","🌯","🫔","🥫","🍱"
        });
        categories.put("⚽ Sport", new String[]{
                "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🏓","🏸",
                "🏒","🏑","🥍","🏏","🪃","🥅","⛳","🪁","🏹","🎣","🤿","🥊"
        });
        categories.put("🚗 Voyages", new String[]{
                "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🛻","🚚",
                "🚛","🚜","🏍️","🛵","🚲","🛴","🛺","🚁","✈️","🚀","🛸","⛵"
        });

        // Grille d'emojis (scrollable)
        VBox emojiContent = new VBox(6);

        Runnable fillEmojis = () -> {
            emojiContent.getChildren().clear();
            String filter = searchField.getText().toLowerCase().trim();

            for (java.util.Map.Entry<String, String[]> entry : categories.entrySet()) {
                List<String> matching = new ArrayList<>();
                for (String e : entry.getValue()) {
                    // Si recherche vide → tout afficher. Sinon filtrer par nom de catégorie ou emoji
                    if (filter.isEmpty() || entry.getKey().toLowerCase().contains(filter) || e.contains(filter)) {
                        matching.add(e);
                    }
                }
                if (matching.isEmpty()) continue;

                // Titre catégorie
                Label catLabel = new Label(entry.getKey());
                catLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #667781; -fx-font-weight: bold; -fx-padding: 4 0 2 2;");
                emojiContent.getChildren().add(catLabel);

                // Grille de boutons
                javafx.scene.layout.FlowPane grid = new javafx.scene.layout.FlowPane();
                grid.setHgap(2);
                grid.setVgap(2);
                grid.setPrefWrapLength(320);

                for (String emoji : matching) {
                    // Utiliser Label dans un StackPane pour forcer l'affichage de l'emoji
                    Label emojiLabel = new Label(emoji);
                    emojiLabel.setStyle("-fx-font-size: 22px; -fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji', sans-serif;");
                    emojiLabel.setAlignment(javafx.geometry.Pos.CENTER);

                    javafx.scene.layout.StackPane cell = new javafx.scene.layout.StackPane(emojiLabel);
                    cell.setMinSize(36, 36);
                    cell.setMaxSize(36, 36);
                    cell.setStyle("-fx-background-radius: 6; -fx-cursor: hand;");

                    cell.setOnMouseEntered(e -> cell.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 6; -fx-cursor: hand;"));
                    cell.setOnMouseExited(e -> cell.setStyle("-fx-background-radius: 6; -fx-cursor: hand;"));
                    cell.setOnMouseClicked(e -> {
                        int pos = messageField.getCaretPosition();
                        messageField.insertText(pos, emoji);
                        messageField.requestFocus();
                        popup.hide();
                    });
                    grid.getChildren().add(cell);
                }
                emojiContent.getChildren().add(grid);
            }
        };

        fillEmojis.run();
        searchField.textProperty().addListener((obs, o, n) -> fillEmojis.run());

        ScrollPane scroll = new ScrollPane(emojiContent);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(260);
        scroll.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-width: 0;");

        container.getChildren().addAll(searchField, scroll);
        popup.getContent().add(container);

        // Positionner le popup au-dessus du bouton emoji
        javafx.geometry.Bounds bounds = emojiBtn.localToScreen(emojiBtn.getBoundsInLocal());
        popup.show(emojiBtn.getScene().getWindow(),
                bounds.getMinX() - 10,
                bounds.getMinY() - 310);
    }

    public void handleSendFile() {
        if (selectedUser == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir un fichier");
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            client.sendFile(username, selectedUser, file);
        }
    }

    public void handleEndCall() {
        if (!callActive) return;

        if (currentCallId != -1 && selectedUser != null) {
            client.endCall(username, selectedUser);
        }

        callActive = false;
        currentCallType = "audio";
        stopAudioCall();
        stopVideoCall();

        if (endCallBtn != null) endCallBtn.setVisible(false);

        if (chatStatusLabel != null && selectedUser != null) {
            chatStatusLabel.setText(
                    onlineUsers.contains(selectedUser) ? "en ligne" : "hors ligne"
            );
        }

        setStatus("Appel terminé");
    }

    public void handleStartAudioCall() {
        if (selectedUser == null) {
            showAlert("Erreur", "Sélectionnez un utilisateur d'abord");
            return;
        }

        currentCallType = "audio";
        client.sendCallRequest(username, selectedUser, "audio");
        setStatus("Appel en cours vers " + selectedUser + "...");
    }

    public void handleStartVideoCall() {
        if (selectedUser == null) {
            showAlert("Erreur", "Sélectionnez un utilisateur d'abord");
            return;
        }

        currentCallType = "video";
        client.sendCallRequest(username, selectedUser, "video");
        setStatus("Appel vidéo en cours vers " + selectedUser + "...");
    }

    // Méthode pour ajouter un contact
    @FXML
    public void handleAddContact() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Ajouter un contact");
        dialog.setHeaderText("Entrez le nom d'utilisateur à ajouter :");
        dialog.setContentText("Nom d'utilisateur :");
        dialog.showAndWait().ifPresent(contactName -> {
            contactName = contactName.trim();
            if (!contactName.isEmpty() && !contactName.equals(username)) {
                client.addContact(username, contactName);
            } else if (contactName.equals(username)) {
                showAlert("Erreur", "Vous ne pouvez pas vous ajouter vous-même.");
            }
        });
    }

    // Méthodes pour les groupes (appelées depuis FXML)
    public void handleCreateGroup() {
        if (groupNameField != null) {
            String groupName = groupNameField.getText().trim();
            if (groupName.isEmpty()) {
                showAlert("Erreur", "Nom de groupe requis");
                return;
            }
            client.createGroup(username, groupName);
            groupNameField.clear();
        }
    }

    public void handleSendGroupMessage() {
        if (groupMessageField != null) {
            String content = groupMessageField.getText().trim();
            if (content.isEmpty() || currentGroupId == -1) return;

            client.sendGroupMessage(currentGroupId, username, content);
            groupMessageField.clear();
            // Ne pas afficher localement ici - le serveur renvoie le message à tous y compris l'expéditeur
        }
    }

    public void handleAddMember() {
        if (addMemberField != null) {
            String memberToAdd = addMemberField.getText().trim();
            if (memberToAdd.isEmpty() || currentGroupId == -1) return;

            client.addGroupMember(currentGroupId, username, memberToAdd);
            addMemberField.clear();
        }
    }

    public void handleRemoveMember(String memberToRemove) {
        if (currentGroupId == -1) return;
        client.removeGroupMember(currentGroupId, username, memberToRemove);
    }

    public void handleLeaveGroup() {
        if (currentGroupId == -1) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Quitter le groupe");
        alert.setHeaderText("Voulez-vous vraiment quitter ce groupe ?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                client.leaveGroup(currentGroupId, username);
            }
        });
    }

    public void handleDeleteGroup() {
        if (currentGroupId == -1) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Supprimer le groupe");
        alert.setHeaderText("Supprimer définitivement ce groupe ?");
        alert.setContentText("Cette action est irréversible !");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                client.deleteGroup(currentGroupId, username);
            }
        });
    }

    @FXML
    public void handleDeleteContact() {
        if (selectedUser == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Supprimer le contact");
        alert.setHeaderText("Supprimer « " + selectedUser + " » de vos contacts ?");
        alert.setContentText("Cette action est irréversible !");
        alert.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                client.deleteContact(username, selectedUser);
                contactsList.getItems().remove(selectedUser);
                contactsList.refresh();
                messagesBox.getChildren().clear();
                selectedUser = null;
                if (contactMenuBtn != null) contactMenuBtn.setVisible(false);
            }
        });
    }

    @FXML
    public void handleBlockContact() {
        if (selectedUser == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Bloquer le contact");
        alert.setHeaderText("Bloquer « " + selectedUser + " » ?");
        alert.setContentText("Vous ne recevrez plus ses messages.");
        alert.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                client.blockContact(username, selectedUser);
                blockedContacts.add(selectedUser);
                contactsList.refresh();
                if (blockContactMenu   != null) blockContactMenu.setVisible(false);
                if (unblockContactMenu != null) unblockContactMenu.setVisible(true);
            }
        });
    }

    @FXML
    public void handleUnblockContact() {
        if (selectedUser == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Débloquer le contact");
        alert.setHeaderText("Débloquer « " + selectedUser + " » ?");
        alert.setContentText("Vous recevrez à nouveau ses messages.");
        alert.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                client.unblockContact(username, selectedUser);
                blockedContacts.remove(selectedUser);
                contactsList.refresh();
                if (blockContactMenu   != null) blockContactMenu.setVisible(true);
                if (unblockContactMenu != null) unblockContactMenu.setVisible(false);
            }
        });
    }

    public void showGroupMembersDialog() {
        if (currentGroupId != -1) {
            client.getGroupMembers(currentGroupId);
        }
    }

    private String conversationKey(String user1, String user2) {
        List<String> list = Arrays.asList(user1, user2);
        Collections.sort(list);
        return list.get(0) + "_" + list.get(1);
    }

    private void setStatus(String status) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(status);
            }
        });
    }

    private void startAudioCallSession() {
        // Implémentation de l'appel audio
        System.out.println("Démarrage de l'appel audio...");
    }

    private void startVideoCallSession() {
        // Implémentation de l'appel vidéo
        System.out.println("Démarrage de l'appel vidéo...");
    }

    private void stopAudioCall() {
        // Arrêt de l'appel audio
        System.out.println("Arrêt de l'appel audio");
    }

    private void stopVideoCall() {
        // Arrêt de l'appel vidéo
        System.out.println("Arrêt de l'appel vidéo");
    }

    // Classe interne pour les bulles de messages privés
    private static class MessageBubble extends HBox {
        public MessageBubble(String sender, String content, boolean isMine, boolean isSeen) {
            VBox bubble = new VBox();
            bubble.setPadding(new Insets(5, 10, 5, 10));
            bubble.setMaxWidth(400);

            Label messageLabel = new Label(content);
            messageLabel.setWrapText(true);
            messageLabel.setStyle(
                    "-fx-font-size: 12px;" +
                            "-fx-text-fill: black;"
            );

            Label timeLabel = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: gray;");

            Label seenLabel = new Label(isSeen ? "✓✓" : "✓");
            seenLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #34b7f1;");

            HBox footer = new HBox(5, timeLabel, seenLabel);
            footer.setAlignment(Pos.BOTTOM_RIGHT);

            bubble.getChildren().addAll(messageLabel, footer);

            if (isMine) {
                bubble.setStyle("-fx-background-color: #dcf8c6; -fx-background-radius: 10;");
                this.setAlignment(Pos.CENTER_RIGHT);
            } else {
                bubble.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #ddd; -fx-border-radius: 10;");
                this.setAlignment(Pos.CENTER_LEFT);
            }

            this.getChildren().add(bubble);
            this.setPadding(new Insets(5));
        }
    }

    // Classe interne pour les bulles de messages de groupe
    private static class GroupHubble extends HBox {
        public GroupHubble(String sender, String content, boolean isMine) {
            VBox bubble = new VBox();
            bubble.setPadding(new Insets(5, 10, 5, 10));
            bubble.setMaxWidth(400);

            Label senderLabel = new Label(sender);
            senderLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray; -fx-font-weight: bold;");

            Label messageLabel = new Label(content);
            messageLabel.setWrapText(true);
            messageLabel.setStyle(
                    "-fx-font-size: 12px;" +
                            "-fx-text-fill: black;"
            );

            bubble.getChildren().addAll(senderLabel, messageLabel);

            if (isMine) {
                bubble.setStyle("-fx-background-color: #dcf8c6; -fx-background-radius: 10;");
                this.setAlignment(Pos.CENTER_RIGHT);
            } else {
                bubble.setStyle("-fx-background-color: #e8f0fe; -fx-background-radius: 10; -fx-border-color: #c5d5ea; -fx-border-radius: 10;");
                this.setAlignment(Pos.CENTER_LEFT);
            }

            this.getChildren().add(bubble);
            this.setPadding(new Insets(5));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HANDLERS GROUPE — ajoutés
    // ════════════════════════════════════════════════════════════════════════

    // ── Panneau créer groupe ─────────────────────────────────────────────────
    @FXML public void handleShowCreateGroup() {
        if (createGroupPanel != null) { createGroupPanel.setVisible(true); createGroupPanel.setManaged(true); }
    }
    @FXML public void handleHideCreateGroup() {
        if (createGroupPanel != null) { createGroupPanel.setVisible(false); createGroupPanel.setManaged(false); }
    }

    // ── Panneau ajouter membre ───────────────────────────────────────────────
    @FXML public void handleShowAddMember() {
        if (currentGroupId == -1) { showAlert("Erreur", "Sélectionnez un groupe d'abord."); return; }
        if (addMemberPanel != null) { addMemberPanel.setVisible(true); addMemberPanel.setManaged(true); }
    }
    @FXML public void handleHideAddMember() {
        if (addMemberPanel != null) { addMemberPanel.setVisible(false); addMemberPanel.setManaged(false); }
    }

    // ── Panneau supprimer membre ─────────────────────────────────────────────
    @FXML public void handleShowRemoveMember() {
        if (currentGroupId == -1) { showAlert("Erreur", "Sélectionnez un groupe d'abord."); return; }
        if (removeMemberPanel != null) { removeMemberPanel.setVisible(true); removeMemberPanel.setManaged(true); }
    }
    @FXML public void handleHideRemoveMember() {
        if (removeMemberPanel != null) { removeMemberPanel.setVisible(false); removeMemberPanel.setManaged(false); }
    }
    @FXML public void handleRemoveMemberFromPanel() {
        if (removeMemberField == null) return;
        String member = removeMemberField.getText().trim();
        if (member.isEmpty()) { showAlert("Erreur", "Entrez un nom d'utilisateur."); return; }
        if (currentGroupId == -1) { showAlert("Erreur", "Sélectionnez un groupe."); return; }
        client.removeGroupMember(currentGroupId, username, member);
        removeMemberField.clear();
        handleHideRemoveMember();
    }

    // ── Envoi fichier groupe ─────────────────────────────────────────────────
    @FXML public void handleSendGroupFile() {
        if (currentGroupId == -1) { showAlert("Erreur", "Sélectionnez un groupe d'abord."); return; }
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Choisir un fichier à envoyer au groupe");
        java.io.File file = fc.showOpenDialog(null);
        if (file == null) return;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            String encoded = java.util.Base64.getEncoder().encodeToString(bytes);
            client.sendRaw("GROUP_FILE:" + currentGroupId + ":" + username + ":" + file.getName() + ":" + encoded);
            addGroupMessageBubble(username, "📎 Fichier envoyé : " + file.getName(), true);
        } catch (Exception e) {
            showAlert("Erreur", "Impossible d'envoyer le fichier : " + e.getMessage());
        }
    }

    // ── Appel audio de groupe ────────────────────────────────────────────────
    @FXML public void handleStartGroupAudioCall() {
        if (currentGroupId == -1) return;
        if (inGroupAudioCall || inGroupVideoCall) {
            showAlert("Appel en cours", "Terminez l'appel en cours d'abord.");
            return;
        }
        client.sendRaw(network.Protocol.GROUP_CALL_START + ":audio:" + currentGroupId + ":" + username);
        inGroupAudioCall = true; inGroupCall = true;
        Platform.runLater(() -> {
            if (groupAudioCallBtn != null) groupAudioCallBtn.setVisible(false);
            if (groupVideoCallBtn != null) groupVideoCallBtn.setVisible(false);
            if (leaveGroupCallBtn != null) leaveGroupCallBtn.setVisible(true);
            if (joinGroupCallBtn  != null) joinGroupCallBtn.setVisible(false);
        });
        showAlert("📞 Appel audio de groupe", "Appel lancé ! Les membres seront notifiés.");
    }

    // ── Appel vidéo de groupe ────────────────────────────────────────────────
    @FXML public void handleStartGroupVideoCall() {
        if (currentGroupId == -1) return;
        if (inGroupAudioCall || inGroupVideoCall) {
            showAlert("Appel en cours", "Terminez l'appel en cours d'abord.");
            return;
        }
        client.sendRaw(network.Protocol.GROUP_CALL_START + ":video:" + currentGroupId + ":" + username);
        inGroupVideoCall = true; inGroupCall = true;
        Platform.runLater(() -> {
            if (groupAudioCallBtn != null) groupAudioCallBtn.setVisible(false);
            if (groupVideoCallBtn != null) groupVideoCallBtn.setVisible(false);
            if (leaveGroupCallBtn != null) leaveGroupCallBtn.setVisible(true);
            if (joinGroupCallBtn  != null) joinGroupCallBtn.setVisible(false);
        });
        showAlert("📹 Appel vidéo de groupe", "Appel vidéo lancé ! Les membres seront notifiés.");
    }

    // ── Rejoindre un appel de groupe ─────────────────────────────────────────
    @FXML public void handleJoinGroupCall() {
        if (currentGroupId == -1) return;
        client.sendRaw(network.Protocol.GROUP_CALL_JOIN + ":" + currentGroupId + ":" + username);
        inGroupCall = true;
        Platform.runLater(() -> {
            if (joinGroupCallBtn  != null) joinGroupCallBtn.setVisible(false);
            if (leaveGroupCallBtn != null) leaveGroupCallBtn.setVisible(true);
            if (groupAudioCallBtn != null) groupAudioCallBtn.setVisible(false);
            if (groupVideoCallBtn != null) groupVideoCallBtn.setVisible(false);
        });
    }

    // ── Quitter un appel de groupe ───────────────────────────────────────────
    @FXML public void handleLeaveGroupCall() {
        if (currentGroupId == -1) return;
        client.sendRaw(network.Protocol.GROUP_CALL_LEAVE + ":" + currentGroupId + ":" + username);
        inGroupCall = false; inGroupAudioCall = false; inGroupVideoCall = false;
        Platform.runLater(() -> {
            if (leaveGroupCallBtn != null) leaveGroupCallBtn.setVisible(false);
            if (groupAudioCallBtn != null) groupAudioCallBtn.setVisible(true);
            if (groupVideoCallBtn != null) groupVideoCallBtn.setVisible(true);
            if (joinGroupCallBtn  != null) joinGroupCallBtn.setVisible(false);
        });
    }

    // ── Emoji picker pour le groupe ──────────────────────────────────────────
    @FXML public void handleGroupEmojiPicker() {
        if (groupMessageField == null) return;
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);
        VBox container = new VBox(6);
        container.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 12;"
                + "-fx-background-radius: 12; -fx-effect: dropshadow(three-pass-box,rgba(0,0,0,0.18),12,0,0,4); -fx-padding: 8;");
        container.setPrefWidth(310);
        String[] emojis = {"😀","😂","😍","🥰","😎","🤔","😭","😡","👍","👎",
                "❤️","🎉","🔥","✅","🚀","💬","👏","🙏","😊","🤣","😅","😇","🥳","😜","🤗"};
        javafx.scene.layout.FlowPane grid = new javafx.scene.layout.FlowPane();
        grid.setHgap(4); grid.setVgap(4); grid.setPrefWrapLength(295);
        for (String emoji : emojis) {
            Label lbl = new Label(emoji);
            lbl.setStyle("-fx-font-size: 22px; -fx-font-family: 'Segoe UI Emoji','Apple Color Emoji','Noto Color Emoji',sans-serif;");
            javafx.scene.layout.StackPane cell = new javafx.scene.layout.StackPane(lbl);
            cell.setMinSize(36,36); cell.setMaxSize(36,36);
            cell.setStyle("-fx-background-radius: 6; -fx-cursor: hand;");
            cell.setOnMouseEntered(e -> cell.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 6; -fx-cursor: hand;"));
            cell.setOnMouseExited(e -> cell.setStyle("-fx-background-radius: 6; -fx-cursor: hand;"));
            cell.setOnMouseClicked(e -> {
                int pos = groupMessageField.getCaretPosition();
                groupMessageField.insertText(pos, emoji);
                groupMessageField.requestFocus();
                popup.hide();
            });
            grid.getChildren().add(cell);
        }
        container.getChildren().add(grid);
        popup.getContent().add(container);
        javafx.geometry.Bounds b = groupMessageField.localToScreen(groupMessageField.getBoundsInLocal());
        popup.show(groupMessageField.getScene().getWindow(), b.getMinX(), b.getMinY() - 170);
    }
}