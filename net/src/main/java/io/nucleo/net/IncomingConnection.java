package io.nucleo.net;

import io.nucleo.net.exceptions.InvalidMessageException;
import io.nucleo.net.listeners.ConnectionListener;
import io.nucleo.net.listeners.MessageListener;
import io.nucleo.net.messages.Header;
import io.nucleo.net.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class IncomingConnection extends Connection {
    private static final Logger log = LoggerFactory.getLogger(IncomingConnection.class);

    public IncomingConnection(String peerAddress,
                              Socket socket,
                              ObjectOutputStream objectOutputStream,
                              ObjectInputStream objectInputStream, ConnectionListener connectionListener,
                              MessageListener messageListener) throws IOException {
        super(peerAddress, socket, objectOutputStream, objectInputStream, connectionListener, messageListener);
    }

    protected void processIncomingMessage(Message message) throws InvalidMessageException, IOException {
        log.debug("onMessage: " + message.toString());

        Header header = message.header;
        switch (header.type) {
            case Header.HEART_BEAT:
                break;
            case Header.ADD_TO_MAP:
            case Header.REMOVE_FROM_MAP:
            case Header.SUBSCRIBE_TO_MAP:
            case Header.UN_SUBSCRIBE_FROM_MAP:
            case Header.GET_FULL_MAP:
                notifyMessageListeners(message);
                break;
            default:
                super.processIncomingMessage(message);
        }
    }

    public void onOpenConnection() throws IOException {
        log.debug("onOpenConnection");
        connected = true;
        sendMessage(new Message(new Header(Header.CONFIRM_CONNECTION)));
        startHeartbeat();
        synchronized (connectionListeners) {
            for (ConnectionListener connectionListener : connectionListeners)
                connectionListener.onConnected(this);
        }
    }
}
