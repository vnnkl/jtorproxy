package io.nucleo.messages;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = -3308685205511653120L;

    public final Header header;
    public final String key;
    public final Serializable value;

    public Message(Header header, String key) {
        this(header, key, null);
    }

    public Message(Header header, String key, Serializable value) {
        this.header = header;
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Message{" +
                "header=" + header +
                ", key='" + key + '\'' +
                ", value=" + value +
                '}';
    }
}
