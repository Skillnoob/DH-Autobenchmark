package com.skillnoob.dh.benchmark.util;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.skillnoob.dh.benchmark.Main;
import com.skillnoob.dh.benchmark.data.BenchmarkConfig;
import com.skillnoob.dh.benchmark.data.BenchmarkResult;

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
        try (FileConfig config = FileConfig.builder(CONFIG_FILE).preserveInsertionOrder().autosave().build()) {
            config.load();

            // Set default values if not present
            setDefaultIfMissing(config, "ram_gb", 8);
            setDefaultIfMissing(config, "seeds", List.of(5057296280818819649L, 2412466893128258733L, 3777092783861568240L, -8505774097130463405L, 4753729061374190018L));
            setDefaultIfMissing(config, "thread_preset", "I_PAID_FOR_THE_WHOLE_CPU");
            setDefaultIfMissing(config, "generation_radius", 128);
            setDefaultIfMissing(config, "fabric_download_url", "https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.12/1.0.3/server/jar");
            setDefaultIfMissing(config, "dh_download_url", "https://cdn.modrinth.com/data/uCdwusMi/versions/jkSxZOJh/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar");

            // Extract configuration values
            int ramGb = config.getInt("ram_gb");
            long[] seeds = config.<List<Number>>get("seeds").stream().mapToLong(Number::longValue).toArray();
            String threadPreset = config.get("thread_preset");
            int generationRadius = config.getInt("generation_radius");
            String fabricDownloadUrl = config.get("fabric_download_url");
            String dhDownloadUrl = config.get("dh_download_url");

            return new BenchmarkConfig(ramGb, seeds, threadPreset, generationRadius, fabricDownloadUrl, dhDownloadUrl);
        }
    }

    /**
     * Helper method to set a default value in the config if the key is missing.
     */
    private static <T> void setDefaultIfMissing(FileConfig config, String key, T defaultValue) {
        if (!config.contains(key)) {
            config.set(key, defaultValue);
        }
    }

    /**
     * Writes benchmark results to a CSV file.
     */
    public static void writeResultsToCSV(String filePath, long[] seeds, List<BenchmarkResult> results, String avgTime, long avgDbSizeInMB, int ramGB) throws IOException {
        try (PrintWriter writer = new PrintWriter(filePath)) {
            // Write header row
            StringBuilder header = new StringBuilder();

            for (int i = 0; i < seeds.length; i++) {
                header.append("Run ").append(i + 1).append("\t");
            }
            header.append("Average\t");

            // Add DB size columns
            for (int i = 0; i < seeds.length; i++) {
                header.append("DB Size Run ").append(i + 1).append("\t");
            }
            header.append("DB Size Average\t");
            header.append("Allocated RAM");
            writer.println(header);

            // Write data row
            StringBuilder data = new StringBuilder();

            // Add run times
            for (BenchmarkResult result : results) {
                data.append(Main.formatDuration(result.elapsedTime())).append("\t");
            }
            data.append(avgTime).append("\t");

            // Add DB sizes
            for (BenchmarkResult result : results) {
                double dbSizeInMB = result.dbSize() / (1024.0 * 1024.0);
                data.append(Math.round(dbSizeInMB)).append("MB\t");
            }
            data.append(avgDbSizeInMB).append("MB\t");
            data.append(ramGB).append("GB");

            writer.println(data);
        }
    }

    /**
     * Updates the benchmark results csv with the hardware information.
     */
    public static void writeHardwareInfoToCSV(String filePath, List<String> hardwareInfo) throws IOException {
        Path csvPath = Paths.get(filePath);
        List<String> lines = Files.exists(csvPath) ?
            Files.readAllLines(csvPath, StandardCharsets.UTF_8) :
            new ArrayList<>();

        lines.add("");

        lines.add("CPU\tRAM\tDRIVE");
        lines.add(hardwareInfo.get(0) + "\t" + hardwareInfo.get(1) + "\t" + hardwareInfo.get(2));

        Files.write(csvPath, lines, StandardCharsets.UTF_8);
        System.out.println("Hardware information added to results file");
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

