package dev.billow.core;

import java.util.ArrayList;
import java.util.List;

public final class Shapes {
    public record Shape(String name, int weight, boolean[][] pixels) {
        public int width() {
            return pixels[0].length;
        }

        public int depth() {
            return pixels.length;
        }
    }

    public static final List<Shape> ALL = new ArrayList<>();
    private static int totalWeight;

    static {
        add("creeper", 4,
                "########",
                "########",
                "#..##..#",
                "#..##..#",
                "###..###",
                "##....##",
                "##....##",
                "##.##.##");
        add("heart", 2,
                ".##...##.",
                "####.####",
                "#########",
                "#########",
                ".#######.",
                "..#####..",
                "...###...",
                "....#....");
        add("smiley", 2,
                "..#####..",
                ".#######.",
                "##.###.##",
                "##.###.##",
                "#########",
                "#.#####.#",
                "##.....##",
                ".##...##.",
                "..#####..");
        add("star", 2,
                "....#....",
                "....#....",
                "...###...",
                "#########",
                ".#######.",
                "..#####..",
                "..##.##..",
                ".##...##.",
                ".#.....#.");
        add("sword", 2,
                "........###",
                ".......####",
                "......###.#",
                ".....###...",
                "#...###....",
                ".#.###.....",
                "..###......",
                "..##.......",
                ".#..#......",
                "#..........");
        add("diamond", 1,
                "..#####..",
                ".#######.",
                "#########",
                ".#######.",
                "..#####..",
                "...###...",
                "....#....");
        add("mushroom", 1,
                "...#####...",
                ".#########.",
                "###.###.###",
                "###########",
                "#.#######.#",
                "....###....",
                "....###....",
                "...#####...");
        add("ghast", 2,
                "#########",
                "#########",
                "#..###..#",
                "#########",
                "###...###",
                "#########",
                "#.#.#.#.#",
                "#.#.#.#.#",
                "..#...#..");
        add("fish", 1,
                "...####...#",
                ".#######.##",
                "##.########",
                "###########",
                ".#######.##",
                "...####...#");
        add("pig", 1,
                "##########",
                "##########",
                "#..####..#",
                "##########",
                "###....###",
                "###.##.###",
                "###....###",
                "##########");
        add("cat", 1,
                "#.......#",
                "##.....##",
                "#########",
                "#..###..#",
                "#########",
                "####.####",
                "##.#.#.##",
                ".#######.");
    }

    private Shapes() {
    }

    private static void add(String name, int weight, String... rows) {
        boolean[][] pixels = new boolean[rows.length][rows[0].length()];
        for (int z = 0; z < rows.length; z++) {
            if (rows[z].length() != rows[0].length()) {
                throw new IllegalStateException("ragged shape " + name);
            }
            for (int x = 0; x < rows[z].length(); x++) {
                pixels[z][x] = rows[z].charAt(x) == '#';
            }
        }
        ALL.add(new Shape(name, weight, pixels));
        totalWeight += weight;
    }

    static Shape pick(int roll) {
        int r = Math.floorMod(roll, totalWeight);
        for (Shape shape : ALL) {
            r -= shape.weight();
            if (r < 0) {
                return shape;
            }
        }
        return ALL.get(0);
    }

    public static CellMap map(CellMap background, int seed) {
        return new ShapeMap(background, seed);
    }

    private static final class ShapeMap implements CellMap {
        private static final int SLOT = 22;
        private static final int MARGIN = 2;
        private final CellMap background;
        private final int seed;

        ShapeMap(CellMap background, int seed) {
            this.background = background;
            this.seed = seed;
        }

        @Override
        public boolean filled(int x, int z) {
            int slotX = Math.floorDiv(x, SLOT);
            int slotZ = Math.floorDiv(z, SLOT);
            int localX = x - slotX * SLOT;
            int localZ = z - slotZ * SLOT;
            int hash = Noise.hash(slotX, 0, slotZ, seed);
            if ((hash & 0xFF) < 150) {
                Shape shape = pick(hash >>> 8);
                int rotation = (hash >>> 20) & 3;
                boolean mirror = ((hash >>> 22) & 1) == 1;
                boolean swap = (rotation & 1) == 1;
                int w = swap ? shape.depth() : shape.width();
                int d = swap ? shape.width() : shape.depth();
                int freeX = SLOT - w - 2 * MARGIN;
                int freeZ = SLOT - d - 2 * MARGIN;
                int ox = MARGIN + Math.floorMod(Noise.hash(slotX, 1, slotZ, seed), freeX + 1);
                int oz = MARGIN + Math.floorMod(Noise.hash(slotX, 2, slotZ, seed), freeZ + 1);
                int px = localX - ox;
                int pz = localZ - oz;
                if (px >= 0 && pz >= 0 && px < w && pz < d) {
                    return pixel(shape, rotation, mirror, px, pz);
                }
                if (px >= -MARGIN && pz >= -MARGIN && px < w + MARGIN && pz < d + MARGIN) {
                    return false;
                }
            }
            return background.filled(x, z);
        }

        private static boolean pixel(Shape shape, int rotation, boolean mirror, int x, int z) {
            int w = shape.width();
            int d = shape.depth();
            int sx;
            int sz;
            switch (rotation) {
                case 1 -> {
                    sx = z;
                    sz = d - 1 - x;
                }
                case 2 -> {
                    sx = w - 1 - x;
                    sz = d - 1 - z;
                }
                case 3 -> {
                    sx = w - 1 - z;
                    sz = x;
                }
                default -> {
                    sx = x;
                    sz = z;
                }
            }
            if (mirror) {
                sx = w - 1 - sx;
            }
            return shape.pixels()[sz][sx];
        }
    }
}
