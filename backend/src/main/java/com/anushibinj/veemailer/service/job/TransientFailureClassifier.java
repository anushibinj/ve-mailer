package com.anushibinj.veemailer.service.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Decides whether a failure from a job execution should be retried.
 *
 * <p>Octane SDK exception types are not on this class's compile classpath by design, so an
 * unrecognised SDK-thrown {@link RuntimeException} is matched only by its message text
 * (status-code-like numbers, "connection reset", "Service Unavailable", "Gateway"). This keeps
 * the classifier testable and decoupled from SDK internals.
 */
@Component
public class TransientFailureClassifier {

    private static final Pattern TRANSIENT_STATUS_PATTERN = Pattern.compile("\\b(408|429|5\\d{2})\\b");
    private static final Pattern PERMANENT_STATUS_PATTERN = Pattern.compile("\\b4\\d{2}\\b");
    private static final Pattern TRANSIENT_KEYWORD_PATTERN = Pattern.compile(
            "connection reset|service unavailable|gateway", Pattern.CASE_INSENSITIVE);

    /**
     * Walks the full cause chain. A permanent signal anywhere in the chain wins over a transient
     * one — an explicit validation failure (e.g. bad filter criteria) must never retry even if it
     * happens to be wrapped around something that also looks transient. With no signal anywhere in
     * the chain the failure defaults to transient: an outage surfacing as an unrecognised SDK
     * {@link RuntimeException} must still retry, which is the whole point of this feature. The
     * 4-attempt cap bounds the cost of guessing wrong.
     */
    public boolean isTransient(Throwable throwable) {
        Set<Throwable> seen = new HashSet<>();
        Throwable current = throwable;
        while (current != null && seen.add(current)) {
            Boolean verdict = classifyOne(current);
            if (verdict != null && !verdict) {
                return false;
            }
            current = current.getCause();
        }
        return true;
    }

    /** Returns TRUE for a transient signal, FALSE for a permanent one, or null if this throwable carries no signal. */
    private Boolean classifyOne(Throwable t) {
        if (t instanceof HttpStatusCodeException hsce) {
            int code = hsce.getStatusCode().value();
            if (code == 408 || code == 429 || code >= 500) {
                return true;
            }
            return false;
        }
        if (t instanceof IllegalArgumentException || t instanceof JsonProcessingException) {
            return false;
        }
        if (t instanceof SocketTimeoutException || t instanceof ConnectException
                || t instanceof UnknownHostException || t instanceof NoRouteToHostException
                || t instanceof SSLException || t instanceof TimeoutException) {
            return true;
        }

        String message = t.getMessage();
        if (message != null) {
            if (TRANSIENT_KEYWORD_PATTERN.matcher(message).find()
                    || TRANSIENT_STATUS_PATTERN.matcher(message).find()) {
                return true;
            }
            if (PERMANENT_STATUS_PATTERN.matcher(message).find()) {
                return false;
            }
        }
        return null;
    }
}
