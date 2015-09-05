package io.nucleo.net;

import java.io.Serializable;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerHandler {
    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    private final ExecutorService executorService;
    private final Function<Serializable, Serializable> processor;

    public ServerHandler(Function<Serializable, Serializable> processor) {
        this.processor = processor;
        executorService = Executors.newCachedThreadPool();
    }

    public ServerHandler() {
        this(null);
    }

    public Serializable process(final Serializable data) {
        return processData(data);
    }

    public Future<Serializable> processAsync(final Serializable data) {
        return executorService.submit(new Callable<Serializable>() {
            @Override
            public Serializable call() throws Exception {
                Thread.currentThread().setName("ServerHandler.process " + new Random().nextInt(1000));
                return processData(data);
            }
        });
    }

    private Serializable processData(Serializable data) {
        if (processor != null)
            return processor.apply(data);
        else
            return null;
    }
}
