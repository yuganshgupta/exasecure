package com.examsystem.client.proctor;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.function.BooleanSupplier;

/** Runs CoreGraphics permission checks in the process that captures the screen. */
final class MacScreenCapturePermission {
    private final BooleanSupplier preflight;
    private final BooleanSupplier request;
    private boolean requested;

    MacScreenCapturePermission(BooleanSupplier preflight, BooleanSupplier request) {
        this.preflight = preflight;
        this.request = request;
    }

    static MacScreenCapturePermission coreGraphics() {
        var library = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global());
        return new MacScreenCapturePermission(
                function(library, "CGPreflightScreenCaptureAccess"),
                function(library, "CGRequestScreenCaptureAccess"));
    }

    boolean isGranted() {
        return preflight.getAsBoolean();
    }

    synchronized boolean canRequest() {
        return !requested;
    }

    synchronized void requestOnce() {
        if (!requested && !isGranted()) {
            requested = true;
            request.getAsBoolean();
        }
    }

    private static BooleanSupplier function(SymbolLookup library, String name) {
        MethodHandle function = Linker.nativeLinker().downcallHandle(
                library.findOrThrow(name), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN));
        return () -> {
            try {
                return (boolean) function.invokeExact();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException("Unable to call " + name, e);
            }
        };
    }
}
