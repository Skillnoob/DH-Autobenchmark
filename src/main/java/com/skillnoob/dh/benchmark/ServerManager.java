package com.skillnoob.dh.benchmark;

import com.skillnoob.dh.benchmark.data.BenchmarkConfig;
import com.skillnoob.dh.benchmark.util.LogMonitor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class ServerManager {
    private static final String SERVER_DIR = "server";
    private static final String FABRIC_JAR = "fabric-server.jar";

    private final BenchmarkConfig config;
    private volatile Process serverProcess = null;
    private PrintWriter processWriter = null;
    private BufferedReader processReader = null;
    private LogMonitor logMonitor = null;

    public ServerManager(BenchmarkConfig config) {
        this.config = config;
        // We don't want stray servers when the JVM exits.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("Shutdown hook triggered, shutting down the active server, if one is running.");
            stopServer(true);
        }));
    }

    /**
     * Starts the server with the given command and waits until it finished starting.
     */
    public boolean startServer(List<String> command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command).directory(new File(SERVER_DIR)).redirectErrorStream(true);

        serverProcess = pb.start();
        processReader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8));
        processWriter = new PrintWriter(serverProcess.getOutputStream(), true);
        logMonitor = new LogMonitor(processReader, config.debugMode());

        return waitForLogMessage(line -> line.contains("Done"), (int) (120 * config.timeoutScale()));
    }

    /**
     * Stops the server and waits for process termination.
     */
    public void stopServer(boolean kill) {
        if (serverProcess != null && serverProcess.isAlive()) {
            try {
                if (kill) {
                    serverProcess.destroyForcibly();
                } else {
                    executeCommand("stop");

                    boolean terminated = serverProcess.waitFor((long) (60 * config.timeoutScale()), TimeUnit.SECONDS);
                    if (!terminated) {
                        System.out.println("Server did not stop gracefully, forcing termination");
                        serverProcess.destroyForcibly();
                    }
                }
            } catch (Exception e) {
                System.err.println("Error stopping server:");
                e.printStackTrace();
            } finally {
                closeResources();
            }
        }
    }

    /**
     * Executes a command on the running server.
     */
    public void executeCommand(String command) {
        if (serverProcess != null && serverProcess.isAlive() && processWriter != null) {
            processWriter.println(command);
        } else {
            System.err.println("Cannot execute command: server is not running");
        }
    }

    /**
     * Waits for a specific message in the server logs that matches the predicate.
     */
    public boolean waitForLogMessage(Predicate<String> messagePredicate, int timeoutSeconds) {
        try {
            long start = System.nanoTime();
            long timeoutNanos = timeoutSeconds == 0 ? Long.MAX_VALUE : timeoutSeconds * 1_000_000_000L;

            while (isServerRunning() && (System.nanoTime() - start) < timeoutNanos) {
                String line = logMonitor.pollLine(1, TimeUnit.SECONDS);

                if (line != null && messagePredicate.test(line)) {
                    return true;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    /**
     * Waits for a specific message in the server logs that matches the predicate with no timeout.
     */
    public void waitForLogMessage(Predicate<String> messagePredicate) {
        waitForLogMessage(messagePredicate, 0);
    }

    /**
     * Checks if the server process is running.
     */
    public boolean isServerRunning() {
        if (serverProcess == null) {
            return false;
        }
        return serverProcess.isAlive();
    }

    /**
     * Gets the standard command list for starting the server.
     */
    public List<String> getServerStartCommand() {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-Xmx" + config.ramGb() + "G");
        command.addAll(config.extraJvmArgs());
        command.add("-jar");
        command.add(FABRIC_JAR);
        command.add("nogui");
        return command;
    }

    /**
     * Closes resources used by the server manager.
     */
    private void closeResources() {
        try {
            if (processWriter != null) {
                processWriter.close();
                processWriter = null;
            }
            if (processReader != null) {
                processReader.close();
                processReader = null;
            }
            if (logMonitor != null) {
                logMonitor.close();
                logMonitor = null;
            }
            serverProcess = null;
        } catch (IOException e) {
            System.err.println("Error closing resources:");
            e.printStackTrace();
        }
    }
}
