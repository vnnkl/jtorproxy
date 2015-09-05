package io.nucleo.net.tor;

import io.nucleo.net.exceptions.CommunicationException;
import io.nucleo.net.exceptions.SetupSocketException;
import io.nucleo.net.exceptions.SetupTorException;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.Serializable;

import java.net.UnknownHostException;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;
import org.apache.commons.lang3.SerializationUtils;

public class Proxy implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(Proxy.class);

    private final ListeningExecutorService executorService;

    private Socks5Proxy socks5Proxy;

    public Proxy(int proxyPort) throws SetupTorException {
        executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        int counter = 0;
        while (socks5Proxy == null && counter < 3) {
            try {
                socks5Proxy = new Socks5Proxy("localhost", proxyPort);
                socks5Proxy.resolveAddrLocally(false);
                log.debug("socks5Proxy created on localhost:port " + proxyPort);
            } catch (UnknownHostException e) {
                log.warn("Could not create socks5Proxy. We try again. " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            } finally {
                counter++;
            }
        }
        if (socks5Proxy == null) {
            throw new SetupTorException("Could not create socks5Proxy. We tried 3 times but keep failing.");
        }
    }

    @Override
    public void close() throws IOException {
        if (socks5Proxy != null) socks5Proxy.clone();
        executorService.shutdown();
    }

    public ListenableFuture<Serializable> sendAsyncAndCloseSocket(final String address, final Serializable data)
            throws CommunicationException, SetupSocketException {
        return executorService.submit(new Callable<Serializable>() {
            @Override
            public Serializable call() throws Exception {
                Thread.currentThread().setName("sendAsyncAndCloseSocket_" + address + "_" +
                        +new Random().nextInt(1000));
                int counter = 0;
                while (!Thread.interrupted() && counter < 10) {
                    try {
                        final String[] tokens = address.split(":");
                        log.debug("sendAsyncAndCloseSocket tokens " + tokens);
                        SocksSocket socksSocket = new SocksSocket(socks5Proxy, tokens[0], Integer.parseInt(tokens[1]));

                        DataInputStream dataInputStream = null;
                        DataOutputStream dataOutputStream = null;
                        try {
                            // send data
                            dataOutputStream = new DataOutputStream(socksSocket.getOutputStream());
                            dataOutputStream.write(SerializationUtils.serialize(data));
                            dataOutputStream.flush();
                            log.debug("data sent completed");

                            // await response
                            dataInputStream = new DataInputStream(socksSocket.getInputStream());
                            final Serializable response = SerializationUtils.deserialize(dataInputStream);
                            log.debug("response received");
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
                                socksSocket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (Throwable e1) {
                        e1.printStackTrace();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException var7) {
                        }
                        ++counter;
                    }
                }
                throw new SetupSocketException("Could not setup SocksSocket to " + address);
            }
        });
    }
}
