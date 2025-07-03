package com.skillnoob.dh.benchmark.util;

import oshi.SystemInfo;
import oshi.hardware.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HardwareInfo {
	private static final SystemInfo systemInfo = new SystemInfo();

	public static List<String> getHardwareInfo() {
		return List.of(
				getCpuInfo(),
				getRamInfo(),
				getCurrentDriveModel()
		);
	}

	// Gets CPU information
	private static String getCpuInfo() {
		HardwareAbstractionLayer hardware = systemInfo.getHardware();

		CentralProcessor cpu = hardware.getProcessor();
		String rawName = cpu.getProcessorIdentifier().getName();
		String cleanedCpuName = rawName
				.replaceAll("\\b\\d+(?:st|nd|rd|th) Gen\\s+", "")
				.replaceAll("\\(R\\)|\\(TM\\)", "")
				.replaceAll("\\s*@\\s*[0-9]+(?:\\.[0-9]+)?\\s*GHz", "")
				.replaceAll("\\s+\\d+-Core\\s+Processor|\\s+Processor|\\bCPU\\b", "")
				.replaceAll(" {2,}", " ")
				.trim();

		return String.format("%s %dC/%dT", cleanedCpuName,
				cpu.getPhysicalProcessorCount(),
				cpu.getLogicalProcessorCount());
	}

	// Gets RAM information
	private static String getRamInfo() {
		GlobalMemory memory = systemInfo.getHardware().getMemory();

		List<PhysicalMemory> modules = memory.getPhysicalMemory();
		// MacOS doesn't report this for some reason
		long installedBytes = modules.stream()
				.mapToLong(PhysicalMemory::getCapacity)
				.sum();

		double gb;
		if (installedBytes > 0) {
			gb = installedBytes / (1024.0 * 1024.0 * 1024.0);
		} else {
			// Fallback to OS‑reported usable memory, because MacOS
			gb = memory.getTotal() / (1024.0 * 1024.0 * 1024.0);
		}

		String sizeString = String.valueOf(Math.round(gb));

		// Memory type, MacOS doesn't give us this either
		String memoryType = modules.stream()
				.map(PhysicalMemory::getMemoryType)
				.filter(type -> type != null && !type.equalsIgnoreCase("Unknown") && !type.isBlank())
				.findFirst().orElse("DDR?");

		// As usual, MacOS says no to memory speed
		long maxSpeedHz = modules.stream()
				.mapToLong(PhysicalMemory::getClockSpeed)
				.max().orElse(0);
		String memorySpeed = maxSpeedHz > 0
				? String.format(" %.0f MT/s", maxSpeedHz / 1_000_000.0)
				: "? MT/s";

		return String.format("%sGB %s%s", sizeString, memoryType, memorySpeed);
	}

	private static String getCurrentDriveModel() {
		String driveModel = "Unknown";
		try {
			String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
			if (os.contains("win")) {
				char drive = Paths.get(".").toAbsolutePath().getRoot().toString().charAt(0);
				List<String> cmd = List.of(
						"powershell.exe",
						"-NoProfile",
						"-Command",
						"$d=Get-Partition -DriveLetter '" + drive + "' | Select-Object -ExpandProperty DiskNumber; " +
								"Get-PhysicalDisk -DeviceNumber $d | Select-Object -ExpandProperty FriendlyName"
				);
				driveModel = runCommand(cmd).trim();
			} else if (os.contains("linux")) {
				List<String> findMount = List.of("bash", "-lc",
						"findmnt -T . -n --nofsroot -o SOURCE || findmnt -T . -n -o SOURCE");
				String mount = runCommand(findMount).trim();

				// for older util-linux versions that don't have --nofsroot
				mount = mount.replaceAll("\\[.*$", "");

				List<String> pkName = List.of("bash", "-lc", "lsblk -no PKNAME " + mount);
				String parentMount = runCommand(pkName).trim();

				List<String> modelName = List.of("bash", "-lc", "lsblk -dn -o MODEL /dev/" + parentMount);
				driveModel = runCommand(modelName).trim();
			}

			if (driveModel.equals("Unknown")) {
				// Fallback if the above methods fail and always used for MacOS
				driveModel = getCurrentDriveModelBackup();
			}
		} catch (IOException e) {
			System.err.println("Error getting current drive model:");
			e.printStackTrace();
			return "Unknown";
		}

		return driveModel;
	}

	/*
	 * This iterates over every partition on every drive and finds the longest mount point
	 * that matches the current path.
	 * Reason being that for example, "/" would match "/mnt/drive1"
	 */
	private static String getCurrentDriveModelBackup() {
		String currentPath = Paths.get(".").toAbsolutePath().toString();
		String bestModel = "Unknown";
		int bestMatchLength = -1;

		for (HWDiskStore disk : systemInfo.getHardware().getDiskStores()) {
			for (HWPartition partition : disk.getPartitions()) {
				if (partition.getMountPoint() != null) {
					String mount = unescapeMountPoint(partition.getMountPoint());

					if (currentPath.startsWith(mount) && mount.length() > bestMatchLength) {
						bestMatchLength = mount.length();
						bestModel = disk.getModel();
					}
				}
			}
		}

		return bestModel;
	}

	private static String runCommand(List<String> cmd) throws IOException {
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		Process process = pb.start();

		try {
			if (!process.waitFor(10, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return "Unknown";
			}

			if (process.exitValue() != 0) {
				return "Unknown";
			}

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String result = reader.lines().collect(Collectors.joining("\n"));
				return result.isBlank() ? "Unknown" : result.trim();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "Unknown";
		}
	}

	/*
	 * Unescapes mount points that are escaped with octal codes.
	 */
	private static String unescapeMountPoint(String raw) {
		Pattern p = Pattern.compile("\\\\([0-7]{3})");
		Matcher m = p.matcher(raw);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			int code = Integer.parseInt(m.group(1), 8);
			m.appendReplacement(sb, Matcher.quoteReplacement(Character.toString((char) code)));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
