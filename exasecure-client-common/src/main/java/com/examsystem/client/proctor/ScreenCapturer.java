package com.examsystem.client.proctor;

@FunctionalInterface
public interface ScreenCapturer {
    byte[] capture();
}
