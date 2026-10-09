package dev.billow.core;

public final class CloudField {
    private final int seed;
    private final double threshold;
    private final double maxHeight;

    public CloudField(int seed, double coverage, double maxHeight) {
        this.seed = seed;
        double c = Math.max(0, Math.min(1, coverage));
        this.threshold = c <= 0 ? Double.POSITIVE_INFINITY : 0.5 + 0.35 * (1 - 2 * c);
        this.maxHeight = maxHeight;
    }

    public double maxHeight() {
        return maxHeight;
    }

    public double[] column(double x, double z) {
        double base = Noise.fbm2(x / 180.0, z / 180.0, 4, seed);
        double strength = Double.isInfinite(threshold) ? 0 : Noise.smoothstep(threshold, threshold + 0.14, base);
        double puff = Noise.fbm2(x / 48.0, z / 48.0, 3, seed + 31);
        double top = maxHeight * Math.pow(strength, 0.8) * (0.3 + 0.95 * puff);
        double bottom = maxHeight * 0.08 * Noise.value2(x / 70.0, z / 70.0, seed + 57);
        return new double[]{top, bottom, strength};
    }

    public double density(double[] column, double x, double y, double z) {
        double top = column[0];
        double bottom = column[1];
        double strength = column[2];
        double shape = Math.min(y - bottom, top - y);
        if (strength <= 0) {
            return shape;
        }
        double detail = (Noise.fbm3(x / 20.0, y / 12.0, z / 20.0, 3, seed + 101) - 0.5) * 2.0;
        double lift = Math.max(0, Math.min(1, (y - bottom) / Math.max(1, top - bottom)));
        return shape + detail * maxHeight * 0.16 * strength * (0.35 + 0.65 * lift);
    }

    public double density(double x, double y, double z) {
        return density(column(x, z), x, y, z);
    }
}
