package net.rustcore.duel.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A 2D polygon region bounded by minY..maxY.
 * Uses ray-casting algorithm with explicit edge checks so that
 * points exactly on the boundary count as INSIDE (prevents
 * ender pearls from teleporting through arena walls).
 *
 * <p>When the {@code world} field is null (e.g. during template loading),
 * the world check in {@link #contains(Location)} is skipped.
 */
public class CustomPoly2D {

    /** Lightweight 2D integer point — avoids dependency on WorldEdit's BlockVector2. */
    public record Point(int x, int z) {}

    private final List<Point> points;
    private final int minY;
    private final int maxY;
    private final World world;

    public CustomPoly2D(List<Point> points, int minY, int maxY, World world) {
        if (points.size() < 3) {
            throw new IllegalArgumentException("Polygon requires at least 3 points, got " + points.size());
        }
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
        this.minY = minY;
        this.maxY = maxY;
        this.world = world;
    }

    /**
     * Check if a location is inside this polygon region.
     * Uses ray-casting with explicit edge inclusion.
     *
     * @param loc the location to test
     * @return true if the location is inside or on the boundary
     */
    public boolean contains(Location loc) {
        if (world != null && !world.equals(loc.getWorld())) {
            return false;
        }

        double y = loc.getY();
        if (y < minY || y > maxY) {
            return false;
        }

        double testX = loc.getX();
        double testZ = loc.getZ();

        // First: explicit edge check (points on edges count as inside)
        if (isOnEdge(testX, testZ)) {
            return true;
        }

        // Ray-casting algorithm
        int n = points.size();
        boolean inside = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = points.get(i).x();
            double zi = points.get(i).z();
            double xj = points.get(j).x();
            double zj = points.get(j).z();

            if ((zi > testZ) != (zj > testZ)
                    && testX < (xj - xi) * (testZ - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }

        return inside;
    }

    /**
     * Check if a point lies exactly on any edge of the polygon.
     * Uses cross-product distance with a small epsilon tolerance.
     */
    private boolean isOnEdge(double x, double z) {
        double epsilon = 0.01;
        int n = points.size();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double x1 = points.get(i).x();
            double z1 = points.get(i).z();
            double x2 = points.get(j).x();
            double z2 = points.get(j).z();

            // Check bounding box first
            double minX = Math.min(x1, x2) - epsilon;
            double maxX = Math.max(x1, x2) + epsilon;
            double minZ = Math.min(z1, z2) - epsilon;
            double maxZ = Math.max(z1, z2) + epsilon;

            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                continue;
            }

            // Cross product to check distance from line segment
            double dx = x2 - x1;
            double dz = z2 - z1;
            double cross = Math.abs((x - x1) * dz - (z - z1) * dx);
            double lengthSq = dx * dx + dz * dz;

            if (lengthSq > 0 && (cross * cross / lengthSq) <= epsilon * epsilon) {
                return true;
            }
        }

        return false;
    }

    // ---- YAML serialization ----

    public List<Point> getPoints() {
        return points;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public World getWorld() {
        return world;
    }

    /**
     * Deserialize from a ConfigurationSection.
     * Expected YAML structure:
     * <pre>
     * polygon:
     *   min-y: 60
     *   max-y: 120
     *   points:
     *     - "0,0"
     *     - "50,0"
     *     - "50,50"
     *     - "0,50"
     * </pre>
     *
     * @param section the "polygon" config section
     * @param world   the world this polygon belongs to (may be null during template loading)
     * @return the CustomPoly2D instance
     */
    public static CustomPoly2D fromConfig(ConfigurationSection section, World world) {
        int minY = section.getInt("min-y", 0);
        int maxY = section.getInt("max-y", 319);
        List<String> rawPoints = section.getStringList("points");

        List<Point> points = new ArrayList<>();
        for (String raw : rawPoints) {
            String[] parts = raw.split(",");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid polygon point: '" + raw + "' (expected 'x,z')");
            }
            int x = Integer.parseInt(parts[0].trim());
            int z = Integer.parseInt(parts[1].trim());
            points.add(new Point(x, z));
        }

        return new CustomPoly2D(points, minY, maxY, world);
    }

    /**
     * Create a new CustomPoly2D with all point coordinates shifted by the given offset.
     * Used when cloning a template polygon into a live arena world.
     *
     * @param offsetX X offset to add to all points
     * @param offsetZ Z offset to add to all points
     * @param newWorld the new world for the shifted polygon
     * @return a new CustomPoly2D with shifted coordinates
     */
    public CustomPoly2D shift(int offsetX, int offsetZ, World newWorld) {
        List<Point> shifted = new ArrayList<>(points.size());
        for (Point p : points) {
            shifted.add(new Point(p.x() + offsetX, p.z() + offsetZ));
        }
        return new CustomPoly2D(shifted, minY, maxY, newWorld);
    }
}
