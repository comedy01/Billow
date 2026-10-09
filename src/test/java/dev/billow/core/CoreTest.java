package dev.billow.core;

import dev.billow.config.BillowConfig;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTest {
    private static final float S = 12f;

    private static CellMap cells(String... rows) {
        return (x, z) -> z >= 0 && z < rows.length && x >= 0 && x < rows[z].length() && rows[z].charAt(x) == '#';
    }

    private static Mesh rounded(CellMap map, int count, float height, float radius) {
        return CellMesher.mesh(map, 0, 0, count, S, height, radius, 4, true);
    }

    private static void assertClosed(Mesh mesh) {
        Map<String, Integer> edges = new HashMap<>();
        for (int q = 0; q < mesh.quads(); q++) {
            for (int c = 0; c < 4; c++) {
                int a = q * 4 + c;
                int b = q * 4 + (c + 1) % 4;
                String from = key(mesh, a);
                String to = key(mesh, b);
                if (from.equals(to)) {
                    continue;
                }
                edges.merge(from + ">" + to, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> edge : edges.entrySet()) {
            String[] ends = edge.getKey().split(">");
            assertEquals(1, edge.getValue(), "edge used twice the same way: " + edge.getKey());
            assertTrue(edges.containsKey(ends[1] + ">" + ends[0]), "open edge (crack): " + edge.getKey());
        }
    }

    private static String key(Mesh mesh, int v) {
        return Math.round(mesh.x(v) * 1000) + "," + Math.round(mesh.y(v) * 1000) + "," + Math.round(mesh.z(v) * 1000);
    }

    private static double volume(Mesh mesh) {
        double total = 0;
        for (int q = 0; q < mesh.quads(); q++) {
            int a = q * 4;
            total += tetra(mesh, a, a + 1, a + 2) + tetra(mesh, a, a + 2, a + 3);
        }
        return total;
    }

    private static double tetra(Mesh m, int a, int b, int c) {
        double ax = m.x(a), ay = m.y(a), az = m.z(a);
        double bx = m.x(b), by = m.y(b), bz = m.z(b);
        double cx = m.x(c), cy = m.y(c), cz = m.z(c);
        return (ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)) / 6.0;
    }

    @Test
    void singleRoundedCellIsClosedAndFacesOutward() {
        Mesh mesh = rounded(cells("#"), 1, 6, 3);
        assertClosed(mesh);
        double v = volume(mesh);
        double round = 36 * 6 + 24 * Math.PI * 4.5 + 4.0 / 3 * Math.PI * 27;
        assertTrue(v > 0.95 * round && v <= round, "volume " + v);
    }

    @Test
    void shapesWithInnerCornersHolesAndDiagonalsAreClosed() {
        CellMap map = cells(
                "##....#",
                "#..##.#",
                "###.###",
                ".#.#...",
                "##.####");
        for (float radius : new float[]{1f, 2.5f, 3f}) {
            for (float height : new float[]{4f, 6f, 10f}) {
                Mesh mesh = rounded(map, 7, height, radius);
                assertClosed(mesh);
                assertTrue(volume(mesh) > 0);
            }
        }
    }

    @Test
    void creeperFaceIsClosed() {
        Shapes.Shape creeper = Shapes.ALL.get(0);
        assertEquals("creeper", creeper.name());
        boolean[][] px = creeper.pixels();
        Mesh mesh = rounded((x, z) -> z >= 0 && z < 8 && x >= 0 && x < 8 && px[z][x], 8, 6, 3);
        assertClosed(mesh);
    }

    @Test
    void boxesHaveTheRightVolume() {
        CellMap map = cells("###", "#.#", "###");
        Mesh mesh = CellMesher.mesh(map, 0, 0, 3, S, 4, 2, 4, false);
        assertEquals(8 * S * S * 4, volume(mesh), 1e-2);
    }

    @Test
    void noRoundnessMeansBoxes() {
        Mesh mesh = CellMesher.mesh(cells("#"), 0, 0, 1, S, 4, 0, 4, true);
        assertEquals(6, mesh.quads());
        assertEquals(S * S * 4, volume(mesh), 1e-3);
    }

    @Test
    void tilesOnlyMeshTheirOwnCells() {
        CellMap all = (x, z) -> true;
        Mesh mesh = CellMesher.mesh(all, 5, 5, 4, S, 4, 2, 4, true);
        for (int v = 0; v < mesh.vertexCount(); v++) {
            assertTrue(mesh.x(v) >= -1e-4 && mesh.x(v) <= 4 * S + 1e-4);
            assertTrue(mesh.z(v) >= -1e-4 && mesh.z(v) <= 4 * S + 1e-4);
        }
    }

    @Test
    void gasFollowsCoverage() {
        CloudField clear = new CloudField(1, 0.0, 30);
        CloudField overcast = new CloudField(1, 1.0, 30);
        int clearPuffs = 0;
        int overcastPuffs = 0;
        for (int t = 0; t < 4; t++) {
            clearPuffs += GasMesher.mesh(clear, t * 128, 0, 128, 8, 5).quads();
            overcastPuffs += GasMesher.mesh(overcast, t * 128, 0, 128, 8, 5).quads();
        }
        assertEquals(0, clearPuffs);
        assertTrue(overcastPuffs > 500, "overcast puffs " + overcastPuffs);
    }

    @Test
    void gasPuffsSitInsideTheCloudAndTheirTile() {
        CloudField field = new CloudField(3, 0.7, 30);
        GasMesher.Puffs puffs = GasMesher.mesh(field, 256, -128, 128, 8, 5);
        assertTrue(puffs.quads() > 0);
        for (int i = 0; i < puffs.quads(); i++) {
            float x = puffs.x(i), y = puffs.y(i), z = puffs.z(i);
            assertTrue(x >= 0 && x <= 128 && z >= 0 && z <= 128, "puff outside its tile");
            assertTrue(field.density(256 + x, y, -128 + z) > -8 * 0.31, "puff in clear sky");
            assertTrue(puffs.radius(i) > 6 && puffs.radius(i) < 16);
            assertTrue(puffs.alpha(i) > 0 && puffs.alpha(i) <= 1);
        }
        ByteBuffer buffer = puffs.toBuffer();
        assertEquals(puffs.quads() * 4 * GasMesher.VERTEX_BYTES, buffer.remaining());
    }

    @Test
    void coarserGasHasFewerBiggerPuffs() {
        CloudField field = new CloudField(9, 0.6, 30);
        GasMesher.Puffs fine = GasMesher.mesh(field, 0, 0, 256, 8, 5);
        GasMesher.Puffs coarse = GasMesher.mesh(field, 0, 0, 256, 16, 5);
        assertTrue(coarse.quads() < fine.quads() / 2, coarse.quads() + " vs " + fine.quads());
    }

    @Test
    void thickCloudsHaveDarkerBases() {
        assertTrue(GasMesher.shade(0, 30, 36, 0) < GasMesher.shade(0, 5, 36, 0));
        assertTrue(GasMesher.shade(30, 30, 36, 0) > GasMesher.shade(0, 30, 36, 0));
    }

    @Test
    void shapesAppearAndStayClearOfOtherClouds() {
        CellMap shapes = Shapes.map((x, z) -> true, 42);
        int filled = 0;
        int total = 0;
        for (int z = 0; z < 220; z++) {
            for (int x = 0; x < 220; x++) {
                total++;
                if (shapes.filled(x, z)) {
                    filled++;
                }
            }
        }
        assertTrue(filled < total, "shapes should carve gaps into a full sky");
        CellMap only = Shapes.map(CellMap.EMPTY, 42);
        int shapeCells = 0;
        for (int z = 0; z < 220; z++) {
            for (int x = 0; x < 220; x++) {
                if (only.filled(x, z)) {
                    shapeCells++;
                }
            }
        }
        assertTrue(shapeCells > 300, "too few shape cells: " + shapeCells);
    }

    @Test
    void everyShapeIsUsed() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(Shapes.pick(i).name());
        }
        assertEquals(Shapes.ALL.size(), seen.size());
    }

    @Test
    void selectedTilesNeverOverlapOnceBuilt() {
        Sky sky = new Sky(new Sky.Settings(CloudStyle.ROUNDED, 6, 3, 50, true), cells("#"));
        TileSelector.Result result = TileSelector.select(sky, 1234.5, -987.0, 2048, key -> true);
        List<TileKey> draw = result.draw();
        assertFalse(draw.isEmpty());
        for (int i = 0; i < draw.size(); i++) {
            for (int j = i + 1; j < draw.size(); j++) {
                assertFalse(overlap(sky, draw.get(i), draw.get(j)), draw.get(i) + " overlaps " + draw.get(j));
            }
        }
        assertTrue(draw.size() < 400, "too many tiles: " + draw.size());
        assertTrue(draw.stream().anyMatch(k -> k.level() == 0));
    }

    @Test
    void coarseTilesStandInUntilChildrenAreBuilt() {
        Sky sky = new Sky(new Sky.Settings(CloudStyle.ROUNDED, 6, 3, 50, true), cells("#"));
        Set<TileKey> built = new HashSet<>();
        TileSelector.Result first = TileSelector.select(sky, 0, 0, 1024, key -> false);
        assertTrue(first.draw().isEmpty());
        assertFalse(first.wanted().isEmpty());
        built.addAll(first.wanted());
        TileSelector.Result second = TileSelector.select(sky, 0, 0, 1024, built::contains);
        for (int i = 0; i < second.draw().size(); i++) {
            for (int j = i + 1; j < second.draw().size(); j++) {
                assertFalse(overlap(sky, second.draw().get(i), second.draw().get(j)));
            }
        }
    }

    private static boolean overlap(Sky sky, TileKey a, TileKey b) {
        long sa = sky.tileSize(a.level()), sb = sky.tileSize(b.level());
        long ax = a.x() * sa, az = a.z() * sa, bx = b.x() * sb, bz = b.z() * sb;
        return ax < bx + sb && bx < ax + sa && az < bz + sb && bz < az + sa;
    }

    @Test
    void configClampsAndDefaults() {
        BillowConfig config = new BillowConfig();
        assertEquals(CloudStyle.ROUNDED, config.style());
        assertEquals(0, config.height());
        config.setHeight(10);
        assertEquals(0, config.height());
        config.setHeight(9999);
        assertEquals(BillowConfig.MAX_HEIGHT, config.height());
        config.setThickness(1);
        assertEquals(BillowConfig.MIN_THICKNESS, config.thickness());
        config.setRoundness(100);
        assertTrue(config.radius() <= config.thickness() / 2f);
        config.resetToDefaults();
        assertEquals(100, config.speed());
        assertEquals(CloudStyle.SHAPES, CloudStyle.byId("shapes"));
        assertEquals(CloudStyle.ROUNDED, CloudStyle.byId("nonsense"));
    }
}
