package com.bshs.geohint.game;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.item.Items;
import com.bshs.geohint.model.PlayerState;
import com.bshs.geohint.model.RockLayer;
import com.bshs.geohint.model.TeamState;
import com.bshs.geohint.ui.TitleQueue.Frame;
import com.bshs.geohint.ui.Ui;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /hint → "분석할 시료를 손에 들고 우클릭" → 시료 30개 소모 → 원소·반감기·모원소/자원소 비율
 *
 * 퇴적암은 근원암 연대가 나오는 함정이지만, 화면·소리·노트 형식은 화성암과 완전히 같다.
 * 잘못된 아이템·시료 부족일 때는 힌트를 차감하지 않는다.
 */
public class HintService {

    private final GeoHintPlugin plugin;

    public HintService(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    public void begin(Player p) {
        PlayerState st = plugin.data().state(p);
        TeamState t = plugin.game().team(st.team);
        if (!plugin.game().isRunning()) {
            deny(p, "아직 탐사 중이 아닙니다", null);
            return;
        }
        if (t == null) {
            deny(p, "팀이 없습니다", "/team <1-5> 로 팀을 정하세요");
            return;
        }
        if (t.hintsLeft <= 0) {
            deny(p, "힌트를 모두 사용했습니다", "0/" + TeamState.MAX_HINTS);
            return;
        }
        st.pendingAgeSlot = -1;
        st.hintWaiting = true;
        st.hintExpire = System.currentTimeMillis() + PlayerState.HINT_WAIT_MS;
        plugin.titles().now(p, Frame.of(Ui.t("시료 분석", Ui.INFO),
                Ui.t("분석할 시료를 손에 들고 우클릭  ·  시료 " + TeamState.HINT_COST + "개 소모", Ui.INFO),
                40, Ui.S_NOTICE, 1.2f));
    }

    public void analyze(Player p, ItemStack item) {
        PlayerState st = plugin.data().state(p);
        st.hintWaiting = false;
        TeamState t = plugin.game().team(st.team);
        if (!plugin.game().isRunning() || t == null) return;
        if (t.hintsLeft <= 0) {
            deny(p, "힌트를 모두 사용했습니다", null);
            return;
        }

        Items items = plugin.items();
        RockLayer layer = items.layerOf(item);
        if (layer == null) {
            deny(p, "올바른 시료가 아닙니다", "/hint 를 다시 입력하세요");
            return;
        }
        int have = items.count(p, layer);
        if (have < TeamState.HINT_COST) {
            deny(p, "시료 부족", layer.sampleName() + " " + TeamState.HINT_COST + "개 필요  ·  현재 " + have + "개");
            return;
        }

        items.take(p, layer, TeamState.HINT_COST);
        t.hintsLeft--;
        t.hintRecords++;
        t.notes.add(Items.Notebook.hintPage(t.hintRecords, layer));
        for (Player m : plugin.game().members(t.id)) items.refreshNotebook(m, t);

        // 화면 중앙(작은 줄) : 원소·반감기 / 핫바 위 한 줄 : 비율
        plugin.titles().now(p, Frame.of(
                Ui.join(Ui.t(layer.element, Ui.INFO), Ui.t(" · 반감기 ", Ui.SUB),
                        Ui.t(Ui.num(layer.halfLife) + " Ma", Ui.INFO)),
                Ui.join(Ui.t("모원소 " + Ui.num(layer.parentPercent()) + "%", Ui.TEXT),
                        Ui.t("  ·  ", Ui.DIM),
                        Ui.t("자원소 " + Ui.num(layer.daughterPercent()) + "%", Ui.TEXT),
                        Ui.t("   │   남은 힌트 " + t.hintsLeft + "/" + TeamState.MAX_HINTS, Ui.SUB)),
                90, Ui.S_HINT, 1.0f));
    }

    private void deny(Player p, String title, String sub) {
        plugin.titles().now(p, Frame.of(Ui.t(title, Ui.BAD),
                sub == null ? null : Ui.t(sub, Ui.SUB), 40, Ui.S_BAD, 0.8f));
    }
}
