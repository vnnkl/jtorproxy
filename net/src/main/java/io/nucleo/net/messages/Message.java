package io.nucleo.net.messages;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 3789164145723089637L;

    public final Header header;
    public final Serializable payload;

    public Message(Header header) {
        this(header, null);
    }

    public Message(Header header, Serializable payload) {
        this.header = header;
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "Message{" +
                "header=" + header +
                ", payload=" + payload +
                '}';
    }
}
