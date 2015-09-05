package io.nucleo.storage.app;

import io.nucleo.net.ProcessDataHandler;
import io.nucleo.net.Server;
import io.nucleo.storage.StorageProcessor;
import java.io.IOException;
import java.net.ServerSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryStorageServer {
    private static final Logger log = LoggerFactory.getLogger(InMemoryStorageServer.class);
    private boolean stopped;
    private ServerSocket serverSocket;
    private Server server;

    private static int port = 8888;

    // optional args: port
    public static void main(String[] args) throws IOException {
        if (args.length > 0) port = Integer.parseInt(args[1]);

        new InMemoryStorageServer();
    }

    public InMemoryStorageServer() throws IOException {
        StorageProcessor storageProcessor = new StorageProcessor();
        ProcessDataHandler processDataHandler = new ProcessDataHandler(storageProcessor::process);
        serverSocket = new ServerSocket(port);
        server = new Server(serverSocket, processDataHandler);
        server.start();
        while (!stopped) {
        }
    }

    public void stop() {
        stopped = true;
        try {
            if (serverSocket != null) serverSocket.close();
            if (server != null) server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
