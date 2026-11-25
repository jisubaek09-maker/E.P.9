package com.bshs.geohint.ui;

import com.bshs.geohint.model.Result;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 화면 공통 규칙
 *  - 색 : 금색(제목·시스템) / 하늘색(정보) / 회색(보조) / 초록·노랑·빨강(채점 결과에만) / 흰색(본문)
 *  - 소리 : 이벤트 하나에 소리 하나
 */
public final class Ui {

    private Ui() {}

    public static final TextColor GOLD = NamedTextColor.GOLD;
    public static final TextColor INFO = NamedTextColor.AQUA;
    public static final TextColor SUB = NamedTextColor.GRAY;
    public static final TextColor DIM = NamedTextColor.DARK_GRAY;
    public static final TextColor TEXT = NamedTextColor.WHITE;
    public static final TextColor OK = NamedTextColor.GREEN;
    public static final TextColor WARN = NamedTextColor.YELLOW;
    public static final TextColor BAD = NamedTextColor.RED;

    // ── 효과음 (이벤트 → 소리 하나) ──
    public static final String S_PAGE = "minecraft:item.book.page_turn";       // 제출·노트 기록
    public static final String S_TICK = "minecraft:block.note_block.hat";      // 채점 째깍, 카운트다운 마지막 10초
    public static final String S_HINT = "minecraft:block.amethyst_block.chime"; // 힌트 결과 (함정도 같은 소리)
    public static final String S_FOSSIL = "minecraft:block.bell.use";          // 화석 발견
    public static final String S_GOOD = "minecraft:entity.player.levelup";     // 결과 좋음, 시작
    public static final String S_BAD = "minecraft:block.note_block.bass";      // 오답·거부·부족
    public static final String S_SOFT = "minecraft:entity.experience_orb.pickup"; // 입력 완료, 잠금 해제
    public static final String S_CLICK = "minecraft:ui.button.click";           // 답안지 칸 조작
    public static final String S_CLEAR = "minecraft:ui.toast.challenge_complete"; // 추론 성공
    public static final String S_NOTICE = "minecraft:block.note_block.pling";  // 알림·카운트다운·경고
    public static final String S_OTHER = "minecraft:block.note_block.bit";     // 다른 팀 소식
    public static final String S_END = "minecraft:item.goat_horn.sound.0";     // 종료

    public static void play(Player p, String sound, float pitch) {
        p.playSound(p.getLocation(), sound, 1.0f, pitch);
    }

    public static void play(Player p, String sound, float volume, float pitch) {
        p.playSound(p.getLocation(), sound, volume, pitch);
    }

    // ── 글자 ──

    /** 기울임 없는 색 글자 */
    public static Component t(String s, TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    public static Component bold(String s, TextColor c) {
        return t(s, c).decoration(TextDecoration.BOLD, true);
    }

    public static Component join(Component... parts) {
        Component out = Component.empty().decoration(TextDecoration.ITALIC, false);
        for (Component c : parts) out = out.append(c);
        return out;
    }

    public static TextColor color(Result r) {
        switch (r) {
            case GREEN: return OK;
            case YELLOW: return WARN;
            case RED: return BAD;
            default: return DIM;
        }
    }

    public static String label(Result r) {
        switch (r) {
            case GREEN: return "초록";
            case YELLOW: return "노랑";
            case RED: return "빨강";
            default: return "없음";
        }
    }

    /** ■■■■■■■■ (gap 은 칸 사이 간격) */
    public static Component squares(Result[] rs, String gap) {
        Component out = Component.empty();
        for (int i = 0; i < rs.length; i++) {
            if (i > 0 && !gap.isEmpty()) out = out.append(Component.text(gap));
            out = out.append(t("■", color(rs[i])));
        }
        return out;
    }

    /** "초록 6 · 노랑 1 · 빨강 1" */
    public static Component counts(int g, int y, int r) {
        return join(t("초록 " + g, OK), t("  ·  ", DIM), t("노랑 " + y, WARN), t("  ·  ", DIM), t("빨강 " + r, BAD));
    }

    public static final String[] CIRCLED = {"①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧"};

    /** 122.0 → "122", 165.5 → "165.5", 6.25 → "6.25" */
    public static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        String s = String.format(Locale.ROOT, "%.2f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static String ageText(double[] a) {
        if (a == null) return null;
        if (a.length == 1) return num(a[0]) + " Ma";
        return num(a[0]) + " ~ " + num(a[1]) + " Ma";
    }

    public static String mmss(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    // ── 아이템 ──

    public static ItemStack item(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isEmpty()) {
            List<Component> fixed = new ArrayList<>();
            for (Component c : lore) fixed.add(c.decoration(TextDecoration.ITALIC, false));
            meta.lore(fixed);
        }
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack filler(Material m) {
        return item(m, Component.text(" "), null);
    }
}
