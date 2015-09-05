package oi.nucleo.net;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class HiddenServiceDescriptor {

    private final String onionUrl;
    private final int localPort;
    private final int servicePort;
    private final ServerSocket serverSocket;

    public HiddenServiceDescriptor(String onionUrl, int localPort, int servicePort) throws IOException {
        this.onionUrl = onionUrl;
        this.localPort = localPort;
        this.servicePort = servicePort;
        this.serverSocket = new ServerSocket();
        this.serverSocket.bind(new InetSocketAddress(localPort));
    }

    public String getOnionUrl() {
        return onionUrl;
    }

    public int getLocalPort() {
        return localPort;
    }

    public int getservicePort() {
        return servicePort;
    }

    public String getFullAddress() {
        return onionUrl + ":" + servicePort;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }
}
