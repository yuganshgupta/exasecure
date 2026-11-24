package com.examsystem.client.proctor;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

public class ScreenCaptureService implements ScreenCapturer {
    public enum Readiness { READY, CAN_REQUEST, NEEDS_SETTINGS, UNAVAILABLE }

    private static final boolean MAC = System.getProperty("os.name", "").startsWith("Mac");
    private static final Object MAC_CAPTURE_LOCK = new Object();
    private static Robot macRobot;
    private static boolean macCapturePrepared;

    private static class MacPermission {
        private static final MacScreenCapturePermission INSTANCE = MacScreenCapturePermission.coreGraphics();
    }

    private static class MacCaptureBackend {
        private static final boolean NATIVE = MacDesktopCapture.load();
    }

    public static Readiness checkReadiness() {
        if (!MAC) return Readiness.READY;
        if (GraphicsEnvironment.isHeadless()) return Readiness.UNAVAILABLE;
        try {
            var permission = MacPermission.INSTANCE;
            if (permission.isGranted()) {
                synchronized (MAC_CAPTURE_LOCK) {
                    // Exercise the same backend once before any attempt can start. Discard the image locally.
                    if (!macCapturePrepared) macCapturePrepared = captureScreenAsJpeg() != null;
                    return macCapturePrepared ? Readiness.READY : Readiness.UNAVAILABLE;
                }
            }
            synchronized (MAC_CAPTURE_LOCK) {
                macCapturePrepared = false;
            }
            return permission.canRequest() ? Readiness.CAN_REQUEST : Readiness.NEEDS_SETTINGS;
        } catch (Exception | LinkageError e) {
            return Readiness.UNAVAILABLE;
        }
    }

    public static Readiness requestMacPermission() {
        if (!MAC) return Readiness.READY;
        try {
            MacPermission.INSTANCE.requestOnce();
            return checkReadiness();
        } catch (Exception | LinkageError e) {
            return Readiness.UNAVAILABLE;
        }
    }

    private static synchronized Robot macRobot() throws java.awt.AWTException {
        if (macRobot == null) macRobot = new Robot();
        return macRobot;
    }

    @Override
    public byte[] capture() {
        return captureScreenAsJpeg();
    }

    public static byte[] captureScreenAsJpeg() {
        if (MAC) {
            // Serialize capture across attempt coordinators that may still be shutting down.
            synchronized (MAC_CAPTURE_LOCK) {
                try {
                    if (GraphicsEnvironment.isHeadless() || !MacPermission.INSTANCE.isGranted()) return null;
                    if (MacCaptureBackend.NATIVE) {
                        byte[] bytes = MacDesktopCapture.captureJpeg();
                        if (bytes == null) return null;
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                        return image == null || isBlack(image) ? null : bytes;
                    }
                } catch (Exception | LinkageError e) {
                    return null;
                }
                return captureDesktop();
            }
        }
        return captureDesktop();
    }

    private static byte[] captureDesktop() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return null;
            }
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle screenRect = new Rectangle(screenSize);
            Robot robot = MAC ? macRobot() : new Robot();
            BufferedImage image = robot.createScreenCapture(screenRect);
            if (image == null || (MAC && isBlack(image))) {
                return null;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (ImageIO.write(image, "jpeg", baos)) {
                byte[] bytes = baos.toByteArray();
                if (bytes.length > 0) {
                    return bytes;
                }
            }
        } catch (Throwable ignored) {
            // In headless/container/permission-restricted/Robot-failure environments, capture returns null.
            // Never fabricate fake screenshot evidence.
        }
        return null;
    }

    static boolean isBlack(BufferedImage image) {
        int width = image.getWidth();
        int[] row = new int[width];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int pixel : row) {
                if ((pixel & 0x00ffffff) != 0) return false;
            }
        }
        return true;
    }
}
