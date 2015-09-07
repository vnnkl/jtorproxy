package io.nucleo.storage;

import io.nucleo.messages.Header;
import io.nucleo.messages.Message;
import io.nucleo.net.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.util.Set;

public class LocalHostStorageClient extends Storage {
    private static final Logger log = LoggerFactory.getLogger(LocalHostStorageClient.class);

    private String address;
    private Socket socket;
    private Client client;

    public LocalHostStorageClient(String address) {
        this.address = address;
    }

    public Serializable put(String key, Serializable value) {
        return send(new Message(new Header(Header.OP_PUT), key, value));
    }

    public Set<Serializable> add(String key, Serializable value) {
        return (Set) send(new Message(new Header(Header.OP_ADD), key, value));
    }

    public Serializable get(String key) {
        return send(new Message(new Header(Header.OP_GET), key));
    }

    protected Serializable send(Serializable data) {
        try {
            socket = getSocket(address);
            client = new Client(socket);
            return client.sendSync(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected Socket getSocket(String address) throws IOException {
        String[] tokens = address.split(":");
        return new Socket(tokens[0], Integer.parseInt(tokens[1]));
    }

    public void stop() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
