package dev.billow.core;

@FunctionalInterface
public interface CellMap {
    boolean filled(int cellX, int cellZ);

    CellMap EMPTY = (x, z) -> false;

    static CellMap texture(boolean[] cells, int width, int height) {
        return (x, z) -> cells[Math.floorMod(x, width) + Math.floorMod(z, height) * width];
    }
}
