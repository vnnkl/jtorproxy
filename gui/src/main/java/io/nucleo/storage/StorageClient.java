package io.nucleo.storage;

import io.nucleo.messages.Header;
import io.nucleo.messages.Message;
import io.nucleo.net.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.function.Consumer;

public class StorageClient {
    private static final Logger log = LoggerFactory.getLogger(StorageClient.class);
    private final String id;

    private String address;

    private Network network;

    public StorageClient(String address, String id, Network network) {
        this.address = address;
        this.id = id;
        this.network = network;
    }

    public void start() {
        network.start("StorageClient_" + id);
    }

    public void put(String key, Serializable value, Consumer<Serializable> responseHandler) {
        send(new Message(new Header(Header.OP_PUT), key, value), responseHandler);
    }

    public void add(String key, Serializable value, Consumer<Serializable> responseHandler) {
        send(new Message(new Header(Header.OP_ADD), key, value), responseHandler);
    }

    public void get(String key, Consumer<Serializable> responseHandler) {
        send(new Message(new Header(Header.OP_GET), key), responseHandler);
    }

    protected void send(Serializable data, Consumer<Serializable> responseHandler) {
        network.send(data, address, responseHandler);
    }

    public void stop() {
        if (network != null) network.shutDown();
    }
}
