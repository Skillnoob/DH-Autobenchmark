package com.skillnoob.dh.benchmark.data;

public record BenchmarkConfig(
        int ramGb,
        long[] seeds,
        String threadPreset,
        int generationRadius,
        String fabricDownloadUrl,
        String dhDownloadUrl
) {
}
