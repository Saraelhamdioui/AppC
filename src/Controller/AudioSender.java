package controller;

import javax.sound.sampled.*;
import java.io.*;
import java.net.Socket;

/**
 * Envoie le flux microphone vers le serveur.
 * CORRECTION : utilise son propre socket indépendant (audioSocket),
 * ne partage plus le socket principal du chat.
 */
public class AudioSender {

    private TargetDataLine mic;
    private Socket socket;
    private volatile boolean running = true;

    public AudioSender(Socket socket) {
        this.socket = socket;
    }

    public void start() {
        try {
            AudioFormat format = getFormat();
            DataLine.Info info  = new DataLine.Info(TargetDataLine.class, format);

            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(format);
            mic.start();

            OutputStream out = socket.getOutputStream();
            byte[] buffer = new byte[4096];

            while (running && !socket.isClosed()) {
                int count = mic.read(buffer, 0, buffer.length);
                if (count > 0) {
                    out.write(buffer, 0, count);
                    out.flush();
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            closeMic();
        }
    }

    public void stop() {
        running = false;
        closeMic();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }

    private void closeMic() {
        if (mic != null && mic.isOpen()) {
            mic.stop();
            mic.close();
        }
    }

    private AudioFormat getFormat() {
        return new AudioFormat(16000.0f, 16, 1, true, false);
    }
}
