/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.l2j.gameserver.pathfinding.geonodes;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import net.sf.l2j.gameserver.GeoEngine;
import net.sf.l2j.gameserver.geoengine.geodata.ABlock;
import net.sf.l2j.gameserver.geoengine.geodata.GeoStructure;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.L2World;

/**
 * Geo-grid based A* pathfinder following VERGE SOURCE 2.2 pattern.
 * Each geo cell is a node in the A* graph, using NSWE flags directly.
 * No pathnode files required.
 *
 * Key differences from old pathnode-based approach:
 * - Uses getBlock().getHeightBelow() to find floor layers (not getHeightNearest)
 * - Adds CELL_IGNORE_HEIGHT offset when expanding neighbors
 * - Proper diagonal expansion with intermediate node NSWE checks
 * - Full bounds checking on the geo grid
 */
public class GeoGridPathFinder
{
    private static final int MOVE_WEIGHT = 10;
    private static final int MOVE_WEIGHT_DIAG = 14;
    private static final int OBSTACLE_WEIGHT = 50;
    private static final int OBSTACLE_WEIGHT_DIAG = 70;
    private static final int HEURISTIC_WEIGHT = 10;
    private static final int MAX_ITERATIONS = 6000;

    // Total geo grid size (~20000+ cells spanning all regions)
    private static final int GEO_CELLS_MAX = 65536;

    // Target coordinates (instance state like VERGE)
    private int _gtx;
    private int _gty;
    private int _gtz;

    private PathNode _current;

    // Per-pathfinding caches for LOS pruning (avoid redundant getBlock/NSWE lookups)
    private HashMap<Long, ABlock> _blockCache;
    private HashMap<Long, Byte> _nsweCache;

    /**
     * Inner Node class for A* pathfinding.
     */
    private static class PathNode implements Comparable<PathNode>
    {
        final int geoX;
        final int geoY;
        int z;
        final byte nswe;

        int costG;
        int costH;
        int costF;

        PathNode parent;

        PathNode(int gx, int gy, int gz, byte nsweVal)
        {
            this.geoX = gx;
            this.geoY = gy;
            this.z = gz;
            this.nswe = nsweVal;
        }

