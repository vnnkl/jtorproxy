package io.nucleo.storage.app;

import io.nucleo.net.LocalHostNetwork;
import io.nucleo.net.Network;
import io.nucleo.net.ProcessDataHandler;
import io.nucleo.net.TorNetwork;
import io.nucleo.storage.StorageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class InMemoryStorageServer {
    private static final Logger log = LoggerFactory.getLogger(InMemoryStorageServer.class);
    private Network network;
    private boolean stopped;

    private static int port = 8888;
    private static boolean useTor = true;

    // optional args: port
    public static void main(String[] args) throws IOException {
        if (args.length > 0) port = Integer.parseInt(args[0]);
        if (args.length > 1) useTor = Boolean.parseBoolean(args[1]);

        new InMemoryStorageServer();
    }

    public InMemoryStorageServer() throws IOException {
        StorageProcessor storageProcessor = new StorageProcessor();
        ProcessDataHandler processDataHandler = new ProcessDataHandler(storageProcessor::process);

        network = useTor ? new TorNetwork() : new LocalHostNetwork();
        network.start("InMemoryStorageServer");
        network.startServer(port, processDataHandler);

        while (!stopped) {
        }
        stop();
    }

    public void stop() {
        stopped = true;
        network.shutDown();
    }
}
