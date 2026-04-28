package Controller;

import Controller.UIController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.TextInputDialog;

public class ClientApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("chat.fxml"));
        Scene scene = new Scene(loader.load());

        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Enter username:");
        String username = dialog.showAndWait().orElse("User");

        UIController controller = loader.getController();
        controller.setUsername(username);

        stage.setScene(scene);
        stage.setTitle("Chat App");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}