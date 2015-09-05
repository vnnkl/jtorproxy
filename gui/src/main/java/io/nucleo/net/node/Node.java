package io.nucleo.net.node;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nucleo.net.HiddenServiceDescriptor;
import io.nucleo.net.TorNode;
import io.nucleo.net.proto.ContainerMessage;
import io.nucleo.net.proto.HELOMessage;
import io.nucleo.net.proto.IDMessage;
import io.nucleo.net.proto.Message;
import io.nucleo.net.proto.NetMessage;
import io.nucleo.net.proto.exceptions.ProtocolViolationException;

public class Node {

  /**
   * Use this whenever to flush the socket header over the socket!
   * 
   * @param socket
   *          the socket to construct an objectOutputStream from
   * @return the outputstream from the socket
   * @throws IOException
   *           in case something goes wrong, duh!
   */
  static ObjectOutputStream prepareOOSForSocket(Socket socket) throws IOException {
    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

    out.flush();
    return out;
  }

  private static final Logger log = LoggerFactory.getLogger(Node.class);

  private final HiddenServiceDescriptor descriptor;

  private final ConcurrentHashMap<String, Connection> connections;

  private final TorNode tor;

  public Node(HiddenServiceDescriptor descriptor, TorNode tor) {
    this.connections = new ConcurrentHashMap<>();
    this.descriptor = descriptor;
    this.tor = tor;

  }

  public String getLocalName() {
    return descriptor.getFullAddress();
  }

  public Connection connect(String peer, MessageListener listener) throws NumberFormatException, IOException {
    if(peer.equals(descriptor.getOnionUrl()))
      throw new IOException("If you find yourself talking to yourself too often, you shoudl really seek help!");
    final String[] split = peer.split(Pattern.quote(":"));
    final Socket sock = tor.connectToHiddenService(split[0], Integer.parseInt(split[1]));
    sock.setSoTimeout(60000);
    return new OutgoingConnection(peer, sock, listener);
  }

  public Server startServer(ConnectionListener con) {
    final Server server = new Server(descriptor.getServerSocket(), con);
    server.start();
    return server;
  }

  public List<Connection> getConnections() {
    return new LinkedList<Connection>(connections.values());
  }

  public class Server extends Thread implements Closeable {

    private boolean stopped;

    private final ServerSocket    serverSocket;
    private final ExecutorService executorService;

    private final ConnectionListener connListener;

    private Server(ServerSocket serverSocket, ConnectionListener listener) {
      super("Server");
      this.serverSocket = descriptor.getServerSocket();
      this.connListener = listener;

      executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void close() throws IOException {
      stopped = true;
      serverSocket.close();
      executorService.shutdown();
    }

    @Override
    public void run() {
      try {
        while (!stopped) {
          final Socket socket = serverSocket.accept();
          log.info("Accepting Client on port " + socket.getLocalPort());

          executorService.submit(() -> accept(socket));
        }
      } catch (IOException var3) {
        var3.printStackTrace();
      }
    }

    private Void accept(Socket socket) {
      {
        try {
          socket.setSoTimeout(60 * 1000);
        } catch (SocketException e2) {

          e2.printStackTrace();
          try {
            socket.close();
          } catch (IOException e) {
          }
          return null;
        }

        ObjectInputStream objectInputStream = null;
        ObjectOutputStream out = null;

        // get incoming data
        try {
          out = prepareOOSForSocket(socket);
          objectInputStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
          e.printStackTrace();
          try {
            socket.close();
          } catch (IOException e1) {
          }
          return null;
        }

        String peer = null;
        try {
          log.info("Waiting for HELO");
          final Message helo = (Message) objectInputStream.readObject();
          if (helo instanceof HELOMessage) {
            log.info("Got HELO from " + ((HELOMessage) helo).getPeer());
            if (!verifyIdentity((HELOMessage) helo, objectInputStream)) {
              log.info("verification failed");
              out.writeObject(NetMessage.HANDSHAKE_FAILED);
              out.writeObject(NetMessage.DISCONNECT);
              out.close();
              objectInputStream.close();
              socket.close();
              return null;
            }
            peer = ((HELOMessage) helo).getPeer();
            log.info("Verification of " + peer + " successful");
          } else if (helo instanceof IDMessage) {
            log.info("got IDMessage from " + ((IDMessage) helo).getPeer());
            final Connection client = connections.get(((IDMessage) helo).getPeer());
            if (client != null) {
              log.info("Got preexisting connection for " + ((IDMessage) helo).getPeer());
              client.sendMsg(((IDMessage) helo).reply());
              log.info("Sent response for challenge");
            }

            objectInputStream.close();
            socket.close();
            log.info("Closed socket for identification");
            return null;

          } else
            throw new ClassNotFoundException("First Message was neither HELO, nor ID");
        } catch (ClassNotFoundException e) {
          new ProtocolViolationException(e);
        } catch (IOException e) {
          try {
            objectInputStream.close();
            socket.close();
          } catch (IOException e1) {
          }
          return null;
        }
        // Here we go
        log.info("Incoming Connection ready!");
        IncomingConnection incomingConnection;
        try {
          //TODO: listeners are only added afterwards, so messages can be lost!
          incomingConnection = new IncomingConnection(peer, socket, out, objectInputStream);
          connListener.onConnect(incomingConnection);
          

        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      return null;
    }
  }

  private boolean verifyIdentity(HELOMessage helo, ObjectInputStream in) throws IOException {
    log.info("Verifying HELO msg");
    final Socket sock = tor.connectToHiddenService(helo.getOnionUrl(), helo.getPort());
    log.info("Connected to advertised client " + helo.getPeer());
    ObjectOutputStream out = prepareOOSForSocket(sock);
    final IDMessage challenge = new IDMessage(descriptor);
    out.writeObject(challenge);
    log.info("Sent IDMessage to");
    out.flush();
    out.close();
    sock.close();
    log.info("Closed socket after sending IDMessage");
    try {
      log.info("Waiting for response of challenge");
      IDMessage response = (IDMessage) in.readObject();
      log.info("Got response for challenge");
      final boolean veryfied = challenge.verify(response);
      log.info("Response verifyed correctly!");
      return veryfied;
    } catch (ClassNotFoundException e) {
      new ProtocolViolationException(e).printStackTrace();
    }
    return false;
  }

  private class IncomingConnection extends Connection {
    private IncomingConnection(String peer, Socket socket, ObjectOutputStream out, ObjectInputStream in)
        throws IOException {
      super(peer, socket, out, in);
      available=true;
      connections.put(peer, this);
      sendMsg(NetMessage.AVAILABLE);
      listen();
    }

    protected void onMessage(Message msg) throws IOException {
      if ((msg instanceof NetMessage) && (NetMessage.HEARTBEAT == (NetMessage) msg)) {
        log.debug("RX+REPLY HEARTBEAT");
        sendMsg(NetMessage.HEARTBEAT);
      }
      super.onMessage(msg);
    }

    @Override
    public void onDisconnect() {
      super.onDisconnect();
      connections.remove(getPeer());

    }
  }

  private class OutgoingConnection extends Connection {


    private OutgoingConnection(String peer, Socket socket, MessageListener listener) throws IOException {
      super(peer, socket);
      connections.put(peer, this);
      addMessageListener(listener);
      listen();
      log.info("Sending HELO");
      sendMsg(new HELOMessage(descriptor));
      log.info("Sent HELO");
    }

  
    @Override
    public void onDisconnect() {
      super.onDisconnect();
      connections.remove(getPeer());

    }

  }
}
