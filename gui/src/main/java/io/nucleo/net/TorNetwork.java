package io.nucleo.net;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.Socket;

public class TorNetwork extends Network {
    private static final Logger log = LoggerFactory.getLogger(TorNetwork.class);

    private TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode;

    public TorNetwork() {
        super();
    }

    @Override
    public void start(String id) {
        new Thread(() -> {
            log.info("Status: Starting up tor");
            status.set("Status: Starting up tor");
            try {
                torNode = new TorNode<JavaOnionProxyManager, JavaOnionProxyContext>(new File(id)) {
                };
                netWorkReady.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void startServer(int serverPort, ProcessDataHandler processDataHandler) {
        new Thread(() -> {
            netWorkReady.addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    log.info("Status: Starting up tor");
                    status.set("Status: Starting up tor");
                    try {
                        log.info("Status: Create hidden service");
                        status.set("Status: Create hidden service");
                        HiddenServiceDescriptor descriptor = torNode.createHiddenService(serverPort);
                        String fullAddress = descriptor.getFullAddress();
                        log.info("Onion address: " + fullAddress);
                        address.set(fullAddress);

                        log.info("Status: Start server");
                        status.set("Status: Start server");
                        server = new Server(descriptor.getServerSocket(), processDataHandler);
                        server.start();

                        log.info("Server running");
                        status.set("Server running");
                        serverReady.set(true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }).start();
    }

    @Override
    public void shutDown() {
        super.shutDown();
        try {
            if (torNode != null) torNode.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected Socket getSocket(String address) throws IOException {
        String[] tokens = address.split(":");
        if (torNode != null) return torNode.connectToHiddenService(tokens[0], Integer.parseInt(tokens[1]));
        else return null;
    }
}
