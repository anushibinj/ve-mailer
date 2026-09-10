package com.anushibinj.veemailer.service.job;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class TransientFailureClassifierTest {

    private final TransientFailureClassifier classifier = new TransientFailureClassifier();

    @Test
    void illegalArgumentException_isPermanent() {
        assertThat(classifier.isTransient(new IllegalArgumentException("Filter not found"))).isFalse();
    }

    @Test
    void numberFormatException_isPermanent() {
        assertThat(classifier.isTransient(new NumberFormatException("bad number"))).isFalse();
    }

    @Test
    void jsonProcessingException_isPermanent() throws Exception {
        assertThat(classifier.isTransient(new JsonParseException(null, "bad json"))).isFalse();
    }

    @Test
    void httpClientError4xxOtherThan408Or429_isPermanent() {
        assertThat(classifier.isTransient(
                new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"))).isFalse();
    }

    @Test
    void httpClientError408_isTransient() {
        assertThat(classifier.isTransient(
                new HttpClientErrorException(HttpStatus.REQUEST_TIMEOUT, "Request Timeout"))).isTrue();
    }

    @Test
    void httpClientError429_isTransient() {
        assertThat(classifier.isTransient(
                new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests"))).isTrue();
    }

    @Test
    void httpServerError5xx_isTransient() {
        assertThat(classifier.isTransient(
                new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable"))).isTrue();
    }

    @Test
    void socketTimeoutException_isTransient() {
        assertThat(classifier.isTransient(new SocketTimeoutException("Read timed out"))).isTrue();
    }

    @Test
    void connectException_isTransient() {
        assertThat(classifier.isTransient(new ConnectException("Connection refused"))).isTrue();
    }

    @Test
    void unknownHostException_isTransient() {
        assertThat(classifier.isTransient(new UnknownHostException("octane.invalid"))).isTrue();
    }

    @Test
    void javaUtilConcurrentTimeoutException_isTransient() {
        assertThat(classifier.isTransient(new TimeoutException("bounded fetch timed out"))).isTrue();
    }

    @Test
    void messageWithConnectionReset_isTransient() {
        assertThat(classifier.isTransient(new RuntimeException("java.io.IOException: Connection reset"))).isTrue();
    }

    @Test
    void messageWithServiceUnavailable_isTransient() {
        assertThat(classifier.isTransient(new RuntimeException("HTTP 503 Service Unavailable"))).isTrue();
    }

    @Test
    void messageWithGateway_isTransient() {
        assertThat(classifier.isTransient(new RuntimeException("504 Gateway Timeout"))).isTrue();
    }

    @Test
    void messageWith4xxOtherThan408Or429_isPermanent() {
        assertThat(classifier.isTransient(new RuntimeException("Octane returned HTTP 403 Forbidden"))).isFalse();
    }

    @Test
    void unrecognizedRuntimeExceptionWithNoSignal_defaultsToTransient() {
        assertThat(classifier.isTransient(new RuntimeException("something went sideways"))).isTrue();
    }

    @Test
    void nestedCauseChain_transientCauseFoundDeep() {
        Exception wrapped = new RuntimeException("Octane query failed",
                new RuntimeException("wrapper", new SocketTimeoutException("Read timed out")));
        assertThat(classifier.isTransient(wrapped)).isTrue();
    }

    @Test
    void nestedCauseChain_permanentSignalWinsOverTransientElsewhereInChain() {
        // Outer transient-looking message wraps an explicit permanent validation failure.
        Exception wrapped = new RuntimeException("connection reset while validating",
                new IllegalArgumentException("Workspace is disabled"));
        assertThat(classifier.isTransient(wrapped)).isFalse();
    }

    @Test
    void selfReferentialCauseChain_doesNotInfiniteLoop() {
        RuntimeException e1 = new RuntimeException("a");
        RuntimeException e2 = new RuntimeException("b", e1);
        e1.initCause(e2); // pathological, but must not hang
        assertThat(classifier.isTransient(e1)).isTrue();
    }
}
