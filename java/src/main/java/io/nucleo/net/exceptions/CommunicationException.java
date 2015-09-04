package io.nucleo.net.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommunicationException extends Exception {
    private static final Logger log = LoggerFactory.getLogger(CommunicationException.class);

    public CommunicationException(String message) {
        super(message);
    }
}
