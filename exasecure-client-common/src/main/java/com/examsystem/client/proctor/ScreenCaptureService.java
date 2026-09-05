package com.examsystem.client.proctor;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

public class ScreenCaptureService implements ScreenCapturer {

    @Override
    public byte[] capture() {
        return captureScreenAsJpeg();
    }

    public static byte[] captureScreenAsJpeg() {
        try {
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                return null;
            }
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle screenRect = new Rectangle(screenSize);
            Robot robot = new Robot();
            BufferedImage image = robot.createScreenCapture(screenRect);
            if (image == null) {
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
}
