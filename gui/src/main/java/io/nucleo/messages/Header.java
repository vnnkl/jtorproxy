package io.nucleo.messages;

import java.io.Serializable;

public class Header implements Serializable {
    private static final long serialVersionUID = 1581428066340260352L;

    public static byte OP_PUT = 0x01;
    public static byte OP_ADD = 0x02;
    public static byte OP_GET = 0x03;

    public byte type;

    public Header(byte type) {
        this.type = type;
    }


    @Override
    public String toString() {
        return "Header{" +
                "type=" + type +
                '}';
    }
}
