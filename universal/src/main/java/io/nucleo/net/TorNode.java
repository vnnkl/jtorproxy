package io.nucleo.net;

import com.msopentech.thali.toronionproxy.OnionProxyContext;
import com.msopentech.thali.toronionproxy.OnionProxyManager;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class TorNode<M extends OnionProxyManager, C extends OnionProxyContext> {

    static final String PROXY_LOCALHOST = "127.0.0.1";
    private static final int SLEEP_UNTIL_FIRST_HS_TEST = 30 * 1000;
    private static final int RETRY_SLEEP = 500;
    private static final int PROGRESSION = 200;
    private static final int TOTAL_SEC_PER_STARTUP = 4 * 60;
    private static final int TRIES_PER_STARTUP = 5;
    private static final int TRIES_PER_HS_STARTUP = 30;

    private final ExecutorService executorService;

    private static final Logger log = LoggerFactory.getLogger(TorNode.class);

    private final OnionProxyManager tor;
    private final Socks5Proxy proxy;

    public TorNode(M mgr) throws IOException {
        OnionProxyContext ctx = mgr.getOnionProxyContext();
        log.debug("Running Tornode with " + mgr.getClass().getSimpleName() + " and  " + ctx.getClass().getSimpleName());
        tor = initTor(mgr, ctx);
        executorService = Executors.newFixedThreadPool(2);
        int proxyPort = tor.getIPv4LocalHostSocksPort();
        log.info("TorSocks running on port " + proxyPort);
        this.proxy = setupSocksProxy(proxyPort);
    }

    private Socks5Proxy setupSocksProxy(int proxyPort) throws UnknownHostException {
        Socks5Proxy proxy = new Socks5Proxy(PROXY_LOCALHOST, proxyPort);
        proxy.resolveAddrLocally(false);
        return proxy;
    }

    public Socket connectToHiddenService(String onionUrl, int port) throws IOException {
        return connectToHiddenService(onionUrl, port, 5);
    }

    public Socket connectToHiddenService(String onionUrl, int port, int numTries) throws IOException {
        return connectToHiddenService(onionUrl, port, numTries, true);
    }

    private Socket connectToHiddenService(String onionUrl, int port, int numTries, boolean debug) throws IOException {
        long before = GregorianCalendar.getInstance().getTimeInMillis();
        for (int i = 0; i < numTries; ++i) {
            try {
                SocksSocket ssock = new SocksSocket(proxy, onionUrl, port);
                if (debug)
                    log.info("Took " + (GregorianCalendar.getInstance().getTimeInMillis() - before)
                            + " milliseconds to connect to " + onionUrl + ":" + port);
                ssock.setTcpNoDelay(true);
                return ssock;
            } catch (UnknownHostException exx) {
                try {
                    if (debug)
                        log.debug(
                                "Try " + (i + 1) + " connecting to " + onionUrl + ":" + port + " failed. retrying...");
                    Thread.sleep(RETRY_SLEEP);
                    continue;
                } catch (InterruptedException e) {
                }
            } catch (Exception e) {
                throw new IOException("Cannot connect to hidden service");
            }
        }
        throw new IOException("Cannot connect to hidden service");
    }

    public HiddenServiceDescriptor createHiddenService(final int localPort, final int servicePort,
            final HiddenServiceReadyListener listener) throws IOException {
        final long startTime = GregorianCalendar.getInstance().getTimeInMillis();
        final String hiddenServiceName = tor.publishHiddenService(servicePort, localPort);
        final HiddenServiceDescriptor hiddenServiceDescriptor = new HiddenServiceDescriptor(hiddenServiceName,
                localPort, servicePort);
        final CountDownLatch latch = new CountDownLatch(1);
        executorService.submit((new Runnable() {
            @Override
            public void run() {
                try {
                    latch.countDown();
                    hiddenServiceDescriptor.getServerSocket().accept().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }));
        try {
            latch.await();
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        executorService.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    // wait a random interval of 30-35 sec. as it takes about 35-40 sec. until HS is available
                    try {
                        Thread.sleep(SLEEP_UNTIL_FIRST_HS_TEST + new Random().nextInt(5000)); 
                    } catch (InterruptedException e1) {
                    }
                    tryConnectToHiddenService(servicePort, hiddenServiceName);
                    log.info("Took " + (GregorianCalendar.getInstance().getTimeInMillis() - startTime)
                            + " milliseconds to connect to publish " + hiddenServiceName + ":" + servicePort);
                    listener.onConnect(hiddenServiceDescriptor);
                } catch (IOException e) {
                    listener.onConnectionFailure(hiddenServiceDescriptor, e);
                }

            }
        });
        return hiddenServiceDescriptor; 

    }

    private void tryConnectToHiddenService(int servicePort, String hiddenServiceName) throws IOException {
        for (int i = 0; i < TRIES_PER_HS_STARTUP; ++i) {
            try {
                // Wait for tor 0.2.7  
                // System.out.println(tor.isHiddenServiceAvailable(hiddenServiceName));
                final Socket socket = connectToHiddenService(hiddenServiceName, servicePort, 1, false);
                socket.close();
            } catch (IOException e) {
                log.info("Hidden service " + hiddenServiceName + ":" + servicePort + " is not yet reachable");
                // wait a random interval of 0.5-3 sec. until retry, add a 200 ms increase at every retry
                // total time before final timeout is thrown is between 2.2 and 3.5 minutes (incl. initial wait time)
                // with RETRY_SLEEP=500, PROGRESSION=200 and TRIES_PER_HS_STARTUP=30 
                // minTotalTimeInMinutes=(RETRY_SLEEP*TRIES_PER_HS_STARTUP 
                //                  + PROGRESSION*TRIES_PER_HS_STARTUP*(TRIES_PER_HS_STARTUP-1)/2 
                //                  + SLEEP_UNTIL_FIRST_HS_TEST)/60000
                try {
                    Thread.sleep(RETRY_SLEEP + new Random().nextInt(2500) + i * PROGRESSION);
                } catch (InterruptedException e1) {
                }
                continue;
            }
            return;
        }
        throw new IOException("Could not publish Hidden Service!");
    }

    public HiddenServiceDescriptor createHiddenService(int port, HiddenServiceReadyListener listener)
            throws IOException {
        return createHiddenService(port, port, listener);
    }

    public void shutdown() throws IOException {
        tor.stop();
    }

    static <M extends OnionProxyManager, C extends OnionProxyContext> OnionProxyManager initTor(final M mgr, C ctx)
            throws IOException {

        log.debug("Trying to start tor in directory {}", mgr.getWorkingDirectory());

        try {
            if (!mgr.startWithRepeat(TOTAL_SEC_PER_STARTUP, TRIES_PER_STARTUP)) {
                throw new IOException("Could not Start Tor. Is another instance already running?");
            } else {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    public void run() {
                        try {
                            mgr.stop();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        return mgr;
    }
}
