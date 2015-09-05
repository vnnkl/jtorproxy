package io.nucleo.net.node;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import io.nucleo.net.proto.ContainerMessage;
import io.nucleo.net.proto.Message;
import io.nucleo.net.proto.NetMessage;

public abstract class Connection implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(Connection.class);

  private final Socket                      socket;
  private final ObjectOutputStream          out;
  private final ObjectInputStream           in;
  private final LinkedList<MessageListener> listeners;
  private final String                      peer;
  private boolean                           running;
  protected boolean                           available;

  private final ListeningExecutorService executorService;

  public Connection(String peer, Socket socket) throws IOException {
    this(peer, socket, Node.prepareOOSForSocket(socket), new ObjectInputStream(socket.getInputStream()));
  }

  Connection(String peer, Socket socket, ObjectOutputStream out, ObjectInputStream in) {
    log.info("Initiating new connection");
    this.available = false;
    this.peer = peer;
    this.socket = socket;
    this.in = in;
    this.out = out;
    running = true;
    this.listeners = new LinkedList<>();
    executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
  }

  public void addMessageListener(MessageListener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  public void removeMessageListener(MessageListener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  void sendMsg(Message msg) throws IOException {
    out.writeObject(msg);
    out.flush();
  }

  public void sendMessage(ContainerMessage msg) throws IOException {
    if (!available)
      throw new IOException("Connection is not yet available!");
    sendMsg(msg);
  }

  protected void onMessage(Message msg) throws IOException {
    log.debug("RXD: " + msg.toString());
    if (msg instanceof ContainerMessage) {
      synchronized (listeners) {
        for (MessageListener l : listeners)
          l.onMessage(this, (ContainerMessage) msg);
      }
    } else {
      if (msg instanceof NetMessage) {
        switch ((NetMessage) msg) {
          case DISCONNECT:
            onDisconnect();
            out.close();
            in.close();
            socket.close();
            break;
          case AVAILABLE:
            available = true;
            startHeartbeat();
            break;
          default:
            break;
        }
      }
    }
  }

  public void onDisconnect() {
    running = false;
    synchronized (listeners) {
      for (MessageListener l : listeners) {
        l.onDisconnect(this);
      }
    }
  }

  public void close() throws IOException {
    onDisconnect();
    sendMsg(NetMessage.DISCONNECT);
    out.close();
    in.close();
    socket.close();
  }

  public String getPeer() {
    return peer;
  }

  void startHeartbeat() {
    executorService.submit(() -> {
      Thread.sleep(30000);
      while (running) {
        log.debug("TX Heartbeat");
        sendMsg(NetMessage.HEARTBEAT);
        Thread.sleep(30000);
      }
      return null;
    });
  }

  void listen() {
    executorService.submit(() -> {
      while (running) {
        try {
          Message msg = (Message) in.readObject();
          onMessage(msg);

        } catch (SocketTimeoutException e) {
          synchronized (listeners) {
            for (MessageListener l : listeners) {
              l.onTimeout(this);
            }
          }
          running = false;
          out.close();
          in.close();
          socket.close();
          onDisconnect();
        }
      }
      return null;
    });
  }
}
