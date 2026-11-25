package com.bshs.geohint.game;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.item.Items;
import com.bshs.geohint.model.Fossil;
import com.bshs.geohint.model.Region;
import com.bshs.geohint.model.TeamState;
import com.bshs.geohint.ui.TitleQueue.Frame;
import com.bshs.geohint.ui.Ui;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** 화석(로드스톤) 우클릭 → 종 이름·분류 공개 + 조사 노트 기록. 연대는 학습지 대조표에서 찾는다. */
public class FossilService {

    private final GeoHintPlugin plugin;

    public FossilService(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    /** 화석 지점이면 true */
    public boolean discover(Player p, Block block) {
        Region r = plugin.data().regionAt(block);
        if (r == null) return false;
        Fossil f = Fossil.at(block.getX() - r.ox, block.getY() - r.oy, block.getZ() - r.oz);
        if (f == null) return false;
        if (!plugin.game().isRunning()) return true;

        TeamState t = plugin.game().team(plugin.data().state(p).team);
        if (t == null) {
            plugin.bars().flash(p, Ui.t("/team <1-5> 로 팀을 먼저 정하세요", Ui.BAD));
            return true;
        }

        boolean first = t.fossils.add(f);
        if (first) {
            t.notes.add(Items.Notebook.fossilPage(f));
            for (Player m : plugin.game().members(t.id)) plugin.items().refreshNotebook(m, t);
        }
        // 화면 중앙(작은 줄) : 화석 번호 / 핫바 위 한 줄 : 종 이름
        plugin.titles().now(p, Frame.of(
                Ui.bold(f.title() + " 발견", Ui.GOLD),
                Ui.join(Ui.t(f.species, Ui.INFO), Ui.t("  ·  " + f.group, Ui.SUB),
                        first ? Ui.t("", Ui.SUB) : Ui.t("  (이미 기록함)", Ui.DIM)),
                70, first ? Ui.S_FOSSIL : Ui.S_SOFT, 1.0f));
        return true;
    }
}
