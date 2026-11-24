package com.examsystem.server.http.controllers;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.UploadedFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class EvidenceUploadImage {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 20_000_000;

    private EvidenceUploadImage() {}

    static byte[] read(UploadedFile file) {
        String format = switch (file.contentType() == null ? "" : file.contentType()) {
            case "image/jpeg" -> "JPEG";
            case "image/png" -> "PNG";
            default -> throw new BadRequestResponse("Screenshot must be JPEG or PNG");
        };
        if (file.size() > MAX_BYTES) throw new BadRequestResponse("Screenshot too large (max 5 MiB)");
        try (var content = file.content()) {
            byte[] bytes = content.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new BadRequestResponse("Screenshot too large (max 5 MiB)");
            validate(bytes, format);
            return bytes;
        } catch (IOException e) {
            throw new BadRequestResponse("Screenshot could not be decoded");
        }
    }

    private static void validate(byte[] bytes, String declaredFormat) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestResponse("Screenshot format is invalid");
            var reader = readers.next();
            try {
                if (!declaredFormat.equalsIgnoreCase(reader.getFormatName())) {
                    throw new BadRequestResponse("Screenshot format does not match its content type");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!allowedDimensions(width, height)) {
                    throw new BadRequestResponse("Screenshot dimensions exceed the allowed limit");
                }
                // Validate the compressed data with a subsampled output to limit decoded storage.
                var parameters = reader.getDefaultReadParam();
                parameters.setSourceSubsampling(4, 4, 0, 0);
                boolean[] warned = {false};
                reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
                var decoded = reader.read(0, parameters);
                if (decoded == null || warned[0]) throw new BadRequestResponse("Screenshot could not be decoded");
                decoded.flush();
            } finally {
                reader.dispose();
            }
        }
    }

    static boolean allowedDimensions(int width, int height) {
        return width > 0 && width <= MAX_DIMENSION && height > 0 && height <= MAX_DIMENSION
                && (long) width * height <= MAX_PIXELS;
    }
}
