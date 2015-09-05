package io.nucleo.net.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupTorException extends Exception {
    private static final Logger log = LoggerFactory.getLogger(SetupTorException.class);

    public SetupTorException(String message) {
        super(message);
    }
}
