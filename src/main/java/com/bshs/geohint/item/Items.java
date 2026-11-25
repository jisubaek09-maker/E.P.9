package com.bshs.geohint.item;

import com.bshs.geohint.model.Fossil;
import com.bshs.geohint.model.Region;
import com.bshs.geohint.model.RockLayer;
import com.bshs.geohint.model.TeamState;
import com.bshs.geohint.ui.Ui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 시료·곡괭이·화석 나침반·조사 노트 */
public class Items {

    public static final String TOOL_PICKAXE = "pickaxe";
    public static final String TOOL_COMPASS = "compass";
    public static final String TOOL_NOTEBOOK = "notebook";

    public static final int EFFICIENCY_LEVEL = 2;

    private final NamespacedKey layerKey;
    private final NamespacedKey toolKey;

    public Items(Plugin plugin) {
        this.layerKey = new NamespacedKey(plugin, "layer");
        this.toolKey = new NamespacedKey(plugin, "tool");
    }

    // ── 시료 ──

    public ItemStack sample(RockLayer layer, int amount) {
        ItemStack it = Ui.item(layer.icon, Ui.t(layer.sampleName(), Ui.GOLD),
                List.of(Ui.t("지질 시료", Ui.SUB)));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(layerKey, PersistentDataType.STRING, layer.name());
        it.setItemMeta(meta);
        it.setAmount(amount);
        return it;
    }

