package io.nucleo.msg;

import io.nucleo.net.Connection;
import io.nucleo.net.exceptions.InvalidMessageException;
import io.nucleo.net.listeners.MessageListener;
import io.nucleo.net.messages.Header;
import io.nucleo.net.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MessagingHandler implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(MessagingHandler.class);
    private final List<Consumer<Serializable>> listeners = new ArrayList<>();

    public MessagingHandler() {
    }

    public void addListener(Consumer<Serializable> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<Serializable> listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public void onMessage(Connection connection, Message message) throws IOException, InvalidMessageException {
        log.debug("onMessage message" + message);
        log.debug("onMessage connection " + connection);
        Header header = message.header;
        switch (header.type) {
            case Header.SEND_MSG:
                log.debug("SEND_MSG");
                Serializable payload = message.payload;
                synchronized (listeners) {
                    listeners.stream().forEach(e -> e.accept(payload));
                }

                Header replyHeader = new Header(Header.SEND_MSG_RESULT, message.header.hash);
                Message replyMessage = new Message(replyHeader, new Boolean(true));
                connection.sendMessage(replyMessage);
                log.debug("SEND_MSG replyMessage sent. " + replyMessage);
                break;
            default:
                break;
        }
    }
}
