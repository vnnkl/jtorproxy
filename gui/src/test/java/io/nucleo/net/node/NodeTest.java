package io.nucleo.net.node;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.regex.Pattern;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;

import io.nucleo.net.TorNode;
import io.nucleo.net.node.Connection;
import io.nucleo.net.node.ConnectionListener;
import io.nucleo.net.node.MessageListener;
import io.nucleo.net.node.Node;
import io.nucleo.net.proto.ContainerMessage;

public class NodeTest {
  private static boolean running;

  static Connection currentCon = null;

  static class Listener implements MessageListener {
    @Override
    public void onMessage(Connection con, ContainerMessage msg) {
      System.out.println("RXD: " + msg.getPayload().toString() + " < " + con.getPeer());

    }

    @Override
    public void onDisconnect(Connection con) {
      if (con.equals(currentCon))
        currentCon = null;
      System.out.println(con.getPeer() + " has disconnected");

    }

    @Override
    public void onTimeout(Connection con) {
      if (con.equals(currentCon))
        currentCon = null;
      System.out.println(con.getPeer() + " has timed out");

    }
  }

  public static void main(String[] args) throws InstantiationException, IOException {
    if (args.length != 2){
      System.err.println("2 params required: hidden service dir + port");
      return;
    }
    File dir = new File(args[0]);
    dir.mkdirs();
    TorNode<JavaOnionProxyManager, JavaOnionProxyContext> tor = new TorNode<JavaOnionProxyManager, JavaOnionProxyContext>(
        dir) {
    };
    Listener listener = new Listener();

    Node node = new Node(tor.createHiddenService(Integer.parseInt(args[1])), tor);

    node.startServer(new ConnectionListener() {
      @Override
      public void onConnect(Connection con) {
        con.addMessageListener(listener);
        System.out.println("Connection to " + con.getPeer() + " established :-)");

      }
    });
    running = true;
    Scanner scan = new Scanner(System.in);
    System.out.println("READY!");
    String line = null;
    System.out.print("\n" + node.getLocalName() + " >");
    while (running && ((line = scan.nextLine()) != null)) {
      String[] cmd = { line };
      if (line.contains(" "))
        cmd = line.split(Pattern.quote(" "));

      switch (cmd[0]) {
        case "con":
          if (cmd.length == 2) {
            String host = cmd[1];
            try {
              currentCon = node.connect(host, listener);
            } catch (Exception e) {
              System.out.println(e.getMessage());
            }
          }
          break;
        case "list":
          for (Connection con : node.getConnections()) {
            System.out.println("\t" + con.getPeer());
          }
          break;
        case "sel":
          try {
            if (cmd.length == 2) {
              int index = Integer.parseInt(cmd[1]);
              currentCon = node.getConnections().get(index);
            }
          } catch (Exception e) {
            System.out.println(e.getMessage());
          }
          break;
        case "send":
          try {
            if (cmd.length >= 2) {
              if (currentCon != null) {
                currentCon.sendMessage(new ContainerMessage(line.substring(4)));
              }else
                System.err.println("NO node active!");
            }
          } catch (Exception e) {
            System.out.println(e.getMessage());
          }
          break;
        default:
          break;
      }
      System.out.print("\n" + node.getLocalName() + ":" + (currentCon == null ? "" : currentCon.getPeer()) + " >");
    }

  }

}
