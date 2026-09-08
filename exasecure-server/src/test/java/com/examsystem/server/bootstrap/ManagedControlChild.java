package com.examsystem.server.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Exercises production control-loop code in a real disposable Java child, without a DB. */
public final class ManagedControlChild {
    public static void main(String[] args) throws Exception {
        Path marker = Path.of(args[0]);
        String mode = args[1];
        ManagedServerControl.run(new ManagedServerControl.Lifecycle() {
            public void start() {
                try {
                    Files.writeString(marker, "start-invoked");
                    if (mode.equals("fail")) throw new IllegalStateException("fixture secret must not be echoed");
                    if (mode.equals("delayed")) {
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                        while (!Files.exists(Path.of(marker + ".release")) && System.nanoTime() < deadline) TimeUnit.MILLISECONDS.sleep(10);
                    }
                } catch (java.io.IOException | InterruptedException e) { throw new RuntimeException(e); }
            }
            public void stop() {
                try { Files.writeString(Path.of(marker + ".stopped"), "normal-stop-invoked"); }
                catch (java.io.IOException e) { throw new RuntimeException(e); }
            }
        }, 19123, System.in, System.out);
    }
}