        @Override
        public int compareTo(PathNode o)
        {
            return this.costF - o.costF;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof PathNode))
                return false;
            PathNode other = (PathNode) obj;
            return this.geoX == other.geoX && this.geoY == other.geoY && this.z == other.z;
        }

        @Override
        public int hashCode()
        {
            return geoX * 31 + geoY * 31 + z;
        }

        void setCost(PathNode parentNode, int weight, int costHVal)
        {
            this.costG = weight;
            if (parentNode != null)
                this.costG += parentNode.costG;
            this.costH = costHVal;
            this.costF = this.costG + this.costH;
            this.parent = parentNode;
        }
    }

    /**
     * Find a path from world coordinates to world coordinates.
     */
    public List<Location> findPath(int ox, int oy, int oz, int tx, int ty, int tz)
    {
        // Convert to geo coordinates
        int gox = GeoEngine.getInstance().getGeoX(ox);
        int goy = GeoEngine.getInstance().getGeoY(oy);
        int gtx = GeoEngine.getInstance().getGeoX(tx);
        int gty = GeoEngine.getInstance().getGeoY(ty);

        // Check bounds (total geo grid, not per-region)
        if (gox < 0 || gox >= GEO_CELLS_MAX || goy < 0 || goy >= GEO_CELLS_MAX)
            return Collections.emptyList();
        if (gtx < 0 || gtx >= GEO_CELLS_MAX || gty < 0 || gty >= GEO_CELLS_MAX)
            return Collections.emptyList();

        // Check if origin and target are the same cell
        if (gox == gtx && goy == gty)
        {
            return Collections.emptyList();
        }

        // Get blocks for start and target — use getHeightBelow consistently
        // so start/target Z matches what addNode produces for neighbors.
        ABlock startBlock = GeoEngine.getInstance().getBlock(gox, goy);
        if (!startBlock.hasGeoPos())
            return Collections.emptyList();

        ABlock targetBlock = GeoEngine.getInstance().getBlock(gtx, gty);
        if (!targetBlock.hasGeoPos())
            return Collections.emptyList();

        // Use getHeightBelow for both start and target to stay consistent
        // with addNode's getHeightBelow usage.
        int goz = startBlock.getHeightBelow(gox, goy, oz);
        int gtz = targetBlock.getHeightBelow(gtx, gty, tz);

        // Set target coordinates
        _gtx = gtx;
        _gty = gty;
        _gtz = gtz;

        // Initialize per-path caches
        _blockCache = new HashMap<>(512);
        _nsweCache = new HashMap<>(1024);

        byte startNswe = startBlock.getNsweBelow(gox, goy, oz);

        // Create start node
        _current = new PathNode(gox, goy, goz, startNswe);
        _current.setCost(null, 0, getCostH(gox, goy, goz));

        // A* containers
        PriorityQueue<PathNode> opened = new PriorityQueue<>();
        Set<PathNode> closed = new HashSet<>();

        opened.add(_current);

        int count = 0;
        while (!opened.isEmpty() && count < MAX_ITERATIONS)
        {
            _current = opened.poll();

            // Reached target
            if (_current.geoX == _gtx && _current.geoY == _gty && _current.z == _gtz)
            {
                return constructPath();
            }

            closed.add(_current);
            expand(_current, opened, closed);

            count++;
        }

        // No path found
        return Collections.emptyList();
    }

    /**
     * Expand current node — 4 cardinal + 4 diagonal, matching VERGE logic.
     */
    private void expand(PathNode current, PriorityQueue<PathNode> opened,
            Set<PathNode> closed)
    {
        final byte nswe = current.nswe;
        if (nswe == GeoStructure.CELL_FLAG_NONE)
            return;

        final int x = current.geoX;
        final int y = current.geoY;
        final int z = current.z + GeoStructure.CELL_IGNORE_HEIGHT;

        // Cardinal directions — capture returned NSWE for diagonal checks
        final byte nsweN = addDirectionalNode(x, y, z, nswe, 0, -1, GeoStructure.CELL_FLAG_N, opened, closed);
        final byte nsweS = addDirectionalNode(x, y, z, nswe, 0, 1, GeoStructure.CELL_FLAG_S, opened, closed);
        final byte nsweW = addDirectionalNode(x, y, z, nswe, -1, 0, GeoStructure.CELL_FLAG_W, opened, closed);
        final byte nsweE = addDirectionalNode(x, y, z, nswe, 1, 0, GeoStructure.CELL_FLAG_E, opened, closed);

        // Diagonal directions — VERGE checks intermediate node NSWE
        addCornerNode(x, y, z, nswe, -1, -1, GeoStructure.CELL_FLAG_W, GeoStructure.CELL_FLAG_N, nsweW, nsweN, opened, closed);
        addCornerNode(x, y, z, nswe, 1, -1, GeoStructure.CELL_FLAG_E, GeoStructure.CELL_FLAG_N, nsweE, nsweN, opened, closed);
        addCornerNode(x, y, z, nswe, -1, 1, GeoStructure.CELL_FLAG_W, GeoStructure.CELL_FLAG_S, nsweW, nsweS, opened, closed);
        addCornerNode(x, y, z, nswe, 1, 1, GeoStructure.CELL_FLAG_E, GeoStructure.CELL_FLAG_S, nsweE, nsweS, opened, closed);
    }

    private byte addDirectionalNode(int x, int y, int z, byte nswe, int dx, int dy,
            byte directionFlag, PriorityQueue<PathNode> opened, Set<PathNode> closed)
    {
        return ((nswe & directionFlag) != 0) ? addNode(x + dx, y + dy, z, false, opened, closed) : GeoStructure.CELL_FLAG_NONE;
    }

    private void addCornerNode(int x, int y, int z, byte nswe, int dx, int dy,
            byte directionFlagX, byte directionFlagY, byte nsweX, byte nsweY,
            PriorityQueue<PathNode> opened, Set<PathNode> closed)
    {
        // Check intermediate nodes allow movement in the diagonal components
        if ((nsweX & directionFlagY) != 0 && (nsweY & directionFlagX) != 0)
        {
            if ((getNodeNswe(x + dx, y, z) & directionFlagY) != 0)
                addNode(x + dx, y + dy, z, true, opened, closed);
        }
    }

    /**
     * Get NSWE for a cell using getBlock (same as VERGE getNodeNswe).
     */
    private static byte getNodeNswe(int gx, int gy, int gz)
    {
        if (gx < 0 || gx >= GEO_CELLS_MAX || gy < 0 || gy >= GEO_CELLS_MAX)
            return GeoStructure.CELL_FLAG_NONE;

        final ABlock block = GeoEngine.getInstance().getBlock(gx, gy);
        if (!block.hasGeoPos())
            return GeoStructure.CELL_FLAG_NONE;	short h = block.getHeightBelow(gx, gy, gz);
        return block.getNsweBelow(gx, gy, h);
    }

    /**
     * Generate a node, validate it, and add to opened list.
     * Uses getBlock + getHeightBelow like VERGE addNode.
     */
    private byte addNode(int gx, int gy, int gz, boolean diagonal,
            PriorityQueue<PathNode> opened, Set<PathNode> closed)
    {
        // Bounds check (total geo grid)
        if (gx < 0 || gx >= GEO_CELLS_MAX || gy < 0 || gy >= GEO_CELLS_MAX)
            return GeoStructure.CELL_FLAG_NONE;

        // Get geodata block
        final ABlock block = GeoEngine.getInstance().getBlock(gx, gy);
        if (!block.hasGeoPos())
            return GeoStructure.CELL_FLAG_NONE;	// Get floor height below gz (using getHeightBelow like VERGE getIndexBelow)
        gz = block.getHeightBelow(gx, gy, gz);
        final byte nswe = block.getNsweBelow(gx, gy, gz);

        // Create node
        final PathNode node = new PathNode(gx, gy, gz, nswe);

        // Skip if already visited
        if (opened.contains(node) || closed.contains(node))
            return nswe;

        // Calculate weight — VERGE pattern
        final int weight = (nswe == GeoStructure.CELL_FLAG_ALL)
                ? (diagonal ? MOVE_WEIGHT_DIAG : MOVE_WEIGHT)
                : (diagonal ? OBSTACLE_WEIGHT_DIAG : OBSTACLE_WEIGHT);

        // Set cost and add
        node.setCost(_current, weight, getCostH(gx, gy, gz));
        opened.add(node);

        return nswe;
    }

    /**
     * Heuristic: diagonal distance method (matching VERGE getCostH).
     */
    private int getCostH(int gx, int gy, int gz)
    {
        final int dx = Math.abs(gx - _gtx);
        final int dy = Math.abs(gy - _gty);
        final int dz = Math.abs(gz - _gtz) / GeoStructure.CELL_HEIGHT;

        return (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * HEURISTIC_WEIGHT);
    }

    /**
     * Construct path from target back to start. Keeps only corner waypoints
     * and prunes intermediate nodes that have line-of-sight to non-consecutive
     * points (smooth path, no zigzag).
     *
     * The returned path EXCLUDES the start node (the cell the character is
     * already standing in): routing the first segment through its center would
     * cause a visible micro-hop (abrupt turn) before the route begins. The
     * character moves directly toward the first real waypoint instead.
     *
     * Note: the degenerate early return (raw.size() <= 2) still contains the
     * start node — the caller requires a path of at least 2 nodes, and a
     * 2-node path is already a single tiny segment, so the hop is negligible.
     */
    private List<Location> constructPath()
    {
        // Step 1: Build raw path from target to start (reversed)
        LinkedList<Location> raw = new LinkedList<>();
        PathNode current = _current;
        PathNode parent = current.parent;

        while (parent != null)
        {
            raw.addFirst(new Location(
                    (current.geoX << 4) + L2World.MAP_MIN_X + 8,
                    (current.geoY << 4) + L2World.MAP_MIN_Y + 8,
                    current.z));
            current = parent;
            parent = current.parent;
        }
        // Add start node
        raw.addFirst(new Location(
                (current.geoX << 4) + L2World.MAP_MIN_X + 8,
                (current.geoY << 4) + L2World.MAP_MIN_Y + 8,
                current.z));

        if (raw.size() <= 2)
            return raw;

        // Step 2: Line-of-sight pruning — remove intermediate nodes
        // when there is a clear path between non-consecutive points.
        LinkedList<Location> path = new LinkedList<>();
        path.add(raw.get(0));

        int lastKept = 0;
        for (int i = 2; i < raw.size(); i++)
        {
            if (!hasLineOfSight(raw.get(lastKept), raw.get(i)))
            {
                path.add(raw.get(i - 1));
                lastKept = i - 1;
            }
        }

        // Always include the last node (target)
        if (lastKept != raw.size() - 1)
        {
            path.add(raw.get(raw.size() - 1));
        }

        // Drop the start node: the character is already standing inside that
        // cell, so routing the first segment through its center would cause a
        // visible micro-hop (abrupt turn) before the route actually begins.
        // Only drop it when at least one real waypoint remains (path must keep
        // size >= 2 so moveToLocation() still treats it as a valid path).
        if (path.size() > 2)
        {
            path.removeFirst();
        }

        return path;
    }

    /**
     * Get a cached geo block for the given cell. Blocks don't change during
     * pathfinding, so this avoids repeated GeoEngine.getBlock() lookups.
     */
    private ABlock cachedBlock(int gx, int gy)
    {
        long key = ((long) gx << 16) | (gy & 0xFFFFL);
        ABlock block = _blockCache.get(key);
        if (block == null)
        {
            block = GeoEngine.getInstance().getBlock(gx, gy);
            _blockCache.put(key, block);
        }
        return block;
    }

    /**
     * Get a cached NSWE for a cell at a given height. Avoids repeated
     * getHeightBelow + getNsweBelow calls for the same cell.
     */
    private byte cachedNswe(int gx, int gy, int worldZ)
    {
        // Key packs geoX, geoY, and worldZ into a single long
        long key = ((long) gx << 32) | ((long) gy << 16) | (worldZ & 0xFFFFL);
        Byte cached = _nsweCache.get(key);
        if (cached != null)
            return cached;

        ABlock block = cachedBlock(gx, gy);
        if (!block.hasGeoPos())
        {
            _nsweCache.put(key, GeoStructure.CELL_FLAG_NONE);
            return GeoStructure.CELL_FLAG_NONE;
        }
        short h = block.getHeightBelow(gx, gy, worldZ);
        byte nswe = block.getNsweBelow(gx, gy, h);
        _nsweCache.put(key, nswe);
        return nswe;
    }

    /**
     * Get a cached floor height for a cell at a given Z.
     * Also warms _nsweCache to avoid a redundant lookup for the same cell.
     */
    private short cachedHeight(int gx, int gy, int worldZ)
    {
        ABlock block = cachedBlock(gx, gy);
        if (!block.hasGeoPos())
            return (short) worldZ;
        short h = block.getHeightBelow(gx, gy, worldZ);
        // Warm the NSWE cache for this cell/height to avoid redundant lookup
        long nsweKey = ((long) gx << 32) | ((long) gy << 16) | (worldZ & 0xFFFFL);
        if (!_nsweCache.containsKey(nsweKey))
            _nsweCache.put(nsweKey, block.getNsweBelow(gx, gy, h));
        return h;
    }

    /**
     * Convert direction deltas to NSWE flag bits.
     */
    private static byte dirFlag(int dx, int dy)
    {
        if (dx == 0 && dy < 0) return GeoStructure.CELL_FLAG_N;  // North
        if (dx == 0 && dy > 0) return GeoStructure.CELL_FLAG_S;  // South
        if (dx < 0 && dy == 0) return GeoStructure.CELL_FLAG_W;  // West
        if (dx > 0 && dy == 0) return GeoStructure.CELL_FLAG_E;  // East
        // Diagonal — both cardinal flags must be set
        if (dx < 0 && dy < 0) return (byte) (GeoStructure.CELL_FLAG_W | GeoStructure.CELL_FLAG_N);
        if (dx > 0 && dy < 0) return (byte) (GeoStructure.CELL_FLAG_E | GeoStructure.CELL_FLAG_N);
        if (dx < 0 && dy > 0) return (byte) (GeoStructure.CELL_FLAG_W | GeoStructure.CELL_FLAG_S);
        if (dx > 0 && dy > 0) return (byte) (GeoStructure.CELL_FLAG_E | GeoStructure.CELL_FLAG_S);
        return 0;
    }

    /**
     * Check line-of-sight between two world-coordinate points using a
     * Bresenham line walk with cached block/NSWE lookups. Much faster than
     * calling GeoEngine.moveCheck() repeatedly during LOS pruning.
     *
     * Returns true if every cell along the line allows movement toward the
     * next cell and the height difference is within CELL_IGNORE_HEIGHT.
     */
    private boolean hasLineOfSight(Location from, Location to)
    {
        // Convert to geo cell coordinates (cell center)
        int fromGX = (from.getX() - L2World.MAP_MIN_X - 8) >> 4;
        int fromGY = (from.getY() - L2World.MAP_MIN_Y - 8) >> 4;
        int toGX = (to.getX() - L2World.MAP_MIN_X - 8) >> 4;
        int toGY = (to.getY() - L2World.MAP_MIN_Y - 8) >> 4;

        // Same cell = always clear
        if (fromGX == toGX && fromGY == toGY)
            return true;

        // Bounds check
        if (fromGX < 0 || fromGX >= GEO_CELLS_MAX || fromGY < 0 || fromGY >= GEO_CELLS_MAX)
            return false;
        if (toGX < 0 || toGX >= GEO_CELLS_MAX || toGY < 0 || toGY >= GEO_CELLS_MAX)
            return false;

        // Starting cell NSWE check
        int curZ = from.getZ();
        short curH = cachedHeight(fromGX, fromGY, curZ);
        byte curNswe = cachedNswe(fromGX, fromGY, curZ);
        if (curNswe == GeoStructure.CELL_FLAG_NONE)
            return false;

        // Bresenham line walk
        int dx = Math.abs(toGX - fromGX);
        int dy = Math.abs(toGY - fromGY);
        int sx = fromGX < toGX ? 1 : -1;
        int sy = fromGY < toGY ? 1 : -1;
        int err = dx - dy;

        int curX = fromGX;
        int curY = fromGY;

        while (curX != toGX || curY != toGY)
        {
            // Compute next cell
            int e2 = 2 * err;
            int nextX = curX;
            int nextY = curY;
            if (e2 > -dy)
            {
                err -= dy;
                nextX += sx;
            }
            if (e2 < dx)
            {
                err += dx;
                nextY += sy;
            }

            // Determine required direction flag
            byte dir = dirFlag(nextX - curX, nextY - curY);
            if (dir == 0)
                return false;

            // Check current cell allows this movement
            if ((curNswe & dir) == 0)
                return false;

            // Diagonal: verify intermediate cells allow the corner cut
            // (matches addCornerNode logic in A* expansion)
            if (nextX != curX && nextY != curY)
            {
                byte nsweSideX = cachedNswe(nextX, curY, curH + GeoStructure.CELL_IGNORE_HEIGHT);
                byte nsweSideY = cachedNswe(curX, nextY, curH + GeoStructure.CELL_IGNORE_HEIGHT);
                byte sideDirX = dirFlag(nextX - curX, 0);
                byte sideDirY = dirFlag(0, nextY - curY);
                if ((nsweSideX & sideDirX) == 0 || (nsweSideY & sideDirY) == 0)
                    return false;
            }

            // Get next cell info
            if (nextX < 0 || nextX >= GEO_CELLS_MAX || nextY < 0 || nextY >= GEO_CELLS_MAX)
                return false;

            short nextH = cachedHeight(nextX, nextY, curH + GeoStructure.CELL_IGNORE_HEIGHT);
            byte nextNswe = cachedNswe(nextX, nextY, curH + GeoStructure.CELL_IGNORE_HEIGHT);

            // Check height difference — mirror A* expansion tolerance
            if (Math.abs(nextH - curH) > GeoStructure.CELL_IGNORE_HEIGHT)
                return false;

            // Advance
            curX = nextX;
            curY = nextY;
            curH = nextH;
            curNswe = nextNswe;
        }

        return true;
    }
}
