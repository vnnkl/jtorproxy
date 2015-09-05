package io.nucleo.net;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TorNetwork extends Network {
    private static final Logger log = LoggerFactory.getLogger(TorNetwork.class);

    private TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode;

    public TorNetwork() {
        super();
    }

    @Override
    public void start(String id, int serverPort, Repo repo, ServerHandler serverHandler) {
        new Thread(() -> {
            status.set("Status: Starting up tor");
            try {
                torNode = new TorNode<JavaOnionProxyManager, JavaOnionProxyContext>(new File(id)) {
                };

                status.set("Status: Create hidden service");
                HiddenServiceDescriptor descriptor = torNode.createHiddenService(serverPort);
                repo.add(descriptor.getFullAddress());
                address.set(descriptor.getFullAddress());

                status.set("Status: Start server");
                server = new Server(descriptor.getServerSocket(), serverHandler);
                server.start();

                status.set("Server running");
                netWorkReady.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        return torNode.connectToHiddenService(tokens[0], Integer.parseInt(tokens[1]));
    }
}
