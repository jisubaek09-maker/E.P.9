package com.bshs.geohint.model;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 한 판 동안의 팀 진행 상태 (/start 때 새로 만든다) */
public class TeamState {

    public static final int SLOTS = 8;
    public static final int MAX_HINTS = 5;
    public static final int HINT_COST = 30;
    public static final int SLOT_COST = 10;
    public static final long SUBMIT_COOLDOWN_MS = 5_000;

    public final int id;

    public int hintsLeft = MAX_HINTS;
    /** 조사 노트 페이지 (힌트·화석 기록 순서대로) */
    public final List<Component> notes = new ArrayList<>();
    public int hintRecords = 0;
    public final Set<Fossil> fossils = EnumSet.noneOf(Fossil.class);

    // ── 답안지 ──
    public final RockLayer[] layers = new RockLayer[SLOTS];
    /** 칸별 입력 연대. null = 미입력, 길이 1 = 단일값, 길이 2 = 범위(오름차순) */
    public final double[][] ages = new double[SLOTS][];
    public final Result[] results = new Result[SLOTS];
    /** 지난 제출 때 그 칸에 들어 있던 암석 (비용 계산용) */
    public final RockLayer[] paid = new RockLayer[SLOTS];

    public int submissions = 0;
    public boolean revealing = false;
    public boolean finished = false;
    public long finishedElapsedMs = 0;
    public long cooldownUntil = 0;

    public TeamState(int id) {
        this.id = id;
        Arrays.fill(results, Result.NONE);
    }

    public boolean locked(int i) {
        return results[i] == Result.GREEN;
    }

    public boolean complete() {
        for (int i = 0; i < SLOTS; i++) {
            if (layers[i] == null || ages[i] == null) return false;
        }
        return true;
    }

    public int filledSlots() {
        int n = 0;
        for (RockLayer l : layers) if (l != null) n++;
        return n;
    }

    public int enteredAges() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (layers[i] != null && ages[i] != null) n++;
        return n;
    }

    public boolean anyFilled() {
        return filledSlots() > 0;
    }

    /** 초록 고정·무료, 같은 암석 그대로인 노랑 무료, 나머지(빨강·새 칸)는 그 칸 시료 10개 */
    public int costFor(int i) {
        if (results[i] == Result.GREEN) return 0;
        if (results[i] == Result.YELLOW && layers[i] == paid[i]) return 0;
        return SLOT_COST;
    }

    public int count(Result r) {
        int n = 0;
        for (Result x : results) if (x == r) n++;
        return n;
    }

    public boolean hasResult() {
        for (Result x : results) if (x != Result.NONE) return true;
        return false;
    }

    public static Result[] grade(RockLayer[] layers, double[][] ages) {
        Result[] out = new Result[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            RockLayer placed = layers[i];
            if (placed == null || placed != RockLayer.ORDER.get(i)) {
                out[i] = Result.RED;
            } else {
                out[i] = placed.gradeAge(ages[i]);
            }
        }
        return out;
    }
}
