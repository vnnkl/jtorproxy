package io.nucleo.storage;

import java.io.Serializable;
import java.util.Arrays;

public class ProtectedDataEntry implements Serializable {
    private static final long serialVersionUID = -5452285962193347624L;
    public final Serializable data;
    public final byte[] pubKey;

    public ProtectedDataEntry(Serializable data, byte[] pubKey) {
        this.data = data;
        this.pubKey = pubKey;
    }

    @Override
    public String toString() {
        return "ProtectedDataEntry{" +
                "data=" + data +
                ", pubKey=" + Arrays.toString(pubKey) +
                '}';
    }
}
