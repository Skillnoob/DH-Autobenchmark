package com.skillnoob.dh.benchmark.util;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FileManager {
    private static final String CONFIG_FILE = "dh-benchmark.toml";

    // Default config values
    private static final int DEFAULT_RAM_GB = 8;
    private static final long[] DEFAULT_SEEDS = {5057296280818819649L, 2412466893128258733L, 3777092783861568240L, -8505774097130463405L, 4753729061374190018L};
    private static final String DEFAULT_THREAD_PRESET = "I_PAID_FOR_THE_WHOLE_CPU";
    private static final int DEFAULT_GENERATION_RADIUS = 128;
    private static final String DEFAULT_FABRIC_DOWNLOAD_URL = "https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.12/1.0.3/server/jar";
    private static final String DEFAULT_DH_DOWNLOAD_URL = "https://cdn.modrinth.com/data/uCdwusMi/versions/jkSxZOJh/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar";
    private static final List<String> DEFAULT_EXTRA_JVM_ARGS = new ArrayList<>();

    /**
     * Loads the benchmark configuration from a TOML file using NightConfig.
     */
    public static BenchmarkConfig loadBenchmarkConfig() {
        try (CommentedFileConfig config = CommentedFileConfig.builder(CONFIG_FILE).preserveInsertionOrder().autosave().build()) {
            config.load();

            // Set default values if not present
            setDefaultIfMissing(config, "ram_gb", DEFAULT_RAM_GB);
            setDefaultIfMissing(config, "seeds", DEFAULT_SEEDS);
            setDefaultIfMissing(config, "thread_preset", DEFAULT_THREAD_PRESET);
            setDefaultIfMissing(config, "generation_radius", DEFAULT_GENERATION_RADIUS);
            setDefaultIfMissing(config, "fabric_download_url", DEFAULT_FABRIC_DOWNLOAD_URL);
            setDefaultIfMissing(config, "dh_download_url", DEFAULT_DH_DOWNLOAD_URL);
            setDefaultIfMissing(config, "extra_jvm_args", DEFAULT_EXTRA_JVM_ARGS);

            config.setComment("ram_gb",
                    String.format("""
                            RAM allocated to the server in GB.
                            Default is: %sGB.
                            """, DEFAULT_RAM_GB)
            );
            config.setComment("seeds",
                    String.format("""
                            List of world seeds to use for the benchmark.
                            Default is: %s
                            """, Arrays.toString(DEFAULT_SEEDS)
                    )
            );
            config.setComment("thread_preset",
                    String.format("""
                            This controls the Distant Horizons thread preset used when generating chunks.
                            Available presets are: MINIMAL_IMPACT, LOW_IMPACT, BALANCED, AGGRESSIVE, I_PAID_FOR_THE_WHOLE_CPU.
                            Default is: %s.
                            """, DEFAULT_THREAD_PRESET
                    )
            );
            config.setComment("generation_radius",
                    String.format("""
                            The radius in chunks of the area to generate around the center of the world.
                            Default is: %s.
                            """, DEFAULT_GENERATION_RADIUS
                    ));
            config.setComment("fabric_download_url",
                    String.format("""
                            The URL to download the Fabric server jar from.
                            Default is: %s.
                            """, DEFAULT_FABRIC_DOWNLOAD_URL
                    )
            );
            config.setComment("dh_download_url",
                    String.format("""
                            The URL to download the Distant Horizons mod jar from.
                            Default is: %s.
                            """, DEFAULT_DH_DOWNLOAD_URL
                    )
            );
            config.setComment("extra_jvm_args",
                    String.format("""
                            Extra JVM arguments to pass to the server.
                            Example: ["arg1", "arg2"]
                            Default is: %s.
                            """, DEFAULT_EXTRA_JVM_ARGS
                    )
            );

            // Extract configuration values
            int ramGb = config.getInt("ram_gb");
            long[] seeds = config.<List<Number>>get("seeds").stream().mapToLong(Number::longValue).toArray();
            String threadPreset = config.get("thread_preset");
            int generationRadius = config.getInt("generation_radius");
            String fabricDownloadUrl = config.get("fabric_download_url");
            String dhDownloadUrl = config.get("dh_download_url");
            List<String> extraJvmArgs = config.get("extra_jvm_args");

            return new BenchmarkConfig(ramGb, seeds, threadPreset, generationRadius, fabricDownloadUrl, dhDownloadUrl, extraJvmArgs);
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

