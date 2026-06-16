package com.dolos.common;

import java.time.Instant;

/**
 * Standard error envelope returned by every service's REST layer, so clients see a consistent
 * shape regardless of which service failed. Framework-agnostic (plain record).
 *
 * @param timestamp when the error was produced
 * @param status    HTTP status code
 * @param error     short reason phrase (e.g. "Not Found")
 * @param message   human-readable detail
 * @param path      request path that produced the error
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path);
    }
}