    public RockLayer layerOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        String key = it.getItemMeta().getPersistentDataContainer().get(layerKey, PersistentDataType.STRING);
        return RockLayer.fromKey(key);
    }

    public int count(Player p, RockLayer layer) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (layerOf(it) == layer) n += it.getAmount();
        }
        return n;
    }

    /** 부족하면 아무것도 가져가지 않고 false */
    public boolean take(Player p, RockLayer layer, int amount) {
        if (count(p, layer) < amount) return false;
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (layerOf(it) != layer) continue;
            int use = Math.min(left, it.getAmount());
            left -= use;
            if (use == it.getAmount()) {
                inv.setItem(i, null);
            } else {
                it.setAmount(it.getAmount() - use);
                inv.setItem(i, it);
            }
        }
        return true;
    }

    /** 인벤토리가 꽉 차면 발밑에 떨어뜨린다 */
    public void give(Player p, ItemStack it) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ── 도구 ──

    public String toolOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
    }

    public boolean hasTool(Player p, String tool) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (tool.equals(toolOf(it))) return true;
        }
        return false;
    }

    private ItemStack tag(ItemStack it, String tool) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack pickaxe() {
        ItemStack it = Ui.item(Material.DIAMOND_PICKAXE, Ui.t("지질 조사용 곡괭이", Ui.GOLD),
                List.of(Ui.t("한 번에 2×2 · 웅크리면 1칸", Ui.SUB)));
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.EFFICIENCY, EFFICIENCY_LEVEL, true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return tag(it, TOOL_PICKAXE);
    }

    public ItemStack compass(Fossil f, Region region) {
        ItemStack it = Ui.item(Material.COMPASS, Ui.t("화석 나침반 " + f.mark, Ui.GOLD),
                List.of(Ui.t("가리키는 곳에 화석이 묻혀 있다", Ui.SUB),
                        Ui.t("화석(로드스톤)을 우클릭해 기록", Ui.SUB)));
        CompassMeta meta = (CompassMeta) it.getItemMeta();
        meta.setLodestone(region.fossilLocation(f));
        meta.setLodestoneTracked(true);
        it.setItemMeta(meta);
        return tag(it, TOOL_COMPASS);
    }

    public ItemStack notebook(TeamState team) {
        ItemStack it = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) it.getItemMeta();
        meta.title(Component.text("야외 조사 노트"));
        meta.author(Component.text(team.id + "팀"));
        List<Component> pages = new ArrayList<>();
        pages.add(Notebook.cover(team));
        pages.addAll(team.notes);
        meta.addPages(pages.toArray(new Component[0]));
        meta.displayName(Ui.t("야외 조사 노트", Ui.GOLD));
        meta.lore(List.of(Ui.t("힌트·화석 기록이 자동으로 쌓인다", Ui.SUB)));
        it.setItemMeta(meta);
        return tag(it, TOOL_NOTEBOOK);
    }

    /** 곡괭이(없을 때만) + 화석 나침반 4개 + 조사 노트 */
    public void giveKit(Player p, TeamState team, Region region) {
        PlayerInventory inv = p.getInventory();
        boolean hasPickaxe = false;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            String tool = toolOf(contents[i]);
            if (TOOL_PICKAXE.equals(tool)) hasPickaxe = true;
            if (TOOL_COMPASS.equals(tool) || TOOL_NOTEBOOK.equals(tool)) inv.setItem(i, null);
        }
        if (!hasPickaxe) give(p, pickaxe());
        if (region != null) {
            for (Fossil f : Fossil.values()) give(p, compass(f, region));
        }
        if (team != null) give(p, notebook(team));
    }

    /** 팀원들의 조사 노트를 최신 내용으로 바꾼다 */
    public void refreshNotebook(Player p, TeamState team) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        ItemStack book = notebook(team);
        for (int i = 0; i < contents.length; i++) {
            if (TOOL_NOTEBOOK.equals(toolOf(contents[i]))) {
                inv.setItem(i, book);
                return;
            }
        }
        give(p, book);
    }

    // ── 조사 노트 페이지 ──

    public static final class Notebook {

        private Notebook() {}

        private static Component line(String s, NamedTextColor c) {
            return Component.text(s + "\n", c).decoration(TextDecoration.ITALIC, false);
        }

        public static Component cover(TeamState t) {
            return Component.empty()
                    .append(line("야외 조사 노트", NamedTextColor.DARK_AQUA))
                    .append(line(t.id + "팀", NamedTextColor.DARK_GRAY))
                    .append(line("", NamedTextColor.BLACK))
                    .append(line("힌트 기록  " + t.hintRecords + "건", NamedTextColor.BLACK))
                    .append(line("화석 기록  " + t.fossils.size() + "건", NamedTextColor.BLACK))
                    .append(line("남은 힌트  " + t.hintsLeft + "/" + TeamState.MAX_HINTS, NamedTextColor.BLACK))
                    .append(line("", NamedTextColor.BLACK))
                    .append(line("다음 장부터 기록한", NamedTextColor.DARK_GRAY))
                    .append(line("순서대로 쌓인다.", NamedTextColor.DARK_GRAY));
        }

        public static Component hintPage(int index, RockLayer l) {
            return Component.empty()
                    .append(line("[힌트 " + index + "]", NamedTextColor.DARK_AQUA))
                    .append(line("", NamedTextColor.BLACK))
                    .append(line("시료", NamedTextColor.DARK_GRAY))
                    .append(line(l.sampleName(), NamedTextColor.BLACK))
                    .append(line("", NamedTextColor.BLACK))
                    .append(line("구성원소  " + l.element, NamedTextColor.BLACK))
                    .append(line("반감기    " + Ui.num(l.halfLife) + " Ma", NamedTextColor.BLACK))
                    .append(line("모원소    " + Ui.num(l.parentPercent()) + "%", NamedTextColor.BLACK))
                    .append(line("자원소    " + Ui.num(l.daughterPercent()) + "%", NamedTextColor.BLACK));
        }

        public static Component fossilPage(Fossil f) {
            return Component.empty()
                    .append(line("[" + f.title() + "]", NamedTextColor.DARK_AQUA))
                    .append(line("", NamedTextColor.BLACK))
                    .append(line("종", NamedTextColor.DARK_GRAY))
                    .append(line(f.species, NamedTextColor.BLACK))
                    .append(line("", NamedTextColor.BLACK))
                    .append(line("분류  " + f.group, NamedTextColor.BLACK))
                    .append(line("", NamedTextColor.BLACK))
                    .append(line("이 지층에서 처음", NamedTextColor.BLACK))
                    .append(line("출현한 종이다.", NamedTextColor.BLACK))
                    .append(line("", NamedTextColor.BLACK))
                    .append(line("연대는 화석 연대", NamedTextColor.DARK_GRAY))
                    .append(line("대조표에서 찾을 것", NamedTextColor.DARK_GRAY));
        }
    }
}
