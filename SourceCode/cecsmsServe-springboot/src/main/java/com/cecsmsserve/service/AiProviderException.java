package com.cecsmsserve.service;

/**
 * Provider-neutral error returned by an upstream model service.
 */
public class AiProviderException extends RuntimeException {

    private final int httpStatus;

    public AiProviderException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public AiProviderException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
