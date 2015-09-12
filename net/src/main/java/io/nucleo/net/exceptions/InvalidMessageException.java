package io.nucleo.net.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvalidMessageException extends Exception {
    private static final Logger log = LoggerFactory.getLogger(InvalidMessageException.class);

    public InvalidMessageException() {
    }

    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidMessageException(Throwable cause) {
        super(cause);
    }

    public InvalidMessageException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
