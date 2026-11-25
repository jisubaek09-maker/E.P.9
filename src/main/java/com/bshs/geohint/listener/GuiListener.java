package com.bshs.geohint.listener;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.ui.AnswerGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * 답안지 조작
 *  - 위쪽(답안지) 칸은 전부 막고, 클릭을 해석만 한다 (시료가 실제로 옮겨지지 않음)
 *  - 아래쪽(내 인벤토리)은 자유롭게 시료를 집을 수 있다. 단 Shift 이동·모아 집기는 막는다
 */
public class GuiListener implements Listener {

    private final GeoHintPlugin plugin;

    public GuiListener(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof AnswerGui gui)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int raw = e.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();
        if (!inTop) {
            InventoryAction act = e.getAction();
            if (act == InventoryAction.MOVE_TO_OTHER_INVENTORY || act == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
            }
            return;
        }
        e.setCancelled(true);
        plugin.answers().click(p, gui, raw, e.getClick(), e.getCursor());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof AnswerGui gui)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        boolean touchesTop = false;
        for (int s : e.getRawSlots()) {
            if (s < top.getSize()) touchesTop = true;
        }
        if (!touchesTop) return;
        e.setCancelled(true);
        if (e.getRawSlots().size() == 1) {
            int raw = e.getRawSlots().iterator().next();
            plugin.answers().click(p, gui, raw, ClickType.LEFT, e.getOldCursor());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof AnswerGui gui) {
            AnswerGui.onClosed(gui, e.getPlayer());
        }
    }
}
