package io.nucleo.net;

import io.nucleo.net.exceptions.InvalidMessageException;
import io.nucleo.net.listeners.ConnectionListener;
import io.nucleo.net.listeners.MessageListener;
import io.nucleo.net.messages.Header;
import io.nucleo.net.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server extends Thread implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final ConnectionListener connectionListener;
    private final MessageListener messageListener;
    private final ConcurrentHashMap<String, IncomingConnection> incomingConnections = new ConcurrentHashMap<>();
    private boolean running;

    public Server(ServerSocket serverSocket, ConnectionListener connectionListener, MessageListener messageListener) {
        super("Server");
        this.serverSocket = serverSocket;
        this.connectionListener = connectionListener;
        this.messageListener = messageListener;

        executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void close() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        executorService.shutdown();
    }

    @Override
    public void run() {
        running = true;
        try {
            while (running) {
                final Socket socket = serverSocket.accept();
                log.info("Accepting Client on port " + socket.getLocalPort());
                executorService.submit(() -> accept(socket));
            }
        } catch (IOException e) {
            log.debug("Client socket closed");
        }
    }

    private Void accept(Socket socket) throws InvalidMessageException {
        ObjectInputStream objectInputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            socket.setSoTimeout(60 * 1000);
            objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectInputStream = new ObjectInputStream(socket.getInputStream());
            Object object = objectInputStream.readObject();
            if (object instanceof Message) {
                Message message = (Message) object;
                log.debug("accept Message: " + message.toString());
                Header header = message.header;
                switch (header.type) {
                    case Header.OPEN_CONNECTION:
                        if (message.payload instanceof String) {
                            String clientAddress = (String) message.payload;
                            IncomingConnection incomingConnection = new IncomingConnection(clientAddress,
                                    socket,
                                    objectOutputStream,
                                    objectInputStream,
                                    connectionListener,
                                    messageListener);
                            incomingConnections.put(clientAddress, incomingConnection);
                            incomingConnection.onOpenConnection();
                            incomingConnection.addConnectionListener(new ConnectionListener() {
                                @Override
                                public void onConnected(Connection connection) {

                                }

                                @Override
                                public void onDisconnected(Connection connection) {
                                    incomingConnections.remove(clientAddress);
                                }
                            });
                            log.debug("server sent CONFIRM_CONNECTION Message");
                            break;
                        } else {
                            throw new InvalidMessageException("Payload in OPEN_CONNECTION Message is not a String.");
                        }
                    default:
                        throw new InvalidMessageException("Message is not type of OPEN_CONNECTION.");
                }
            } else {
                throw new InvalidMessageException("Received object not of type Message");
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            try {
                if (objectOutputStream != null) objectOutputStream.close();
                if (objectInputStream != null) objectInputStream.close();
                if (socket != null) socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        return null;
    }

    public boolean isRunning() {
        return running;
    }
}
