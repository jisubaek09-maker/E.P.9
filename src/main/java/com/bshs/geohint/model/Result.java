package com.bshs.geohint.model;

/** 답안 한 칸의 채점 결과 */
public enum Result {
    /** 아직 채점 안 함 */
    NONE,
    /** 그 자리 암석도 맞고 연대도 맞음 → 확정(잠금) */
    GREEN,
    /** 그 자리 암석은 맞는데 연대가 틀림 */
    YELLOW,
    /** 그 자리 암석이 틀림 */
    RED
}
