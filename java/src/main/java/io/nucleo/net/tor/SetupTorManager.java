package io.nucleo.net.tor;

import io.nucleo.net.Server;
import io.nucleo.net.ServerHandler;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.net.Socket;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.msopentech.thali.toronionproxy.FileUtilities;
import com.msopentech.thali.toronionproxy.OnionProxyManagerEventHandler;
import com.msopentech.thali.toronionproxy.OsData;
import net.freehaven.tor.control.TorControlConnection;

public class SetupTorManager {
    private static final Logger log = LoggerFactory.getLogger(SetupTorManager.class);

    private static final int TIMEOUT_COOKIE = 10 * 1000;
    private static final int TIMEOUT_BOOTSTRAP = 20 * 1000;

    private static final String TORRC = "torrc";
    private static final String GEOIP = "geoip";
    private static final String GEOIP6 = "geoip6";
    private static final String COOKIE = ".tor/control_auth_cookie";
    private static final String TOR_EXEC = Util.getTorExecutableFileName();
    private static final String HOSTNAME = "hiddenservice/hostname";
    private static final String PROCESS_OWNER = "__OwningControllerProcess";

    private final File directory;
    private final File torrcFile, geoipFile, geoip6File, cookieFile, torExecFile, hostnameFile;

    // mutable
    private volatile boolean shuttingDown;
    private Process torProcess;
    private Socket controlSocket;
    private TorControlConnection controlConnection;
    private Integer controlListenerPort;
    private int bootstrappedRepeatCounter;
    private Proxy proxy;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SetupTorManager(File directory) {
        this.directory = directory;

        cookieFile = new File(directory, COOKIE);
        torrcFile = new File(directory, TORRC);
        geoipFile = new File(directory, GEOIP);
        geoip6File = new File(directory, GEOIP6);
        torExecFile = new File(directory, TOR_EXEC);
        hostnameFile = new File(directory, HOSTNAME);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ListenableFuture<Proxy> start() {
        log.debug("start");
        shuttingDown = false;
        ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        return service.submit(new Callable<Proxy>() {
            @Override
            public Proxy call() {
                Thread.currentThread().setName("SetupTorManager.start" + new Random().nextInt(1000));
                boolean bootstrapped = false;
                try {
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
                            start();
                            return null;
                        }
                        else {
                            throw new Exception("Bootstrapped failed after 3 attempts");
                        }
                    }

                    int proxyPort = requestListeningProxyPort();
                    proxy = new Proxy(proxyPort);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (torProcess != null) torProcess.destroy();
                    if (!bootstrapped || controlConnection == null) shutDown();

                    return proxy;
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
                    try {
                        proxy.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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

    public ListenableFuture<String> setupHiddenService(int hiddenServicePort, int localPort,
                                                       ServerHandler serverHandler) throws Exception {
        log.debug("setupHiddenService");
        ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        return service.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                if (!hostnameFile.getParentFile().exists() && !hostnameFile.getParentFile().mkdirs())
                    throw new IOException("Could not create hostnameFile parent directory");

                // Use the control connection to update the Tor config
                List<String> config = Arrays.asList(
                        "HiddenServiceDir " + hostnameFile.getParentFile().getAbsolutePath(),
                        "HiddenServicePort " + hiddenServicePort + " 127.0.0.1:" + localPort);
                controlConnection.setConf(config);
                controlConnection.saveConf();

                while (!Thread.interrupted() && !hostnameFile.exists()) Thread.sleep(100, 0);

                // give a bit of time for the file to be finished
                Thread.sleep(200, 0);

                // Publish the hidden service's onion hostname in transport properties
                String onionAddress = new String(FileUtilities.read(hostnameFile), "UTF-8").trim();
                log.debug("onionAddress=" + onionAddress);

                Server server = new Server(hiddenServicePort, serverHandler);
                server.start();

                return onionAddress + ":" + String.valueOf(hiddenServicePort);
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


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
            if (printWriter != null) printWriter.close();
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
        if (controlListenerPort == null) return false;
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
            if (System.currentTimeMillis() - startTs < TIMEOUT_COOKIE) return false;
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
            if (phase.contains("PROGRESS=100")) return true;
            else if (System.currentTimeMillis() - startTs > TIMEOUT_BOOTSTRAP) return false;
            else Thread.sleep(300, 0);
        }
        return false;
    }

    private int requestListeningProxyPort() throws IOException, InterruptedException {
        log.debug("checkBootstrappedState");
        // This returns a set of space delimited quoted strings which could be Ipv4, Ipv6 or unix sockets
        String[] socksIpPorts = controlConnection.getInfo("net/listeners/socks").split(" ");
        for (String address : socksIpPorts) {
            if (address.contains("\"127.0.0.1:")) {
                // Remember, the last character will be a " so we have to remove that
                int proxyPort = Integer.parseInt(address.substring(address.lastIndexOf(":") + 1, address.length() - 1));
                log.debug("listeningProxyPort=" + proxyPort);
                return proxyPort;
            }
        }
        throw new IOException("Could not request listeningProxyPort from controlConnection");
    }

}
