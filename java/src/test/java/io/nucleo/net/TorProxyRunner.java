package io.nucleo.net;

import java.io.File;
import java.io.Serializable;

import java.nio.file.Paths;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TorProxyRunner {
    private static final Logger log = LoggerFactory.getLogger(TorProxyRunner.class);

    private static final int torProxy1LocalPort = 7071;
    private static final int torProxy2LocalPort = 7072;
    private static final int torProxy1HiddenServicePort = 8081;
    private static final int torProxy2HiddenServicePort = 8082;

    public static void main(String[] args) {
        new TorProxyRunner();
    }

    private final TorProxy torProxy1;
    private final TorProxy torProxy2;
    private boolean isTorProxy1SetupHiddenServiceCompleted, isTorProxy2SetupHiddenServiceCompleted,
            torProxy1DataSent, torProxy2DataSent, torProxy1DataArrived, torProxy2DataArrived;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TorProxyRunner() {
        String userDataDir = Paths.get(System.getProperty("user.home"), "Library", "Application Support",
                "TorProxyRunner").toString();
        torProxy1 = new TorProxy(torProxy1LocalPort, new File(userDataDir, "torProxy1"));
        torProxy1.addStartupCompletedListener(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                torProxy1StartupCompleted();
                return null;
            }
        });
        torProxy1.startTor();

        torProxy2 = new TorProxy(torProxy2LocalPort, new File(userDataDir, "torProxy2"));
        torProxy2.addStartupCompletedListener(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                torProxy2StartupCompleted();
                return null;
            }
        });
        torProxy2.startTor();
    }

    private void torProxy1StartupCompleted() throws Exception {
        log.debug("torProxy1StartupCompleted");
        torProxy1.setupHiddenService(torProxy1HiddenServicePort, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                torProxy1SetupHiddenServiceCompleted();
                return null;
            }
        });
    }

    private void torProxy2StartupCompleted() throws Exception {
        log.debug("torProxy2StartupCompleted");
        torProxy2.setupHiddenService(torProxy2HiddenServicePort, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                torProxy2SetupHiddenServiceCompleted();
                return null;
            }
        });

        torProxy1.sendData("test from torProxy1", torProxy2.getOnionAddress());
    }

    private void torProxy1SetupHiddenServiceCompleted() throws Exception {
        log.debug("torProxy1SetupHiddenServiceCompleted");
        torProxy1.addReceivingDataListener(new Consumer<Serializable>() {
            @Override
            public void accept(Serializable data) {
                torProxy1DataArrived = true;
                log.debug("torProxy1 received Data: " + data);
                checkAllDataSentAndArrived();
            }
        });
        isTorProxy1SetupHiddenServiceCompleted = true;
        checkAllHiddenServicesCompleted();
    }

    private void torProxy2SetupHiddenServiceCompleted() throws Exception {
        log.debug("torProxy2SetupHiddenServiceCompleted");
        torProxy2.addReceivingDataListener(new Consumer<Serializable>() {
            @Override
            public void accept(Serializable data) {
                torProxy2DataArrived = true;
                log.debug("torProxy2 received Data: " + data);
                checkAllDataSentAndArrived();
            }
        });
        isTorProxy2SetupHiddenServiceCompleted = true;
        checkAllHiddenServicesCompleted();
    }

    private void checkAllHiddenServicesCompleted() throws Exception {
        log.debug("checkAllHiddenServicesCompleted");
        if (isTorProxy1SetupHiddenServiceCompleted && isTorProxy2SetupHiddenServiceCompleted) {
            torProxy1.setupClientSocket(torProxy2.getOnionAddress(), torProxy2HiddenServicePort, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    torProxy1SetupClientSocketCompleted();
                    return null;
                }
            });
            torProxy2.setupClientSocket(torProxy1.getOnionAddress(), torProxy1HiddenServicePort, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    torProxy2SetupClientSocketCompleted();
                    return null;
                }
            });
        }
    }

    private void torProxy1SetupClientSocketCompleted() throws Exception {
        log.debug("torProxy1SetupClientSocketCompleted");
        torProxy1.sendData("test data from torProxy1 to torProxy2", torProxy2.getOnionAddress());
        torProxy1DataSent = true;
        checkAllDataSentAndArrived();
    }

    private void torProxy2SetupClientSocketCompleted() throws Exception {
        log.debug("torProxy2SetupClientSocketCompleted");
        torProxy2.sendData("test data from torProxy2 to torProxy1", torProxy1.getOnionAddress());
        torProxy2DataSent = true;
        checkAllDataSentAndArrived();
    }

    private void checkAllDataSentAndArrived() {
        if (torProxy1DataSent && torProxy2DataSent && torProxy1DataArrived && torProxy2DataArrived) {
            torProxy1.shutDown();
            torProxy2.shutDown();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //Runtime.getRuntime().exit(0);
        }
    }
}
