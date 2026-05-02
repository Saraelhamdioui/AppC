package controller;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.*;
import java.net.Socket;

/**
 * Reçoit les frames JPEG et les affiche dans l'ImageView.
 * CORRECTION CRITIQUE : remplace la lecture 2 octets par DataInputStream.readInt()
 * (4 octets) pour être cohérent avec VideoSender.writeInt().
 * L'ancienne version lisait : (in.read() << 8) | in.read()  → BUG : désynchronisation
 */
public class VideoReceiver implements Runnable {

    private Socket socket;
    private ImageView view;
    private volatile boolean running = true;

    public VideoReceiver(Socket socket, ImageView view) {
        this.socket = socket;
        this.view   = view;
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()))) {

            while (running && !socket.isClosed()) {
                // ✅ Lecture 4 octets — cohérent avec VideoSender.writeInt()
                int size = in.readInt();
                if (size <= 0 || size > 5_000_000) continue; // protection anti-données corrompues

                byte[] data = new byte[size];
                in.readFully(data); // readFully garantit la lecture complète

                Image img = new Image(new ByteArrayInputStream(data));
                Platform.runLater(() -> view.setImage(img));
            }
        } catch (Exception e) {
            if (running) System.out.println("VideoReceiver arrêté: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }
}
