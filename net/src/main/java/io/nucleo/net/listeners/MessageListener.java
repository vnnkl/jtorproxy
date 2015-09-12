package io.nucleo.net.listeners;

import io.nucleo.net.Connection;
import io.nucleo.net.exceptions.InvalidMessageException;
import io.nucleo.net.messages.Message;

import java.io.IOException;

public interface MessageListener {
    void onMessage(Connection connection, Message message) throws IOException, InvalidMessageException;
}
