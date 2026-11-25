package com.bshs.geohint.ui;

import com.bshs.geohint.model.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 팀 모형 위에 팀 색으로 빛나는 기둥.
 * 색은 점수판(Scoreboard) 팀 색으로 정해지므로 Scoreboards 가 각 점수판에 팀을 등록한다.
 *
 * 주의 : 아머스탠드를 marker(true) 로 만들면 히트박스가 0이 되어 발광 윤곽선이 안 그려진다.
 */
public final class GlowMarkers {

    private GlowMarkers() {}

    private static final int COLUMN = 6;
    private static final double SPACING = 2.0;
    private static final Map<Integer, List<UUID>> IDS = new HashMap<>();

    private static String tag(int id) {
        return "geo_marker_" + id;
    }

    public static void spawn(Plugin plugin, Region r) {
        remove(r.id);
        World world = r.bukkitWorld();
        if (world == null) {
            plugin.getLogger().warning(r.id + "팀 모형의 월드를 찾을 수 없습니다: " + r.world);
            return;
        }
        List<UUID> ids = new ArrayList<>();
        Location base = r.markerBase();
        for (int i = 0; i < COLUMN; i++) {
            Location loc = base.clone().add(0, i * SPACING, 0);
            ArmorStand stand = world.spawn(loc, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(false);
                as.setSmall(true);
                as.setInvulnerable(true);
                as.setSilent(true);
                as.setGravity(false);
                as.setGlowing(true);
                as.setPersistent(true);
                as.setCollidable(false);
                as.addScoreboardTag(tag(r.id));
            });
            ids.add(stand.getUniqueId());
        }
        IDS.put(r.id, ids);
    }

    public static void remove(int id) {
        String tag = tag(id);
        for (World w : Bukkit.getWorlds()) {
            for (ArmorStand as : w.getEntitiesByClass(ArmorStand.class)) {
                if (as.getScoreboardTags().contains(tag)) as.remove();
            }
        }
        IDS.remove(id);
    }

    public static void respawnAll(Plugin plugin, Collection<Region> regions) {
        for (Region r : regions) spawn(plugin, r);
    }

    public static List<UUID> ids(int id) {
        return IDS.getOrDefault(id, List.of());
    }
}
