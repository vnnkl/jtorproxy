package io.nucleo.net;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.msopentech.thali.toronionproxy.FileUtilities;
import com.msopentech.thali.toronionproxy.OnionProxyManagerEventHandler;
import com.msopentech.thali.toronionproxy.OsData;
import net.freehaven.tor.control.TorControlConnection;
import org.apache.commons.lang3.SerializationUtils;
import socks.Socks5Proxy;
import socks.SocksException;
import socks.SocksSocket;

public class TorProxy {
    private static final Logger log = LoggerFactory.getLogger(TorProxy.class);

    private static final int TIMEOUT_COOKIE = 10 * 1000;
    private static final int TIMEOUT_BOOTSTRAP = 20 * 1000;

    private static final String TORRC = "torrc";
    private static final String GEOIP = "geoip";
    private static final String GEOIP6 = "geoip6";
    private static final String COOKIE = ".tor/control_auth_cookie";
    private static final String TOR_EXEC = Util.getTorExecutableFileName();
    private static final String HOSTNAME = "hiddenservice/hostname";
    private static final String PROCESS_OWNER = "__OwningControllerProcess";

    private final int localPort;
    private final File directory;

    private final List<Callable<Void>> startupCompletedListeners = new CopyOnWriteArrayList<Callable<Void>>();
    private final List<Consumer<Serializable>> receivingDataListener = new
            CopyOnWriteArrayList<Consumer<Serializable>>();
    private final Map<String, ClientSocket> clientSockets = new ConcurrentHashMap<String, ClientSocket>();

    // mutable
    private File torrcFile, geoipFile, geoip6File, cookieFile, torExecFile, hostnameFile;
    private volatile boolean shuttingDown;

