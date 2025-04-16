package com.skillnoob.dh.benchmark;

import java.io.*;
import java.nio.charset.StandardCharsets;
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

    public ServerManager(BenchmarkConfig config) {
        this.config = config;
        // We don't want stray servers when the JVM exits.
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));
    }

    /**
     * Starts the server with the given command and waits until it finished starting.
     */
    public boolean startServer(List<String> command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(SERVER_DIR));
        pb.redirectErrorStream(true);

        serverProcess = pb.start();
        processReader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8));
        processWriter = new PrintWriter(serverProcess.getOutputStream(), true);

        return waitForLogMessage(line -> line.contains("Done"), 60);
    }

    /**
     * Stops the server and waits for process termination.
     */
    public void stopServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            try {
                executeCommand("stop");

                boolean terminated = serverProcess.waitFor(60, TimeUnit.SECONDS);

                if (!terminated) {
                    System.out.println("Server did not stop gracefully, forcing termination");
                    serverProcess.destroyForcibly();
                }

                System.out.println("Server stopped");
            } catch (Exception e) {
                System.err.println("Error stopping server: " + e.getMessage());
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
        if (processReader == null) {
            return false;
        }

        try {
            long startTime = System.currentTimeMillis();
            long timeoutMillis = timeoutSeconds * 1000L;
            String line;

            while (serverProcess.isAlive() && (timeoutSeconds == -1 || (System.currentTimeMillis() - startTime) < timeoutMillis)) {
                if (processReader.ready() && (line = processReader.readLine()) != null) {
                    System.out.println(line);
                    if (messagePredicate.test(line)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error waiting for log message: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the server process is running.
     */
    public boolean isServerRunning() {
        return serverProcess != null && serverProcess.isAlive();
    }

    /**
     * Gets the standard command list for starting the server.
     */
    public List<String> getServerStartCommand() {
        return List.of("java", "-Xmx" + config.ramGb() + "G", "-jar", FABRIC_JAR, "nogui");
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
            serverProcess = null;
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }
}
