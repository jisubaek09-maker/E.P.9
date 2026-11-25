package com.bshs.geohint.model;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Locale;

/**
 * 팀 하나의 지층 모형. 원점 = 스키매틱 (0,0,0) 의 강화 심층암(원점 마커) 위치.
 * 모형 크기는 32(x) × 60(y) × 32(z).
 */
public class Region {

    public static final int SIZE_X = 32;
    public static final int SIZE_Y = 60;
    public static final int SIZE_Z = 32;

    public final int id;
    public final String world;
    public final int ox, oy, oz;
    public final String color;

    public Region(int id, String world, int ox, int oy, int oz, String color) {
        this.id = id;
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.color = color.toUpperCase(Locale.ROOT);
    }

    public World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    public boolean contains(Block b) {
        if (!b.getWorld().getName().equals(world)) return false;
        int x = b.getX() - ox, y = b.getY() - oy, z = b.getZ() - oz;
        return x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z;
    }

    // ── 벽면 (모형 바깥 한 겹. 단면을 가려서 직접 파고 들어가게 한다) ──

    public static final Material WALL = Material.SMOOTH_STONE;

    /** 모형 옆면 바로 바깥 한 칸 두께의 자리인가 */
    public boolean isWall(Block b) {
        if (!b.getWorld().getName().equals(world)) return false;
        int x = b.getX() - ox, y = b.getY() - oy, z = b.getZ() - oz;
        if (y < 0 || y >= SIZE_Y || x < -1 || x > SIZE_X || z < -1 || z > SIZE_Z) return false;
        return x == -1 || x == SIZE_X || z == -1 || z == SIZE_Z;
    }

    /** 네 옆면을 지표 높이까지 벽으로 덮는다. 빈 칸만 채우고, 채운 블록 수를 돌려준다 */
    public int buildWalls() {
        World w = bukkitWorld();
        if (w == null) return 0;
        int placed = 0;
        for (int x = -1; x <= SIZE_X; x++) {
            for (int z = -1; z <= SIZE_Z; z++) {
                if (!(x == -1 || x == SIZE_X || z == -1 || z == SIZE_Z)) continue;
                int mx = Math.max(0, Math.min(SIZE_X - 1, x));
                int mz = Math.max(0, Math.min(SIZE_Z - 1, z));
                int top = -1;
                for (int y = SIZE_Y - 1; y >= 0; y--) {
                    if (!w.getBlockAt(ox + mx, oy + y, oz + mz).getType().isAir()) {
                        top = y;
                        break;
                    }
                }
                for (int y = 0; y <= top; y++) {
                    Block b = w.getBlockAt(ox + x, oy + y, oz + z);
                    if (b.getType().isAir()) {
                        b.setType(WALL, false);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    public int removeWalls() {
        World w = bukkitWorld();
        if (w == null) return 0;
        int removed = 0;
        for (int x = -1; x <= SIZE_X; x++) {
            for (int z = -1; z <= SIZE_Z; z++) {
                if (!(x == -1 || x == SIZE_X || z == -1 || z == SIZE_Z)) continue;
                for (int y = 0; y < SIZE_Y; y++) {
                    Block b = w.getBlockAt(ox + x, oy + y, oz + z);
                    if (b.getType() == WALL) {
                        b.setType(Material.AIR, false);
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    public Location fossilLocation(Fossil f) {
        return new Location(bukkitWorld(), ox + f.dx, oy + f.dy, oz + f.dz);
    }

    /** 발광 기둥 맨 아래 위치 (모형 중앙, 지표 위) */
    public Location markerBase() {
        return new Location(bukkitWorld(), ox + SIZE_X / 2.0, oy + SIZE_Y + 1, oz + SIZE_Z / 2.0);
    }

    public NamedTextColor textColor() {
        NamedTextColor c = NamedTextColor.NAMES.value(color.toLowerCase(Locale.ROOT));
        return c != null ? c : NamedTextColor.WHITE;
    }

    public static boolean isValidColor(String name) {
        return NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT)) != null;
    }
}
