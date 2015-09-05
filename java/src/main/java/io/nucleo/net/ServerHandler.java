package io.nucleo.net;

import java.io.Serializable;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerHandler {
    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);
    private final ExecutorService executorService;

    public ServerHandler() {
        executorService = Executors.newCachedThreadPool();
    }

    public Future<Serializable> process(final Serializable data) {
        return executorService.submit(new Callable<Serializable>() {
            @Override
            public Serializable call() throws Exception {
                Thread.currentThread().setName("ServerHandler.process " + new Random().nextInt(1000));
                return processData(data);
            }
        });
    }

    private Serializable processData(Serializable data) {
        return "dummy result";
    }
}
