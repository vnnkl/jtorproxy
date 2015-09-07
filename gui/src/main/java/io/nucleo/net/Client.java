package io.nucleo.net;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;

public class Client {
    private static final Logger log = LoggerFactory.getLogger(Client.class);

    private final ListeningExecutorService executorService;

    private final Socket socket;

    public Client(Socket socket) {
        this.socket = socket;
        executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    }

    public Serializable sendSync(final Serializable data) {
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
        } catch (IOException | ClassNotFoundException e) {
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

    public ListenableFuture<Serializable> sendAsync(final Serializable data) {
        return executorService.submit(() -> {
            DataInputStream dataInputStream = null;
            DataOutputStream dataOutputStream = null;
            try {
                // send data
                //log.debug("Send data: " + data);
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataOutputStream.write(SerializationUtils.serialize(data));
                dataOutputStream.flush();

                // await response
                dataInputStream = new DataInputStream(socket.getInputStream());
                ObjectInputStream objectInputStream = new ObjectInputStream(dataInputStream);
                final Serializable response = (Serializable) objectInputStream.readObject();
                //log.debug("Received response: " + response);
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
        });
    }
}
