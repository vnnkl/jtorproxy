package io.nucleo.net.tor;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import io.nucleo.net.Client;
import io.nucleo.net.Server;
import io.nucleo.net.ServerHandler;
import io.nucleo.net.exceptions.CommunicationException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import oi.nucleo.net.HiddenServiceDescriptor;
import oi.nucleo.net.TorNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TorNodeTest2 {
    private static final Logger log = LoggerFactory.getLogger(TorNodeTest2.class);

    private static final int hsPort = 55555;
    private static TorNode<JavaOnionProxyManager, JavaOnionProxyContext> node;
    private static boolean stopped;

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException, 
            CommunicationException {
        File dir = new File("tor-test");
        dir.mkdirs();
        log.debug("Starting up tor");
        node = new TorNode<JavaOnionProxyManager, JavaOnionProxyContext>(dir) {
        };

        log.debug("Create hidden service");
        final HiddenServiceDescriptor hiddenService = node.createHiddenService(hsPort);

        log.debug("Setup server");
        ServerHandler serverHandler = new ServerHandler();
        final Server server = new Server(hiddenService.getServerSocket(), serverHandler);
        server.start();
        
        Thread.sleep(1000);

        log.debug("Connect to hidden service");
        Socket socket = node.connectToHiddenService(hiddenService.getOnionUrl(), hiddenService.getservicePort());

        log.debug("Setup client and send data");
        final Client client = new Client(socket);
        ListenableFuture<Serializable> future = client.sendAsyncAndCloseSocket("test 1");
        Futures.addCallback(future, new FutureCallback<Serializable>() {
            @Override
            public void onSuccess(Serializable serializable) {
                log.debug("Client received response data: " + serializable);
                try {
                    log.debug("shutdown");
                    client.close();
                    server.close();
                    node.shutdown();
                    stopped = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
            }
        });

        while (!stopped) {
        }
    }
}
