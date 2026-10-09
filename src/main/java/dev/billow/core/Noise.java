package dev.billow.core;

public final class Noise {
    private Noise() {
    }

    public static int hash(int x, int y, int z, int seed) {
        int h = seed;
        h ^= x * 0x27d4eb2d;
        h ^= y * 0x165667b1;
        h ^= z * 0x1b873593;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    private static float unit(int x, int y, int z, int seed) {
        return (hash(x, y, z, seed) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double value2(double x, double z, int seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = fade(x - x0);
        double tz = fade(z - z0);
        double a = lerp(unit(x0, 0, z0, seed), unit(x0 + 1, 0, z0, seed), tx);
        double b = lerp(unit(x0, 0, z0 + 1, seed), unit(x0 + 1, 0, z0 + 1, seed), tx);
        return lerp(a, b, tz);
    }

    public static double value3(double x, double y, double z, int seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double tx = fade(x - x0);
        double ty = fade(y - y0);
        double tz = fade(z - z0);
        double c00 = lerp(unit(x0, y0, z0, seed), unit(x0 + 1, y0, z0, seed), tx);
        double c10 = lerp(unit(x0, y0 + 1, z0, seed), unit(x0 + 1, y0 + 1, z0, seed), tx);
        double c01 = lerp(unit(x0, y0, z0 + 1, seed), unit(x0 + 1, y0, z0 + 1, seed), tx);
        double c11 = lerp(unit(x0, y0 + 1, z0 + 1, seed), unit(x0 + 1, y0 + 1, z0 + 1, seed), tx);
        return lerp(lerp(c00, c10, ty), lerp(c01, c11, ty), tz);
    }

    public static double fbm2(double x, double z, int octaves, int seed) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        for (int i = 0; i < octaves; i++) {
            sum += value2(x, z, seed + i * 1013) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            x = x * 2.03 + 17.1;
            z = z * 2.03 - 9.7;
        }
        return sum / total;
    }

    public static double fbm3(double x, double y, double z, int octaves, int seed) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        for (int i = 0; i < octaves; i++) {
            sum += value3(x, y, z, seed + i * 7919) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            x = x * 2.01 + 5.3;
            y = y * 2.01 - 3.1;
            z = z * 2.01 + 11.9;
        }
        return sum / total;
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }
}
