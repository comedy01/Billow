package dev.billow.core;

public final class CellMesher {
    private static final float[][] SIDE_BASE = {{0, 0}, {0, 1}, {0, 0}, {1, 0}};
    private static final float[][] SIDE_ALONG = {{1, 0}, {1, 0}, {0, 1}, {0, 1}};
    private static final float[][] SIDE_INWARD = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
    private static final int NORTH = 0;
    private static final int SOUTH = 1;
    private static final int WEST = 2;
    private static final int EAST = 3;

    private final CellMap map;
    private final float size;
    private final float height;
    private final float radius;
    private final int segments;
    private final Mesh mesh = new Mesh();

    private float[] profileD;
    private float[] profileY;
    private float[] profileNd;
    private float[] profileNy;
    private float[] arcD;
    private float[] arcTop;
    private float[] arcBottom;
    private float[] arcCos;
    private float[] arcSin;

    private CellMesher(CellMap map, float size, float height, float radius, int segments) {
        this.map = map;
        this.size = size;
        this.height = height;
        this.radius = radius;
        this.segments = segments;
    }

    public static Mesh mesh(CellMap map, int cellX, int cellZ, int count, float size, float height, float radius,
                            int segments, boolean rounded) {
        float r = Math.min(radius, Math.min(height * 0.5f, size * 0.45f));
        CellMesher mesher = new CellMesher(map, size, height, r, Math.max(1, segments));
        if (rounded && r > 0.05f) {
            mesher.buildProfile();
            for (int z = 0; z < count; z++) {
                for (int x = 0; x < count; x++) {
                    if (map.filled(cellX + x, cellZ + z)) {
                        mesher.roundedCell(cellX + x, cellZ + z, x * size, z * size);
                    }
                }
            }
        } else {
            mesher.boxes(cellX, cellZ, count);
        }
        return mesher.mesh;
    }

    private void buildProfile() {
        int k = segments;
        arcD = new float[k + 1];
        arcTop = new float[k + 1];
        arcBottom = new float[k + 1];
        arcCos = new float[k + 1];
        arcSin = new float[k + 1];
        for (int i = 0; i <= k; i++) {
            double theta = Math.PI * 0.5 * i / k;
            float cos = (float) Math.cos(theta);
            float sin = (float) Math.sin(theta);
            if (i == k) {
                cos = 0;
                sin = 1;
            }
            arcCos[i] = cos;
            arcSin[i] = sin;
            arcD[i] = radius - radius * cos;
            arcTop[i] = height - radius + radius * sin;
            arcBottom[i] = radius - radius * sin;
        }
        boolean flat = height - 2 * radius > 1e-3f;
        int points = flat ? 2 * (k + 1) : 2 * k + 1;
        profileD = new float[points];
        profileY = new float[points];
        profileNd = new float[points];
        profileNy = new float[points];
        int p = 0;
        for (int i = k; i >= 0; i--) {
            profileD[p] = arcD[i];
            profileY[p] = arcBottom[i];
            profileNd[p] = -arcCos[i];
            profileNy[p] = -arcSin[i];
            p++;
        }
        for (int i = flat ? 0 : 1; i <= k; i++) {
            profileD[p] = arcD[i];
            profileY[p] = arcTop[i];
            profileNd[p] = -arcCos[i];
            profileNy[p] = arcSin[i];
            p++;
        }
    }

    private boolean open(int x, int z) {
        return !map.filled(x, z);
    }

