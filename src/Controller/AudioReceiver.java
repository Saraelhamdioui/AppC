package Controller;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.net.Socket;

public class AudioReceiver implements Runnable {

    private SourceDataLine speakers;
    private Socket socket;
    private boolean running = true;

    public AudioReceiver(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {

        try {
            AudioFormat format = getFormat();

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(format);
            speakers.start();

            InputStream in = socket.getInputStream();

            byte[] buffer = new byte[4096];

            while (running && !socket.isClosed()) {

                int bytesRead = in.read(buffer);

                if (bytesRead == -1) break;

                if (bytesRead > 0) {
                    speakers.write(buffer, 0, bytesRead);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {

        running = false;

        try {
            if (speakers != null) {
                speakers.stop();
                speakers.close();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close(); // فقط close مرة وحدة
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private AudioFormat getFormat() {

        return new AudioFormat(
                16000.0f,
                16,
                1,
                true,
                false
        );
    }
}