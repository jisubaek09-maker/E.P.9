package com.bshs.geohint.listener;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.model.Region;
import com.bshs.geohint.model.RockLayer;
import com.bshs.geohint.ui.Ui;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 채굴 규칙
 *  - 팀 모형(32×60×32) 안에서만, 탐사 중에만 캘 수 있다
 *  - 한 번에 바라보는 면 2×2 (깊이 1). 웅크리면 1칸
 *  - 시료는 클릭한 칸 하나만 나온다
 *  - 변성암·흙·겉껍질은 캘 수 있지만 시료가 안 나온다. 기반암·원점 마커·화석(로드스톤)은 보호
 */
public class MiningListener implements Listener {

    /** 2 = 2×2. 3 으로 바꾸면 클릭한 칸을 가운데로 한 3×3 */
    public static final int AREA = 2;

    private static final Set<Material> NO_SAMPLE = EnumSet.of(
            Material.BLACKSTONE,      // 혼펠스
            Material.QUARTZ_BLOCK,    // 대리암
            Material.END_STONE,       // 규암
            Material.DIRT, Material.GRASS_BLOCK,
            Material.SMOOTH_STONE);   // SHELL=True 일 때 겉껍질

    private final GeoHintPlugin plugin;

    public MiningListener(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean breakable(Material m) {
        return RockLayer.fromMaterial(m) != null || NO_SAMPLE.contains(m);
    }

    /** 모형 안의 캘 수 있는 블록이거나, 모형 바깥 벽면 블록 */
    private static boolean canMine(Region r, Block b) {
        if (r.contains(b)) return breakable(b.getType());
        return r.isWall(b) && b.getType() == Region.WALL;
    }

    private boolean bypass(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && plugin.isAdmin(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (bypass(p)) return;
        Block block = event.getBlock();

        Region region = plugin.data().regionAt(block);
        if (region == null) region = plugin.data().wallRegionAt(block);   // /geo wall 로 만든 벽면
        if (region == null) {
            event.setCancelled(true);
            return;
        }
        if (!plugin.game().isRunning()) {
            event.setCancelled(true);
            plugin.bars().flash(p, Ui.t("아직 탐사 중이 아닙니다", Ui.SUB));
            return;
        }
        if (!canMine(region, block)) {
            event.setCancelled(true);
            plugin.bars().flash(p, Ui.t("캘 수 없는 블록입니다", Ui.SUB));
            return;
        }

        RockLayer layer = RockLayer.fromMaterial(block.getType());
        plugin.game().recordBreak(block);
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (layer != null) plugin.items().give(p, plugin.items().sample(layer, 1));

        if (p.isSneaking() || AREA <= 1) return;
        for (Block extra : extras(p, block)) {
            if (!canMine(region, extra)) continue;
            plugin.game().recordBreak(extra);
            BlockData data = extra.getBlockData();
            extra.setType(Material.AIR, false);
            extra.getWorld().spawnParticle(Particle.BLOCK, extra.getLocation().add(0.5, 0.5, 0.5),
                    12, 0.25, 0.25, 0.25, data);
        }
    }

    /**
     * 수평으로 볼 때 : 클릭한 칸 + 오른쪽 + (머리 높이면 아래, 발 높이면 위) 줄 → 사람 키만 한 2×2 굴
     * 위·아래를 볼 때 : 클릭한 칸 + 오른쪽 + 앞쪽 → 2×2 수직 갱도
     */
    static List<Block> extras(Player p, Block b) {
        List<Block> out = new ArrayList<>();
        BlockFace facing = p.getFacing();
        BlockFace right = rightOf(facing);
        float pitch = p.getLocation().getPitch();

        if (AREA >= 3) {
            BlockFace[] a, c;
            if (pitch > 55 || pitch < -55) {
                a = new BlockFace[]{right, right.getOppositeFace()};
                c = new BlockFace[]{facing, facing.getOppositeFace()};
            } else {
                a = new BlockFace[]{right, right.getOppositeFace()};
                c = new BlockFace[]{BlockFace.UP, BlockFace.DOWN};
            }
            for (BlockFace u : a) out.add(b.getRelative(u));
            for (BlockFace v : c) {
                out.add(b.getRelative(v));
                for (BlockFace u : a) out.add(b.getRelative(v).getRelative(u));
            }
            return out;
        }

        BlockFace second;
        if (pitch > 55 || pitch < -55) {
            second = facing;
        } else {
            int feetY = p.getLocation().getBlockY();
            second = b.getY() > feetY ? BlockFace.DOWN : BlockFace.UP;
        }
        out.add(b.getRelative(right));
        out.add(b.getRelative(second));
        out.add(b.getRelative(second).getRelative(right));
        return out;
    }

    static BlockFace rightOf(BlockFace f) {
        switch (f) {
            case NORTH: return BlockFace.EAST;
            case EAST: return BlockFace.SOUTH;
            case SOUTH: return BlockFace.WEST;
            case WEST: return BlockFace.NORTH;
            default: return BlockFace.EAST;
        }
    }
}
