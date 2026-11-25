package com.bshs.geohint.game;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.model.PlayerState;
import com.bshs.geohint.model.Result;
import com.bshs.geohint.model.RockLayer;
import com.bshs.geohint.model.TeamState;
import com.bshs.geohint.ui.AnswerGui;
import com.bshs.geohint.ui.TitleQueue.Frame;
import com.bshs.geohint.ui.Ui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 답안지 : 8칸을 다 채워 한 번에 제출 → 뜸 들인 뒤 칸별 결과(초록·노랑·빨강)를 한꺼번에 공개
 *   제출 횟수 무제한, 제출 간격 5초
 *   비용 : 초록 고정·무료 / 같은 암석 그대로인 노랑 무료 / 나머지 칸은 그 칸 시료 10개
 */
public class AnswerService {

    /** 채점 연출 프레임 (tick) */
    private static final int[] TICK_FRAMES = {10, 9, 8, 7, 6, 5};

    private final GeoHintPlugin plugin;

    public AnswerService(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    // ── 답안지 열기 ──

    public void open(Player p) {
        TeamState t = teamOf(p);
        if (!plugin.game().isRunning()) {
            deny(p, "아직 탐사 중이 아닙니다", null);
            return;
        }
        if (t == null) {
            deny(p, "팀이 없습니다", "/team <1-5> 로 팀을 정하세요");
            return;
        }
        plugin.data().state(p).pendingAgeSlot = -1;
        new AnswerGui(t).open(p);
    }

    private TeamState teamOf(Player p) {
        return plugin.game().team(plugin.data().state(p).team);
    }

    // ── 답안지 클릭 ──

    public void click(Player p, AnswerGui gui, int raw, ClickType click, ItemStack cursor) {
        TeamState t = gui.team;
        if (raw == AnswerGui.CLOSE) {
            p.closeInventory();
            return;
        }
        if (raw == AnswerGui.SUBMIT) {
            submit(p);
            return;
        }
        int i = AnswerGui.slotIndex(raw);
        if (i < 0) return;

        if (!plugin.game().isRunning() || t.finished || t.revealing) return;
        if (t.locked(i)) {
            plugin.bars().flash(p, Ui.t("확정된 칸은 바꿀 수 없습니다", Ui.OK));
            Ui.play(p, Ui.S_BAD, 0.6f, 1.2f);
            return;
        }

        RockLayer held = plugin.items().layerOf(cursor);
        if (held != null) {
            if (t.layers[i] != held) {
                t.layers[i] = held;
                t.ages[i] = null;
            }
            Ui.play(p, Ui.S_CLICK, 0.6f, 1.2f);
            AnswerGui.refreshTeam(t.id);
            return;
        }
        if (cursor != null && !cursor.getType().isAir()) {
            plugin.bars().flash(p, Ui.t("지질 시료만 놓을 수 있습니다", Ui.BAD));
            Ui.play(p, Ui.S_BAD, 0.6f, 1.0f);
            return;
        }
        if (t.layers[i] == null) {
            plugin.bars().flash(p, Ui.t("인벤토리에서 시료를 집어서 이 칸을 클릭하세요", Ui.SUB));
            return;
        }
        if (click.isRightClick()) {
            t.layers[i] = null;
            t.ages[i] = null;
            Ui.play(p, Ui.S_CLICK, 0.6f, 0.8f);
            AnswerGui.refreshTeam(t.id);
            return;
        }
        beginAgeInput(p, t, i);
    }

    // ── 연대 입력 (채팅) ──

    private void beginAgeInput(Player p, TeamState t, int i) {
        PlayerState st = plugin.data().state(p);
        st.hintWaiting = false;
        st.pendingAgeSlot = i;
        p.closeInventory();
        plugin.titles().now(p, Frame.of(
                Ui.join(Ui.t(Ui.CIRCLED[i] + "  ", Ui.GOLD), Ui.t(t.layers[i].sampleName(), Ui.GOLD)),
                Ui.t("채팅에 연대(Ma)를 입력하세요  ·  범위는 숫자 두 개 (예: 300 310)", Ui.INFO),
                70, Ui.S_CLICK, 1.0f));
    }

    /** 메인 스레드에서 호출 */
    public void handleAgeInput(Player p, String raw) {
        PlayerState st = plugin.data().state(p);
        int i = st.pendingAgeSlot;
        if (i < 0) return;
        TeamState t = teamOf(p);
        String text = raw.trim();

        if (t == null || !plugin.game().isRunning()) {
            st.pendingAgeSlot = -1;
            return;
        }
        if (text.equals("취소") || text.equalsIgnoreCase("cancel")) {
            st.pendingAgeSlot = -1;
            new AnswerGui(t).open(p);
            return;
        }
        double[] value = parse(text);
        if (value == null) {
            plugin.titles().now(p, Frame.of(Ui.t("입력 오류", Ui.BAD),
                    Ui.t("숫자 1개, 범위라면 숫자 2개를 입력하세요  ·  취소 : 취소", Ui.SUB), 50, Ui.S_BAD, 0.8f));
            return;   // 계속 입력 대기
        }
        st.pendingAgeSlot = -1;
        if (t.locked(i) || t.layers[i] == null || t.finished || t.revealing) {
            new AnswerGui(t).open(p);
            return;
        }
        t.ages[i] = value;
        Ui.play(p, Ui.S_SOFT, 0.8f, 1.2f);
        AnswerGui.refreshTeam(t.id);
        new AnswerGui(t).open(p);
    }

    /** "366" → [366], "331 346.7" / "331~346.7" / "331-346.7" → [331, 346.7] (오름차순) */
    public static double[] parse(String text) {
        String[] parts = text.trim().split("[\\s~,\\-]+");
        List<Double> nums = new ArrayList<>();
        for (String s : parts) {
            if (s.isEmpty()) continue;
            s = s.replace("Ma", "").replace("ma", "");
            if (s.isEmpty()) continue;
            try {
                double v = Double.parseDouble(s);
                if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return null;
                nums.add(v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (nums.size() == 1) return new double[]{nums.get(0)};
        if (nums.size() == 2) {
            double a = nums.get(0), b = nums.get(1);
            return a <= b ? new double[]{a, b} : new double[]{b, a};
        }
        return null;
    }

    // ── 제출 ──

    public void submit(Player p) {
        TeamState t = teamOf(p);
        if (t == null || !plugin.game().isRunning() || t.finished || t.revealing) return;
        long now = System.currentTimeMillis();
        if (!t.complete()) {
            plugin.bars().flash(p, Ui.t("8칸을 모두 채우고 연대를 입력하세요", Ui.BAD));
            Ui.play(p, Ui.S_BAD, 0.6f, 1.0f);
            return;
        }
        if (t.cooldownUntil > now) {
            plugin.bars().flash(p, Ui.t(((t.cooldownUntil - now + 999) / 1000) + "초 후에 제출할 수 있습니다", Ui.WARN));
            Ui.play(p, Ui.S_BAD, 0.6f, 1.0f);
            return;
        }

        Map<RockLayer, Integer> cost = AnswerGui.cost(t);
        for (Map.Entry<RockLayer, Integer> e : cost.entrySet()) {
            int have = plugin.items().count(p, e.getKey());
            if (have < e.getValue()) {
                p.closeInventory();
                plugin.titles().now(p, Frame.of(Ui.t("시료 부족", Ui.BAD),
                        Ui.t(e.getKey().sampleName() + "  " + (e.getValue() - have) + "개 더 필요", Ui.SUB),
                        50, Ui.S_BAD, 0.8f));
                return;
            }
        }
        for (Map.Entry<RockLayer, Integer> e : cost.entrySet()) {
            plugin.items().take(p, e.getKey(), e.getValue());
        }

        Result[] res = TeamState.grade(t.layers, t.ages);
        for (int i = 0; i < TeamState.SLOTS; i++) t.paid[i] = t.layers[i];
        t.submissions++;
        t.revealing = true;
        AnswerGui.closeTeam(t.id);
        reveal(t, res);
    }

    /** 제출 완료 → 채점 중 . . . → 결과는 . . . → 8칸 동시 공개 */
    private void reveal(TeamState t, Result[] res) {
        int g = count(res, Result.GREEN), y = count(res, Result.YELLOW), r = count(res, Result.RED);
        boolean clear = g == TeamState.SLOTS;

        List<Frame> frames = new ArrayList<>();
        // 연출 문구는 모두 짧아서 큰 글씨로 띄운다 (한글 9자 이내면 좁은 화면에서도 안 잘림)
        frames.add(Frame.big(Ui.bold("답안 제출 완료", Ui.GOLD), Ui.t(t.submissions + "번째 제출", Ui.SUB),
                5, 20, 0, Ui.S_PAGE, 1.0f));
        String[] dots = {" .", " . .", " . . ."};
        for (int k = 0; k < TICK_FRAMES.length; k++) {
            frames.add(Frame.big(Ui.t("채점 중" + dots[k % 3], Ui.GOLD), Component.empty(),
                    0, TICK_FRAMES[k], 0, Ui.S_TICK, 0.9f + 0.2f * k));
        }
        frames.add(Frame.big(Ui.t("결과는 . . .", Ui.GOLD), Component.empty(), 0, 30, 0, null, 1f));

        String sound;
        float pitch;
        if (clear) { sound = Ui.S_GOOD; pitch = 1.2f; }
        else if (g >= 5) { sound = Ui.S_GOOD; pitch = 1.0f; }
        else if (g >= 1) { sound = Ui.S_NOTICE; pitch = 1.0f; }
        else { sound = Ui.S_BAD; pitch = 0.7f; }
        frames.add(Frame.big(Ui.squares(res, ""), Ui.counts(g, y, r), 0, 70, 15, sound, pitch));

        if (clear) {
            frames.add(Frame.big(Ui.bold("지층 추론 성공!", Ui.OK),
                    Ui.t(t.id + "팀  ·  기록 " + Ui.mmss(plugin.game().elapsedMs() + revealDelay(false) * 50L), Ui.TEXT),
                    5, 80, 10, Ui.S_CLEAR, 1.0f));
        }

        List<Player> members = plugin.game().members(t.id);
        Frame[] arr = frames.toArray(new Frame[0]);
        plugin.titles().nowAll(members, arr);

        int delay = revealDelay(false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(t, res), delay);
        if (clear) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player m : plugin.game().members(t.id)) firework(m);
            }, revealDelay(true));
        }
    }

