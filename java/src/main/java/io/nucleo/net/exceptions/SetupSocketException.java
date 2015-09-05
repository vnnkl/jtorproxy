package io.nucleo.net.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupSocketException extends Exception {
    private static final Logger log = LoggerFactory.getLogger(SetupSocketException.class);

    public SetupSocketException(String message) {
        super(message);
    }
}
