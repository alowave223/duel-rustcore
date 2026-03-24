package net.rustcore.duel.arena;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Arena template: spawn-point offsets and polygon boundary definition.
 * Does NOT track availability — multiple duels can use the same template
 * simultaneously via separate {@link ActiveArena} instances.
 *
 * Spawn points are split into two teams (A and B).
 * At least one offset per team is required.
 */
public class Arena {

    private final String id;
    private final String displayName;
    private final List<Location> teamASpawns = new ArrayList<>();
    private final List<Location> teamBSpawns = new ArrayList<>();
    private CustomPoly2D templatePolygon;

    public Arena(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }

    public List<Location> getTeamASpawns() { return teamASpawns; }
    public List<Location> getTeamBSpawns() { return teamBSpawns; }

    public void addTeamASpawn(Location loc) { teamASpawns.add(loc); }
    public void addTeamBSpawn(Location loc) { teamBSpawns.add(loc); }

    public CustomPoly2D getTemplatePolygon() { return templatePolygon; }
    public void setTemplatePolygon(CustomPoly2D polygon) { this.templatePolygon = polygon; }

    public void clearSpawns() {
        teamASpawns.clear();
        teamBSpawns.clear();
    }
}
