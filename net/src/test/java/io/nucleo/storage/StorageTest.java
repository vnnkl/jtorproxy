package io.nucleo.storage;

import io.nucleo.net.Client;
import io.nucleo.net.Connection;
import io.nucleo.net.OutgoingConnection;
import io.nucleo.net.Server;
import io.nucleo.net.exceptions.InvalidMessageException;
import io.nucleo.net.listeners.ConnectionListener;
import io.nucleo.net.messages.ProtectedData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class StorageTest {
    private static final Logger log = LoggerFactory.getLogger(StorageTest.class);
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

        }, new StorageMessageHandler());

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

        CountDownLatch latch = new CountDownLatch(11);
        Map<String, Object> results = new HashMap<>();
        List<Object> subscriberResults = new ArrayList<>();
        storage.subscribeToMap("setKey1", new Consumer<Boolean>() {
            @Override
            public void accept(Boolean result) {
                log.debug("subscribeToMap result=" + result);
                latch.countDown();
                results.put("subscribeToMap", result);
            }
        }, new Consumer<Serializable>() {
            @Override
            public void accept(Serializable result) {
                log.debug("getFullSet result=" + result);
                // should be called 3 times
                latch.countDown();
                results.put("getFullSet", result);
                subscriberResults.add(result);
            }
        });

        storage.addToMap("setKey1", "key1", null, "val1", new Consumer<Boolean>() {
            @Override
            public void accept(Boolean result) {
                log.debug("addToSet result=" + result);
                latch.countDown();
                results.put("addToMap", result);
            }
        });

        // try to overwrite existing entry, not allowed
        storage.addToMap("setKey1", "key1", null, "val_new", new Consumer<Boolean>() {
            @Override
            public void accept(Boolean result) {
                log.debug("addToSet2 result=" + result);
                latch.countDown();
                results.put("addToMap2", result);
            }
        });

        storage.addToMap("setKey1", "key2", null, "val2", new Consumer<Boolean>() {
            @Override
            public void accept(Boolean result) {
                log.debug("addToSet3 result=" + result);
                latch.countDown();
                results.put("addToMap3", result);
            }
        });

        storage.removeFromMap("setKey1", "key1", null, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean result) {
                log.debug("removeFromMap result=" + result);
                latch.countDown();
                results.put("removeFromMap", result);
            }
        });

        Thread.sleep(200);
        storage.unSubscribeToMap("setKey1", new Consumer<Boolean>() {
            @Override
            public void accept(Boolean result) {
                log.debug("unSubscribeToMap result=" + result);
                latch.countDown();
                results.put("unSubscribeToMap", result);
            }
        });
        storage.addToMap("setKey1", "key3", null, "val3", new Consumer<Boolean>() {
            @Override
            public void accept(Boolean result) {
                log.debug("addToSet4 result=" + result);
                latch.countDown();
                results.put("addToMap4", result);
            }
        });
        storage.getFullSet("setKey1", new Consumer<Map<String, Serializable>>() {
            @Override
            public void accept(Map<String, Serializable> result) {
                log.debug("getFullSet result=" + result);
                latch.countDown();
                results.put("getFullSet", result);
            }
        });
        Thread.sleep(500);
        latch.await();
        assertTrue((Boolean) results.get("subscribeToMap"));
        assertTrue((Boolean) results.get("addToMap"));
        assertFalse((Boolean) results.get("addToMap2"));
        assertTrue((Boolean) results.get("addToMap3"));
        assertTrue((Boolean) results.get("removeFromMap"));
        assertTrue((Boolean) results.get("addToMap4"));
        assertEquals(3, subscriberResults.size());
        assertEquals(((Map<String, ProtectedData>) results.get("getFullSet")).get("key2").data, "val2");


        outgoingConnection.disconnect();
        Thread.sleep(100);
        assertFalse(outgoingConnection.isConnected());
        assertFalse(serverConnection.isConnected());
        assertTrue(server.isRunning());

        server.close();
        assertFalse(server.isRunning());
    }
}
