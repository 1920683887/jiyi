package com.jiyi.infra.util;

public class WinRateCalculator {
    private static final double K = 200;

    public static double fromScore(int score, boolean isRed) {
        if (score > 10000) return 1.0;
        if (score < -10000) return 0.0;
        double adjusted = isRed ? score : -score;
        return 1.0 / (1.0 + Math.pow(10, -adjusted / K));
    }

    public static String format(int score, boolean isRed) {
        double wr = fromScore(score, isRed);
        if (wr >= 1.0) return "必胜";
        if (wr <= 0.0) return "必败";
        if (wr >= 0.999) return "99.9%";
        return String.format("%.1f%%", wr * 100);
    }
}
