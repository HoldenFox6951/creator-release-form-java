package com.infrai.example.release;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Thin HTTP client over the Infrai PDF endpoints. One API key and one base URL cover
 * document rendering and job polling alike, so there is nothing else to wire up here.
 */
public final class InfraiPdfClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl;
    private final String apiKey;

    public InfraiPdfClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    /** POST /v1/pdf/generate — renders the release document. */
    public Object generate(Map<String, Object> body, String idempotencyKey) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/pdf/generate"))
                .method("POST", HttpRequest.BodyPublishers.ofString(Json.object(body)))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .timeout(Duration.ofSeconds(60))
                .build();
        return send(request);
    }

    /** GET /v1/pdf/job/get/{job_id} — reads the state of an asynchronous render. */
    public Object job(String jobId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/pdf/job/get/" + jobId))
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .build();
        return send(request);
    }

    private Object send(HttpRequest request) {
        int attempt = 0;
        while (true) {
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new IllegalStateException("transport failure calling " + request.uri(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted calling " + request.uri(), e);
            }
            if (response.statusCode() == 429 && attempt < 4) {
                sleep(retryDelayMs(response, attempt));
                attempt++;
                continue;
            }
            return decode(response);
        }
    }

    /**
     * Decode the {ok, data, error, metadata} envelope first, then decide. A refused argument
     * arrives as a complete envelope with an HTTP status attached, so the code that reads it
     * gets the same structure every time.
     */
    private Object decode(HttpResponse<String> response) {
        Object envelope;
        try {
            envelope = Json.parse(response.body());
        } catch (RuntimeException e) {
            throw new IllegalStateException("upstream returned a non-JSON body (HTTP "
                    + response.statusCode() + ")", e);
        }
        Object ok = Json.get(envelope, "ok");
        if (Boolean.TRUE.equals(ok)) {
            return Json.get(envelope, "data");
        }
        String code = Json.text(envelope, "error", "code");
        String message = Json.text(envelope, "error", "message");
        throw new InfraiException(code == null ? "UNKNOWN" : code,
                message == null ? "no message" : message,
                response.statusCode());
    }

    private long retryDelayMs(HttpResponse<String> response, int attempt) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                // a non-numeric hint just falls through to the exponential schedule
            }
        }
        return 500L * (1L << attempt);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while backing off", e);
        }
    }
}
