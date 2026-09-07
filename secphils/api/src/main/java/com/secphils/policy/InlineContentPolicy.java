package com.secphils.policy;

import org.springframework.http.MediaType;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Inline-rendering allowlist for user-uploaded files served same-origin with
 * the portal (document previews, message-attachment previews).
 *
 * <p>Only a fixed set of non-executable types may ever be returned with
 * {@code Content-Disposition: inline} and a real MIME type. Everything else —
 * notably SVG (which can carry scripts) and any HTML/JS/XML — must be
 * downgraded to {@code attachment} + {@code application/octet-stream} +
 * {@code X-Content-Type-Options: nosniff}, or an uploaded file could execute
 * in the portal's own origin (stored XSS).
 *
 * <p>Dispatch is by <b>file name extension only</b>. The uploader-supplied
 * content type (stored on message attachments) is attacker-controlled and is
 * NEVER consulted here.
 */
public final class InlineContentPolicy {

    private InlineContentPolicy() {}

    private static final Map<String, MediaType> INLINE_TYPES = Map.of(
            "pdf", MediaType.APPLICATION_PDF,
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "gif", MediaType.IMAGE_GIF,
            "webp", MediaType.parseMediaType("image/webp"),
            "bmp", MediaType.parseMediaType("image/bmp"),
            "tiff", MediaType.parseMediaType("image/tiff"),
            "txt", MediaType.TEXT_PLAIN);

    /** Real MIME to inline for this file name, or empty → serve as attachment. */
    public static Optional<MediaType> inlineType(String fileName) {
        if (fileName == null) return Optional.empty();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return Optional.empty();
        return Optional.ofNullable(INLINE_TYPES.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT)));
    }
}
