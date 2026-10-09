package dev.billow.core;

public record TileKey(int level, int x, int z) {
    public TileKey child(int dx, int dz) {
        return new TileKey(level - 1, x * 2 + dx, z * 2 + dz);
    }
}
