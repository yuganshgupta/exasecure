package com.examsystem.client.proctor;

final class MacDesktopCapture {
    private MacDesktopCapture() {}

    static boolean load() {
        String library = System.getProperty("exasecure.macos.capture.library");
        if (library == null) return false;
        System.load(library);
        return isSupported();
    }

    private static native boolean isSupported();

    static native byte[] captureJpeg();
}
