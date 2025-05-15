package com.skillnoob.dh.benchmark.util;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PhysicalMemory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
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
                .replaceFirst("\\s+(\\d+-Core\\s+Processor|CPU|Processor)$", "")
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
                : "";

        return String.format("%sGB %s%s", sizeString, memoryType, memorySpeed);
    }

    private static String getCurrentDriveModel() {
        try {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                // Windows: use PowerShell Storage module
                char drive = Paths.get("").toAbsolutePath().getRoot().toString().charAt(0);
                List<String> cmd = List.of(
                        "powershell.exe",
                        "-NoProfile",
                        "-Command",
                        "$d=Get-Partition -DriveLetter '" + drive + "' | Select-Object -ExpandProperty DiskNumber; " +
                                "Get-PhysicalDisk -DeviceNumber $d | Select-Object -ExpandProperty FriendlyName"
                );
                return runCommand(cmd).trim();
            } else if (os.contains("mac")) {
                Path currentPath = Paths.get(".").toAbsolutePath();

                // This iterates over every partition on every drive and finds the longest mount point
                // that matches the current path.
                // Reason being that for example, "/" would match "/mnt/drive1"
                return systemInfo.getHardware().getDiskStores().stream()
                        .flatMap(disk -> disk.getPartitions().stream()
                                .filter(part -> part.getMountPoint() != null)
                                .map(part -> {
                                    String mount = part.getMountPoint();
                                    return new Object() {
                                        final String mountPoint = mount;
                                        final String model = disk.getModel();
                                    };
                                }))
                        .filter(part -> currentPath.toString().startsWith(part.mountPoint))
                        .max((partA, partB) -> partA.mountPoint.length() - partB.mountPoint.length())
                        .map(part -> part.model)
                        .orElse("Unknown");
            } else if (os.contains("linux")) {
                List<String> findMount = List.of("bash", "-lc", "findmnt -T . -n -o SOURCE");
                String mount = runCommand(findMount).trim();
                List<String> pkName = List.of("bash", "-lc", "lsblk -no PKNAME " + mount);
                String parentMount = runCommand(pkName).trim();
                List<String> modelName = List.of("bash", "-lc", "lsblk -dn -o MODEL /dev/" + parentMount);
                return runCommand(modelName).trim();
            } else {
                return "Unknown";
            }
        } catch (IOException e) {
            System.err.println("Error getting current drive model:");
            e.printStackTrace();
            return "Unknown";
        }
    }

    private static String runCommand(List<String> cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
