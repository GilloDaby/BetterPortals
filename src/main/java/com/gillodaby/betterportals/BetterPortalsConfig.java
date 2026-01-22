package com.gillodaby.betterportals;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class BetterPortalsConfig {

    private final Path dataDir;
    private final List<PortalLink> links;
    private final List<PortalSource> sources;

    private BetterPortalsConfig(Path dataDir, List<PortalLink> links, List<PortalSource> sources) {
        this.dataDir = dataDir;
        this.links = links;
        this.sources = sources;
    }

    Path dataDir() {
        return dataDir;
    }

    List<PortalLink> links() {
        return links;
    }

    List<PortalSource> sources() {
        return sources;
    }

    static BetterPortalsConfig load(Path dataDir) {
        if (dataDir == null) {
            dataDir = Path.of("BetterPortals");
        }
        Path configPath = dataDir.resolve("config.yaml");
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException ignored) {
        }

        if (!Files.exists(configPath)) {
            BetterPortalsConfig defaults = defaults(dataDir);
            persist(configPath, defaults);
            return defaults;
        }

        List<PortalLink> links = new ArrayList<>();
        List<PortalSource> sources = new ArrayList<>();
        ParseMode mode = ParseMode.NONE;

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("links:")) {
                    mode = ParseMode.LINKS;
                    continue;
                }
                if (line.startsWith("sources:")) {
                    mode = ParseMode.SOURCES;
                    continue;
                }
                if (!line.startsWith("-")) {
                    continue;
                }
                String payload = trimQuotes(line.substring(1).trim());
                if (payload.isEmpty()) {
                    continue;
                }
                String[] parts = payload.split("\\|");
                if (mode == ParseMode.LINKS && parts.length >= 8) {
                    PortalLink link = parseLink(parts);
                    if (link != null) {
                        links.add(link);
                    }
                } else if (mode == ParseMode.SOURCES && parts.length >= 5) {
                    PortalSource source = parseSource(parts);
                    if (source != null) {
                        sources.add(source);
                    }
                }
            }
        } catch (IOException ignored) {
        }

        return new BetterPortalsConfig(dataDir, links, sources);
    }

    void save() {
        Path configPath = dataDir.resolve("config.yaml");
        persist(configPath, this);
    }

    private static void persist(Path configPath, BetterPortalsConfig config) {
        List<String> lines = new ArrayList<>();
        lines.add("# BetterPortals configuration");
        lines.add("#");
        lines.add("# links:");
        lines.add("#   - \"name|world|x|y|z|yaw|pitch|roll\"");
        lines.add("# sources:");
        lines.add("#   - \"world|x|y|z|name\"");
        lines.add("");

        lines.add("links:");
        if (config.links.isEmpty()) {
            lines.add("  []");
        } else {
            for (PortalLink link : config.links) {
                lines.add("  - \"" + link.asLine() + "\"");
            }
        }
        lines.add("");
        lines.add("sources:");
        if (config.sources.isEmpty()) {
            lines.add("  []");
        } else {
            for (PortalSource source : config.sources) {
                lines.add("  - \"" + source.asLine() + "\"");
            }
        }

        try {
            Files.createDirectories(configPath.getParent());
            Files.write(configPath, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static BetterPortalsConfig defaults(Path dataDir) {
        return new BetterPortalsConfig(dataDir, new ArrayList<>(), new ArrayList<>());
    }

    private static PortalLink parseLink(String[] parts) {
        String name = sanitize(parts[0]);
        String world = sanitize(parts[1]);
        Double x = parseDouble(parts[2]);
        Double y = parseDouble(parts[3]);
        Double z = parseDouble(parts[4]);
        Float yaw = parseFloat(parts[5]);
        Float pitch = parseFloat(parts[6]);
        Float roll = parseFloat(parts[7]);
        if (name.isEmpty() || world.isEmpty() || x == null || y == null || z == null) {
            return null;
        }
        return new PortalLink(name, world, x, y, z,
                yaw == null ? 0f : yaw,
                pitch == null ? 0f : pitch,
                roll == null ? 0f : roll);
    }

    private static PortalSource parseSource(String[] parts) {
        String world = sanitize(parts[0]);
        Integer x = parseInt(parts[1]);
        Integer y = parseInt(parts[2]);
        Integer z = parseInt(parts[3]);
        String name = sanitize(parts[4]);
        if (world.isEmpty() || name.isEmpty() || x == null || y == null || z == null) {
            return null;
        }
        return new PortalSource(world, x, y, z, name);
    }

    private static String trimQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Float parseFloat(String value) {
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private enum ParseMode {
        NONE,
        LINKS,
        SOURCES
    }
}
