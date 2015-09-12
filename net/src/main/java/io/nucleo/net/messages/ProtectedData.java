package io.nucleo.net.messages;

import java.io.Serializable;
import java.util.Arrays;

public class ProtectedData implements Serializable {
    private static final long serialVersionUID = -5452285962193347624L;
    public final Serializable data;
    public final byte[] pubKey;

    public ProtectedData(Serializable data, byte[] pubKey) {
        this.data = data;
        this.pubKey = pubKey;
    }

    @Override
    public String toString() {
        return "ProtectedData{" +
                "data=" + data +
                ", pubKey=" + Arrays.toString(pubKey) +
                '}';
    }
}
