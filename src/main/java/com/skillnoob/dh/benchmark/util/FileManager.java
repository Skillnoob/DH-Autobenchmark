package com.skillnoob.dh.benchmark.util;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.skillnoob.dh.benchmark.Main;
import com.skillnoob.dh.benchmark.data.BenchmarkConfig;
import com.skillnoob.dh.benchmark.data.BenchmarkResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FileManager {
	private static final String CONFIG_FILE = "dh-benchmark.toml";

	// Default config values
	private static final int DEFAULT_RAM_GB = 8;
	private static final List<String> DEFAULT_SEEDS = List.of("5057296280818819649", "2412466893128258733", "3777092783861568240", "-8505774097130463405", "4753729061374190018");
	private static final String DEFAULT_THREAD_PRESET = "I_PAID_FOR_THE_WHOLE_CPU";
	private static final int DEFAULT_GENERATION_RADIUS = 256;
	private static final String DEFAULT_FABRIC_DOWNLOAD_URL = "https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.14/1.0.3/server/jar";
	private static final String DEFAULT_DH_DOWNLOAD_URL = "https://cdn.modrinth.com/data/uCdwusMi/versions/jkSxZOJh/DistantHorizons-neoforge-fabric-2.3.2-b-1.21.1.jar";
	private static final String DEFAULT_EXTRA_JVM_ARGS = "";
	private static final boolean DEFAULT_DEBUG_MODE = false;
	private static final double DEFAULT_TIMEOUT_SCALE = 1.0;

	/**
	 * Loads the benchmark configuration from a TOML file using NightConfig.
	 */
	public static BenchmarkConfig loadBenchmarkConfig() {
		try (CommentedFileConfig config = CommentedFileConfig.builder(CONFIG_FILE).preserveInsertionOrder().autosave().build()) {
			config.load();

			setDefaultIfMissing(config, "ram_gb", DEFAULT_RAM_GB);
			setDefaultIfMissing(config, "seeds", DEFAULT_SEEDS);
			setDefaultIfMissing(config, "thread_preset", DEFAULT_THREAD_PRESET);
			setDefaultIfMissing(config, "generation_radius", DEFAULT_GENERATION_RADIUS);
			setDefaultIfMissing(config, "fabric_download_url", DEFAULT_FABRIC_DOWNLOAD_URL);
			setDefaultIfMissing(config, "dh_download_url", DEFAULT_DH_DOWNLOAD_URL);
			setDefaultIfMissing(config, "extra_jvm_args", DEFAULT_EXTRA_JVM_ARGS);
			setDefaultIfMissing(config, "debug_mode", DEFAULT_DEBUG_MODE);
			setDefaultIfMissing(config, "timeout_scale", DEFAULT_TIMEOUT_SCALE);

			config.setComment("ram_gb",
					String.format("""
							RAM allocated to the server in GB.
							Default: %s
							""", DEFAULT_RAM_GB)
			);
			config.setComment("seeds",
					String.format("""
							List of world seeds to use for the benchmark.
							Default: %s
							""", DEFAULT_SEEDS
					)
			);
			config.setComment("thread_preset",
					String.format("""
							This controls the Distant Horizons thread preset used when generating chunks.
							Available presets are: MINIMAL_IMPACT, LOW_IMPACT, BALANCED, AGGRESSIVE, I_PAID_FOR_THE_WHOLE_CPU.
							Default: %s
							""", DEFAULT_THREAD_PRESET
					)
			);
			config.setComment("generation_radius",
					String.format("""
							The radius in chunks of the area to generate around the center of the world.
							Default: %s
							""", DEFAULT_GENERATION_RADIUS
					));
			config.setComment("fabric_download_url",
					String.format("""
							The URL to download the Fabric server jar from.
							Default: %s
							""", DEFAULT_FABRIC_DOWNLOAD_URL
					)
			);
			config.setComment("dh_download_url",
					String.format("""
							The URL to download the Distant Horizons mod jar from.
							Default: %s
							""", DEFAULT_DH_DOWNLOAD_URL
					)
			);
			config.setComment("extra_jvm_args",
					String.format("""
							Extra JVM arguments to pass to the server.
							Example: "-XX:+UseZGC -XX:AllocatePrefetchStyle=1 -XX:-ZProactive"
							Note: You don't need to include the -Xmx flag here, it is set automatically based on the ram_gb config value.
							Default: %s
							""", DEFAULT_EXTRA_JVM_ARGS
					)
			);
			config.setComment("debug_mode",
					String.format("""
							Enables the debug mode.
							This will print the server log instead of a progress bar, which can be used for debugging issues with the minecraft server.
							Default: %s
							""", DEFAULT_DEBUG_MODE
					)
			);
			config.setComment("timeout_scale",
					String.format("""
							The timeout scale to use.
							This can be used to increase the timeout of starting/shutting down the server if the hardware is too slow or large mods are installed.
							Default: %s
							""", DEFAULT_TIMEOUT_SCALE
					)
			);

			int ramGb = config.getInt("ram_gb");
			List<String> seeds = config.get("seeds");
			String threadPreset = config.get("thread_preset");
			int generationRadius = config.getInt("generation_radius");
			String fabricDownloadUrl = config.get("fabric_download_url");
			String dhDownloadUrl = config.get("dh_download_url");
			String extraJvmArgs = config.get("extra_jvm_args");
			boolean debugMode = config.get("debug_mode");
			double timeoutScale = config.get("timeout_scale");

			return new BenchmarkConfig(ramGb, seeds, threadPreset, generationRadius, fabricDownloadUrl, dhDownloadUrl, extraJvmArgs, debugMode, timeoutScale);
		}
	}

	/**
	 * Set a default value in the config if the key is missing.
	 */
	private static <T> void setDefaultIfMissing(CommentedFileConfig config, String key, T defaultValue) {
		if (!config.contains(key)) {
			config.set(key, defaultValue);
		}
	}

	/**
	 * Writes benchmark results to a CSV file.
	 */
	public static void writeResultsToCSV(String filePath, List<BenchmarkResult> results, String avgTime, long avgCps, long avgDbSizeInMB, int ramGB) throws IOException {
		try (PrintWriter writer = new PrintWriter(filePath)) {
			StringBuilder data = new StringBuilder();

			data.append(ramGB).append("GB,");

			for (BenchmarkResult result : results) {
				data.append(Main.formatDuration(result.elapsedTime())).append(",");
			}
			data.append(avgTime).append(",");
			data.append(avgCps).append(",");

			// Add DB sizes
			for (BenchmarkResult result : results) {
				double dbSizeInMB = result.dbSize() / (1024.0 * 1024.0);
				data.append(Math.round(dbSizeInMB)).append("MB,");
			}
			data.append(avgDbSizeInMB).append("MB");

			writer.println(data);
		}
	}

	/**
	 * Updates the benchmark results csv with the hardware information.
	 */
	public static void writeHardwareInfoToCSV(String filePath, List<String> hardwareInfo) throws IOException {
		Path csvPath = Paths.get(filePath);
		if (!csvPath.toFile().exists()) {
			System.out.println("The results csv does not exist, you need to run the benchmark first.");
			return;
		}

		List<String> existingLines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
		List<String> newLines = new ArrayList<>();

		String newDataRow = hardwareInfo.get(0) + "," + hardwareInfo.get(1) + "," + hardwareInfo.get(2) + "," + existingLines.getFirst();
		newLines.add(newDataRow);

		Files.write(csvPath, newLines, StandardCharsets.UTF_8);
		System.out.println("Hardware information added to results file");
	}

	/**
	 * Ensures that a directory exists; if not, creates it.
	 */
	public static void ensureDirectoryExists(String directoryPath) throws IOException {
		Path path = Paths.get(directoryPath);
		if (!Files.exists(path)) {
			Files.createDirectories(path);
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
						System.err.println("Warning: Failed to delete " + path + ":");
						e.printStackTrace();
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

	/**
	 * Copies datapack files from the "custom-datapacks" directory to the world datapack directory.
	 */
	public static void copyDatapacks(String sourceDir, String targetDir) throws IOException {
		Path sourcePath = Paths.get(sourceDir);
		Path targetPath = Paths.get(targetDir);

		ensureDirectoryExists(sourceDir);
		ensureDirectoryExists(targetDir);

		if (Files.list(sourcePath).findFirst().isEmpty()) {
			return;
		}

		try (Stream<Path> paths = Files.walk(sourcePath)) {
			paths.filter(path -> !path.equals(sourcePath))  // Skip the root directory itself
					.forEach(source -> {
						Path relativePath = sourcePath.relativize(source);
						Path target = targetPath.resolve(relativePath);

						try {
							if (Files.isDirectory(source)) {
								Files.createDirectories(target);
							} else {
								// Ensure parent directories exist
								Files.createDirectories(target.getParent());
								Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
							}
						} catch (IOException e) {
							System.err.println("Failed to copy " + relativePath + ":");
							e.printStackTrace();
						}
					});
		}
	}

	/**
	 * Loads the benchmark progress from a file.
	 * Returns a list of completed seed indices.
	 */
	public static List<Integer> loadBenchmarkProgress(String progressFile) {
		Path path = Paths.get(progressFile);
		List<Integer> completedSeeds = new ArrayList<>();

		if (!Files.exists(path)) {
			return completedSeeds;
		}

		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("SEED_COMPLETE,")) {
					String[] parts = line.split(",");
					if (parts.length >= 2) {
						try {
							int seedIndex = Integer.parseInt(parts[1].trim());
							completedSeeds.add(seedIndex);
						} catch (NumberFormatException ignored) {
						}
					}
				}
			}
		} catch (IOException e) {
			System.err.println("Error loading benchmark progress:");
			e.printStackTrace();
		}

		return completedSeeds;
	}

	/**
	 * Saves seed completion and result data to the progress file.
	 */
	public static void saveSeedResult(String progressFile, int seedIndex, BenchmarkResult result) {
		Path path = Paths.get(progressFile);

		try (PrintWriter writer = new PrintWriter(new FileWriter(path.toFile(), true))) {
			// Format: SEED_COMPLETE:index:elapsedTimeNanos:dbSize:avgCps
			writer.println("SEED_COMPLETE," + seedIndex + "," +
					result.elapsedTime() + "," +
					result.dbSize() + "," +
					result.averageCps());
		} catch (IOException e) {
			System.err.println("Error saving benchmark progress:");
			e.printStackTrace();
		}
	}

	/**
	 * Loads saved result data for a specific seed.
	 */
	public static List<BenchmarkResult> loadSeedResults(String progressFile, int startSeedIndex) throws IOException {
		Path path = Paths.get(progressFile);
		List<BenchmarkResult> results = new ArrayList<>();

		if (!Files.exists(path)) {
			return results;
		}

		List<String> lines = Files.readAllLines(path);

		lines.stream()
				.filter(line -> line.startsWith("SEED_COMPLETE,"))
				.forEach(line -> {
					String[] parts = line.split(",");
					if (parts.length == 5) {
						try {
							int index = Integer.parseInt(parts[1].trim());
							if (index < startSeedIndex) {
								long elapsedTime = Long.parseLong(parts[2].trim());
								long dbSize = Long.parseLong(parts[3].trim());
								long avgCps = Long.parseLong(parts[4].trim());
								results.add(new BenchmarkResult(elapsedTime, dbSize, avgCps));
							}
						} catch (NumberFormatException ignored) {
						}
					}
				});

		return results;
	}

	/**
	 * Clears the benchmark progress file.
	 */
	public static void clearBenchmarkProgress(String progressFile) {
		Path path = Paths.get(progressFile);

		try {
			if (Files.exists(path)) {
				Files.delete(path);
			}
		} catch (IOException e) {
			System.err.println("Error clearing benchmark progress:");
			e.printStackTrace();
		}
	}
}
