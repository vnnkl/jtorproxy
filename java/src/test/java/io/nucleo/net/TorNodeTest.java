package io.nucleo.net;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import oi.nucleo.net.HiddenServiceDescriptor;
import oi.nucleo.net.TorNode;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;

public class TorNodeTest {

    private static final int hsPort = 55555;
    private static CountDownLatch serverLatch = new CountDownLatch(1);
    private static TorNode<JavaOnionProxyManager, JavaOnionProxyContext> node;

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        File dir = new File("tor-test");
        dir.mkdirs();
        node = new TorNode<JavaOnionProxyManager, JavaOnionProxyContext>(dir) {
        };
        final HiddenServiceDescriptor hiddenService = node.createHiddenService(hsPort);
        new Thread(new Server(hiddenService.getServerSocket())).start();
        serverLatch.await();
        
        new Client(node.connectToHiddenService(hiddenService.getOnionUrl(), hiddenService.getservicePort())).run();
        node.shutdown();
    }

    private static class Client implements Runnable {

        private Socket sock;

        private Client(Socket sock) {
            this.sock = sock;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                OutputStreamWriter out = new OutputStreamWriter(sock.getOutputStream());
                Scanner scan = new Scanner(System.in);
                System.out.print("\n> ");
                String input = scan.nextLine();
                out.write(input + "\n");
                out.flush();
                String aLine = null;
                while ((aLine = in.readLine()) != null) {
                    System.out.println(aLine);
                    System.out.print("\n> ");
                    input = scan.nextLine();
                    out.write(input + "\n");
                    out.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private static class Server implements Runnable {
        private final ServerSocket socket;

        private Server(ServerSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            System.out.println("Wating for incoming connections...");
            serverLatch.countDown();
            try {
                Socket sock = socket.accept();
                System.out.println("Accepting Client on port " + sock.getLocalPort());
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                OutputStreamWriter out = new OutputStreamWriter(sock.getOutputStream());
                String aLine = null;
                while ((aLine = in.readLine()) != null) {
                    System.out.println("ECHOING " + aLine);
                    out.write("ECHO " + aLine + "\n");
                    out.flush();
                    if (aLine.equals("END"))
                        break;
                }
                sock.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

    }

}
