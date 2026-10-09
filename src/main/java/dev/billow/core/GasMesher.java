package dev.billow.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class GasMesher {
    public static final int VERTEX_BYTES = 24;
    private static final float[][] CORNERS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

    private GasMesher() {
    }

    public static final class Puffs implements Geometry {
        private float[] data = new float[64 * 6];
        private int count;

        void add(float x, float y, float z, float radius, float shade, float alpha) {
            if ((count + 1) * 6 > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            int p = count * 6;
            data[p] = x;
            data[p + 1] = y;
            data[p + 2] = z;
            data[p + 3] = radius;
            data[p + 4] = shade;
            data[p + 5] = alpha;
            count++;
        }

        @Override
        public int quads() {
            return count;
        }

        public float x(int puff) {
            return data[puff * 6];
        }

        public float y(int puff) {
            return data[puff * 6 + 1];
        }

        public float z(int puff) {
            return data[puff * 6 + 2];
        }

        public float radius(int puff) {
            return data[puff * 6 + 3];
        }

        public float alpha(int puff) {
            return data[puff * 6 + 5];
        }

        @Override
        public ByteBuffer toBuffer() {
            ByteBuffer buffer = ByteBuffer.allocateDirect(count * 4 * VERTEX_BYTES).order(ByteOrder.nativeOrder());
            for (int i = 0; i < count; i++) {
                int p = i * 6;
                float radius = data[p + 3];
                byte shade = (byte) Math.round(Math.max(0, Math.min(1, data[p + 4])) * 255);
                byte alpha = (byte) Math.round(Math.max(0, Math.min(1, data[p + 5])) * 255);
                for (float[] corner : CORNERS) {
                    buffer.putFloat(data[p]).putFloat(data[p + 1]).putFloat(data[p + 2]);
                    buffer.putFloat(corner[0] * radius).putFloat(corner[1] * radius);
                    buffer.put(shade).put(shade).put(shade).put(alpha);
                }
            }
            return buffer.flip();
        }
    }

    public static Puffs mesh(CloudField field, double originX, double originZ, int size, int spacing, int seed) {
        Puffs puffs = new Puffs();
        int n = size / spacing;
        double maxHeight = field.maxHeight();
        int layers = (int) Math.ceil(maxHeight * 1.2 / spacing) + 1;
        long cellX0 = Math.round(originX / spacing);
        long cellZ0 = Math.round(originZ / spacing);
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                int cx = (int) (cellX0 + i);
                int cz = (int) (cellZ0 + k);
                double baseX = originX + (i + 0.5) * spacing;
                double baseZ = originZ + (k + 0.5) * spacing;
                double[] centre = field.column(baseX, baseZ);
                if (centre[0] <= 0 && field.column(baseX + spacing, baseZ)[0] <= 0
                        && field.column(baseX, baseZ + spacing)[0] <= 0 && field.column(baseX - spacing, baseZ)[0] <= 0
                        && field.column(baseX, baseZ - spacing)[0] <= 0) {
                    continue;
                }
                for (int j = 0; j < layers; j++) {
                    int h = Noise.hash(cx, j, cz, seed ^ spacing);
                    double x = baseX + jitter(h) * spacing;
                    double y = (j + 0.35) * spacing + jitter(h >>> 8) * spacing * 0.6;
                    double z = baseZ + jitter(h >>> 16) * spacing;
                    double[] column = field.column(x, z);
                    if (column[2] <= 0) {
                        continue;
                    }
                    double depth = Math.max(0, column[0] - column[1]);
                    double density = field.density(column, x, y, z);
                    if (density < -spacing * 0.3) {
                        continue;
                    }
                    double fill = Noise.smoothstep(-spacing * 0.3, spacing * 1.2, density);
                    float radius = (float) (spacing * (1.05 + 0.55 * fill) * (0.85 + 0.3 * ((h >>> 24) / 255.0)));
                    float alpha = (float) Math.min(1, (0.28 + 0.62 * fill) * (0.8 + 0.35 * Math.min(1, (y - column[1]) / Math.max(4, depth))));
                    puffs.add((float) (x - originX), (float) y, (float) (z - originZ), radius,
                            shade(y, depth, maxHeight, column[1]), alpha);
                }
            }
        }
        return puffs;
    }

    private static double jitter(int bits) {
        return ((bits & 0xFF) / 255.0 - 0.5) * 0.8;
    }

    static float shade(double y, double depth, double maxHeight, double bottom) {
        double up = Math.max(0, Math.min(1, (y - bottom) / Math.max(4, depth)));
        double thick = Math.max(0, Math.min(1, depth / Math.max(1, maxHeight * 0.9)));
        double base = 0.9 - 0.26 * thick;
        return (float) Math.max(0.4, Math.min(1, base + (1.0 - base) * Math.pow(up, 0.6)));
    }
}
