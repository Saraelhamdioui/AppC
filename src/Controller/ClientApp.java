package controller;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

/**
 * Point d'entrée JavaFX.
 * CORRECTION : demande d'abord l'IP du serveur, puis le username.
 * Cela permet à deux PCs différents de se connecter au même serveur.
 */
public class ClientApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // 1️⃣ Demander l'IP du serveur
        TextInputDialog ipDialog = new TextInputDialog("192.168.1.x");
        ipDialog.setTitle("Connexion");
        ipDialog.setHeaderText("Entrez l'adresse IP du serveur :");
        ipDialog.setContentText("IP :");
        String serverIp = ipDialog.showAndWait().orElse("127.0.0.1").trim();

        // 2️⃣ Demander le pseudo
        TextInputDialog nameDialog = new TextInputDialog("Alice");
        nameDialog.setTitle("Connexion");
        nameDialog.setHeaderText("Entrez votre pseudo :");
        nameDialog.setContentText("Pseudo :");
        String username = nameDialog.showAndWait().orElse("User").trim();

        // 3️⃣ Charger le FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ressources/chat.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root);

        // 4️⃣ Initialiser le controller avec l'IP et le pseudo
        UIController controller = loader.getController();
        controller.init(serverIp, username);

        stage.setScene(scene);
        stage.setTitle("ChatApp — " + username);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
