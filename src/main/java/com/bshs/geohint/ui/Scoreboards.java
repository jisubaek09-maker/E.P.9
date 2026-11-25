package com.bshs.geohint.ui;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.model.Region;
import com.bshs.geohint.model.TeamState;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 오른쪽 점수판. 보는 사람의 팀마다 점수판을 따로 만들어 내 팀 줄 앞에만 ▶ 를 붙인다.
 * 오른쪽 빨간 숫자는 숨긴다.
 *
 *    지층 추론 현황
 *
 *  ▶ 1팀  ■■■■■■■■  2회
 *    2팀  ✔ 완료  12:34
 *    3팀  ─ 제출 전
 */
public class Scoreboards {

    private static final String OBJECTIVE = "geo_side";
    private static final String GLOW_PREFIX = "geo_glow_";

    private final GeoHintPlugin plugin;
    /** 보는 사람의 팀 번호(0 = 팀 없음) → 점수판 */
    private final Map<Integer, Scoreboard> boards = new HashMap<>();

    public Scoreboards(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    private Scoreboard board(int viewerTeam) {
        return boards.computeIfAbsent(viewerTeam, k -> {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = sb.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                    Ui.bold("지층 추론 현황", Ui.GOLD));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            o.numberFormat(NumberFormat.blank());
            return sb;
        });
    }

    public void assign(Player p) {
        Scoreboard sb = board(plugin.data().state(p).team);
        render(sb, plugin.data().state(p).team);
        p.setScoreboard(sb);
    }

    public void refresh() {
        for (Player p : Bukkit.getOnlinePlayers()) board(plugin.data().state(p).team);
        for (Map.Entry<Integer, Scoreboard> e : boards.entrySet()) {
            render(e.getValue(), e.getKey());
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = board(plugin.data().state(p).team);
            if (p.getScoreboard() != sb) p.setScoreboard(sb);
        }
    }

    private void render(Scoreboard sb, int viewerTeam) {
        syncGlow(sb);
        Objective o = sb.getObjective(OBJECTIVE);
        if (o == null) return;
        for (String entry : sb.getEntries()) {
            if (entry.startsWith("line_")) sb.resetScores(entry);
        }

        List<TeamState> ranked = plugin.game().rankedTeams();
        int score = 100;
        line(o, "line_top", score--, Component.text(" "));
        int n = 0;
        for (TeamState t : ranked) {
            line(o, "line_t" + t.id, score--, teamLine(t, t.id == viewerTeam));
            n++;
        }
        if (n == 0) {
            line(o, "line_none", score--, Ui.t("  팀 모형이 등록되지 않음", Ui.SUB));
        }
        line(o, "line_bottom", score, Component.text("  "));
    }

    private static void line(Objective o, String entry, int score, Component text) {
        Score s = o.getScore(entry);
        s.setScore(score);
        s.customName(text);
    }

    private Component teamLine(TeamState t, boolean mine) {
        Region r = plugin.data().region(t.id);
        TextColor teamColor = r != null ? r.textColor() : Ui.TEXT;
        Component head = Ui.join(
                mine ? Ui.t(" ▶ ", Ui.GOLD) : Ui.t("    ", Ui.SUB),
                Ui.bold(t.id + "팀", teamColor),
                Ui.t("  ", Ui.SUB));

        if (t.finished) {
            return Ui.join(head, Ui.t("✔ 완료  ", Ui.OK), Ui.t(Ui.mmss(t.finishedElapsedMs), Ui.TEXT), Ui.t(" ", Ui.SUB));
        }
        if (!t.hasResult()) {
            return Ui.join(head, Ui.t("─ 제출 전 ", Ui.SUB));
        }
        return Ui.join(head, Ui.squares(t.results, ""), Ui.t("  " + t.submissions + "회 ", Ui.SUB));
    }

    /** 발광 기둥 색 = 각 점수판의 팀 색 */
    private void syncGlow(Scoreboard sb) {
        for (Region r : plugin.data().allRegions()) {
            String name = GLOW_PREFIX + r.id;
            Team team = sb.getTeam(name);
            if (team == null) team = sb.registerNewTeam(name);
            team.color(r.textColor());
            for (UUID id : GlowMarkers.ids(r.id)) {
                String entry = id.toString();
                if (!team.hasEntry(entry)) team.addEntry(entry);
            }
        }
    }
}
