package com.skillnoob.dh.benchmark;

import com.skillnoob.dh.benchmark.data.BenchmarkConfig;
import com.skillnoob.dh.benchmark.data.BenchmarkResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
    private static ServerManager serverManager;

    public static void main(String[] args) {
        try {
            benchmarkConfig = FileManager.loadBenchmarkConfig();

            System.out.println("Loaded configuration:");
            System.out.println("RAM (GB): " + benchmarkConfig.ramGb());
            System.out.println("Seeds: " + Arrays.toString(benchmarkConfig.seeds()));
            System.out.println("Thread Preset: " + benchmarkConfig.threadPreset());
            System.out.println("Benchmark Radius: " + benchmarkConfig.generationRadius());
            System.out.println("Fabric Download URL: " + benchmarkConfig.fabricDownloadUrl());
            System.out.println("Distant Horizons Download URL: " + benchmarkConfig.dhDownloadUrl());

            serverManager = new ServerManager(benchmarkConfig);
            List<String> serverCmd = serverManager.getServerStartCommand();

            // If fabric isn't downloaded, download it and run once to accept the EULA and enable white-list.
            if (DownloadManager.downloadFile(benchmarkConfig.fabricDownloadUrl(), SERVER_DIR, FABRIC_JAR)) {
                System.out.println("Fabric downloaded successfully. Starting the server once to accept the EULA.");

                serverManager.startServer(serverCmd);
                serverManager.stopServer();

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

            System.out.println("Benchmark completed. Results:");

            long totalTime = 0;
            double totalDBSizeInMB = 0;

            for (int i = 0; i < seeds.length; i++) {
                BenchmarkResult res = benchmarkResults.get(i);
                double dbSizeInMB = res.dbSize() / (1024.0 * 1024.0);
                String formattedTime = formatDuration(res.elapsedTime());
                System.out.println("Seed " + seeds[i] + ": Elapsed Time: " + formattedTime + ", Database Size: " + Math.round(dbSizeInMB) + " MB");

                totalTime += res.elapsedTime();
                totalDBSizeInMB += dbSizeInMB;
            }

            long avgTime = totalTime / seeds.length;
            long avgDBSizeInMB = Math.round(totalDBSizeInMB / seeds.length);
            String formattedAvgTime = formatDuration(avgTime);
            System.out.println("Average: Elapsed Time: " + formattedAvgTime + ", Database Size: " + avgDBSizeInMB + " MB");

            FileManager.writeResultsToCSV(seeds, benchmarkResults, formattedAvgTime, avgDBSizeInMB, "benchmark-results.csv");
            System.out.println("Results saved to benchmark-results.csv");
        } catch (Exception e) {
            throw new RuntimeException("An error occurred during the benchmark process.", e);
        }
    }

    /**
     * Runs the benchmark on a given seed.
     */
    private static BenchmarkResult runBenchmark(long seed, List<String> cmd) throws IOException, InterruptedException {
        // Delete the previous world
        Path worldDir = Paths.get(WORLD_DIR);
        if (Files.exists(worldDir)) {
            FileManager.deleteDirectory(worldDir);
        }
        // Select the seed.
        FileManager.updateConfigLine(Paths.get(SERVER_DIR, SERVER_PROPERTIES_FILE), "level-seed", "level-seed=" + seed);

        if (!serverManager.startServer(cmd)) {
            throw new IOException("Failed to start server, or server took too long to start.");
        }
        Thread.sleep(5000);

        // Configure the thread preset.
        serverManager.executeCommand("dh config common.threadPreset " + benchmarkConfig.threadPreset());
        Thread.sleep(5000);

        // Start pregen.
        System.out.println("Starting pregen with radius " + benchmarkConfig.generationRadius() + " for seed " + seed);
        serverManager.executeCommand("dh pregen start minecraft:overworld 0 0 " + benchmarkConfig.generationRadius());

        AtomicLong benchmarkStartTime = new AtomicLong(0);
        AtomicBoolean pregenComplete = new AtomicBoolean(false);
        AtomicLong elapsedTime = new AtomicLong(0);

        // Read server output until pregen completes
        while (serverManager.isServerRunning() && !pregenComplete.get()) {
            serverManager.waitForLogMessage(line -> {
                if (line.contains("Starting pregen")) {
                    benchmarkStartTime.set(System.currentTimeMillis());
                    return false;
                }

                if (line.contains("Pregen is complete")) {
                    if (benchmarkStartTime.get() != 0) {
                        elapsedTime.set(System.currentTimeMillis() - benchmarkStartTime.get());
                    }

                    System.out.println("Pregen completed, shutting down server.");
                    pregenComplete.set(true);
                    return true;
                }

                return false;
            });

            if (pregenComplete.get()) {
                System.out.println("Waiting 30 seconds before server shutdown to ensure DB is properly finalized...");
                Thread.sleep(30000); // Safety, otherwise DH will complain about SQLite being closed.
                serverManager.stopServer();
                break;
            }
        }

        Path dhDbPath = Paths.get(DH_DB_FILE);
        long dbSize = Files.exists(dhDbPath) ? Files.size(dhDbPath) : 0;
        return new BenchmarkResult(elapsedTime.get(), dbSize);
    }

    private static String formatDuration(long millis) {
        Duration d = Duration.ofMillis(millis);
        long hours = d.toHours();
        long minutes = d.toMinutes() % 60;
        long seconds = d.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