    private void roundedCell(int cx, int cz, float ox, float oz) {
        boolean[] exposed = {open(cx, cz - 1), open(cx, cz + 1), open(cx - 1, cz), open(cx + 1, cz)};
        float s = size;
        float r = radius;

        for (int side = 0; side < 4; side++) {
            if (exposed[side]) {
                boolean alongX = side == NORTH || side == SOUTH;
                boolean startOpen = alongX ? exposed[WEST] : exposed[NORTH];
                boolean endOpen = alongX ? exposed[EAST] : exposed[SOUTH];
                strip(side, ox, oz, startOpen ? r : 0, endOpen ? s - r : s);
            }
        }

        float[] cuts = {0, r, s - r, s};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (cuts[col + 1] - cuts[col] < 1e-4f || cuts[row + 1] - cuts[row] < 1e-4f) {
                    continue;
                }
                boolean flat;
                if (col == 1 && row == 1) {
                    flat = true;
                } else if (col == 1) {
                    flat = !exposed[row == 0 ? NORTH : SOUTH];
                } else if (row == 1) {
                    flat = !exposed[col == 0 ? WEST : EAST];
                } else {
                    flat = corner(cx, cz, ox, oz, col == 0 ? 0 : s, row == 0 ? 0 : s, exposed);
                }
                if (flat) {
                    flatSquare(ox + cuts[col], oz + cuts[row], ox + cuts[col + 1], oz + cuts[row + 1]);
                }
            }
        }
    }

    private boolean corner(int cx, int cz, float ox, float oz, float cu, float cw, boolean[] exposed) {
        int dx = cu == 0 ? -1 : 1;
        int dz = cw == 0 ? -1 : 1;
        boolean xOpen = exposed[dx < 0 ? WEST : EAST];
        boolean zOpen = exposed[dz < 0 ? NORTH : SOUTH];
        if (xOpen && zOpen) {
            convexCorner(ox + cu - dx * radius, oz + cw - dz * radius, dx, dz);
            return false;
        }
        if (xOpen || zOpen) {
            return false;
        }
        if (open(cx + dx, cz + dz)) {
            concaveCorner(ox + cu, oz + cw, dx, dz);
            return false;
        }
        return true;
    }

    private void flatSquare(float x0, float z0, float x1, float z1) {
        float top = Mesh.shade(0, 1, 0);
        float bottom = Mesh.shade(0, -1, 0);
        mesh.quad(v(x0, height, z0, top), v(x1, height, z0, top), v(x1, height, z1, top), v(x0, height, z1, top),
                0, 1, 0);
        mesh.quad(v(x0, 0, z0, bottom), v(x1, 0, z0, bottom), v(x1, 0, z1, bottom), v(x0, 0, z1, bottom),
                0, -1, 0);
    }

    private void strip(int side, float ox, float oz, float from, float to) {
        float s = size;
        float bx = ox + SIDE_BASE[side][0] * s;
        float bz = oz + SIDE_BASE[side][1] * s;
        float ax = SIDE_ALONG[side][0];
        float az = SIDE_ALONG[side][1];
        float ix = SIDE_INWARD[side][0];
        float iz = SIDE_INWARD[side][1];
        float[] stops = {from, radius, s - radius, to};
        float last = from;
        for (int i = 1; i < stops.length; i++) {
            float t = i == stops.length - 1 ? to : stops[i];
            if (t <= last + 1e-4f || t > to + 1e-4f) {
                continue;
            }
            for (int p = 0; p + 1 < profileD.length; p++) {
                float[] a = stripVertex(bx, bz, ax, az, ix, iz, last, p);
                float[] b = stripVertex(bx, bz, ax, az, ix, iz, t, p);
                float[] c = stripVertex(bx, bz, ax, az, ix, iz, t, p + 1);
                float[] d = stripVertex(bx, bz, ax, az, ix, iz, last, p + 1);
                float nd = (profileNd[p] + profileNd[p + 1]) * 0.5f;
                float ny = (profileNy[p] + profileNy[p + 1]) * 0.5f;
                mesh.quad(a, b, c, d, ix * nd, ny, iz * nd);
            }
            last = t;
        }
    }

    private float[] stripVertex(float bx, float bz, float ax, float az, float ix, float iz, float t, int p) {
        float d = profileD[p];
        float nd = profileNd[p];
        float ny = profileNy[p];
        return v(bx + ax * t + ix * d, profileY[p], bz + az * t + iz * d, Mesh.shade(ix * nd, ny, iz * nd));
    }

    private void convexCorner(float centerX, float centerZ, int dx, int dz) {
        int k = segments;
        for (int j = 0; j < k; j++) {
            double phi0 = Math.PI * 0.5 * j / k;
            double phi1 = Math.PI * 0.5 * (j + 1) / k;
            float ox0 = (float) Math.cos(phi0) * dx, oz0 = (float) Math.sin(phi0) * dz;
            float ox1 = (float) Math.cos(phi1) * dx, oz1 = (float) Math.sin(phi1) * dz;
            for (int p = 0; p + 1 < profileD.length; p++) {
                float[] a = cornerVertex(centerX, centerZ, ox0, oz0, p);
                float[] b = cornerVertex(centerX, centerZ, ox1, oz1, p);
                float[] c = cornerVertex(centerX, centerZ, ox1, oz1, p + 1);
                float[] d = cornerVertex(centerX, centerZ, ox0, oz0, p + 1);
                float out = -(profileNd[p] + profileNd[p + 1]) * 0.5f;
                float ny = (profileNy[p] + profileNy[p + 1]) * 0.5f;
                mesh.quad(a, b, c, d, (ox0 + ox1) * 0.5f * out, ny, (oz0 + oz1) * 0.5f * out);
            }
        }
    }

    private float[] cornerVertex(float centerX, float centerZ, float ox, float oz, int p) {
        float rho = radius - profileD[p];
        float out = -profileNd[p];
        float ny = profileNy[p];
        return v(centerX + ox * rho, profileY[p], centerZ + oz * rho, Mesh.shade(ox * out, ny, oz * out));
    }

    private void concaveCorner(float cornerX, float cornerZ, int dx, int dz) {
        for (boolean top : new boolean[]{true, false}) {
            valley(cornerX, cornerZ, dx, dz, top);
        }
    }

    private void valley(float cornerX, float cornerZ, int dx, int dz, boolean top) {
        int k = segments;
        float ny = top ? 1 : -1;
        for (int i = 0; i < k - 1; i++) {
            for (int j = 0; j < k - 1; j++) {
                mesh.quad(valleyVertex(cornerX, cornerZ, dx, dz, i, j, top),
                        valleyVertex(cornerX, cornerZ, dx, dz, i + 1, j, top),
                        valleyVertex(cornerX, cornerZ, dx, dz, i + 1, j + 1, top),
                        valleyVertex(cornerX, cornerZ, dx, dz, i, j + 1, top), 0, ny, 0);
            }
        }
        int[][] inner = new int[2 * k - 1][];
        int n = 0;
        for (int j = 0; j < k; j++) {
            inner[n++] = new int[]{k - 1, j};
        }
        for (int i = k - 2; i >= 0; i--) {
            inner[n++] = new int[]{i, k - 1};
        }
        float[] far = valleyVertex(cornerX, cornerZ, dx, dz, k, k, top);
        float[] start = valleyVertex(cornerX, cornerZ, dx, dz, k, 0, top);
        float[] end = valleyVertex(cornerX, cornerZ, dx, dz, 0, k, top);
        float[] previous = start;
        for (int m = 0; m <= inner.length; m++) {
            float[] next = m < inner.length
                    ? valleyVertex(cornerX, cornerZ, dx, dz, inner[m][0], inner[m][1], top)
                    : end;
            mesh.quad(far, previous, next, next, 0, ny, 0);
            previous = next;
        }
    }

    private float[] valleyVertex(float cornerX, float cornerZ, int dx, int dz, int i, int j, boolean top) {
        float x = cornerX - dx * arcD[i];
        float z = cornerZ - dz * arcD[j];
        float ux = arcCos[i] * dx;
        float uz = arcCos[j] * dz;
        float y;
        float nx;
        float ny;
        float nz;
        if (top) {
            boolean useX = arcTop[i] >= arcTop[j];
            y = useX ? arcTop[i] : arcTop[j];
            nx = useX ? ux : 0;
            nz = useX ? 0 : uz;
            ny = useX ? arcSin[i] : arcSin[j];
        } else {
            boolean useX = arcBottom[i] <= arcBottom[j];
            y = useX ? arcBottom[i] : arcBottom[j];
            nx = useX ? ux : 0;
            nz = useX ? 0 : uz;
            ny = -(useX ? arcSin[i] : arcSin[j]);
        }
        return v(x, y, z, Mesh.shade(nx, ny, nz));
    }

    private void boxes(int cellX, int cellZ, int count) {
        float s = size;
        float h = height;
        float top = Mesh.shade(0, 1, 0);
        float bottom = Mesh.shade(0, -1, 0);
        float ns = Mesh.shade(0, 0, 1);
        float we = Mesh.shade(1, 0, 0);
        for (int z = 0; z < count; z++) {
            int x = 0;
            while (x < count) {
                if (!map.filled(cellX + x, cellZ + z)) {
                    x++;
                    continue;
                }
                int end = x;
                while (end < count && map.filled(cellX + end, cellZ + z)) {
                    end++;
                }
                float x0 = x * s, x1 = end * s, z0 = z * s, z1 = (z + 1) * s;
                mesh.quad(v(x0, h, z0, top), v(x1, h, z0, top), v(x1, h, z1, top), v(x0, h, z1, top), 0, 1, 0);
                mesh.quad(v(x0, 0, z0, bottom), v(x1, 0, z0, bottom), v(x1, 0, z1, bottom), v(x0, 0, z1, bottom),
                        0, -1, 0);
                x = end;
            }
            sideRuns(cellX, cellZ, count, z, -1, z * s, -1, ns);
            sideRuns(cellX, cellZ, count, z, 1, (z + 1) * s, 1, ns);
        }
        for (int x = 0; x < count; x++) {
            columnRuns(cellX, cellZ, count, x, -1, x * s, -1, we);
            columnRuns(cellX, cellZ, count, x, 1, (x + 1) * s, 1, we);
        }
    }

    private void sideRuns(int cellX, int cellZ, int count, int z, int dz, float planeZ, int normal, float shade) {
        int x = 0;
        while (x < count) {
            if (!faceOpen(cellX + x, cellZ + z, 0, dz)) {
                x++;
                continue;
            }
            int end = x;
            while (end < count && faceOpen(cellX + end, cellZ + z, 0, dz)) {
                end++;
            }
            float x0 = x * size, x1 = end * size;
            mesh.quad(v(x0, 0, planeZ, shade), v(x1, 0, planeZ, shade), v(x1, height, planeZ, shade),
                    v(x0, height, planeZ, shade), 0, 0, normal);
            x = end;
        }
    }

    private void columnRuns(int cellX, int cellZ, int count, int x, int dx, float planeX, int normal, float shade) {
        int z = 0;
        while (z < count) {
            if (!faceOpen(cellX + x, cellZ + z, dx, 0)) {
                z++;
                continue;
            }
            int end = z;
            while (end < count && faceOpen(cellX + x, cellZ + end, dx, 0)) {
                end++;
            }
            float z0 = z * size, z1 = end * size;
            mesh.quad(v(planeX, 0, z0, shade), v(planeX, 0, z1, shade), v(planeX, height, z1, shade),
                    v(planeX, height, z0, shade), normal, 0, 0);
            z = end;
        }
    }

    private boolean faceOpen(int x, int z, int dx, int dz) {
        return map.filled(x, z) && !map.filled(x + dx, z + dz);
    }

    private static float[] v(float x, float y, float z, float shade) {
        return new float[]{x, y, z, shade};
    }
}
