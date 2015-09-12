package io.nucleo.net.listeners;

import io.nucleo.net.Connection;

public interface ConnectionListener {
    void onConnected(Connection connection);

    void onDisconnected(Connection connection);
}
