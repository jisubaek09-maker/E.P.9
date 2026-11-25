package com.bshs.geohint.listener;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.game.GameManager;
import com.bshs.geohint.model.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffectType;

/** 입장·퇴장 : 메시지 숨김, 야간투시(감마 대체), 점수판, 진행 중이면 도구 지급 */
public class JoinListener implements Listener {

    private final GeoHintPlugin plugin;

    public JoinListener(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.joinMessage(null);
        Player p = e.getPlayer();
        plugin.boards().assign(p);
        plugin.game().onJoin(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.quitMessage(null);
        Player p = e.getPlayer();
        PlayerState st = plugin.data().state(p);
        st.hintWaiting = false;
        st.pendingAgeSlot = -1;
        plugin.titles().clear(p);
    }

    /** 죽으면 효과가 모두 사라지므로 부활 직후 다시 건다 */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyEffects(plugin, p), 2L);
    }

    private static void applyEffects(GeoHintPlugin plugin, Player p) {
        if (!p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) GameManager.applyNightVision(p);
        if (plugin.game().isParticipant(p) && !p.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
            GameManager.applyJumpBoost(p);
        }
    }

    /** 우유 등으로 야간투시·점프 강화가 지워진 경우 5초마다 다시 건다 */
    public static void startEffectTask(GeoHintPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) applyEffects(plugin, p);
        }, 20L * 5, 20L * 5);
    }
}
