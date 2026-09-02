package com.infrai.example.release;

/** Carries the envelope's error code so the service layer can answer its own caller correctly. */
public final class InfraiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public final String code;
    public final int httpStatus;

    public InfraiException(String code, String message, int httpStatus) {
        super(code + ": " + message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
