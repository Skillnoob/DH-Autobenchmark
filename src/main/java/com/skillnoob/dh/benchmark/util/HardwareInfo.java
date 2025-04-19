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
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        GlobalMemory memory = hardware.getMemory();

        // Calculate total installed memory in GB
        long installedGiB = memory.getPhysicalMemory().stream()
                .mapToLong(PhysicalMemory::getCapacity)
                .sum() / (1024 * 1024 * 1024);

        // Get memory speed, requires elevated privileges on linux
        long maxSpeedHz = memory.getPhysicalMemory().stream()
                .mapToLong(PhysicalMemory::getClockSpeed)
                .max().orElse(-1);
        String memorySpeed = maxSpeedHz > 0
                ? String.format(" %.0fMT/s", maxSpeedHz / 1_000_000.0)
                : "";

        // Identify memory type (DDR4, etc.)
        String memoryType = memory.getPhysicalMemory().stream()
                .map(PhysicalMemory::getMemoryType)
                .filter(type -> type != null && !type.equalsIgnoreCase("Unknown") && !type.isBlank())
                .toList()
                .get(0);

        return String.format("%d GB %s%s", installedGiB, memoryType, memorySpeed);
    }

    private static String getCurrentDriveModel() {
        Path currentPath = Paths.get(".").toAbsolutePath();

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
                .filter(item -> currentPath.toString().startsWith(item.mountPoint))
                .max((a, b) -> a.mountPoint.length() - b.mountPoint.length())
                .map(item -> item.model)
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
