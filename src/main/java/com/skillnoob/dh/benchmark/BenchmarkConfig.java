package com.skillnoob.dh.benchmark;

public record BenchmarkConfig(
        int ramGb,
        long[] seeds,
        String threadPreset,
        int generationRadius,
        String fabricDownloadUrl,
        String dhDownloadUrl
) {
}
