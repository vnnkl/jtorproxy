package io.nucleo.net;

import io.nucleo.net.exceptions.InvalidMessageException;
import io.nucleo.net.listeners.ConnectionListener;
import io.nucleo.net.listeners.MessageListener;
import io.nucleo.net.messages.Header;
import io.nucleo.net.messages.Message;
import io.nucleo.storage.StorageMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Client implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(Client.class);

    private Socket socket;
    private final String clientAddress;

    private OutgoingConnection outgoingConnection;
    private final Map<Integer, Consumer> responders = new HashMap<>();
    private final Map<String, Consumer> subscribers = new HashMap<>();

    public Client(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public OutgoingConnection openConnection(String serverAddress, ConnectionListener connectionListener, MessageListener messageListener)
            throws NumberFormatException, IOException {
        log.debug("connect to " + serverAddress);
        String[] tokens = serverAddress.split(":");
        socket = new Socket(tokens[0], Integer.parseInt(tokens[1]));
        socket.setSoTimeout(60000);
        outgoingConnection = new OutgoingConnection(serverAddress, socket, connectionListener, messageListener);
        log.debug("Send OpenConnectionMessage");
        outgoingConnection.sendMessage(new Message(new Header(Header.OPEN_CONNECTION), clientAddress));

        outgoingConnection.addMessageListener((connection, message) -> {
            log.debug("Client MessageListener message " + message);
            switch (message.header.type) {
                case Header.ADD_TO_MAP_FOR_SUBSCRIBERS:
                    if (message.payload instanceof StorageMessageFactory.AddToMapPayload) {
                        synchronized (subscribers) {
                            Consumer subscriber = subscribers.get(((StorageMessageFactory.AddToMapPayload) message.payload).mapKey);
                            log.debug("subscriber " + subscriber);
                            if (subscriber != null) {
                                log.debug("ADD_TO_SET_FOR_SUBSCRIBERS notify subscriber " + message.payload);
                                subscriber.accept(message.payload);
                            }
                        }
                    } else {
                        throw new InvalidMessageException("message.payload is not instance of StorageMessages.AddToMapPayload");
                    }
                    break;
                case Header.REMOVE_FROM_MAP_FOR_SUBSCRIBERS:
                    if (message.payload instanceof StorageMessageFactory.RemoveFromMapPayload) {
                        synchronized (subscribers) {
                            Consumer subscriber = subscribers.get(((StorageMessageFactory.RemoveFromMapPayload) message.payload).mapKey);
                            if (subscriber != null) {
                                log.debug("REMOVE_FROM_SET_FOR_SUBSCRIBERS notify subscriber " + message.payload);
                                subscriber.accept(message.payload);
                            }
                        }
                    } else {
                        throw new InvalidMessageException("message.payload is not instance of StorageMessages.AddToMapPayload");
                    }
                    break;
                default:
                    synchronized (responders) {
                        Consumer responder = responders.get(message.header.hash);
                        if (responder != null) {
                            responder.accept(message.payload);
                            if (message.header.type != Header.SUBSCRIBE_TO_MAP_RESULT && message.header.type != Header.UN_SUBSCRIBE_FROM_MAP_RESULT) {
                                log.debug("remove responder " + message);
                                responders.remove(message.header.hash);
                            }
                        }
                    }
                    break;
            }
        });
        return outgoingConnection;
    }

    public void close() {
        try {
            if (socket != null) socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public OutgoingConnection getOutgoingConnection() {
        return outgoingConnection;
    }

    public void sendSubscribeToMapMessage(Message message, Consumer responder, Consumer subscriber) throws IOException, InvalidMessageException {
        log.debug("sendSubscribeToMapMessage");
        if (message.payload instanceof StorageMessageFactory.SubscribeToMapPayload) {
            StorageMessageFactory.SubscribeToMapPayload payload = (StorageMessageFactory.SubscribeToMapPayload) message.payload;
            synchronized (subscribers) {
                subscribers.put(payload.mapKey, subscriber);
            }
        } else {
            throw new InvalidMessageException("message.payload is not instance of SubscribeToMapPayload");
        }
        sendMessage(message, responder);
    }

    public void sendUnSubscribeToMapMessage(Message message, Consumer responder) throws IOException, InvalidMessageException {
        log.debug("sendUnSubscribeToMapMessage");
        if (message.payload instanceof StorageMessageFactory.UnSubscribeToMapPayload) {
            StorageMessageFactory.UnSubscribeToMapPayload payload = (StorageMessageFactory.UnSubscribeToMapPayload) message.payload;
            synchronized (subscribers) {
                subscribers.remove(payload.mapKey);
            }
        } else {
            throw new InvalidMessageException("message.payload is not instance of UnSubscribeToMapPayload");
        }
        sendMessage(message, responder);
    }

    public void sendMessage(Message message, Consumer responder) throws IOException {
        log.debug("sendMessage message.header.hash " + message.header.hash);
        synchronized (responders) {
            responders.put(message.header.hash, responder);
        }
        outgoingConnection.sendMessage(message);
    }
}
