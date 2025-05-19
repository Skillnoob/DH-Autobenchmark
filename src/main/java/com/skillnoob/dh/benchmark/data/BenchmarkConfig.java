package com.skillnoob.dh.benchmark.data;

import java.util.List;

public record BenchmarkConfig(
        int ramGb,
        List<String> seeds,
        String threadPreset,
        int generationRadius,
        String fabricDownloadUrl,
        String dhDownloadUrl,
        List<String> extraJvmArgs,
        boolean debugMode,
        double timeoutScale
) {
}
