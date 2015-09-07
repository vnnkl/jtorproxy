package io.nucleo.net;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class LocalHostNetwork extends Network {
    private static final Logger log = LoggerFactory.getLogger(LocalHostNetwork.class);

    private TorNode<JavaOnionProxyManager, JavaOnionProxyContext> torNode;
    private ServerSocket serverSocket;

    public LocalHostNetwork() {
        super();
    }

    @Override
    public void start(String id) {
        netWorkReady.set(true);
    }

    @Override
    public void startServer(int serverPort,
                            ProcessDataHandler processDataHandler) {
        new Thread(() -> {
            try {
                status.set("Status: Start server");
                String address = "localhost:" + serverPort;
                this.address.set(address);
                serverSocket = new ServerSocket(serverPort);
                server = new Server(serverSocket, processDataHandler);
                server.start();
                status.set("Server running");
                serverReady.set(true);
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
