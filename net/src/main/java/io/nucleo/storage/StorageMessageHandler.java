package io.nucleo.storage;

import io.nucleo.net.Connection;
import io.nucleo.net.exceptions.InvalidMessageException;
import io.nucleo.net.listeners.MessageListener;
import io.nucleo.net.messages.Header;
import io.nucleo.net.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StorageMessageHandler implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(StorageMessageHandler.class);

    private Map<String, Map<String, ProtectedDataEntry>> map = new HashMap<>();
    private Map<String, Set<Connection>> subscribers = new HashMap<>();

    @Override
    public void onMessage(Connection connection, Message message) throws IOException, InvalidMessageException {
        log.debug("onMessage message" + message);
        log.debug("onMessage connection " + connection);
        Header header = message.header;
        switch (header.type) {
            case Header.ADD_TO_MAP:
                log.debug("ADD_TO_SET");
                if (message.payload instanceof StorageMessageFactory.AddToMapPayload) {
                    StorageMessageFactory.AddToMapPayload payload = (StorageMessageFactory.AddToMapPayload) message.payload;
                    Map<String, ProtectedDataEntry> innerMap = map.get(payload.mapKey);
                    if (innerMap == null) {
                        innerMap = new HashMap<>();
                        map.put(payload.mapKey, innerMap);
                    }
                    ProtectedDataEntry protectedDataEntry = new ProtectedDataEntry(((StorageMessageFactory.AddToMapPayload) message.payload).data, payload.pubKey);
                    boolean result = innerMap.get(payload.key) == null;
                    if (result)
                        innerMap.put(payload.key, protectedDataEntry);

                    Header replyHeader = new Header(Header.ADD_TO_MAP_RESULT, message.header.hash);
                    Message replyMessage = new Message(replyHeader, new Boolean(result));
                    connection.sendMessage(replyMessage);
                    log.debug("ADD_TO_SET replyMessage sent. " + replyMessage);

                    if (result)
                        notifySubscribers(payload.mapKey, new Message(new Header(Header.ADD_TO_MAP_FOR_SUBSCRIBERS), payload));
                } else {
                    throw new InvalidMessageException("message.payload not instance of AddToMapPayload");
                }
                break;
            case Header.REMOVE_FROM_MAP:
                if (message.payload instanceof StorageMessageFactory.RemoveFromMapPayload) {
                    StorageMessageFactory.RemoveFromMapPayload payload = (StorageMessageFactory.RemoveFromMapPayload) message.payload;
                    Map<String, ProtectedDataEntry> innerMap = map.get(payload.mapKey);
                    boolean result = false;
                    if (innerMap != null) {
                        ProtectedDataEntry protectedDataEntry = innerMap.get(payload.key);

                        if (protectedDataEntry != null) {
                            result = verifySignature(protectedDataEntry.pubKey, ((StorageMessageFactory.RemoveFromMapPayload) message.payload).signature);
                            if (result)
                                innerMap.remove(payload.key);
                        } else {
                            log.warn("sealedEntry = null");
                        }
                    } else {
                        log.warn("inner map = null");
                    }
                    log.debug("REMOVE_FROM_SET result= " + result);
                    Header replyHeader = new Header(Header.REMOVE_FROM_MAP_RESULT, message.header.hash);
                    Message replyMessage = new Message(replyHeader, new Boolean(result));
                    connection.sendMessage(replyMessage);
                    log.debug("REMOVE_FROM_SET replyMessage sent. " + replyMessage);

                    if (result)
                        notifySubscribers(payload.mapKey, new Message(new Header(Header.REMOVE_FROM_MAP_FOR_SUBSCRIBERS), payload));
                } else {
                    throw new InvalidMessageException("message.payload not instance of RemoveFromMapPayload");
                }
                break;
            case Header.SUBSCRIBE_TO_MAP:
                if (message.payload instanceof StorageMessageFactory.SubscribeToMapPayload) {
                    log.debug("SUBSCRIBE_TO_SET");
                    StorageMessageFactory.SubscribeToMapPayload payload = (StorageMessageFactory.SubscribeToMapPayload) message.payload;
                    synchronized (subscribers) {
                        Set<Connection> subscribersPerTopic = subscribers.get(payload.mapKey);
                        if (subscribersPerTopic == null) {
                            subscribersPerTopic = new HashSet<>();
                            subscribers.put(payload.mapKey, subscribersPerTopic);
                        }
                        subscribersPerTopic.add(connection);
                    }
                    Header replyHeader = new Header(Header.SUBSCRIBE_TO_MAP_RESULT, message.header.hash);
                    Message replyMessage = new Message(replyHeader, new Boolean(true));
                    connection.sendMessage(replyMessage);
                    log.debug("SUBSCRIBE_TO_SET replyMessage sent. " + replyMessage);
                } else {
                    throw new InvalidMessageException("message.payload not instance of GetFullSMapPayload");
                }
                break;
            case Header.UN_SUBSCRIBE_FROM_MAP:
                if (message.payload instanceof StorageMessageFactory.UnSubscribeToMapPayload) {
                    log.debug("UN_SUBSCRIBE_FROM_SET");
                    StorageMessageFactory.UnSubscribeToMapPayload payload = (StorageMessageFactory.UnSubscribeToMapPayload) message.payload;
                    Boolean result;
                    synchronized (subscribers) {
                        Set<Connection> subscribersPerTopic = subscribers.get(payload.mapKey);
                        result = subscribersPerTopic != null;
                        if (result)
                            subscribersPerTopic.remove(connection);
                    }
                    Header replyHeader = new Header(Header.UN_SUBSCRIBE_FROM_MAP_RESULT, message.header.hash);
                    Message replyMessage = new Message(replyHeader, new Boolean(result));
                    connection.sendMessage(replyMessage);
                    log.debug("UN_SUBSCRIBE_FROM_SET replyMessage sent. " + replyMessage);
                } else {
                    throw new InvalidMessageException("message.payload not instance of GetFullSMapPayload");
                }
                break;
            case Header.GET_FULL_MAP:
                if (message.payload instanceof StorageMessageFactory.GetFullSMapPayload) {
                    log.debug("GET_FULL_SET");
                    StorageMessageFactory.GetFullSMapPayload payload = (StorageMessageFactory.GetFullSMapPayload) message.payload;
                    Map<String, ProtectedDataEntry> innerMap = map.get(payload.mapKey);
                    Header replyHeader = new Header(Header.GET_FULL_MAP_RESULT, message.header.hash);
                    Message replyMessage = new Message(replyHeader, (Serializable) innerMap);
                    connection.sendMessage(replyMessage);
                    log.debug("GET_FULL_SET replyMessage sent. " + replyMessage);
                } else {
                    throw new InvalidMessageException("message.payload not instance of GetFullSMapPayload");
                }
                break;
            default:
                break;
        }
    }

    private void notifySubscribers(String mapKey, Message message) {
        synchronized (subscribers) {
            subscribers.get(mapKey).stream().forEach(con -> {
                try {
                    con.sendMessage(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private boolean verifySignature(byte[] pubKey, byte[] signature) {
        //TODO
        return true;
    }
}
