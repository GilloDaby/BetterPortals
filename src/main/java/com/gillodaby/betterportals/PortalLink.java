package com.gillodaby.betterportals;

final class PortalLink {
    private final String name;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final float roll;

    PortalLink(String name, String world, double x, double y, double z, float yaw, float pitch, float roll) {
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    String name() {
        return name;
    }

    String world() {
        return world;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    float yaw() {
        return yaw;
    }

    float pitch() {
        return pitch;
    }

    float roll() {
        return roll;
    }

    String asLine() {
        return name + "|" + world + "|" + x + "|" + y + "|" + z + "|" + yaw + "|" + pitch + "|" + roll;
    }
}
