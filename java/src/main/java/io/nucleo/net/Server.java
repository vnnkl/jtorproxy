package io.nucleo.net;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.Serializable;

import java.net.ServerSocket;
import java.net.Socket;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang3.SerializationUtils;

public class Server extends Thread implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final int serverPort;
    private final ServerHandler serverHandler;
    private final ExecutorService executorService;

    private boolean stopped;

    public Server(int serverPort, ServerHandler serverHandler) {
        super("Server");

        this.serverPort = serverPort;
        this.serverHandler = serverHandler;
        executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void close() throws IOException {
        stopped = true;
        executorService.shutdown();
    }

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(serverPort);
            while (!stopped) {
                final Socket socket = serverSocket.accept();
                executorService.submit(new Callable<Void>() {
                    public Void call() throws Exception {
                        Thread.currentThread().setName("ServerSocket_" + serverPort + "_" +
                                +new Random().nextInt(1000));
                        DataInputStream dataInputStream = null;
                        DataOutputStream dataOutputStream = null;
                        try {
                            // get incoming data
                            dataInputStream = new DataInputStream(socket.getInputStream());
                            final Serializable receivedData = SerializationUtils.deserialize(dataInputStream);

                            // process received data
                            Future<Serializable> future = serverHandler.process(receivedData);
                            Serializable responseData = future.get();

                            // send response data
                            dataOutputStream = new DataOutputStream(socket.getOutputStream());
                            dataOutputStream.write(SerializationUtils.serialize(responseData));
                            dataOutputStream.flush();
                        } catch (EOFException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                if (dataOutputStream != null) dataOutputStream.close();
                                if (dataInputStream != null) dataInputStream.close();
                                socket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        return null;
                    }
                });
            }
        } catch (IOException var3) {
            var3.printStackTrace();
        }
    }
}
