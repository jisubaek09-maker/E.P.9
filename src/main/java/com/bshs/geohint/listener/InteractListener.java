package com.bshs.geohint.listener;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.model.PlayerState;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 우클릭 처리
 *  1) 화석(로드스톤) 우클릭 → 화석 기록
 *  2) /hint 직후 우클릭 → 손에 든 시료 분석 (한 번의 시도로 끝)
 *  3) 나침반 클릭은 아무 일도 일어나지 않게 막는다 (자석석 재연결, WorldEdit 순간이동 방지)
 */
public class InteractListener implements Listener {

    private final GeoHintPlugin plugin;

    public InteractListener(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * WorldEdit 는 나침반을 "탐색 막대" 로 써서 우클릭(벽 통과)·좌클릭(바라보는 곳으로 이동)에 순간이동한다.
     * WorldEdit 보다 먼저(LOWEST) 이벤트를 취소해 두면 WorldEdit 가 이 클릭을 건너뛴다.
     * 크리에이티브 관리자는 건축 때 쓸 수 있게 그대로 둔다.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCompassClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;
        Action a = e.getAction();
        if (a == Action.PHYSICAL) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE && plugin.isAdmin(p)) return;
        // 블록 좌클릭까지 막으면 나침반을 든 채로는 캘 수 없게 되므로, WorldEdit 권한이 있는 OP 에게만 적용
        if (a == Action.LEFT_CLICK_BLOCK && !plugin.isAdmin(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        Block clicked = e.getClickedBlock();

        if (a == Action.RIGHT_CLICK_BLOCK && clicked != null && clicked.getType() == Material.LODESTONE) {
            if (plugin.fossils().discover(p, clicked)) {
                e.setCancelled(true);
                return;
            }
        }

        PlayerState st = plugin.data().state(p);
        if (st.hintWaiting) {
            e.setCancelled(true);
            plugin.hints().analyze(p, e.getItem());
            return;
        }

        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.COMPASS) {
            e.setCancelled(true);
        }
    }
}
