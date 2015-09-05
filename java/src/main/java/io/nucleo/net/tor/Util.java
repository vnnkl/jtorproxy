package io.nucleo.net.tor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.msopentech.thali.toronionproxy.FileUtilities;
import com.msopentech.thali.toronionproxy.OsData;

public class Util {
    private static final Logger log = LoggerFactory.getLogger(Util.class);

    public static void installFile(final String fileName, final File file, final boolean deleteIfExists) throws
            IOException {
        InputStream inputStream = getAssetOrResourceByName(fileName);
        if (deleteIfExists && file.exists() && !file.delete()) {
            log.error("Could not remove existing file " + file.getName());
            throw new IOException("Could not remove existing file " + file.getName());
        }

        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            FileUtilities.copy(inputStream, outputStream);
        } catch (IOException e) {
            log.error("Error at copying file " + file.getName());
            e.printStackTrace();
            throw e;
        } finally {
            if (outputStream != null)
                try {
                    outputStream.close();
                } catch (IOException e) {
                }
        }
    }

    public static InputStream getAssetOrResourceByName(String fileName) throws IOException {
        return Util.class.getResourceAsStream("/" + fileName);
    }

    public static void extractNativeTorZip(File directory) throws IOException {
        InputStream zipFileInputStream = getAssetOrResourceByName(getPathToTorExecutable());
        ZipInputStream zipInputStream = null;
        try {
            zipInputStream = new ZipInputStream(zipFileInputStream);
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                File file = new File(directory, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    if (!file.exists() && !file.mkdirs()) {
                        throw new RuntimeException("Could not create directory " + file);
                    }
                }
                else {
                    if (file.exists() && !file.delete()) {
                        throw new RuntimeException("Could not delete file in preparation for overwriting it. File - "
                                + file.getAbsolutePath());
                    }

                    if (!file.createNewFile()) {
                        throw new RuntimeException("Could not create file " + file);
                    }

                    OutputStream fileOutputStream = new FileOutputStream(file);
                    // fileOutputStream get closed in copyDoNotCloseInput
                    copyDoNotCloseInput(zipInputStream, fileOutputStream);
                }
            }
        } finally {
            if (zipInputStream != null)
                try {
                    zipInputStream.close();
                } catch (IOException e) {
                }
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        try {
            copyDoNotCloseInput(in, out);
        } finally {
            if (in != null) in.close();
        }
    }

    public static void copyDoNotCloseInput(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buf = new byte[4096];
            while (true) {
                int read = in.read(buf);
                if (read == -1) break;
                out.write(buf, 0, read);
            }
        } finally {
            out.close();
        }
    }

    public static String getPathToTorExecutable() {
        String path = "native/";
        String fileName = "tor.zip";
        switch (OsData.getOsType()) {
            case Android:
                return "";
            case Windows:
                return path + "windows/x86/" + fileName; // We currently only support the x86 build but that should 
            // work everywhere
            case Mac:
                return path + "osx/x64/" + fileName; // I don't think there even is a x32 build of Tor for Mac, but 
            // could be wrong.
            case Linux32:
                return path + "linux/x86/" + fileName;
            case Linux64:
                return path + "linux/x64/" + fileName;
            default:
                throw new RuntimeException("We don't support Tor on this OS");
        }
    }

    public static String getTorExecutableFileName() {
        switch (OsData.getOsType()) {
            case Android:
            case Linux32:
            case Linux64:
                return "tor";
            case Windows:
                return "tor.exe";
            case Mac:
                return "tor.real";
            default:
                throw new RuntimeException("We don't support Tor on this OS");
        }
    }

    public static String getProcessId() {
        // This is a horrible hack. It seems like more JVMs will return the process's PID this way, but not guarantees.
        String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        return processName.split("@")[0];
    }

    protected static Future<Integer> listenOnTorInputStream(final InputStream inputStream) {
        return Executors.newSingleThreadExecutor().submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                Thread.currentThread().setName("listenOnTorInputStream " + new Random().nextInt(10));
                Integer result = -1;
                Scanner scanner = null;
                try {
                    scanner = new Scanner(inputStream);
                    while (scanner.hasNextLine()) {
                        String nextLine = scanner.nextLine();
                        // We need to find the line where it tells us what the control port is.
                        // The line that will appear in stdio with the control port looks like:
                        // Control listener listening on port 39717.
                        if (nextLine.contains("Control listener listening on port ")) {
                            result = Integer.parseInt(nextLine.substring(nextLine.lastIndexOf(" ") + 1, nextLine
                                    .length() - 1));
                            break;
                        }
                        log.info(nextLine);
                    }
                } finally {
                    try {
                        inputStream.close();
                        if (scanner != null) scanner.close();
                    } catch (IOException e) {
                        log.error("Couldn't close input stream", e);
                    }

                }
                return result;
            }
        });
    }

    protected static void listenOnTorErrorStream(final InputStream errorStream) {
        Executors.newSingleThreadExecutor().submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Thread.currentThread().setName("listenOnTorErrorStream " + new Random().nextInt(10));
                Scanner scanner = null;
                try {
                    scanner = new Scanner(errorStream);
                    while (scanner.hasNextLine()) {
                        log.error(scanner.nextLine());
                    }
                } finally {
                    try {
                        errorStream.close();
                        if (scanner != null) scanner.close();
                    } catch (IOException e) {
                        log.error("Couldn't close error stream", e);
                    }
                }
                return null;
            }
        });
    }
}
