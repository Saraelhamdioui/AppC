package controller;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.*;
import java.net.Socket;

/**
 * Capture la webcam et envoie les frames JPEG vers le serveur.
 * CORRECTION : utilise DataOutputStream.writeInt() pour la taille (4 octets),
 * cohérent avec VideoReceiver qui lit aussi 4 octets.
 */
public class VideoSender implements Runnable {

    private Socket socket;
    private volatile boolean running = true;

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public VideoSender(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        VideoCapture camera = new VideoCapture(0);

        if (!camera.isOpened()) {
            System.out.println("❌ Caméra non disponible");
            return;
        }

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(socket.getOutputStream()))) {

            Mat frame = new Mat();

            while (running && camera.isOpened()) {
                camera.read(frame);
                if (frame.empty()) continue;

                byte[] image = matToBytes(frame);

                // ✅ 4 octets pour la taille (writeInt), cohérent avec VideoReceiver
                out.writeInt(image.length);
                out.write(image);
                out.flush();

                Thread.sleep(33); // ~30 FPS
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            camera.release();
        }
    }

    private byte[] matToBytes(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".jpg", frame, buffer);
        return buffer.toArray();
    }

    public void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }
}
