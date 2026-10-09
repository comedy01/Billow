package dev.billow.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class TileSelector {
    public record Result(List<TileKey> draw, List<TileKey> wanted) {
    }

    private final Sky sky;
    private final double cameraX;
    private final double cameraZ;
    private final double radius;
    private final Predicate<TileKey> ready;
    private final List<TileKey> draw = new ArrayList<>();
    private final List<TileKey> wanted = new ArrayList<>();

    private TileSelector(Sky sky, double cameraX, double cameraZ, double radius, Predicate<TileKey> ready) {
        this.sky = sky;
        this.cameraX = cameraX;
        this.cameraZ = cameraZ;
        this.radius = radius;
        this.ready = ready;
    }

    public static Result select(Sky sky, double cameraX, double cameraZ, double radius, Predicate<TileKey> ready) {
        TileSelector selector = new TileSelector(sky, cameraX, cameraZ, radius, ready);
        int level = 0;
        while (level < Sky.MAX_LEVEL && sky.tileSize(level) * 2 < radius) {
            level++;
        }
        int size = sky.tileSize(level);
        int x0 = (int) Math.floor((cameraX - radius) / size);
        int x1 = (int) Math.floor((cameraX + radius) / size);
        int z0 = (int) Math.floor((cameraZ - radius) / size);
        int z1 = (int) Math.floor((cameraZ + radius) / size);
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                selector.visit(new TileKey(level, x, z));
            }
        }
        selector.wanted.sort(Comparator.comparingDouble(selector::distance));
        return new Result(selector.draw, selector.wanted);
    }

    public double distance(TileKey key) {
        int size = sky.tileSize(key.level());
        double minX = (double) key.x() * size;
        double minZ = (double) key.z() * size;
        double dx = Math.max(0, Math.max(minX - cameraX, cameraX - (minX + size)));
        double dz = Math.max(0, Math.max(minZ - cameraZ, cameraZ - (minZ + size)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean inRange(TileKey key) {
        return distance(key) <= radius;
    }

    private List<TileKey> children(TileKey key) {
        List<TileKey> children = new ArrayList<>(4);
        for (int dz = 0; dz < 2; dz++) {
            for (int dx = 0; dx < 2; dx++) {
                TileKey child = key.child(dx, dz);
                if (inRange(child)) {
                    children.add(child);
                }
            }
        }
        return children;
    }

    private boolean allReady(List<TileKey> keys) {
        for (TileKey key : keys) {
            if (!ready.test(key)) {
                return false;
            }
        }
        return true;
    }

    private void visit(TileKey key) {
        if (!inRange(key)) {
            return;
        }
        boolean split = key.level() > 0 && distance(key) < sky.splitFactor() * sky.tileSize(key.level());
        if (split) {
            List<TileKey> children = children(key);
            if (allReady(children) || !ready.test(key)) {
                for (TileKey child : children) {
                    visit(child);
                }
            } else {
                wanted.addAll(children);
                wanted.add(key);
                draw.add(key);
            }
            return;
        }
        wanted.add(key);
        if (ready.test(key)) {
            draw.add(key);
        } else if (key.level() > 0) {
            List<TileKey> children = children(key);
            if (!children.isEmpty() && allReady(children)) {
                draw.addAll(children);
            }
        }
    }
}
