package com.examsystem.server.http.exceptions;

import com.examsystem.contracts.ErrorResponseDto;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static void register(io.javalin.config.JavalinConfig config) {
        config.routes.exception(SecurityException.class, (e, ctx) -> {
            ctx.status(403).json(new ErrorResponseDto(403, e.getMessage(), UUID.randomUUID().toString()));
        });
        config.routes.exception(java.util.NoSuchElementException.class, (e, ctx) -> {
            ctx.status(404).json(new ErrorResponseDto(404, e.getMessage(), UUID.randomUUID().toString()));
        });
        config.routes.exception(io.javalin.http.HttpResponseException.class, (e, ctx) -> {
            ctx.status(e.getStatus());
            ctx.json(new ErrorResponseDto(e.getStatus(), e.getMessage(), UUID.randomUUID().toString()));
        });

        config.routes.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new ErrorResponseDto(400, e.getMessage(), UUID.randomUUID().toString()));
        });

        config.routes.exception(IllegalStateException.class, (e, ctx) -> {
            ctx.status(HttpStatus.CONFLICT);
            ctx.json(new ErrorResponseDto(409, e.getMessage(), UUID.randomUUID().toString()));
        });

        config.routes.exception(Exception.class, (e, ctx) -> {
            String requestId = UUID.randomUUID().toString();
            log.error("Unhandled error [{}]: ", requestId, e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new ErrorResponseDto(500, "Internal Server Error", requestId));
        });
    }
}
