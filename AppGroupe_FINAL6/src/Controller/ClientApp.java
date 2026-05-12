package Controller;

import DAO.UserDao;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import model.User;

import java.util.Optional;

public class ClientApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        String serverIp = askServerIp();
        String username = authenticate();

        if (username == null) {
            System.exit(0);
            return;
        }

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/ressources/chat.fxml")
        );

        Parent root = loader.load();

        UIController controller = loader.getController();
        controller.init(serverIp, username);

        // fenêtre mieux dimensionnée
        Scene scene = new Scene(root, 1200, 700);

        stage.setScene(scene);
        stage.setTitle("ChatApp — " + username);

        stage.setMinWidth(650);
        stage.setMinHeight(550);

        stage.setWidth(760);
        stage.setHeight(620);
        stage.setMaximized(false);
        stage.centerOnScreen();
        stage.show();
    }

    private String askServerIp() {
        TextInputDialog ipDialog = new TextInputDialog("127.0.0.1");
        ipDialog.setTitle("Connexion serveur");
        ipDialog.setHeaderText("Adresse IP du serveur");
        ipDialog.setContentText("IP :");

        return ipDialog.showAndWait()
                .orElse("127.0.0.1")
                .trim();
    }

    private String authenticate() {
        UserDao userDao = new UserDao();

        while (true) {

            ChoiceDialog<String> choiceDialog = new ChoiceDialog<>(
                    "Login",
                    "Login",
                    "Register"
            );

            choiceDialog.setTitle("Authentification");
            choiceDialog.setHeaderText("Choisissez une action");
            choiceDialog.setContentText("Action :");

            Optional<String> choice = choiceDialog.showAndWait();

            if (choice.isEmpty()) {
                return null;
            }

            // LOGIN
            if (choice.get().equals("Login")) {

                String username = askText(
                        "Connexion",
                        "Nom d'utilisateur",
                        "Username :"
                );

                if (username == null || username.isBlank()) {
                    continue;
                }

                String password = askPassword(
                        "Connexion",
                        "Mot de passe",
                        "Password :"
                );

                if (password == null || password.isBlank()) {
                    continue;
                }

                boolean success = userDao.login(
                        username.trim(),
                        password.trim()
                );

                if (success) {
                    showInfo(
                            "Connexion réussie",
                            "Bienvenue " + username
                    );
                    return username.trim();
                } else {
                    showError(
                            "Erreur",
                            "Username ou mot de passe incorrect."
                    );
                }
            }

            // REGISTER
            if (choice.get().equals("Register")) {

                String username = askText(
                        "Créer un compte",
                        "Nouveau compte",
                        "Username :"
                );

                if (username == null || username.isBlank()) {
                    continue;
                }

                if (userDao.userExists(username.trim())) {
                    showError(
                            "Erreur",
                            "Ce username existe déjà."
                    );
                    continue;
                }

                String password = askPassword(
                        "Créer un compte",
                        "Choisissez un mot de passe",
                        "Password :"
                );

                if (password == null || password.isBlank()) {
                    continue;
                }

                boolean created = userDao.register(
                        new User(
                                username.trim(),
                                password.trim()
                        )
                );

                if (created) {
                    showInfo(
                            "Compte créé",
                            "Compte créé avec succès. Connectez-vous."
                    );
                } else {
                    showError(
                            "Erreur",
                            "Impossible de créer le compte."
                    );
                }
            }
        }
    }

    private String askText(
            String title,
            String header,
            String content
    ) {
        TextInputDialog dialog = new TextInputDialog();

        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);

        return dialog.showAndWait().orElse(null);
    }

    private String askPassword(
            String title,
            String header,
            String content
    ) {
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Mot de passe");

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        ButtonType okButton = new ButtonType(
                "OK",
                ButtonBar.ButtonData.OK_DONE
        );

        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(okButton, ButtonType.CANCEL);

        dialog.getDialogPane().setContent(passwordField);

        dialog.setResultConverter(button -> {
            if (button == okButton) {
                return passwordField.getText();
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    private void showInfo(
            String title,
            String message
    ) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    private void showError(
            String title,
            String message
    ) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}