package com.skillnoob.dh.benchmark.util;

import me.tongfei.progressbar.DefaultProgressBarRenderer;
import me.tongfei.progressbar.ProgressBarStyle;
import me.tongfei.progressbar.ProgressState;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Function;

public class NoFractionProgressBarRenderer extends DefaultProgressBarRenderer {
    public NoFractionProgressBarRenderer(ProgressBarStyle style, String unitName, long unitSize, boolean isSpeedShown, DecimalFormat speedFormat, ChronoUnit speedUnit, boolean isEtaShown, Function<ProgressState, Optional<Duration>> eta) {
        super(style, unitName, unitSize, isSpeedShown, speedFormat, speedUnit, isEtaShown, eta);
    }

    // Copied with small modification from me.tongfei.progressbar.Utils#linearEta(ProgressState) because the class is private
    public static Optional<Duration> linearEta(ProgressState progress) {
        if (progress.getCurrent() - progress.getStart() == 0) {
            return Optional.empty();
        } else {
            return Optional.of(
                    progress.getElapsedAfterStart()
                            .dividedBy(progress.getCurrent() - progress.getStart())
                            .multipliedBy(progress.getMax() - progress.getCurrent())
            );
        }
    }

    @Override
    public String render(ProgressState state, int maxLength) {
        String base = super.render(state, maxLength);
        // Remove the fraction part
        return base.replaceAll(" \\d+/\\d+", "");
    }
}
