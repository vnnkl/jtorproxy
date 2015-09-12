package io.nucleo.msg;

import io.nucleo.net.Client;
import io.nucleo.net.Connection;
import io.nucleo.net.OutgoingConnection;
import io.nucleo.net.Server;
import io.nucleo.net.exceptions.InvalidMessageException;
import io.nucleo.net.listeners.ConnectionListener;
import io.nucleo.storage.Storage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessagingTest {
    private static final Logger log = LoggerFactory.getLogger(MessagingTest.class);
    private Server server;
    private Client client;
    private CountDownLatch serverStartCountDownLatch;
    private String serverAddress;
    private CountDownLatch clientConnectCountDownLatch;
    private Connection serverConnection;
    private Storage storage;

    @Before
    public void setup() throws IOException {
        int serverPort = 2222;
        serverAddress = "localhost:" + serverPort;
        ServerSocket serverSocket = new ServerSocket(serverPort);
        server = new Server(serverSocket, new ConnectionListener() {
            @Override
            public void onConnected(Connection connection) {
                log.debug("server.onConnected " + connection);
                serverConnection = connection;
            }

            @Override
            public void onDisconnected(Connection connection) {
                log.debug("client.onDisconnected " + connection);
            }

        }, new MessagingHandler());

        client = new Client("localhost:5555");
        storage = new Storage(client);
    }

    @After
    public void tearDown() throws IOException {
        server.close();
        client.close();
    }

    @Test
    public void testStartServer() throws IOException, InterruptedException, InvalidMessageException {
        log.debug("start server");
        server.start();
        Thread.sleep(100);
        assertTrue(server.isRunning());
        log.debug("server is running");

        log.debug("client connect to server at: " + serverAddress);
        OutgoingConnection outgoingConnection = client.openConnection(serverAddress, new ConnectionListener() {
            @Override
            public void onConnected(Connection connection) {
                log.debug("client.onConnected " + connection);
            }

            @Override
            public void onDisconnected(Connection connection) {
                log.debug("client.onDisconnected " + connection);
            }
        }, (connection, message) -> log.debug("client.onMessage " + message));
        Thread.sleep(100);
        assertTrue(outgoingConnection.isConnected());
        assertTrue(serverConnection.isConnected());

        //TODO

        outgoingConnection.disconnect();
        Thread.sleep(100);
        assertFalse(outgoingConnection.isConnected());
        assertFalse(serverConnection.isConnected());
        assertTrue(server.isRunning());

        server.close();
        assertFalse(server.isRunning());
    }
}
