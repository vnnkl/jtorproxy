package io.nucleo.net;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.concurrent.Executors;

public abstract class Connection implements Closeable {
    private Logger log;

    private final Socket socket;
    private final ObjectOutputStream objectOutputStream;
    private final ObjectInputStream objectInputStream;
    private final LinkedList<MessageListener> messageListeners = new LinkedList<>();
    protected final LinkedList<ConnectionListener> connectionListeners = new LinkedList<>();
    protected final String peerAddress;
    protected boolean running;

    protected final ListeningExecutorService executorService;
    protected boolean connected;

    public Connection(String peerAddress, Socket socket, ConnectionListener connectionListener,
                      MessageListener messageListener) throws IOException {
        this(peerAddress,
                socket,
                new ObjectOutputStream(socket.getOutputStream()),
                new ObjectInputStream(socket.getInputStream()),
                connectionListener,
                messageListener);
    }

    Connection(String peerAddress,
               Socket socket,
               ObjectOutputStream objectOutputStream,
               ObjectInputStream objectInputStream,
               ConnectionListener connectionListener,
               MessageListener messageListener) throws IOException {
        this.peerAddress = peerAddress;
        this.socket = socket;
        this.objectInputStream = objectInputStream;
        this.objectOutputStream = objectOutputStream;

        log = LoggerFactory.getLogger(this.getClass());

        addConnectionListener(connectionListener);
        addMessageListener(messageListener);

        running = true;
        executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        listenForIncomingMessages();
    }

    public void addConnectionListener(ConnectionListener listener) {
        synchronized (connectionListeners) {
            connectionListeners.add(listener);
        }
    }

    public void removeConnectionListener(ConnectionListener listener) {
        synchronized (connectionListeners) {
            connectionListeners.remove(listener);
        }
    }

    public void addMessageListener(MessageListener listener) {
        synchronized (messageListeners) {
            messageListeners.add(listener);
        }
    }

    public void removeMessageListener(MessageListener listener) {
        synchronized (messageListeners) {
            messageListeners.remove(listener);
        }
    }

    public void sendMessage(Message message) throws IOException {
        log.debug("sendMessage: " + message.toString());
        objectOutputStream.writeObject(message);
        objectOutputStream.flush();
    }

    protected void processIncomingMessage(Message message) throws InvalidMessageException, IOException {
        log.debug("onMessage: " + message.toString());
        Header header = message.header;
        switch (header.type) {
            case Header.CLOSE_CONNECTION:
                synchronized (connectionListeners) {
                    for (ConnectionListener connectionListener : connectionListeners)
                        connectionListener.onDisconnected(this);
                }
                close();
                break;
            case Header.HEART_BEAT:
                break;
            default:
                throw new InvalidMessageException("Message with unsupported header arrived.");
        }
    }

    protected void notifyMessageListeners(Message message) throws IOException, InvalidMessageException {
        synchronized (messageListeners) {
            for (MessageListener messageListener : messageListeners)
                messageListener.onMessage(this, message);
        }
    }

    public void close() {
        log.debug("close");
        running = false;
        connected = false;
        try {
            objectOutputStream.close();
            objectInputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        synchronized (connectionListeners) {
            for (ConnectionListener connectionListener : connectionListeners) {
                connectionListener.onDisconnected(this);
            }
        }
    }

    public void disconnect() throws IOException {
        log.debug("disconnect");
        sendMessage(new Message(new Header(Header.CLOSE_CONNECTION)));
        close();
    }

    public boolean isConnected() {
        return connected;
    }

    public String getPeerAddress() {
        return peerAddress;
    }

    protected void startHeartbeat() {
        log.debug("startHeartbeat");
        executorService.submit(() -> {
            while (running) {
                Thread.sleep(30 * 1000);
                if (running) sendMessage(new Message(new Header(Header.HEART_BEAT)));
            }
            return null;
        });
    }

    protected void listenForIncomingMessages() {
        executorService.submit(() -> {
            while (running) {
                try {
                    processIncomingMessage((Message) objectInputStream.readObject());
                } catch (SocketTimeoutException e) {
                    close();
                    synchronized (connectionListeners) {
                        for (ConnectionListener connectionListener : connectionListeners) {
                            connectionListener.onDisconnected(this);
                        }
                    }
                }
            }
            return null;
        });
    }
}
