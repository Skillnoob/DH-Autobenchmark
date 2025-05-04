package com.skillnoob.dh.benchmark.util;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PhysicalMemory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        String cleanedCpuName = rawName.replaceFirst("\\s+(\\d+-Core\\s+Processor|CPU|Processor)$", "");

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
                .filter(t -> t != null && !t.equalsIgnoreCase("Unknown") && !t.isBlank())
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

    // Gets the drive model the program is run from
    private static String getCurrentDriveModel() {
        Path currentPath = Paths.get(".").toAbsolutePath();

        // This iterates over every partition on every drive and finds the longest mount point
        // that matches the current path.
        // Reason being that for example, "/" would match "/mnt/drive1"
        return systemInfo.getHardware().getDiskStores().stream()
                .flatMap(disk -> disk.getPartitions().stream()
                        .filter(part -> part.getMountPoint() != null)
                        .map(part -> {
                            String mount = unescapeMountPoint(part.getMountPoint());
                            return new Object() {
                                final String mountPoint = mount;
                                final String model = disk.getModel();
                            };
                        }))
                .filter(part -> currentPath.toString().startsWith(part.mountPoint))
                .max((partA, partB) -> partA.mountPoint.length() - partB.mountPoint.length())
                .map(part -> part.model)
                .orElse("Unknown");
    }

    // Unescapes the mount point string, otherwise we have spaces that are "\040"
    private static String unescapeMountPoint(String raw) {
        Pattern p = Pattern.compile("\\\\([0-7]{3})");
        Matcher m = p.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            // parse octal and append the actual char
            int code = Integer.parseInt(m.group(1), 8);
            m.appendReplacement(sb, Matcher.quoteReplacement(Character.toString((char) code)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
