package net.rustcore.duel.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * A live instance of an arena allocated for a specific duel.
 * Multiple ActiveArenas can share the same {@link Arena} template
 * in separate SlimeWorld instances simultaneously.
 *
 * Spawn points are split into two teams (A and B).
 * Use {@link #pickSpawns(Random)} to randomly select one spawn per team
 * for each round.
 */
public class ActiveArena {

    private final Arena template;
    private final UUID duelId;
    private final Location origin;
    private final List<Location> teamASpawns = new ArrayList<>();
    private final List<Location> teamBSpawns = new ArrayList<>();
    private CustomPoly2D polygon;

    public ActiveArena(Arena template, UUID duelId, Location origin) {
        this.template = template;
        this.duelId = duelId;
        this.origin = origin;
    }

    public Arena getTemplate() { return template; }
    public UUID getDuelId() { return duelId; }
    public Location getOrigin() { return origin; }

    public List<Location> getTeamASpawns() { return teamASpawns; }
    public List<Location> getTeamBSpawns() { return teamBSpawns; }

    public void addTeamASpawn(Location loc) { teamASpawns.add(loc); }
    public void addTeamBSpawn(Location loc) { teamBSpawns.add(loc); }

    public CustomPoly2D getPolygon() { return polygon; }
    public void setPolygon(CustomPoly2D polygon) { this.polygon = polygon; }

    /** Convenience: get the world this arena instance lives in. */
    public World getWorld() {
        return origin != null ? origin.getWorld() : null;
    }

    /**
     * Pick one random spawn from Team A and one from Team B.
     *
     * @return a two-element list: [teamASpawn, teamBSpawn]
     * @throws IllegalStateException if either team has no spawn points
     */
    public List<Location> pickSpawns(Random rng) {
        if (teamASpawns.isEmpty() || teamBSpawns.isEmpty()) {
            throw new IllegalStateException("Arena '" + template.getId()
                    + "' is missing spawn points for one or both teams.");
        }
        Location a = teamASpawns.get(rng.nextInt(teamASpawns.size()));
        Location b = teamBSpawns.get(rng.nextInt(teamBSpawns.size()));
        List<Location> result = new ArrayList<>(2);
        result.add(a);
        result.add(b);
        return result;
    }

    /** Convenience: the template id. */
    public String getId() { return template.getId(); }
    public String getDisplayName() { return template.getDisplayName(); }
}
