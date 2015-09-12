package io.nucleo.storage;

import io.nucleo.net.messages.Header;
import io.nucleo.net.messages.Message;

import java.io.Serializable;
import java.util.Arrays;

public class StorageMessages implements Serializable {
    private static final long serialVersionUID = -8442458727155470264L;

    public static Message getAddToMapMessage(String mapKey, String key, byte[] pubKey, Serializable value) {
        AddToMapPayload payload = new AddToMapPayload(mapKey, key, pubKey, value);
        return new Message(new Header(Header.ADD_TO_MAP, payload.hashCode()), payload);
    }

    public static Message getRemoveFromMapMessage(String mapKey, String key, byte[] signature) {
        RemoveFromMapPayload payload = new RemoveFromMapPayload(mapKey, key, signature);
        return new Message(new Header(Header.REMOVE_FROM_MAP, payload.hashCode()), payload);
    }

    public static Message getGetFullMapMessage(String mapKey) {
        GetFullSMapPayload payload = new GetFullSMapPayload(mapKey);
        return new Message(new Header(Header.GET_FULL_MAP, payload.hashCode()), payload);
    }

    public static Message getSubscribeToMapMessage(String mapKey) {
        SubscribeToMapPayload payload = new SubscribeToMapPayload(mapKey);
        return new Message(new Header(Header.SUBSCRIBE_TO_MAP, payload.hashCode()), payload);
    }

    public static Message getUnSubscribeToMapMessage(String mapKey) {
        UnSubscribeToMapPayload payload = new UnSubscribeToMapPayload(mapKey);
        return new Message(new Header(Header.UN_SUBSCRIBE_FROM_MAP, payload.hashCode()), payload);
    }

    public static class AddToMapPayload implements Serializable {
        public final String mapKey;
        public final String key;
        public final byte[] pubKey;
        public final Serializable data;

        public AddToMapPayload(String mapKey, String key, byte[] pubKey, Serializable data) {
            this.mapKey = mapKey;
            this.key = key;
            this.pubKey = pubKey;
            this.data = data;
        }

        @Override
        public String toString() {
            return "AddToSetPayload{" +
                    "mapKey='" + mapKey + '\'' +
                    ", key='" + key + '\'' +
                    ", pubKey=" + Arrays.toString(pubKey) +
                    ", data=" + data +
                    '}';
        }
    }

    public static class RemoveFromMapPayload implements Serializable {
        public final String mapKey;
        public final String key;
        public final byte[] signature;

        public RemoveFromMapPayload(String mapKey, String key, byte[] signature) {
            this.mapKey = mapKey;
            this.key = key;
            this.signature = signature;
        }

        @Override
        public String toString() {
            return "RemoveFromSetPayload{" +
                    "mapKey='" + mapKey + '\'' +
                    ", key='" + key + '\'' +
                    ", signature=" + Arrays.toString(signature) +
                    '}';
        }
    }

    public static class GetFullSMapPayload implements Serializable {
        public String mapKey;

        public GetFullSMapPayload(String mapKey) {
            this.mapKey = mapKey;
        }

        @Override
        public String toString() {
            return "GetFullSetPayload{" +
                    "mapKey='" + mapKey + '\'' +
                    '}';
        }
    }

    public static class SubscribeToMapPayload implements Serializable {
        public String mapKey;

        public SubscribeToMapPayload(String mapKey) {
            this.mapKey = mapKey;
        }

        @Override
        public String toString() {
            return "SubscribeToSetPayload{" +
                    "mapKey='" + mapKey + '\'' +
                    '}';
        }
    }

    public static class UnSubscribeToMapPayload implements Serializable {
        public String mapKey;

        public UnSubscribeToMapPayload(String mapKey) {
            this.mapKey = mapKey;
        }

        @Override
        public String toString() {
            return "SubscribeToSetPayload{" +
                    "mapKey='" + mapKey + '\'' +
                    '}';
        }
    }


}
