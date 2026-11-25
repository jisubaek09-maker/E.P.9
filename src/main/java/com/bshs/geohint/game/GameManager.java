package com.bshs.geohint.game;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.data.DataStore;
import com.bshs.geohint.item.Items;
import com.bshs.geohint.model.Region;
import com.bshs.geohint.model.TeamState;
import com.bshs.geohint.ui.AnswerGui;
import com.bshs.geohint.ui.GlowMarkers;
import com.bshs.geohint.ui.TitleQueue.Frame;
import com.bshs.geohint.ui.Ui;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 진행 상태
 *   WAITING   : 크리에이티브 대기. 캐기·설치·인벤토리 잠김
 *   COUNTDOWN : /start 직후 3초
 *   RUNNING   : 서바이벌 + 30분 타이머
 *   ENDED     : 시간 종료 또는 /stop (작성 중인 답안 자동 채점 후 순위 발표)
 */
public class GameManager {

    public enum State { WAITING, COUNTDOWN, RUNNING, ENDED }

    public static final int TIME_LIMIT_SECONDS = 30 * 60;

    private final GeoHintPlugin plugin;
    private State state = State.WAITING;
    private final Map<Integer, TeamState> teams = new LinkedHashMap<>();
    private final Map<Location, Material> broken = new LinkedHashMap<>();

    private BossBar bar;
    private BukkitTask timer;
    private int secondsLeft = TIME_LIMIT_SECONDS;
    private long startMillis = 0;

    public GameManager(GeoHintPlugin plugin) {
        this.plugin = plugin;
        newTeams();
    }

    private void newTeams() {
        teams.clear();
        for (int i = 1; i <= DataStore.TEAM_COUNT; i++) teams.put(i, new TeamState(i));
    }

    // ── 조회 ──

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public TeamState team(int id) {
        return teams.get(id);
    }

    public long elapsedMs() {
        return startMillis == 0 ? 0 : System.currentTimeMillis() - startMillis;
    }

