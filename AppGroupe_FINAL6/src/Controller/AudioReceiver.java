package Controller;

import javax.sound.sampled.*;
import java.io.*;
import java.net.Socket;

/**
 * Reçoit le flux audio depuis le serveur et le joue sur les haut-parleurs.
 * CORRECTION : socket séparé de AudioSender — chacun gère le sien.
 */
public class AudioReceiver implements Runnable {

    private SourceDataLine speakers;
    private Socket socket;
    private volatile boolean running = true;

    public AudioReceiver(Socket socket) {
        this.socket = socket;
    }


    public void run() {
        try {
            AudioFormat format = getFormat();
            DataLine.Info info  = new DataLine.Info(SourceDataLine.class, format);

            speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(format);
            speakers.start();

            InputStream in = socket.getInputStream();
            byte[] buffer  = new byte[4096];

            while (running && !socket.isClosed()) {
                int bytesRead = in.read(buffer);
                if (bytesRead == -1) break;
                speakers.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            closeSpeakers();
        }
    }

    public void stop() {
        running = false;
        closeSpeakers();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }

    private void closeSpeakers() {
        if (speakers != null && speakers.isOpen()) {
            speakers.stop();
            speakers.close();
        }
    }

    private AudioFormat getFormat() {
        return new AudioFormat(16000.0f, 16, 1, true, false);
    }
}