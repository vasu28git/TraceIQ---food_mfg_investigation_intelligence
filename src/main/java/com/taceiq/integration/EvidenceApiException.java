package com.taceiq.integration;

import lombok.Getter;

@Getter
public class EvidenceApiException extends RuntimeException {
    private final int statusCode;
    private final boolean retryable;
    private final String responseBody;

    public EvidenceApiException(int statusCode, boolean retryable, String message, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
        this.responseBody = responseBody;
    }

    public EvidenceApiException(int statusCode, boolean retryable, String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
        this.responseBody = responseBody;
    }

    public static boolean isRetryableStatus(int status) {
        if (status == 429) return true;
        if (status >= 500 && status < 600) return true;
        // 408 Request Timeout also retryable
        if (status == 408) return true;
        return false;
    }

    public static boolean isNonRetryableStatus(int status) {
        if (status == 401 || status == 403) return true;
        if (status == 400 || status == 404 || status == 422) return true;
        return false;
    }
}
