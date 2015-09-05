package io.nucleo.net;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import java.net.Socket;

import java.util.function.Consumer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import oi.nucleo.net.HiddenServiceDescriptor;
import oi.nucleo.net.TorNode;

public class Network {
    private static final Logger log = LoggerFactory.getLogger(Network.class);

    private final StringProperty status = new SimpleStringProperty();


    private final BooleanProperty netWorkReady = new SimpleBooleanProperty();

    private final ObjectProperty<HiddenServiceDescriptor> hiddenServiceDescriptor = new SimpleObjectProperty<>();
    private Server server;
    private TorNode<JavaOnionProxyManager, JavaOnionProxyContext> node;

    public Network(String id, int hiddenServicePort, Repo repo, ServerHandler serverHandler) throws IOException {
        new Thread(() -> {
            status.set("Status: Starting up tor");
            try {
                node = new TorNode<JavaOnionProxyManager,
                        JavaOnionProxyContext>(new File(id)) {
                };

                status.set("Status: Create hidden service");
                HiddenServiceDescriptor descriptor = node.createHiddenService(hiddenServicePort);
                repo.add(descriptor.getFullAddress());
                hiddenServiceDescriptor.set(descriptor);

                status.set("Status: Setup server");
                server = new Server(descriptor.getServerSocket(), serverHandler);
                server.start();

                status.set("Server setup");
                netWorkReady.set(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void connectToPeer(String peerAddress) {
        new Thread(() -> {
            status.set("Status: Setup connection to " + peerAddress);
            try {
                getSocket(peerAddress);
            } catch (IOException e) {
                e.printStackTrace();
            }
            status.set("Status: Connection to " + peerAddress + " setup");
        }).start();
    }

    public ObjectProperty<HiddenServiceDescriptor> hiddenServiceDescriptorProperty() {
        return hiddenServiceDescriptor;
    }

    public StringProperty statusProperty() {
        return status;
    }

    public BooleanProperty netWorkReadyProperty() {
        return netWorkReady;
    }

    public void send(Serializable text, String address, Consumer<Serializable> responseHandler) {
        new Thread(() -> {
            try {
                status.set("Status: Setup connection to " + address);
                Socket socket = getSocket(address);
                Client client = new Client(socket);
                status.set("Status: Connection to " + address + " setup");
                ListenableFuture<Serializable> future = client.sendAsyncAndCloseSocket(text);
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

    private Socket getSocket(String address) throws IOException {
        String[] tokens = address.split(":");
        return node.connectToHiddenService(tokens[0], Integer.parseInt(tokens[1]));
    }

    public void shutDown() {
        try {
            if (node != null) node.shutdown();
            if (server != null) server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
