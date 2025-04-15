package com.skillnoob.dh.benchmark;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final String SERVER_DIR = "server";
    private static final String FABRIC_JAR = "fabric-server.jar";
    private static final String DH_JAR = "distant-horizons.jar";
    private static final String SERVER_PROPERTIES_FILE = "server.properties";
    private static final String EULA_FILE = "eula.txt";
    private static final String MODS_DIR = Paths.get(SERVER_DIR, "mods").toString();
    private static final String WORLD_DIR = Paths.get(SERVER_DIR, "world").toString();
    private static final String DATA_DIR = Paths.get(WORLD_DIR, "data").toString();
    private static final String DH_DB_FILE = Paths.get(DATA_DIR, "DistantHorizons.sqlite").toString();

    private static BenchmarkConfig benchmarkConfig;

    private static volatile Process currentProcess = null;

    public static void main(String[] args) {
        try {
            // Shutdown hook to close any open server
            Runtime.getRuntime().addShutdownHook(new Thread(Main::cleanupProcess));

            benchmarkConfig = FileManager.loadBenchmarkConfig();

            System.out.println("Loaded configuration:");
            System.out.println("RAM (GB): " + benchmarkConfig.ramGb());
            System.out.println("Seeds: " + Arrays.toString(benchmarkConfig.seeds()));
            System.out.println("Thread Preset: " + benchmarkConfig.threadPreset());
            System.out.println("Benchmark Radius: " + benchmarkConfig.generationRadius());
            System.out.println("Fabric Download URL: " + benchmarkConfig.fabricDownloadUrl());
            System.out.println("Distant Horizons Download URL: " + benchmarkConfig.dhDownloadUrl());

            List<String> serverCmd = List.of("java", "-Xmx" + benchmarkConfig.ramGb() + "G", "-jar", FABRIC_JAR, "nogui");

            // If fabric isn't downloaded, download it and run once to accept the EULA and enable white-list.
            if (DownloadManager.downloadFile(benchmarkConfig.fabricDownloadUrl(), SERVER_DIR, FABRIC_JAR)) {
                System.out.println("Fabric downloaded successfully. Starting the server once to accept the EULA.");

                ProcessBuilder pb = new ProcessBuilder(serverCmd);
                pb.directory(new File(SERVER_DIR));
                pb.redirectErrorStream(true);
                Process process = pb.start();
                currentProcess = process;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
                process.waitFor();
                currentProcess = null;

                FileManager.updateConfigLine(Paths.get(SERVER_DIR, EULA_FILE), "eula", "eula=true");
                FileManager.updateConfigLine(Paths.get(SERVER_DIR, SERVER_PROPERTIES_FILE), "white-list", "white-list=true");
            }

            DownloadManager.downloadFile(benchmarkConfig.dhDownloadUrl(), MODS_DIR, DH_JAR);

            long[] seeds = benchmarkConfig.seeds();
            List<BenchmarkResult> benchmarkResults = new ArrayList<>();
            // Run the benchmark for each seed.
            for (long seed : seeds) {
                BenchmarkResult result = runBenchmark(seed, serverCmd);
                benchmarkResults.add(result);
            }

            // Log benchmark results.
            System.out.println("Benchmark completed. Results:");
            for (int i = 0; i < seeds.length; i++) {
                BenchmarkResult res = benchmarkResults.get(i);
                System.out.println("Seed " + seeds[i] + ": Elapsed Time: " + res.elapsedTime() + ", Database Size: " + res.dbSize() + " MB");
            }

            // Save benchmark data to a CSV file.
            FileManager.writeResultsToCSV(seeds, benchmarkResults, "benchmark-results.csv");
        } catch (Exception e) {
            throw new RuntimeException("An error occurred during the benchmark process.", e);
        }
    }

    /**
     * Runs the benchmark on a given seed.
     */
    private static BenchmarkResult runBenchmark(long seed, List<String> cmd) throws IOException, InterruptedException {
        // Delete the previous world to avoid reusing old DB and world files.
        Path worldDir = Paths.get(WORLD_DIR);
        if (Files.exists(worldDir)) {
            FileManager.deleteDirectory(worldDir);
        }
        // Select the seed.
        FileManager.updateConfigLine(Paths.get(SERVER_DIR, SERVER_PROPERTIES_FILE), "level-seed", "level-seed=" + seed);

        // Start the server.
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(SERVER_DIR));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        currentProcess = process;

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            if (line.contains("Done")) {
                break;
            }
        }

        // Let the server run a bit longer to finish startup.
        Thread.sleep(5000);

        PrintWriter processWriter = new PrintWriter(process.getOutputStream(), true);

        // Configure the thread preset.
        processWriter.println("dh config common.threadPreset " + benchmarkConfig.threadPreset());
        Thread.sleep(1000);
        // Start pregen.
        processWriter.println("dh pregen start minecraft:overworld 0 0 " + benchmarkConfig.generationRadius());

        long benchmarkStartTime = 0;
        String elapsedTimeStr = "";
        while (process.isAlive()) {
            if ((line = reader.readLine()) == null) {
                continue;
            }
            System.out.println(line);

            if (line.contains("Starting pregen")) {
                benchmarkStartTime = System.currentTimeMillis();
            }

            if (line.contains("Pregen is complete")) {
                if (benchmarkStartTime != 0) {
                    long elapsedMillis = System.currentTimeMillis() - benchmarkStartTime;
                    elapsedTimeStr = formatDuration(elapsedMillis);
                }
                System.out.println("Pregen completed, shutting down server.");
                Thread.sleep(20000); // Safety, otherwise DH will complain about SQLite being closed.
                processWriter.println("stop");
            }
        }
        currentProcess = null;

        Path dhDbPath = Paths.get(DH_DB_FILE);
        double dbSize = Files.exists(dhDbPath) ? Files.size(dhDbPath) / (1024.0 * 1024.0) : 0; // Get DB Size and convert to MB
        return new BenchmarkResult(elapsedTimeStr, dbSize);
    }

    private static void cleanupProcess() {
        if (currentProcess != null && currentProcess.isAlive()) {
            try {
                System.out.println("Shutdown hook triggered, terminating server process...");

                currentProcess.destroy();

                boolean terminated = currentProcess.waitFor(5, TimeUnit.SECONDS);

                if (!terminated) {
                    currentProcess.destroyForcibly();
                }

                System.out.println("Server process terminated.");
            } catch (Exception e) {
                System.err.println("Error during process cleanup: " + e.getMessage());
            } finally {
                currentProcess = null;
            }
        }
    }

    private static String formatDuration(long millis) {
        Duration d = Duration.ofMillis(millis);
        long hours = d.toHours();
        long minutes = d.toMinutes() % 60;
        long seconds = d.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}