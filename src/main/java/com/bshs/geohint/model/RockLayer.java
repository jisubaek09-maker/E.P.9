package com.bshs.geohint.model;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 지질 단면 모델 v2 의 8개 암석.
 *
 * ── 정답 연대 ──
 *   화성암 F·G·B : 방사성 동위원소로 구한 값이 곧 정답 (나이 = 반감기 × 경과 횟수)
 *   화석층 A·C·D·H : 표준화석(절 경계를 정의하는 종)의 최초 출현 연대, ICS 2024/12 기준
 *   E : 화석·동위원소 모두 쓸 수 없어 범위로만 답한다 (G 331 Ma ~ D 346.7 Ma)
 *
 * ── /hint 표시값 ──
 *   퇴적암(A·C·D·E·H)은 근원암 연대가 나오는 "함정" 이다. 표시 형식과 효과음은 화성암과 완전히 같다.
 *   같은 원소는 반감기가 항상 같다 (λ=122, σ=165.5) → D 는 F 와, H 는 G 와 똑같은 값이 나온다.
 *   원소 이름은 실제 동위원소와 혼동되지 않도록 그리스 문자로 지은 가상의 원소다.
 */
public enum RockLayer {

    A("흑색 셰일", Material.DEEPSLATE, List.of(Material.DEEPSLATE, Material.COBBLED_DEEPSLATE),
      "θ(세타)", 113.0, 4, AnswerType.FOSSIL, 382.31, 0),

    C("석회암", Material.CALCITE, List.of(Material.CALCITE, Material.STONE),
      "ξ(크사이)", 135.0, 3, AnswerType.FOSSIL, 372.15, 0),

    F("화강암", Material.GRANITE, List.of(Material.GRANITE),
      "λ(람다)", 122.0, 3, AnswerType.EXACT, 366, 0),

    D("사암", Material.SANDSTONE, List.of(Material.SANDSTONE, Material.CUT_SANDSTONE),
      "λ(람다)", 122.0, 3, AnswerType.FOSSIL, 346.7, 0),

    E("적색 이암", Material.TERRACOTTA, List.of(Material.TERRACOTTA, Material.ORANGE_TERRACOTTA),
      "τ(타우)", 190.0, 2, AnswerType.RANGE, 331, 346.7),

    G("응회암", Material.TUFF, List.of(Material.TUFF, Material.POLISHED_TUFF),
      "σ(시그마)", 165.5, 2, AnswerType.EXACT, 331, 0),

    H("셰일", Material.MUD_BRICKS, List.of(Material.MUD_BRICKS, Material.PACKED_MUD),
      "σ(시그마)", 165.5, 2, AnswerType.FOSSIL, 323.4, 0),

    B("섬록암", Material.DIORITE, List.of(Material.DIORITE),
      "π(파이)", 312.0, 1, AnswerType.EXACT, 312, 0);

    public enum AnswerType {
        /** 동위원소 계산값 그대로 (오차 없음) */
        EXACT,
        /** 화석 연대 ±1 */
        FOSSIL,
        /** 두 숫자(최소, 최대) 각각 ±1 */
        RANGE
    }

    public static final double EXACT_TOLERANCE = 0.01;
    public static final double FOSSIL_TOLERANCE = 1.0;
    public static final double RANGE_TOLERANCE = 1.0;

    /** 오래된 것 → 젊은 것. 답안지 ①~⑧ 의 정답 순서 */
    public static final List<RockLayer> ORDER = List.of(A, C, F, D, E, G, H, B);

    public final String koreanName;
    public final Material icon;
    public final List<Material> materials;
    public final String element;
    public final double halfLife;
    public final int halfLives;
    public final AnswerType answerType;
    public final double answer;     // EXACT/FOSSIL : 정답, RANGE : 최소
    public final double answerMax;  // RANGE : 최대

    RockLayer(String koreanName, Material icon, List<Material> materials,
              String element, double halfLife, int halfLives,
              AnswerType answerType, double answer, double answerMax) {
        this.koreanName = koreanName;
        this.icon = icon;
        this.materials = materials;
        this.element = element;
        this.halfLife = halfLife;
        this.halfLives = halfLives;
        this.answerType = answerType;
        this.answer = answer;
        this.answerMax = answerMax;
    }

    /** "사암 · D층" */
    public String sampleName() {
        return koreanName + " · " + name() + "층";
    }

    public double parentPercent() {
        return 100.0 * Math.pow(0.5, halfLives);
    }

    public double daughterPercent() {
        return 100.0 - parentPercent();
    }

    /** /hint 로 계산되는 값 (퇴적암은 근원암 연대) */
    public double measuredAge() {
        return halfLife * halfLives;
    }

    /** 이 칸에 이 암석이 맞게 들어갔다는 전제에서 연대 입력을 채점 */
    public Result gradeAge(double[] input) {
        if (input == null || input.length == 0) return Result.YELLOW;
        switch (answerType) {
            case EXACT:
                return input.length == 1 && Math.abs(input[0] - answer) <= EXACT_TOLERANCE
                        ? Result.GREEN : Result.YELLOW;
            case FOSSIL:
                return input.length == 1 && Math.abs(input[0] - answer) <= FOSSIL_TOLERANCE
                        ? Result.GREEN : Result.YELLOW;
            case RANGE:
                return input.length == 2
                        && Math.abs(input[0] - answer) <= RANGE_TOLERANCE
                        && Math.abs(input[1] - answerMax) <= RANGE_TOLERANCE
                        ? Result.GREEN : Result.YELLOW;
            default:
                return Result.YELLOW;
        }
    }

    // ── 블록 ↔ 암석 ──

    private static final Map<Material, RockLayer> BY_MATERIAL = new EnumMap<>(Material.class);

    static {
        for (RockLayer layer : values()) {
            for (Material m : layer.materials) {
                BY_MATERIAL.put(m, layer);
            }
        }
    }

    public static RockLayer fromMaterial(Material m) {
        return BY_MATERIAL.get(m);
    }

    public static RockLayer fromKey(String key) {
        if (key == null) return null;
        try {
            return valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
