package com.bshs.geohint.listener;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.game.GameManager;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * - 블록 설치 금지, 몹 스폰 금지(발광 기둥 아머스탠드만 허용), PvP·폭죽 피해 금지
 * - 시작 전 인벤토리·아이템 버리기 금지, 도구(곡괭이·나침반·노트)는 항상 버릴 수 없음
 * - 화면 정리 : 업적·사망 메시지 숨김, 비·눈 끔
 */
public class ProtectListener implements Listener {

    private final GeoHintPlugin plugin;

    public ProtectListener(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean bypass(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && plugin.isAdmin(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (bypass(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() == EntityType.ARMOR_STAND) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (e.getDamager() instanceof Player || e.getDamager() instanceof Firework) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || bypass(p)) return;
        if (plugin.game().state() == GameManager.State.RUNNING) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) return;
        if (plugin.game().state() != GameManager.State.RUNNING
                || plugin.items().toolOf(e.getItemDrop().getItemStack()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        e.message(null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.deathMessage(null);
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (e.toWeatherState()) e.setCancelled(true);
    }
}
