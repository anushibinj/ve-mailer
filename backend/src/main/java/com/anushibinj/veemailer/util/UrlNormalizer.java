package com.anushibinj.veemailer.util;

/**
 * Shared normalization helpers for values used in workspace duplicate detection and
 * ValueEdge REST calls (root URL, in particular) so all callers treat equivalent inputs
 * identically (e.g. "https://ve.example.com/" and "https://ve.example.com").
 */
public final class UrlNormalizer {

    private UrlNormalizer() {
    }

    /**
     * Trims the URL and strips any trailing slash(es) so that e.g. "https://ve.example.com/"
     * and "https://ve.example.com" are treated as the same Root URL.
     */
    public static String normalizeRootUrl(String rootUrl) {
        String trimmed = rootUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
