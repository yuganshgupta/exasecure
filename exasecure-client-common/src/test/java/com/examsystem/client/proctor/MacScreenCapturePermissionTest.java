package com.examsystem.client.proctor;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MacScreenCapturePermissionTest {
    @Test
    void repeatedPreflightAndRevocationNeverRequestPermission() {
        var granted = new AtomicBoolean(true);
        var requests = new AtomicInteger();
        var permission = new MacScreenCapturePermission(granted::get, () -> requests.incrementAndGet() > 0);

        assertTrue(permission.isGranted());
        permission.requestOnce();
        assertTrue(permission.canRequest());
        assertTrue(permission.isGranted());
        granted.set(false);
        assertFalse(permission.isGranted());
        assertEquals(0, requests.get());
    }

    @Test
    void deniedRequestIsNotRepeatedAndLaterGrantIsDetected() {
        var granted = new AtomicBoolean();
        var requests = new AtomicInteger();
        var permission = new MacScreenCapturePermission(granted::get, () -> {
            requests.incrementAndGet();
            return false;
        });

        permission.requestOnce();
        assertFalse(permission.canRequest());
        assertFalse(permission.isGranted());
        permission.requestOnce();
        assertEquals(1, requests.get());
        granted.set(true);
        assertTrue(permission.isGranted());
        granted.set(false);
        assertFalse(permission.isGranted());
        assertEquals(1, requests.get());
    }

    @Test
    void rejectsBlackFrameAndAcceptsFrameContainingDesktopPixels() {
        var image = new BufferedImage(17, 19, BufferedImage.TYPE_INT_RGB);
        assertTrue(ScreenCaptureService.isBlack(image));
        image.setRGB(16, 18, 0x223344);
        assertFalse(ScreenCaptureService.isBlack(image));
    }
}
