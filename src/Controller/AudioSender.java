package Controller;

import javax.sound.sampled.*;
import java.io.OutputStream;
import java.net.Socket;

public class AudioSender {

    private TargetDataLine mic;
    private Socket socket;
    private OutputStream out;
    private boolean running = true;

    public AudioSender(Socket socket) {
        this.socket = socket;
    }

    public void start() {

        try {
            AudioFormat format = getFormat();

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(format);
            mic.start();

            out = socket.getOutputStream();

            byte[] buffer = new byte[4096];

            // 👇 HERE (position)
            while (running && !socket.isClosed()) {

                int count = mic.read(buffer, 0, buffer.length);

                if (count > 0) {
                    out.write(buffer, 0, count);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {

        running = false;

        try {
            if (mic != null) {
                mic.stop();
                mic.close();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close(); // فقط close
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private AudioFormat getFormat() {

        return new AudioFormat(
                16000.0f,  // sample rate
                16,        // bits
                1,         // mono
                true,      // signed
                false      // little endian
        );
    }
}