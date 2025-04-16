package com.skillnoob.dh.benchmark;

import com.electronwill.nightconfig.core.file.FileConfig;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FileManager {

    private static final String CONFIG_FILE = "dh-benchmark.toml";

    /**
     * Loads the benchmark configuration from a TOML file using NightConfig.
     */
    public static BenchmarkConfig loadBenchmarkConfig() {
        try (FileConfig config = FileConfig.builder(CONFIG_FILE)
                .preserveInsertionOrder()
                .autosave()
                .build()) {
            config.load();

            if (!config.contains("ram_gb")) {
                config.set("ram_gb", 8);
            }
            if (!config.contains("seeds")) {
                config.set("seeds", List.of(
                        5057296280818819649L,
                        2412466893128258733L,
                        3777092783861568240L,
                        -8505774097130463405L,
                        4753729061374190018L));
            }
            if (!config.contains("thread_preset")) {
                config.set("thread_preset", "I_PAID_FOR_THE_WHOLE_CPU");
            }
            if (!config.contains("generation_radius")) {
                config.set("generation_radius", 128);
            }
            if (!config.contains("fabric_download_url")) {
                config.set("fabric_download_url", "https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.12/1.0.3/server/jar");
            }
            if (!config.contains("dh_download_url")) {
                config.set("dh_download_url", "https://cdn.modrinth.com/data/uCdwusMi/versions/jkSxZOJh/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar");
            }

            int ramGb = config.getInt("ram_gb");

            List<Long> seedsList = config.get("seeds");
            long[] seeds = new long[seedsList.size()];
            for (int i = 0; i < seedsList.size(); i++) {
                seeds[i] = seedsList.get(i);
            }
            String threadPreset = config.get("thread_preset");
            int generationRadius = config.getInt("generation_radius");
            String fabricDownloadUrl = config.get("fabric_download_url");
            String dhDownloadUrl = config.get("dh_download_url");

            return new BenchmarkConfig(ramGb, seeds, threadPreset, generationRadius, fabricDownloadUrl, dhDownloadUrl);
        }
    }

    /**
     * Writes benchmark results to a CSV file.
     */
    public static void writeResultsToCSV(long[] seeds, List<BenchmarkResult> results, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(filePath)) {
            writer.println("Seed,Elapsed Time,Database Size (MB)");

            for (int i = 0; i < seeds.length; i++) {
                BenchmarkResult res = results.get(i);
                writer.println(seeds[i] + "," + res.elapsedTime() + "," + res.dbSize());
            }
        }
        System.out.println("Benchmark results saved to " + filePath);
    }


    /**
     * Ensures that a directory exists; if not, creates it.
     */
    public static void ensureDirectoryExists(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            System.out.println("Created directory: " + directoryPath);
        }
    }

    /**
     * Recursively deletes a directory.
     */
    public static void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Warning: Failed to delete " + path + ": " + e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Updates a specific line in a configuration file.
     */
    public static void updateConfigLine(Path filePath, String linePrefix, String newLine) throws IOException {
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<String> newLines = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith(linePrefix)) {
                newLines.add(newLine);
            } else {
                newLines.add(line);
            }
        }
        Files.write(filePath, newLines, StandardCharsets.UTF_8);
    }
}
