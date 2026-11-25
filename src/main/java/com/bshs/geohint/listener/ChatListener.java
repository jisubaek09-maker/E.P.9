package com.bshs.geohint.listener;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.model.PlayerState;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** 답안지 연대 입력 대기 중인 플레이어의 채팅은 다른 사람에게 보내지 않고 연대 입력으로 쓴다 */
public class ChatListener implements Listener {

    private final GeoHintPlugin plugin;

    public ChatListener(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        PlayerState st = plugin.data().state(p.getUniqueId());
        if (st.pendingAgeSlot < 0) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.answers().handleAgeInput(p, text));
    }
}
