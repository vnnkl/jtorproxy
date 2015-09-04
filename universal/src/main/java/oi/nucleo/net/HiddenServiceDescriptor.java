package oi.nucleo.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class HiddenServiceDescriptor {

    private final String name;
    private final int localPort;
    private final int servicePort;
    private final ServerSocket serverSocket;

    public HiddenServiceDescriptor(String name, int localPort, int servicePort) throws IOException {
        this.name=name;
        this.localPort=localPort;
        this.servicePort=servicePort;
        this.serverSocket = new ServerSocket();
        this.serverSocket.bind(new InetSocketAddress(localPort));
    }

    public String getName() {
        return name;
    }

    public int getLocalPort() {
        return localPort;
    }

    public int getservicePort() {
        return servicePort;
    }
    
    public ServerSocket getServerSocket(){
        return serverSocket;
    }
}
