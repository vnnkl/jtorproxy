package io.nucleo.net;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.util.function.Consumer;

public abstract class Network {
    private static final Logger log = LoggerFactory.getLogger(Network.class);

    protected final StringProperty status = new SimpleStringProperty();
    protected final BooleanProperty serverReady = new SimpleBooleanProperty();
    protected final BooleanProperty netWorkReady = new SimpleBooleanProperty();
    protected final StringProperty address = new SimpleStringProperty();
    protected Server server;

    public Network() {
    }

    abstract public void start(String id);

    abstract public void startServer(int serverPort, ProcessDataHandler processDataHandler);

    public void shutDown() {
        try {
            if (server != null) server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    abstract protected Socket getSocket(String address) throws IOException;

    public void connectToPeer(String peerAddress) {
        new Thread(() -> {
            status.set("Status: Connect to " + address);
            try {
                Socket socket = getSocket(peerAddress);
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void send(final Serializable data, String address, Consumer<Serializable> responseHandler) {
        new Thread(() -> {
            try {
                status.set("Status: Connect to " + address);
                Socket socket = getSocket(address);
                Client client = new Client(socket);
                log.debug("data to send " + data);
                ListenableFuture<Serializable> future = client.sendAsync(data);
                Futures.addCallback(future, new FutureCallback<Serializable>() {
                    @Override
                    public void onSuccess(Serializable serializable) {
                        responseHandler.accept(serializable);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }


    public StringProperty addressProperty() {
        return address;
    }

    public StringProperty statusProperty() {
        return status;
    }

    public BooleanProperty serverReadyProperty() {
        return serverReady;
    }
}