    private Process torProcess;
    private Socket controlSocket;
    private TorControlConnection controlConnection;
    private Integer controlListenerPort;
    private String onionAddress;
    private int listeningProxyPort = -1;
    private int bootstrappedRepeatCounter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TorProxy(int localPort, File directory) {
        this.localPort = localPort;
        this.directory = directory;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void startTor() {
        log.debug("startTor");
        shuttingDown = false;

        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("startTor " + localPort);
                boolean bootstrapped = false;
                try {
                    setupFiles();
                    installFiles();
                    installAndUpdateConfig();

                    boolean cookieCreated = false;
                    boolean torProcessStarted = false;
                    int repeatCounter = 0;
                    while (!cookieCreated || !torProcessStarted || controlConnection == null && repeatCounter < 3) {
                        torProcessStarted = startTorProcess();
                        if (torProcessStarted) cookieCreated = checkCookie();
                        if (torProcessStarted && cookieCreated) setupControlConnection();
                        if (cookieCreated && torProcessStarted && controlConnection != null) {
                            // hardest part is done
                            break;
                        }
                        else {
                            if (torProcess != null) torProcess.destroy();
                            if (controlSocket != null) controlSocket.close();
                            Thread.sleep(1000);

                            repeatCounter++;
                        }
                    }

                    bootstrapped = checkBootstrapState();
                    if (!bootstrapped) {
                        if (bootstrappedRepeatCounter < 3) {
                            bootstrappedRepeatCounter++;

                            // In case of repeated problems we delete all files and start new from scratch
                            if (bootstrappedRepeatCounter > 1) deleteAllFiles();

                            shutDown();
                            Thread.sleep(1000);
                            startTor();
                            return;
                        }
                        else {
                            throw new Exception("Bootstrapped failed after 3 attempts");
                        }
                    }

                    requestListeningProxyPort();
                    notifyStartupDoneListeners();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (torProcess != null) torProcess.destroy();
                    if (!bootstrapped || controlConnection == null) shutDown();
                }
            }
        });
    }

    public void shutDown() {
        log.debug("shutDown");
        if (!shuttingDown) {
            shuttingDown = true;
            new Thread("shutDown") {
                @Override
                public void run() {
                    clientSockets.values().stream().forEach(new Consumer<ClientSocket>() {
                        @Override
                        public void accept(ClientSocket clientSocket) {
                            try {
                                clientSocket.getSocksSocket().close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            clientSocket.close();
                        }
                    });
                    try {
                        if (controlConnection != null) {
                            controlConnection.setConf("DisableNetwork", "1");
                            controlConnection.shutdownTor("TERM");
                        }
                        if (torProcess != null) torProcess.destroy();
                        if (controlSocket != null) controlSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }

    public void setupHiddenService(int hiddenServicePort, Callable<Void> onCompleteHandler) throws Exception {
        log.debug("setupHiddenService");

        if (!hostnameFile.getParentFile().exists() && !hostnameFile.getParentFile().mkdirs())
            throw new IOException("Could not create hostnameFile parent directory");

        // Use the control connection to update the Tor config
        List<String> config = Arrays.asList(
                "HiddenServiceDir " + hostnameFile.getParentFile().getAbsolutePath(),
                "HiddenServicePort " + hiddenServicePort + " 127.0.0.1:" + localPort);
        controlConnection.setConf(config);
        controlConnection.saveConf();

        while (!Thread.interrupted() && !hostnameFile.exists())
            Thread.sleep(100, 0);

        // give a bit of time for the file to be finished
        Thread.sleep(200, 0);

        // Publish the hidden service's onion hostname in transport properties
        onionAddress = new String(FileUtilities.read(hostnameFile), "UTF-8").trim();
        log.debug("onionAddress=" + onionAddress);

        setupServerSocket();
        onCompleteHandler.call();
    }

    public void setupClientSocket(String onionAddress, int hiddenServicePort, final Callable<Void> onCompleteHandler)
            throws Exception {
        log.debug("setupClientSocket");
        ClientSocket clientSocket = new ClientSocket(onionAddress, hiddenServicePort);
        clientSocket.createSocket(new Consumer<Socket>() {
            @Override
            public void accept(Socket socket) {
                try {
                    onCompleteHandler.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        clientSockets.put(onionAddress, clientSocket);
    }

    public void sendData(final Serializable data, String onionAddress) {
        final ClientSocket clientSocket = clientSockets.get(onionAddress);
        if (clientSocket != null) {
            clientSocket.createSocket(new Consumer<Socket>() {
                @Override
                public void accept(final Socket socket) {
                    //final Socket socket = clientSocket.getSocksSocket();
                    if (socket != null) {
                        new Thread("sendData " + localPort) {
                            @Override
                            public void run() {
                                DataOutputStream dataOutputStream = null;
                                try {
                                    dataOutputStream = new DataOutputStream(socket.getOutputStream());
                                    dataOutputStream.write(SerializationUtils.serialize(data));
                                    dataOutputStream.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } finally {
                                    log.debug("send completed");
                                    if (dataOutputStream != null) {
                                        try {
                                            dataOutputStream.close();
                                            socket.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }.start();
                    }
                }
            });
        }
    }

    public void addStartupCompletedListener(Callable<Void> listener) {
        startupCompletedListeners.add(listener);
    }

    public void addReceivingDataListener(Consumer<Serializable> listener) {
        receivingDataListener.add(listener);
    }

    public String getOnionAddress() {
        return onionAddress;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupFiles() {
        log.debug("setupFiles");
        cookieFile = new File(directory, COOKIE);
        torrcFile = new File(directory, TORRC);
        geoipFile = new File(directory, GEOIP);
        geoip6File = new File(directory, GEOIP6);
        torExecFile = new File(directory, TOR_EXEC);
        hostnameFile = new File(directory, HOSTNAME);
    }

    private void installFiles() throws IOException {
        log.debug("installFiles");
        if (!directory.exists() && !directory.mkdirs())
            throw new IOException("Could not create root directory: " + directory);

        Util.installFile(GEOIP, geoipFile, false);
        Util.installFile(GEOIP6, geoip6File, false);

        log.debug("setupCookie");
        if (!cookieFile.getParentFile().exists() && !cookieFile.getParentFile().mkdirs())
            throw new IOException("Could not create cookieFile parent directory");
        if (OsData.getOsType() == OsData.OsType.Android) Util.installFile(TOR_EXEC, torExecFile, false);
        else Util.extractNativeTorZip(directory);

        File file = new File(directory, Util.getTorExecutableFileName());
        if (!file.setExecutable(true))
            throw new IOException("Could not set file executable: " + file.getAbsolutePath());
    }

    private void installAndUpdateConfig() throws IOException {
        log.debug("installAndUpdateConfig");
        Util.installFile(TORRC, torrcFile, true);

        // We need to edit the config file to specify exactly where the cookie/geoip files should be stored, on
        // Android this is always a fixed location relative to the configFiles which is why this extra step
        // wasn't needed in Briar's Android code. But in Windows it ends up in the user's AppData/Roaming. Rather
        // than track it down we just tell Tor where to put it.
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(new BufferedWriter(new FileWriter(torrcFile, true)));
            printWriter.println("CookieAuthFile " + cookieFile.getAbsolutePath());
            // For some reason the GeoIP's location can only be given as a file name, not a path and it has
            // to be in the data directory so we need to set both
            printWriter.println("DataDirectory " + directory.getAbsolutePath());
            printWriter.println("GeoIPFile " + geoipFile.getName());
            printWriter.println("GeoIPv6File " + geoip6File.getName());
        } finally {
            if (printWriter != null)
                printWriter.close();
        }
    }

    private void deleteAllFiles() {
        log.debug("deleteAllFiles");
        FileUtilities.recursiveFileDelete(directory);
    }

    private boolean startTorProcess() throws IOException, InterruptedException, ExecutionException {
        log.debug("startTorProcess");
        String torPath = torExecFile.getAbsolutePath();
        String configPath = torrcFile.getAbsolutePath();
        String pid = Util.getProcessId();
        String[] cmd = {torPath, "-f", configPath, PROCESS_OWNER, pid};
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.directory(directory);
        Map<String, String> environment = processBuilder.environment();
        environment.put("HOME", directory.getAbsolutePath());
        switch (OsData.getOsType()) {
            case Linux32:
            case Linux64:
                // We have to provide the LD_LIBRARY_PATH because when looking for dynamic libraries
                // Linux apparently will not look in the current directory by default. By setting this
                // environment variable we fix that.
                environment.put("LD_LIBRARY_PATH", directory.getAbsolutePath());
            default:
                break;
        }

        torProcess = processBuilder.start();

        log.debug("setupProcessListeners");
        Util.listenOnTorErrorStream(torProcess.getErrorStream());
        Future<Integer> future = Util.listenOnTorInputStream(torProcess.getInputStream());
        controlListenerPort = future.get();
        if (controlListenerPort == null)
            return false;
        log.debug("controlListenerPort=" + controlListenerPort);

        // On platforms other than Windows we run as a daemon and so we need to wait for the process to detach
        // or exit. In the case of Windows the equivalent is running as a service and unfortunately that requires
        // managing the service, such as turning it off or uninstalling it when it's time to move on. Any number
        // of errors can prevent us from doing the cleanup and so we would leave the process running around. Rather
        // than do that on Windows we just let the process run on the exec and hence don't look for an exit code.
        // This does create a condition where the process has exited due to a problem but we should hopefully
        // detect that when we try to use the control connection.
        if (OsData.getOsType() != OsData.OsType.Windows) {
            try {
                int exit = torProcess.waitFor();
                torProcess = null;
                if (exit != 0) {
                    log.warn("Tor exited with value " + exit);
                    return false;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private boolean checkCookie() throws InterruptedException {
        log.debug("checkCookie");
        long startTs = System.currentTimeMillis();
        while (!Thread.interrupted() && !cookieFile.exists()) {
            if (System.currentTimeMillis() - startTs < TIMEOUT_COOKIE)
                return false;
            Thread.sleep(100, 0);
        }
        return true;
    }

    private void setupControlConnection() {
        log.debug("initControlConnection");
        try {
            controlSocket = new Socket("127.0.0.1", controlListenerPort);
            // Open a control connection and authenticate using the cookie file
            TorControlConnection controlConnection = new TorControlConnection(controlSocket);
            controlConnection.authenticate(FileUtilities.read(cookieFile));
            // Tell Tor to exit when the control connection is closed
            controlConnection.takeOwnership();
            controlConnection.resetConf(Collections.singletonList(PROCESS_OWNER));
            // Register to receive events from the Tor process
            controlConnection.setEventHandler(new OnionProxyManagerEventHandler());
            controlConnection.setEvents(Arrays.asList("CIRC", "ORCONN", "NOTICE", "WARN", "ERR"));
            // enable network
            controlConnection.setConf("DisableNetwork", "0");
            // We only set the class property once the connection is in a known good state
            this.controlConnection = controlConnection;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkBootstrapState() throws IOException, InterruptedException {
        log.debug("checkBootstrapState");
        long startTs = System.currentTimeMillis();
        while (!Thread.interrupted()) {
            String phase = controlConnection.getInfo("status/bootstrap-phase");
            log.info("bootstrap-phase=" + phase);
            if (phase.contains("PROGRESS=100"))
                return true;
            else if (System.currentTimeMillis() - startTs > TIMEOUT_BOOTSTRAP)
                return false;
            else
                Thread.sleep(300, 0);
        }
        return false;
    }

    private void requestListeningProxyPort() throws IOException, InterruptedException {
        log.debug("checkBootstrappedState");
        // This returns a set of space delimited quoted strings which could be Ipv4, Ipv6 or unix sockets
        String[] socksIpPorts = controlConnection.getInfo("net/listeners/socks").split(" ");
        for (String address : socksIpPorts) {
            if (address.contains("\"127.0.0.1:")) {
                // Remember, the last character will be a " so we have to remove that
                listeningProxyPort = Integer.parseInt(address.substring(address.lastIndexOf(":") + 1, address.length
                        () - 1));
                log.debug("listeningProxyPort=" + listeningProxyPort);
            }
        }
        if (listeningProxyPort == -1)
            throw new IOException("Could not request listeningProxyPort from controlConnection");
    }

    private void setupServerSocket() {
        log.debug("setupServerSocket");
        new Thread("ServerSocket " + localPort) {
            @Override
            public void run() {
                try {
                    final ServerSocket serverSocket = new ServerSocket(localPort);
                    while (!Thread.interrupted()) {
                        final Socket socket = serverSocket.accept();
                        //socket.setTcpNoDelay(true);
                        //socket.setSoTimeout(10 * 1000);

                        new Thread("handleServerSocketData " + localPort) {
                            @Override
                            public void run() {
                                DataInputStream dataInputStream = null;
                                try {
                                    dataInputStream = new DataInputStream(socket.getInputStream());
                                    // TODO got SerializationException/EOFException
                                    final Serializable data = SerializationUtils.deserialize(dataInputStream);
                                    log.info("data size " + SerializationUtils.serialize(data).length);
                                    receivingDataListener.stream().forEach(new Consumer<Consumer<Serializable>>() {
                                        @Override
                                        public void accept(Consumer<Serializable> consumer) {
                                            consumer.accept(data);
                                        }
                                    });
                                } catch (EOFException e) {
                                    e.printStackTrace();
                                    log.warn("EOFException at incoming data");
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } finally {
                                    if (dataInputStream != null) {
                                        try {
                                            dataInputStream.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }.start();
                    }
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void notifyStartupDoneListeners() {
        log.debug("notifyStartupDoneListeners");
        startupCompletedListeners.stream().forEach(new Consumer<Callable<Void>>() {
            @Override
            public void accept(Callable<Void> listener) {
                try {
                    listener.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner class
    ///////////////////////////////////////////////////////////////////////////////////////////

    class ClientSocket {
        private final String onionAddress;
        private final int hiddenServicePort;
        private final ExecutorService executorService;
        private volatile Socket socksSocket;

        public ClientSocket(String onionAddress, int hiddenServicePort) throws IOException, InterruptedException {
            this.onionAddress = onionAddress;
            this.hiddenServicePort = hiddenServicePort;

            executorService = Executors.newFixedThreadPool(1);
            log.debug("onionAddress=" + onionAddress);
            log.debug("hiddenServicePort=" + hiddenServicePort);
        }

        public void createSocket(final Consumer<Socket> socketReadyListener) {
            executorService.submit(new Callable<Void>() {
                @Override
                public Void call() throws InterruptedException, IOException {
                    Thread.currentThread().setName("createSocket " + localPort);
                    int repeat = 0;
                    while (repeat < 5) {
                        try {
                            //socksSocket = Utilities.socks4aSocketConnection(onionAddress, hiddenServicePort, "127.0
                            // .0.1", listeningProxyPort);

                            Socks5Proxy socks5Proxy = new Socks5Proxy("localhost", listeningProxyPort);
                            socks5Proxy.resolveAddrLocally(false);
                            socksSocket = new SocksSocket(socks5Proxy, onionAddress, hiddenServicePort);
                            //socksSocket.setTcpNoDelay(true);
                            //socksSocket.setSoTimeout(10 * 1000);
                            break;
                        } catch (UnknownHostException e1) {
                            e1.printStackTrace();
                            log.warn("Could not create socket. Will repeat trying again after 1 sec.");
                            repeat++;
                            Thread.sleep(5000);
                        } catch (SocksException e2) {
                            e2.printStackTrace();
                            log.warn("Could not create socket. Will repeat trying again after 1 sec.");
                            repeat++;
                            Thread.sleep(5000);
                        }
                    }
                    if (socksSocket == null)
                        throw new IOException("Could not create socket after 5 times retrying");

                    // socksSocket = Utilities.socks4aSocketConnection(onionAddress, hiddenServicePort, "127.0.0.1", 
                    // listeningProxyPort);
                    log.info("socksSocket and socks5Proxy created");

                    socketReadyListener.accept(socksSocket);
                    return null;
                }
            });
        }

        public Socket getSocksSocket() {
            return socksSocket;
        }

        public void close() {
            try {
                socksSocket.getOutputStream().close();
                socksSocket.getInputStream().close();
                socksSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
