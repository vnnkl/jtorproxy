package io.nucleo.msg;

import io.nucleo.net.Client;
import io.nucleo.net.messages.Header;
import io.nucleo.net.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.function.Consumer;

public class Messaging {
    private static final Logger log = LoggerFactory.getLogger(Messaging.class);
    private final Client client;

    public Messaging(Client client) {
        this.client = client;
    }

    public void sendMessage(Serializable payLoad, Consumer responder) throws IOException {
        client.sendMessage(new Message(new Header(Header.SEND_MSG, payLoad.hashCode()), payLoad), responder);
    }
}
