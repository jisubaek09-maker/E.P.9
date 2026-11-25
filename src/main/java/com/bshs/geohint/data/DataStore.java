package com.bshs.geohint.data;

import com.bshs.geohint.model.PlayerState;
import com.bshs.geohint.model.Region;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** regions.yml (팀별 지층 모형 위치) + players.yml (소속 팀) */
public class DataStore {

    public static final int TEAM_COUNT = 5;

    private final JavaPlugin plugin;
    private final File regionsFile;
    private final File playersFile;
    private FileConfiguration playersCfg;

    private final Map<Integer, Region> regions = new TreeMap<>();
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        this.playersFile = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        regions.clear();
        if (regionsFile.exists()) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(regionsFile);
            for (String key : cfg.getKeys(false)) {
                int id;
                try {
                    id = Integer.parseInt(key);
                } catch (NumberFormatException ex) {
                    continue;
                }
                regions.put(id, new Region(id,
                        cfg.getString(key + ".world", "world"),
                        cfg.getInt(key + ".x"), cfg.getInt(key + ".y"), cfg.getInt(key + ".z"),
                        cfg.getString(key + ".color", "WHITE")));
            }
        }
        playersCfg = YamlConfiguration.loadConfiguration(playersFile);
        for (Map.Entry<UUID, PlayerState> e : states.entrySet()) {
            e.getValue().team = playersCfg.getInt(e.getKey() + ".team", e.getValue().team);
        }
    }

    // ── regions ──

    private void saveRegions() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Region r : regions.values()) {
            String k = String.valueOf(r.id);
            cfg.set(k + ".world", r.world);
            cfg.set(k + ".x", r.ox);
            cfg.set(k + ".y", r.oy);
            cfg.set(k + ".z", r.oz);
            cfg.set(k + ".color", r.color);
        }
        try {
            cfg.save(regionsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "regions.yml 저장 실패", e);
        }
    }

    public void putRegion(Region r) {
        regions.put(r.id, r);
        saveRegions();
    }

    public void removeRegion(int id) {
        regions.remove(id);
        saveRegions();
    }

    public Region region(int id) {
        return regions.get(id);
    }

    public List<Region> allRegions() {
        return new ArrayList<>(regions.values());
    }

    public Region regionAt(Block b) {
        for (Region r : regions.values()) {
            if (r.contains(b)) return r;
        }
        return null;
    }

    /** 모형 옆면 바깥 벽 자리에 있는 블록이면 그 모형 */
    public Region wallRegionAt(Block b) {
        for (Region r : regions.values()) {
            if (r.isWall(b)) return r;
        }
        return null;
    }

    // ── players ──

    public PlayerState state(UUID id) {
        return states.computeIfAbsent(id, u -> {
            PlayerState s = new PlayerState();
            s.team = playersCfg.getInt(u + ".team", 0);
            return s;
        });
    }

    public PlayerState state(Player p) {
        return state(p.getUniqueId());
    }

    public void setTeam(Player p, int team) {
        state(p).team = team;
        playersCfg.set(p.getUniqueId() + ".team", team);
        playersCfg.set(p.getUniqueId() + ".name", p.getName());
        try {
            playersCfg.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "players.yml 저장 실패", e);
        }
    }

    public void resetTransient() {
        for (PlayerState s : states.values()) {
            s.hintWaiting = false;
            s.pendingAgeSlot = -1;
            s.flash = null;
        }
    }
}