    public List<Player> members(int teamId) {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.data().state(p).team == teamId) out.add(p);
        }
        return out;
    }

    public List<Player> others(int teamId) {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.data().state(p).team != teamId) out.add(p);
        }
        return out;
    }

    /** 점수판에 보일 팀 = 모형이 등록된 팀 (하나도 없으면 1~5 전부) */
    public List<TeamState> shownTeams() {
        List<TeamState> out = new ArrayList<>();
        for (Region r : plugin.data().allRegions()) {
            TeamState t = teams.get(r.id);
            if (t != null) out.add(t);
        }
        if (out.isEmpty()) out.addAll(teams.values());
        return out;
    }

    /** 완료(빠른 순) → 초록 많은 순 → 노랑 많은 순 → 제출한 팀 → 제출 적은 순 */
    public static final Comparator<TeamState> RANKING = Comparator
            .comparing((TeamState t) -> !t.finished)
            .thenComparingLong(t -> t.finished ? t.finishedElapsedMs : 0L)
            .thenComparingInt(t -> -t.count(com.bshs.geohint.model.Result.GREEN))
            .thenComparingInt(t -> -t.count(com.bshs.geohint.model.Result.YELLOW))
            .thenComparingInt(t -> t.hasResult() ? 0 : 1)
            .thenComparingInt(t -> t.submissions)
            .thenComparingInt(t -> t.id);

    public List<TeamState> rankedTeams() {
        List<TeamState> list = shownTeams();
        list.sort(RANKING);
        return list;
    }

    // ── 시작 ──

    public void start() {
        if (state == State.COUNTDOWN || state == State.RUNNING) return;
        state = State.COUNTDOWN;
        newTeams();
        broken.clear();
        plugin.data().resetTransient();
        GlowMarkers.respawnAll(plugin, plugin.data().allRegions());
        plugin.boards().refresh();
        for (World w : worlds()) {
            w.setStorm(false);
            w.setThundering(false);
            w.setTime(6000);
        }
        countdown(3);
    }

    private void countdown(int n) {
        if (state != State.COUNTDOWN) return;
        if (n <= 0) {
            beginRun();
            return;
        }
        plugin.titles().nowAll(Bukkit.getOnlinePlayers(),
                Frame.big(Ui.bold(String.valueOf(n), Ui.WARN), Component.empty(), 0, 18, 2, Ui.S_NOTICE, 1.0f));
        Bukkit.getScheduler().runTaskLater(plugin, () -> countdown(n - 1), 20L);
    }

    private void beginRun() {
        state = State.RUNNING;
        secondsLeft = TIME_LIMIT_SECONDS;
        startMillis = System.currentTimeMillis();

        bar = BossBar.bossBar(timeText(), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            prepareForRun(p, true);
        }
        plugin.titles().nowAll(Bukkit.getOnlinePlayers(),
                Frame.big(Ui.bold("탐사 시작", Ui.GOLD), Ui.t("제한 시간 30분", Ui.SUB), 5, 40, 10, Ui.S_GOOD, 1.0f));
        plugin.boards().refresh();
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** 시작 순간 또는 진행 중 접속 : 학생은 서바이벌 + 도구 */
    private void prepareForRun(Player p, boolean fresh) {
        int teamId = plugin.data().state(p).team;
        boolean admin = plugin.isAdmin(p);
        if (admin && teamId <= 0) return;   // 선생님은 그대로
        if (fresh && !admin) p.getInventory().clear();
        p.setGameMode(GameMode.SURVIVAL);
        heal(p);
        applyJumpBoost(p);
        if (teamId > 0) {
            plugin.items().giveKit(p, team(teamId), plugin.data().region(teamId));
        } else {
            plugin.items().give(p, plugin.items().pickaxe());
            plugin.titles().push(p, Frame.of(Ui.t("팀이 없습니다", Ui.BAD),
                    Ui.t("/team <1-5> 로 팀을 정하세요", Ui.SUB), 60, Ui.S_BAD, 1.0f));
        }
    }

    // ── 1초마다 ──

    private Component timeText() {
        return Ui.join(Ui.t("남은 시간  ", Ui.SUB), Ui.t(Ui.mmss(secondsLeft * 1000L), Ui.TEXT));
    }

    private void tick() {
        secondsLeft--;
        if (bar != null) {
            bar.name(timeText());
            bar.progress(Math.max(0f, Math.min(1f, secondsLeft / (float) TIME_LIMIT_SECONDS)));
            bar.color(secondsLeft <= 60 ? BossBar.Color.RED : BossBar.Color.YELLOW);
        }
        if (secondsLeft == 300) {
            plugin.titles().pushAll(Bukkit.getOnlinePlayers(),
                    Frame.of(Ui.t("5분 남았습니다", Ui.WARN), Component.empty(), 40, Ui.S_NOTICE, 0.8f));
        } else if (secondsLeft == 60) {
            plugin.titles().pushAll(Bukkit.getOnlinePlayers(),
                    Frame.of(Ui.t("1분 남았습니다", Ui.BAD), Component.empty(), 40, Ui.S_NOTICE, 0.6f));
        } else if (secondsLeft > 0 && secondsLeft <= 10) {
            for (Player p : Bukkit.getOnlinePlayers()) Ui.play(p, Ui.S_TICK, 1.5f);
        }
        if (secondsLeft % 60 == 0) {
            for (World w : worlds()) w.setTime(6000);
        }
        if (secondsLeft <= 0) end("시간 종료");
    }

    // ── 종료 ──

    public void end(String reason) {
        if (state == State.COUNTDOWN) {
            state = State.WAITING;
            return;
        }
        if (state != State.RUNNING) return;
        state = State.ENDED;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        hideBar();
        AnswerGui.closeAll();
        plugin.data().resetTransient();
        for (Player p : Bukkit.getOnlinePlayers()) p.removePotionEffect(PotionEffectType.JUMP_BOOST);

        Set<Integer> graded = plugin.answers().finalizeAll();
        List<TeamState> ranked = rankedTeams();

        Frame endFrame = Frame.big(Ui.bold("탐사 종료", Ui.GOLD), Ui.t(reason, Ui.SUB), 5, 50, 10, Ui.S_END, 1.0f);
        Frame rankFrame = Frame.of(Ui.bold("최종 순위", Ui.GOLD), rankSummary(ranked), 100, Ui.S_GOOD, 1.0f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            int id = plugin.data().state(p).team;
            TeamState t = team(id);
            if (t != null && graded.contains(id)) {
                plugin.titles().now(p, endFrame,
                        Frame.of(Ui.t("최종 답안", Ui.GOLD), Component.empty(), 20),
                        Frame.big(Ui.squares(t.results, ""),
                                Ui.counts(t.count(com.bshs.geohint.model.Result.GREEN),
                                        t.count(com.bshs.geohint.model.Result.YELLOW),
                                        t.count(com.bshs.geohint.model.Result.RED)),
                                0, 60, 10, Ui.S_NOTICE, 1.0f),
                        rankFrame);
            } else {
                plugin.titles().now(p, endFrame, rankFrame);
            }
        }

        // 기록용 순위 (채팅)
        Bukkit.broadcast(Ui.t(" ", Ui.SUB));
        Bukkit.broadcast(Ui.bold("──── 최종 순위 ────", Ui.GOLD));
        int rank = 1;
        for (TeamState t : ranked) {
            Region r = plugin.data().region(t.id);
            Component status = t.finished
                    ? Ui.t("✔ 완료  " + Ui.mmss(t.finishedElapsedMs), Ui.OK)
                    : t.hasResult() ? Ui.squares(t.results, "") : Ui.t("제출 없음", Ui.SUB);
            Bukkit.broadcast(Ui.join(
                    Ui.t(" " + rank + "위  ", Ui.GOLD),
                    Ui.bold(t.id + "팀", r != null ? r.textColor() : Ui.TEXT),
                    Ui.t("   ", Ui.SUB), status,
                    Ui.t("   제출 " + t.submissions + "회 · 힌트 " + (TeamState.MAX_HINTS - t.hintsLeft) + "회 사용", Ui.SUB)));
            rank++;
        }
        plugin.boards().refresh();
    }

    private Component rankSummary(List<TeamState> ranked) {
        Component out = Component.empty();
        int n = Math.min(3, ranked.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) out = out.append(Ui.t("  ·  ", Ui.DIM));
            out = out.append(Ui.t((i + 1) + "위 " + ranked.get(i).id + "팀", i == 0 ? Ui.GOLD : Ui.TEXT));
        }
        return out;
    }

    // ── 초기화 ──

    public void reset() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        hideBar();
        AnswerGui.closeAll();
        state = State.WAITING;
        secondsLeft = TIME_LIMIT_SECONDS;
        startMillis = 0;

        int restored = 0;
        for (Map.Entry<Location, Material> e : broken.entrySet()) {
            Location loc = e.getKey();
            if (loc.getWorld() == null) continue;
            loc.getBlock().setType(e.getValue(), false);
            restored++;
        }
        broken.clear();
        newTeams();
        plugin.data().resetTransient();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            if (plugin.isAdmin(p)) continue;
            p.getInventory().clear();
            p.setGameMode(GameMode.CREATIVE);
            heal(p);
        }
        GlowMarkers.respawnAll(plugin, plugin.data().allRegions());
        plugin.boards().refresh();
        plugin.titles().nowAll(Bukkit.getOnlinePlayers(),
                Frame.of(Ui.t("초기화되었습니다", Ui.GOLD), Ui.t("복구한 블록 " + restored + "개", Ui.SUB), 40));
    }

    // ── 캔 블록 기록 (/reset 복구용) ──

    public void recordBreak(Block block) {
        Location key = block.getLocation();
        broken.putIfAbsent(key, block.getType());
    }

    // ── 접속 ──

    public void onJoin(Player p) {
        applyNightVision(p);
        if (state == State.RUNNING) {
            if (bar != null) p.showBossBar(bar);
            if (p.getGameMode() != GameMode.SURVIVAL || !plugin.items().hasTool(p, Items.TOOL_PICKAXE)) {
                prepareForRun(p, false);
            }
        } else if (!plugin.isAdmin(p)) {
            p.setGameMode(GameMode.CREATIVE);
            if (state == State.WAITING) {
                plugin.titles().push(p, Frame.of(Ui.t("탐사 대기 중", Ui.GOLD),
                        Ui.t("선생님이 시작하면 탐사가 시작됩니다", Ui.SUB), 60));
            }
        }
    }

    public void shutdown() {
        hideBar();
        AnswerGui.closeAll();
    }

    private void hideBar() {
        if (bar == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        bar = null;
    }

    private Set<World> worlds() {
        Set<World> out = new HashSet<>();
        for (Region r : plugin.data().allRegions()) {
            World w = r.bukkitWorld();
            if (w != null) out.add(w);
        }
        return out;
    }

    public static void heal(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
    }

    /** 탐사 중인 참가자인가 (선생님처럼 팀 없는 관리자는 제외) */
    public boolean isParticipant(Player p) {
        return state == State.RUNNING && (plugin.data().state(p).team > 0 || !plugin.isAdmin(p));
    }

    /** 탐사 중 참가자에게 주는 점프 강화. 2 = 점프 강화 II : 약 2.5칸까지 뛰어서 2칸 높이를 올라갈 수 있다 (I 은 약 1.8칸) */
    public static final int JUMP_BOOST_LEVEL = 2;

    public static void applyJumpBoost(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                PotionEffect.INFINITE_DURATION, JUMP_BOOST_LEVEL - 1, true, false, false));
    }

    public static void applyNightVision(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }
}
