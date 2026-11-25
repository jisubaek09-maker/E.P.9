package com.bshs.geohint.ui;

import com.bshs.geohint.model.Result;
import com.bshs.geohint.model.RockLayer;
import com.bshs.geohint.model.TeamState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 답안지 (상자 3줄)
 *
 *  [결과][결과][결과][결과][결과][결과][결과][결과][사용법]   ← 칸 위 유리판 = 지난 결과 색
 *  [ ① ][ ② ][ ③ ][ ④ ][ ⑤ ][ ⑥ ][ ⑦ ][ ⑧ ][ 제출 ]
 *  [    ][    ][    ][    ][    ][    ][    ][    ][ 닫기 ]
 *
 * 시료는 실제로 옮겨지지 않는다(칸에는 그림만 표시). 비용은 제출할 때 인벤토리에서 빠진다.
 */
public final class AnswerGui implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int FIRST_SLOT = 9;
    public static final int INFO = 8;
    public static final int SUBMIT = 17;
    public static final int CLOSE = 26;

    private static final Set<AnswerGui> OPEN = new HashSet<>();

    public final TeamState team;
    private final Inventory inv;

    public AnswerGui(TeamState team) {
        this.team = team;
        this.inv = Bukkit.createInventory(this, SIZE, Ui.bold(team.id + "팀 답안지", Ui.GOLD));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player p) {
        render();
        p.openInventory(inv);
        OPEN.add(this);
    }

    public static int slotIndex(int raw) {
        return raw >= FIRST_SLOT && raw < FIRST_SLOT + TeamState.SLOTS ? raw - FIRST_SLOT : -1;
    }

    // ── 열린 답안지 관리 ──

    public static void onClosed(AnswerGui gui, HumanEntity who) {
        boolean others = false;
        for (HumanEntity v : gui.inv.getViewers()) {
            if (!v.equals(who)) others = true;
        }
        if (!others) OPEN.remove(gui);
    }

    public static void refreshTeam(int teamId) {
        for (AnswerGui g : new ArrayList<>(OPEN)) {
            if (g.team.id == teamId) g.render();
        }
    }

    public static void refreshAll() {
        for (AnswerGui g : new ArrayList<>(OPEN)) g.render();
    }

    /** 제출 버튼만 다시 그림 (쿨다운 숫자 갱신용, 0.5초마다) */
    public static void tickAll() {
        for (AnswerGui g : new ArrayList<>(OPEN)) g.inv.setItem(SUBMIT, g.submitButton());
    }

    public static void closeTeam(int teamId) {
        for (AnswerGui g : new ArrayList<>(OPEN)) {
            if (g.team.id != teamId) continue;
            for (HumanEntity v : new ArrayList<>(g.inv.getViewers())) v.closeInventory();
            OPEN.remove(g);
        }
    }

    public static void closeAll() {
        for (AnswerGui g : new ArrayList<>(OPEN)) {
            for (HumanEntity v : new ArrayList<>(g.inv.getViewers())) v.closeInventory();
        }
        OPEN.clear();
    }

    // ── 그리기 ──

    public void render() {
        for (int i = 0; i < TeamState.SLOTS; i++) {
            inv.setItem(i, indicator(i));
            inv.setItem(FIRST_SLOT + i, slotItem(i));
        }
        inv.setItem(INFO, infoItem());
        inv.setItem(SUBMIT, submitButton());
        for (int s = 18; s < CLOSE; s++) inv.setItem(s, Ui.filler(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(CLOSE, Ui.item(Material.BARRIER, Ui.t("닫기", Ui.TEXT), null));
    }

    private ItemStack indicator(int i) {
        Result r = team.results[i];
        Material m;
        Component name;
        switch (r) {
            case GREEN:
                m = Material.LIME_STAINED_GLASS_PANE;
                name = Ui.t(Ui.CIRCLED[i] + "  초록 · 확정", Ui.OK);
                break;
            case YELLOW:
                m = Material.YELLOW_STAINED_GLASS_PANE;
                name = Ui.t(Ui.CIRCLED[i] + "  노랑 · 암석은 맞음, 연대를 다시", Ui.WARN);
                break;
            case RED:
                m = Material.RED_STAINED_GLASS_PANE;
                name = Ui.t(Ui.CIRCLED[i] + "  빨강 · 이 자리 암석이 아님", Ui.BAD);
                break;
            default:
                m = Material.GRAY_STAINED_GLASS_PANE;
                name = Ui.t(Ui.CIRCLED[i] + "  아직 채점 전", Ui.SUB);
        }
        return Ui.item(m, name, null);
    }

    private ItemStack slotItem(int i) {
        RockLayer layer = team.layers[i];
        String pos = i == 0 ? "가장 오래된 암석" : i == TeamState.SLOTS - 1 ? "가장 젊은 암석" : null;

        if (layer == null) {
            List<Component> lore = new ArrayList<>();
            if (pos != null) lore.add(Ui.t(pos, Ui.SUB));
            lore.add(Ui.t("시료를 집어서 이 칸을 클릭", Ui.DIM));
            return Ui.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Ui.t(Ui.CIRCLED[i] + "  비어 있음", Ui.SUB), lore);
        }

        boolean locked = team.locked(i);
        List<Component> lore = new ArrayList<>();
        if (pos != null) lore.add(Ui.t(pos, Ui.SUB));
        String age = Ui.ageText(team.ages[i]);
        lore.add(age == null
                ? Ui.join(Ui.t("연대  ", Ui.SUB), Ui.t("클릭해서 입력", Ui.WARN))
                : Ui.join(Ui.t("연대  ", Ui.SUB), Ui.t(age, Ui.INFO)));
        if (locked) {
            lore.add(Ui.t("확정된 칸 · 바꿀 수 없음", Ui.OK));
        } else {
            int cost = team.costFor(i);
            lore.add(cost == 0
                    ? Ui.t("제출 비용  없음", Ui.SUB)
                    : Ui.t("제출 비용  이 시료 " + cost + "개", Ui.SUB));
            lore.add(Ui.t("좌클릭 연대 입력 · 우클릭 비우기", Ui.DIM));
        }

        Component name = locked
                ? Ui.join(Ui.t(Ui.CIRCLED[i] + "  ", Ui.OK), Ui.t(layer.sampleName(), Ui.OK), Ui.t("  확정", Ui.OK))
                : Ui.join(Ui.t(Ui.CIRCLED[i] + "  ", Ui.GOLD), Ui.t(layer.sampleName(), Ui.GOLD));
        ItemStack it = Ui.item(layer.icon, name, lore);
        if (locked) {
            ItemMeta meta = it.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack infoItem() {
        return Ui.item(Material.PAPER, Ui.t("사용법", Ui.GOLD), List.of(
                Ui.t("① → ⑧  오래된 암석부터 젊은 암석 순", Ui.TEXT),
                Ui.t(" ", Ui.SUB),
                Ui.t("시료를 집어 칸을 클릭하면 놓임", Ui.SUB),
                Ui.t("칸 좌클릭  연대 입력 (채팅)", Ui.SUB),
                Ui.t("칸 우클릭  칸 비우기", Ui.SUB),
                Ui.t("범위로 답할 땐 숫자 두 개", Ui.SUB),
                Ui.t(" ", Ui.SUB),
                Ui.t("초록  암석·연대 모두 맞음 → 확정", Ui.OK),
                Ui.t("노랑  암석은 맞음, 연대가 틀림", Ui.WARN),
                Ui.t("빨강  이 자리 암석이 아님", Ui.BAD),
                Ui.t(" ", Ui.SUB),
                Ui.t("비용  새 암석을 넣은 칸마다 시료 10개", Ui.SUB),
                Ui.t("      초록 칸·노랑 칸 연대 수정은 무료", Ui.SUB)));
    }

    private ItemStack submitButton() {
        long now = System.currentTimeMillis();
        if (team.finished) {
            return Ui.item(Material.EMERALD, Ui.bold("추론 완료", Ui.OK),
                    List.of(Ui.t("기록  " + Ui.mmss(team.finishedElapsedMs), Ui.TEXT)));
        }
        if (team.revealing) {
            return Ui.item(Material.CLOCK, Ui.t("채점 중 . . .", Ui.GOLD), null);
        }
        if (!team.complete()) {
            return Ui.item(Material.GRAY_DYE, Ui.t("제출 불가", Ui.SUB), List.of(
                    Ui.t("8칸을 모두 채우고 연대를 입력하세요", Ui.SUB),
                    Ui.t("채운 칸 " + team.filledSlots() + "/8  ·  연대 " + team.enteredAges() + "/8", Ui.DIM)));
        }
        if (team.cooldownUntil > now) {
            long left = (team.cooldownUntil - now + 999) / 1000;
            return Ui.item(Material.ORANGE_DYE, Ui.t(left + "초 후 제출 가능", Ui.WARN), null);
        }
        Map<RockLayer, Integer> cost = cost(team);
        List<Component> lore = new ArrayList<>();
        lore.add(Ui.t("이번 제출 비용", Ui.SUB));
        if (cost.isEmpty()) {
            lore.add(Ui.t("  없음", Ui.TEXT));
        } else {
            for (Map.Entry<RockLayer, Integer> e : cost.entrySet()) {
                lore.add(Ui.t("  " + e.getKey().sampleName() + "  ×" + e.getValue(), Ui.TEXT));
            }
        }
        lore.add(Ui.t(" ", Ui.SUB));
        lore.add(Ui.t((team.submissions + 1) + "번째 제출", Ui.DIM));
        return Ui.item(Material.LIME_DYE, Ui.bold("답안 제출", Ui.OK), lore);
    }

    public static Map<RockLayer, Integer> cost(TeamState t) {
        Map<RockLayer, Integer> out = new EnumMap<>(RockLayer.class);
        for (int i = 0; i < TeamState.SLOTS; i++) {
            if (t.layers[i] == null) continue;
            int c = t.costFor(i);
            if (c > 0) out.merge(t.layers[i], c, Integer::sum);
        }
        return out;
    }
}
