package io.nucleo.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.function.Consumer;

public class InProcessStorageClient extends StorageClient {
    private static final Logger log = LoggerFactory.getLogger(InProcessStorageClient.class);
    private final Storage storage;

    public InProcessStorageClient() {
        super(null, null, null);
        storage = new Storage();
    }

    public void start() {
    }

    public void put(String key, Serializable value, Consumer<Serializable> responseHandler) {
        responseHandler.accept(storage.put(key, value));
    }

    public void add(String key, Serializable value, Consumer<Serializable> responseHandler) {
        responseHandler.accept((Serializable) storage.add(key, value));
    }

    public void get(String key, Consumer<Serializable> responseHandler) {
        responseHandler.accept(storage.get(key));
    }

    public void stop() {
    }
}
