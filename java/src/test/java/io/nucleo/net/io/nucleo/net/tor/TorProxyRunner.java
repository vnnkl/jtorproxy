package io.nucleo.net.io.nucleo.net.tor;

import io.nucleo.net.ServerHandler;
import io.nucleo.net.tor.Proxy;
import io.nucleo.net.tor.SetupTorManager;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.Serializable;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TorProxyRunner {
    private static final Logger log = LoggerFactory.getLogger(TorProxyRunner.class);

    private final int localPort;
    private final int hiddenServicePort;
    private final SetupTorManager setupTorManager;

    private Proxy proxy;
    private Consumer<String> onCompleteCallBack;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TorProxyRunner(int localPort, int hiddenServicePort, File directory) throws ExecutionException,
            InterruptedException {
        this.localPort = localPort;
        this.hiddenServicePort = hiddenServicePort;
        setupTorManager = new SetupTorManager(directory);
    }

    public void start(Consumer<String> onCompleteCallBack) throws ExecutionException, InterruptedException {
        log.debug("start");
        this.onCompleteCallBack = onCompleteCallBack;
        ListenableFuture<Proxy> future = setupTorManager.start();
        Futures.addCallback(future, new FutureCallback<Proxy>() {
            @Override
            public void onSuccess(Proxy proxy) {
                startupCompleted(proxy);
            }

            @Override
            public void onFailure(Throwable throwable) {
            }
        });
    }

    private void startupCompleted(Proxy proxy) {
        log.debug("startupCompleted");
        this.proxy = proxy;
        try {
            ListenableFuture<String> future = setupTorManager.setupHiddenService(hiddenServicePort,
                    localPort, new ServerHandler());
            Futures.addCallback(future, new FutureCallback<String>() {
                @Override
                public void onSuccess(String onionAddress) {
                    onCompleteCallBack.accept(onionAddress);
                }

                @Override
                public void onFailure(Throwable throwable) {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendData(final String address, String data) {
        try {
            log.debug("sendData data = " + data);
            ListenableFuture<Serializable> future = proxy.sendAsyncAndCloseSocket(address, data);
            Futures.addCallback(future, new FutureCallback<Serializable>() {

                @Override
                public void onSuccess(Serializable response) {
                    log.debug("sendData result = " + response);
                }

                @Override
                public void onFailure(Throwable throwable) {

                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
