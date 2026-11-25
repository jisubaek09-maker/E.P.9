package com.bshs.geohint.ui;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.game.GameManager;
import com.bshs.geohint.model.PlayerState;
import com.bshs.geohint.model.TeamState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 핫바 위 한 줄 = 항상 떠 있는 내 상태
 *   평소       힌트 3/5  │  제출 2회
 *   제출 직후   힌트 3/5  │  다음 제출까지 3초
 *   힌트 대기   ▶ 분석할 시료를 손에 들고 우클릭  (17초)
 */
public class ActionBars {

    private static final long FLASH_MS = 2500;
    private static final Component SEP = Ui.t("   │   ", Ui.DIM);

    private final GeoHintPlugin plugin;

    public ActionBars(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    /** 잠깐(2.5초) 띄우는 메시지 */
    public void flash(Player p, Component msg) {
        flash(p, msg, FLASH_MS);
    }

    /** 화면 중앙 알림의 보조 문구도 이 줄에 띄운다 (알림이 떠 있는 시간만큼) */
    public void flash(Player p, Component msg, long millis) {
        PlayerState st = plugin.data().state(p);
        st.flash = msg;
        st.flashUntil = System.currentTimeMillis() + millis;
        p.sendActionBar(msg);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerState st = plugin.data().state(p);
            if (st.hintWaiting && now > st.hintExpire) {
                st.hintWaiting = false;
                flash(p, Ui.t("시료 분석이 취소되었습니다 (힌트는 차감되지 않음)", Ui.SUB));
                continue;
            }
            if (st.flash != null && now < st.flashUntil) {
                p.sendActionBar(st.flash);
                continue;
            }
            st.flash = null;
            p.sendActionBar(status(p, st, now));
        }
    }

    private int finishedCount() {
        int n = 0;
        for (TeamState t : plugin.game().shownTeams()) if (t.finished) n++;
        return n;
    }

    private Component status(Player p, PlayerState st, long now) {
        GameManager game = plugin.game();
        GameManager.State state = game.state();

        if (st.team <= 0 && plugin.isAdmin(p)) {
            String s;
            switch (state) {
                case RUNNING: s = "탐사 중 · 완료 " + finishedCount() + "팀"; break;
                case COUNTDOWN: s = "카운트다운"; break;
                case ENDED: s = "탐사 종료 · /reset 으로 초기화"; break;
                default: s = "대기 중 · /start 로 시작";
            }
            return Ui.join(Ui.bold("관리자", Ui.GOLD), SEP, Ui.t(s, Ui.SUB));
        }
        if (st.team <= 0) {
            return Ui.join(Ui.t("/team <1-5>", Ui.GOLD), Ui.t(" 로 팀을 정하세요", Ui.SUB));
        }
        if (state == GameManager.State.WAITING || state == GameManager.State.COUNTDOWN) {
            return Ui.join(Ui.bold(st.team + "팀", Ui.GOLD), SEP, Ui.t("탐사 대기 중", Ui.SUB));
        }
        if (state == GameManager.State.ENDED) {
            return Ui.join(Ui.bold(st.team + "팀", Ui.GOLD), SEP, Ui.t("탐사 종료", Ui.SUB));
        }

        if (st.pendingAgeSlot >= 0) {
            return Ui.join(Ui.t("▶ 채팅에 연대를 입력하세요", Ui.INFO), SEP,
                    Ui.t("범위는 두 숫자", Ui.SUB), SEP, Ui.t("취소 : 취소", Ui.SUB));
        }
        if (st.hintWaiting) {
            long left = Math.max(0, (st.hintExpire - now + 999) / 1000);
            return Ui.join(Ui.t("▶ 분석할 시료를 손에 들고 우클릭", Ui.INFO),
                    Ui.t("  (" + left + "초)", Ui.SUB));
        }

        TeamState t = game.team(st.team);
        if (t == null) return Component.empty();
        Component hints = Ui.join(Ui.t("힌트 ", Ui.SUB), Ui.t(t.hintsLeft + "/" + TeamState.MAX_HINTS, Ui.TEXT));
        if (t.finished) {
            return Ui.join(hints, SEP, Ui.t("✔ 추론 완료  " + Ui.mmss(t.finishedElapsedMs), Ui.OK));
        }
        if (t.revealing) {
            return Ui.join(hints, SEP, Ui.t("채점 중", Ui.GOLD));
        }
        if (t.cooldownUntil > now) {
            long left = (t.cooldownUntil - now + 999) / 1000;
            return Ui.join(hints, SEP, Ui.t("다음 제출까지 " + left + "초", Ui.SUB));
        }
        return Ui.join(hints, SEP, Ui.t("제출 ", Ui.SUB), Ui.t(t.submissions + "회", Ui.TEXT));
    }
}
