package Controller;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

public class TestCamera {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {

        System.out.println("OpenCV version : " + Core.VERSION);

        VideoCapture cam = new VideoCapture(0);

        if (!cam.isOpened()) {
            System.out.println("❌ Caméra introuvable ou déjà utilisée.");
            System.out.println("   → Vérifie qu'aucune autre app n'utilise la caméra.");
            System.out.println("   → Essaie VideoCapture(1) si tu as plusieurs caméras.");
            return;
        }

        System.out.println("✅ Caméra détectée !");

        Mat frame = new Mat();
        cam.read(frame);

        if (frame.empty()) {
            System.out.println("❌ Caméra ouverte mais frame vide — driver ou permissions ?");
        } else {
            System.out.println("✅ Frame capturée : " + frame.width() + "x" + frame.height());
            System.out.println("✅ La caméra fonctionne correctement !");
        }

        cam.release();
    }
}