package io.nucleo.storage;

import io.nucleo.messages.Header;
import io.nucleo.messages.Message;
import java.io.Serializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageProcessor {
    private static final Logger log = LoggerFactory.getLogger(StorageProcessor.class);
    private final Storage storage;

    public StorageProcessor() {
        storage = new Storage();
    }

    public Serializable process(final Serializable data) {
        if (data instanceof Message) {
            Message message = (Message) data;
            if (message.header.type == Header.OP_PUT) storage.put(message.key, message.value);
            else if (message.header.type == Header.OP_ADD) storage.add(message.key, message.value);
            else if (message.header.type == Header.OP_GET) return storage.get(message.key);
        }
        return null;
    }
}
