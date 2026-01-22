package com.gillodaby.betterportals;

final class PortalSource {
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final String name;

    PortalSource(String world, int x, int y, int z, String name) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
    }

    String world() {
        return world;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int z() {
        return z;
    }

    String name() {
        return name;
    }

    String asLine() {
        return world + "|" + x + "|" + y + "|" + z + "|" + name;
    }
}
