package dev.billow.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class Mesh implements Geometry {
    public static final int VERTEX_BYTES = 16;

    private float[] positions = new float[256 * 3];
    private byte[] shades = new byte[256];
    private byte[] alphas = new byte[256];
    private int vertices;

    @Override
    public int quads() {
        return vertices / 4;
    }

    @Override
    public boolean isEmpty() {
        return vertices == 0;
    }

    public void quad(float[] a, float[] b, float[] c, float[] d, float nx, float ny, float nz) {
        float[] n = cross(sub(c, a), sub(d, b));
        boolean forward = n[0] * nx + n[1] * ny + n[2] * nz >= 0;
        float[][] order = forward ? new float[][]{a, b, c, d} : new float[][]{a, d, c, b};
        float[] hint = forward ? n : new float[]{-n[0], -n[1], -n[2]};
        if (worstFacing(order, 0, hint) < worstFacing(order, 1, hint)) {
            order = new float[][]{order[1], order[2], order[3], order[0]};
        }
        for (float[] v : order) {
            vertex(v);
        }
    }

    private static float worstFacing(float[][] q, int shift, float[] hint) {
        float[] p0 = q[shift], p1 = q[(shift + 1) % 4], p2 = q[(shift + 2) % 4], p3 = q[(shift + 3) % 4];
        return Math.min(facing(p0, p1, p2, hint), facing(p2, p3, p0, hint));
    }

    private static float facing(float[] a, float[] b, float[] c, float[] hint) {
        float[] n = cross(sub(b, a), sub(c, a));
        float length = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (length < 1e-9f) {
            return 1;
        }
        return (n[0] * hint[0] + n[1] * hint[1] + n[2] * hint[2]) / length;
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] e, float[] f) {
        return new float[]{e[1] * f[2] - e[2] * f[1], e[2] * f[0] - e[0] * f[2], e[0] * f[1] - e[1] * f[0]};
    }

    private void vertex(float[] v) {
        if (vertices == shades.length) {
            positions = Arrays.copyOf(positions, positions.length * 2);
            shades = Arrays.copyOf(shades, shades.length * 2);
            alphas = Arrays.copyOf(alphas, alphas.length * 2);
        }
        int p = vertices * 3;
        positions[p] = v[0];
        positions[p + 1] = v[1];
        positions[p + 2] = v[2];
        shades[vertices] = (byte) Math.round(Math.max(0f, Math.min(1f, v[3])) * 255f);
        alphas[vertices] = (byte) Math.round(Math.max(0f, Math.min(1f, v.length > 4 ? v[4] : 1f)) * 255f);
        vertices++;
    }

    public float x(int vertex) {
        return positions[vertex * 3];
    }

    public float y(int vertex) {
        return positions[vertex * 3 + 1];
    }

    public float z(int vertex) {
        return positions[vertex * 3 + 2];
    }

    public int vertexCount() {
        return vertices;
    }

    @Override
    public ByteBuffer toBuffer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(vertices * VERTEX_BYTES).order(ByteOrder.nativeOrder());
        for (int i = 0; i < vertices; i++) {
            int p = i * 3;
            buffer.putFloat(positions[p]).putFloat(positions[p + 1]).putFloat(positions[p + 2]);
            byte s = shades[i];
            buffer.put(s).put(s).put(s).put(alphas[i]);
        }
        return buffer.flip();
    }

    public static float shade(float nx, float ny, float nz) {
        return nx * nx * 0.9f + nz * nz * 0.8f + ny * ny * (ny > 0 ? 1.0f : 0.7f);
    }
}
