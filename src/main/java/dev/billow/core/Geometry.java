package dev.billow.core;

import java.nio.ByteBuffer;

public interface Geometry {
    int quads();

    default boolean isEmpty() {
        return quads() == 0;
    }

    ByteBuffer toBuffer();
}
