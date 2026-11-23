package com.cocos.challenge.application.exception;

public abstract class ApplicationException extends RuntimeException {
    protected ApplicationException(String message) {
        super(message);
    }

    public static final class BadRequest extends ApplicationException {
        public BadRequest(String message) { super(message); }
    }

    public static final class NotFound extends ApplicationException {
        public NotFound(String message) { super(message); }
    }

    public static final class Conflict extends ApplicationException {
        public Conflict(String message) { super(message); }
    }
}
