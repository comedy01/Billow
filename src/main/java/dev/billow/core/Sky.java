package dev.billow.core;

public final class Sky {
    public static final float CELL = 12f;
    public static final int CELLS_PER_TILE = 16;
    public static final int REALISTIC_TILE = 128;
    public static final int GAS_SPACING = 8;
    public static final int MAX_LEVEL = 6;
    private static final int SEED = 0x5EED_B111;
    private static final int ARC_SEGMENTS = 4;

    public record Settings(CloudStyle style, int thickness, float radius, int coverage, boolean shapesWithClouds) {
    }

    private final Settings settings;
    private final CellMap cells;
    private final CloudField field;

    public Sky(Settings settings, CellMap texture) {
        this.settings = settings;
        CellMap base = texture == null ? CellMap.EMPTY : texture;
        this.cells = settings.style() == CloudStyle.SHAPES
                ? Shapes.map(settings.shapesWithClouds() ? base : CellMap.EMPTY, SEED)
                : base;
        this.field = settings.style() == CloudStyle.REALISTIC
                ? new CloudField(SEED, settings.coverage() / 100.0, settings.thickness() * 6.0)
                : null;
    }

    public Settings settings() {
        return settings;
    }

    public int tileSize(int level) {
        int base = field != null ? REALISTIC_TILE : (int) (CELLS_PER_TILE * CELL);
        return base << level;
    }

    public double splitFactor() {
        return field != null ? 2.5 : 1.5;
    }

    public double topHeight() {
        return field != null ? field.maxHeight() * 1.3 : settings.thickness();
    }

    public boolean gas() {
        return field != null;
    }

    public Geometry mesh(TileKey key) {
        if (field != null) {
            int size = tileSize(key.level());
            return GasMesher.mesh(field, (double) key.x() * size, (double) key.z() * size, size,
                    GAS_SPACING << key.level(), SEED);
        }
        int count = CELLS_PER_TILE << key.level();
        return CellMesher.mesh(cells, key.x() * count, key.z() * count, count, CELL, settings.thickness(),
                settings.radius(), ARC_SEGMENTS, key.level() == 0);
    }
}
