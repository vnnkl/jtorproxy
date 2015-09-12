package io.nucleo.storage;

import io.nucleo.net.Client;
import io.nucleo.net.exceptions.InvalidMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.function.Consumer;

public class Storage {
    private static final Logger log = LoggerFactory.getLogger(Storage.class);
    private Client client;

    public Storage(Client client) {
        this.client = client;
    }

    public void addToMap(String mapKey, String key, byte[] pubKey, Serializable value, Consumer responder)
            throws IOException {
        client.sendMessage(StorageMessageFactory.getAddToMapMessage(mapKey, key, pubKey, value), responder);
    }

    public void removeFromMap(String mapKey, String key, byte[] signature, Consumer responder)
            throws IOException {
        client.sendMessage(StorageMessageFactory.getRemoveFromMapMessage(mapKey, key, signature), responder);
    }

    public void getFullSet(String mapKey, Consumer responder)
            throws IOException {
        client.sendMessage(StorageMessageFactory.getGetFullMapMessage(mapKey), responder);
    }

    public void subscribeToMap(String mapKey, Consumer responder, Consumer changeListener)
            throws IOException, InvalidMessageException {
        client.sendSubscribeToMapMessage(StorageMessageFactory.getSubscribeToMapMessage(mapKey), responder, changeListener);
    }

    public void unSubscribeToMap(String mapKey, Consumer responder)
            throws IOException, InvalidMessageException {
        client.sendUnSubscribeToMapMessage(StorageMessageFactory.getUnSubscribeToMapMessage(mapKey), responder);
    }
}
