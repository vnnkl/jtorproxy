package io.nucleo.net;

import io.nucleo.net.exceptions.InvalidMessageException;
import io.nucleo.net.listeners.ConnectionListener;
import io.nucleo.net.listeners.MessageListener;
import io.nucleo.net.messages.Header;
import io.nucleo.net.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

public class OutgoingConnection extends Connection {
    private static final Logger log = LoggerFactory.getLogger(OutgoingConnection.class);

    public OutgoingConnection(String peerAddress, Socket socket, ConnectionListener connectionListener, MessageListener messageListener) throws IOException {
        super(peerAddress, socket, connectionListener, messageListener);
    }

    @Override
    protected void processIncomingMessage(Message message) throws InvalidMessageException, IOException {
        log.debug("onMessage: " + message.toString());
        switch (message.header.type) {
            case Header.CONFIRM_CONNECTION:
                connected = true;
                synchronized (connectionListeners) {
                    for (ConnectionListener connectionListener : connectionListeners)
                        connectionListener.onConnected(this);
                }
                break;
            case Header.ADD_TO_MAP_RESULT:
            case Header.ADD_TO_MAP_FOR_SUBSCRIBERS:
            case Header.REMOVE_FROM_MAP_RESULT:
            case Header.REMOVE_FROM_MAP_FOR_SUBSCRIBERS:
            case Header.SUBSCRIBE_TO_MAP_RESULT:
            case Header.UN_SUBSCRIBE_FROM_MAP_RESULT:
            case Header.GET_FULL_MAP_RESULT:
                notifyMessageListeners(message);
                break;
            default:
                super.processIncomingMessage(message);
                break;
        }
    }

}
