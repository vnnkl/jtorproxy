package io.nucleo.net;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalHostNetwork extends Network {
    private static final Logger log = LoggerFactory.getLogger(LocalHostNetwork.class);

    private TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode;
    private ServerSocket serverSocket;

    public LocalHostNetwork() {
        super();
    }

    @Override
    public void start(String id, int serverPort, Repo repo, ServerHandler serverHandler) throws IOException {
        new Thread(() -> {
            try {
                status.set("Status: Start server");
                String address = "localhost:" + serverPort;
                repo.add(address);
                this.address.set(address);
                serverSocket = new ServerSocket(serverPort);
                server = new Server(serverSocket, serverHandler);
                server.start();
                status.set("Server running");
                netWorkReady.set(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void shutDown() {
        super.shutDown();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected Socket getSocket(String address) throws IOException {
        String[] tokens = address.split(":");
        return new Socket(tokens[0], Integer.parseInt(tokens[1]));
    }
}
