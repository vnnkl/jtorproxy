package io.nucleo.net;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nucleo.net.proto.ContainerMessage;
import io.nucleo.net.proto.ControlMessage;
import io.nucleo.net.proto.Message;
import io.nucleo.net.proto.exceptions.ConnectionException;

public abstract class Connection implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(Connection.class);

  private final Socket                         socket;
  private final ObjectOutputStream             out;
  private final ObjectInputStream              in;
  private final LinkedList<ConnectionListener> listeners;
  private final String                         peer;
  private boolean                              running;
  private final AtomicBoolean                  available;
  private final AtomicBoolean                  listening;

  private final ExecutorService executorService;
  private final Listener        listener;

  private final AtomicBoolean heartbeating;

  public Connection(String peer, Socket socket) throws IOException {
    this(peer, socket, Node.prepareOOSForSocket(socket), new ObjectInputStream(socket.getInputStream()));
  }

  Connection(String peer, Socket socket, ObjectOutputStream out, ObjectInputStream in) {
    log.debug("Initiating new connection");
    this.available = new AtomicBoolean(false);
    this.peer = peer;
    this.socket = socket;
    this.in = in;
    this.out = out;
    running = true;
    listening = new AtomicBoolean(false);
    heartbeating = new AtomicBoolean(false);
    this.listeners = new LinkedList<>();
    this.listener = new Listener();
    executorService = Executors.newCachedThreadPool();
  }

  public abstract boolean isIncoming();

  public void addMessageListener(ConnectionListener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  protected void setConnectionListeners(Collection<ConnectionListener> listeners) {
    synchronized (listeners) {
      this.listeners.clear();
      this.listeners.addAll(listeners);
    }
  }

  public void removeMessageListener(ConnectionListener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  void sendMsg(Message msg) throws IOException {
    out.writeObject(msg);
    out.flush();
  }

  public void sendMessage(ContainerMessage msg) throws IOException {
    if (!available.get())
      throw new IOException("Connection is not yet available!");
    sendMsg(msg);
  }

  protected void onMessage(Message msg) throws IOException {
    log.debug("RXD: " + msg.toString());
    if (msg instanceof ContainerMessage) {
      synchronized (listeners) {
        for (ConnectionListener l : listeners)
          l.onMessage(this, (ContainerMessage) msg);
      }
    } else {
      if (msg instanceof ControlMessage) {
        switch ((ControlMessage) msg) {
          case DISCONNECT:
            close(true);
            break;
          case AVAILABLE:
            startHeartbeat();
            onReady();
            break;
          default:
            break;
        }
      }
    }
  }

  protected void onReady() {
    if (!available.getAndSet(true)) {
      synchronized (listeners) {
        for (ConnectionListener l : listeners) {
          l.onReady(this);
        }
      }
    }
  }

  protected abstract void onDisconnect();

  private void onDisconn() {
    onDisconnect();
    synchronized (listeners) {
      for (ConnectionListener l : listeners) {
        l.onDisconnect(this);
      }
    }
  }

  private void onTimeout() {
    synchronized (listeners) {
      for (ConnectionListener l : listeners) {
        l.onTimeout(this);
      }
    }
    try {
      close(false);
    } catch (IOException e1) {
    }
  }

  protected void onError(Exception e) {
    synchronized (listeners) {
      for (ConnectionListener l : listeners) {
        l.onError(this, new ConnectionException(e));
      }
    }
  }

  public void close() throws IOException {
    close(true);
  }

  private void close(boolean graceful) throws IOException {
    running = false;
    onDisconn();
    if (graceful) {
      try {
        sendMsg(ControlMessage.DISCONNECT);
      } catch (Exception e) {
        onError(e);
      }
    }
    out.close();
    in.close();
    socket.close();

  }

  public String getPeer() {
    return peer;
  }

  void startHeartbeat() {
    if (!heartbeating.getAndSet(true)) {
      log.debug("Starting Heartbeat");
      executorService.submit(new Runnable() {
        public void run() {
          try {
            Thread.sleep(30000);
            while (running) {
              try {
                log.debug("TX Heartbeat");
                sendMsg(ControlMessage.HEARTBEAT);
                Thread.sleep(30000);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          } catch (InterruptedException e) {
          }
        }
      });
    }
  }

  public void listen() throws ConnectionException {
    if (listening.getAndSet(true))
      throw new ConnectionException("Already Listening!");
    executorService.submit(listener);
  }

  private class Listener implements Runnable {
    @Override
    public void run() {
      while (running) {
        try {
          Message msg = (Message) in.readObject();
          onMessage(msg);
        } catch (ClassNotFoundException | IOException e) {
          if (e instanceof SocketTimeoutException) {
            onTimeout();
          } else {
            if (running) {
              onError(new ConnectionException(e));
              //TODO: Fault Tolerance?
              if (e instanceof EOFException) {
                try {
                  close(false);
                } catch (IOException e1) {
                  e1.printStackTrace();
                }
              }
            }
          }
        }
      }
    }
  }

}
