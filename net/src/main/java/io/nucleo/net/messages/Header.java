package io.nucleo.net.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

//TODO Header does nto need to be separate class but type and version could be added to Message directly
// also question if messages should use the type field or separation of messages should be done by using custom classes 
// and instanceof checks
public class Header implements Serializable {
    private static final long serialVersionUID = 202114211049868196L;

    private static final Logger log = LoggerFactory.getLogger(Header.class);

    public static final int OPEN_CONNECTION = 1;
    public static final int CONFIRM_CONNECTION = 2;
    public static final int CLOSE_CONNECTION = 3;
    public static final int HEART_BEAT = 4;

    public static final int ADD_TO_MAP = 10;
    public static final int ADD_TO_MAP_RESULT = 11;
    public static final int ADD_TO_MAP_FOR_SUBSCRIBERS = 12;

    public static final int REMOVE_FROM_MAP = 20;
    public static final int REMOVE_FROM_MAP_RESULT = 21;
    public static final int REMOVE_FROM_MAP_FOR_SUBSCRIBERS = 22;

    public static final int GET_FULL_MAP = 30;
    public static final int GET_FULL_MAP_RESULT = 31;

    public static final int SUBSCRIBE_TO_MAP = 40;
    public static final int SUBSCRIBE_TO_MAP_RESULT = 41;
    public static final int UN_SUBSCRIBE_FROM_MAP = 50;
    public static final int UN_SUBSCRIBE_FROM_MAP_RESULT = 51;

    public static final int SEND_MSG = 60;
    public static final int SEND_MSG_RESULT = 61;


    public final int type;
    public int hash;    //TODO used for matching listeners to msg, but should be done differently, 
    // not sure if hash is needed here
    public int version; //TODO not used yet

    public Header(int type) {
        this.type = type;
    }

    public Header(int type, int hash) {
        this.type = type;
        this.hash = hash;
    }

    @Override
    public String toString() {
        return "Header{" +
                "type=" + type +
                ", hash=" + hash +
                '}';
    }
}
