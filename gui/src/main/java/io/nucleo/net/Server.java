package io.nucleo.net;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server extends Thread implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final ServerSocket serverSocket;
    private final ProcessDataHandler processDataHandler;
    private final ExecutorService executorService;

    private boolean stopped;

    public Server(ServerSocket serverSocket) {
        this(serverSocket, null);
    }

    public Server(ServerSocket serverSocket, ProcessDataHandler processDataHandler) {
        super("Server");
        this.serverSocket = serverSocket;
        this.processDataHandler = processDataHandler;
        executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void close() throws IOException {
        stopped = true;
        serverSocket.close();
        executorService.shutdown();
    }

    @Override
    public void run() {
        try {
            while (!stopped) {
                final Socket socket = serverSocket.accept();
                log.info("Accepting Client on port " + socket.getLocalPort());

                executorService.submit(() -> {
                    DataInputStream dataInputStream = null;
                    DataOutputStream dataOutputStream = null;
                    ObjectInputStream objectInputStream = null;
                    try {
                        // get incoming data
                        dataInputStream = new DataInputStream(socket.getInputStream());
                        objectInputStream = new ObjectInputStream(dataInputStream);
                        final Serializable receivedData = (Serializable) objectInputStream.readObject();

                        log.debug("received data: " + receivedData);

                        if (processDataHandler != null) {
                            // process received data
                            Serializable responseData = processDataHandler.process(receivedData);
                            log.debug("send response: " + responseData);

                            // send response data
                            dataOutputStream = new DataOutputStream(socket.getOutputStream());
                            dataOutputStream.write(SerializationUtils.serialize(responseData));
                            dataOutputStream.flush();
                        }
                    } catch (EOFException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            if (objectInputStream != null) objectInputStream.close();
                            if (dataOutputStream != null) dataOutputStream.close();
                            if (dataInputStream != null) dataInputStream.close();
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                });
            }
        } catch (IOException var3) {
            var3.printStackTrace();
        }
    }
}
