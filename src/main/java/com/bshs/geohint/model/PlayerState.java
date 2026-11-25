package com.bshs.geohint.model;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;

/** 플레이어 한 명의 상태. 팀 번호만 파일에 저장하고 나머지는 접속 중에만 쓴다. */
public class PlayerState {

    public static final long HINT_WAIT_MS = 20_000;

    public volatile int team = 0;              // 1~5, 0 = 미배정

    public volatile boolean hintWaiting = false;
    public volatile long hintExpire = 0;

    /** 답안지 칸 연대 입력 대기 중이면 칸 번호(0~7), 아니면 -1 */
    public volatile int pendingAgeSlot = -1;

    public Location backPoint = null;

    /** 액션바에 잠깐 띄울 메시지 */
    public Component flash = null;
    public long flashUntil = 0;
}