    /** 결과 프레임(true 면 성공 프레임)이 뜨기까지의 tick */
    private static int revealDelay(boolean successFrame) {
        int d = 1 + 25;
        for (int f : TICK_FRAMES) d += f;
        d += 30;
        if (successFrame) d += 70 + 5;
        return d;
    }

    private void apply(TeamState t, Result[] res) {
        if (plugin.game().state() != GameManager.State.RUNNING) {
            t.revealing = false;
            return;
        }
        System.arraycopy(res, 0, t.results, 0, TeamState.SLOTS);
        t.revealing = false;
        t.cooldownUntil = System.currentTimeMillis() + TeamState.SUBMIT_COOLDOWN_MS;
        boolean clear = count(res, Result.GREEN) == TeamState.SLOTS;
        if (clear) {
            t.finished = true;
            t.finishedElapsedMs = plugin.game().elapsedMs();
        }
        plugin.boards().refresh();
        AnswerGui.refreshTeam(t.id);

        for (Player o : plugin.game().others(t.id)) {
            if (clear) {
                plugin.titles().push(o, Frame.of(Ui.bold(t.id + "팀 추론 성공", Ui.GOLD),
                        Ui.t("기록 " + Ui.mmss(t.finishedElapsedMs), Ui.SUB), 40, Ui.S_NOTICE, 1.2f));
            } else {
                plugin.bars().flash(o, Ui.t(t.id + "팀 " + t.submissions + "번째 제출 결과가 점수판에 올라왔습니다", Ui.SUB));
                Ui.play(o, Ui.S_OTHER, 0.5f, 1.0f);
            }
        }
    }

    /** 시간 종료 : 끝내지 못한 팀의 작성 중인 답안을 무료로 채점. 채점한 팀 번호를 돌려준다 */
    public Set<Integer> finalizeAll() {
        Set<Integer> graded = new HashSet<>();
        for (TeamState t : plugin.game().shownTeams()) {
            t.revealing = false;
            if (t.finished || !t.anyFilled()) continue;
            Result[] res = TeamState.grade(t.layers, t.ages);
            System.arraycopy(res, 0, t.results, 0, TeamState.SLOTS);
            graded.add(t.id);
        }
        return graded;
    }

    // ── 기타 ──

    private static int count(Result[] rs, Result r) {
        int n = 0;
        for (Result x : rs) if (x == r) n++;
        return n;
    }

    private void deny(Player p, String title, String sub) {
        plugin.titles().now(p, Frame.of(Ui.t(title, Ui.BAD),
                sub == null ? null : Ui.t(sub, Ui.SUB), 40, Ui.S_BAD, 0.8f));
    }

    private static void firework(Player p) {
        p.getWorld().spawn(p.getLocation().add(0, 1, 0), Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.LIME, Color.YELLOW)
                    .withFade(Color.WHITE)
                    .flicker(true)
                    .build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
        });
    }
}
