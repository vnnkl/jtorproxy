package io.nucleo.net;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(Client.class);

    private final ListeningExecutorService executorService;

    private final Socket socket;

    public Client(Socket socket) {
        this.socket = socket;
        executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    }

    @Override
    public void close() throws IOException {
        executorService.shutdown();
    }

    public ListenableFuture<Serializable> sendAsyncAndCloseSocket(final Serializable data) {
        return executorService.submit(new Callable<Serializable>() {
            @Override
            public Serializable call() throws Exception {
                DataInputStream dataInputStream = null;
                DataOutputStream dataOutputStream = null;
                try {
                    // send data
                    log.debug("Send data: " + data);
                    dataOutputStream = new DataOutputStream(socket.getOutputStream());
                    dataOutputStream.write(SerializationUtils.serialize(data));
                    dataOutputStream.flush();

                    // await response
                    dataInputStream = new DataInputStream(socket.getInputStream());
                    ObjectInputStream objectInputStream = new ObjectInputStream(dataInputStream);
                    final Serializable response = (Serializable) objectInputStream.readObject();
                    log.debug("Received response: " + response);
                    return response;
                } catch (EOFException e) {
                    e.printStackTrace();
                    log.debug("EOFException: " + String.valueOf(data));
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
}
