package io.nucleo.net.io.nucleo.net.tor;

import java.io.File;

import java.nio.file.Paths;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TorTwoClientsRunner {
    private static final Logger log = LoggerFactory.getLogger(TorTwoClientsRunner.class);

    private final TorProxyRunner torProxyRunner1;
    private final TorProxyRunner torProxyRunner2;

    private String onionAddress1;
    private String onionAddress2;

    public static void main(String[] args) {
        try {
            new TorTwoClientsRunner();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public TorTwoClientsRunner() throws ExecutionException, InterruptedException {
        String userDataDir = Paths.get(System.getProperty("user.home"), "Library", "Application Support",
                "TorProxyRunner").toString();

        torProxyRunner1 = new TorProxyRunner(8001, 7001, new File(userDataDir, "torProxy1"));
        torProxyRunner1.start(new Consumer<String>() {
            @Override
            public void accept(String onionAddress) {
                setupComplete1(onionAddress);
            }
        });
        torProxyRunner2 = new TorProxyRunner(8002, 7002, new File(userDataDir, "torProxy2"));
        torProxyRunner2.start(new Consumer<String>() {
            @Override
            public void accept(String onionAddress) {
                setupComplete2(onionAddress);
            }
        });
        while (true) {
        }
    }

    private void setupComplete1(String onionAddress) {
        log.debug("setupComplete1");
        onionAddress1 = onionAddress;
        sendIfBothCompleted();
    }

    private void setupComplete2(String onionAddress) {
        log.debug("setupComplete2");
        onionAddress2 = onionAddress;
        sendIfBothCompleted();
    }

    private void sendIfBothCompleted() {
        if (onionAddress1 != null && onionAddress2 != null) {
            log.debug("send Both Completed");
            torProxyRunner1.sendData(onionAddress2, "test data 1");
            torProxyRunner2.sendData(onionAddress1, "test data 2");
        }
    }
}
