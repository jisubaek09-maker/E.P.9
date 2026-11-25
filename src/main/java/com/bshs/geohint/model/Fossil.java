package com.bshs.geohint.model;

/**
 * 지질 단면 모델 v2 의 화석 4곳 (로드스톤). 좌표는 스키매틱 원점(강화 심층암) 기준 상대좌표.
 *
 * 게임 안에서는 종 이름과 분류만 알려준다. 연대는 학습지의 "화석 연대 대조표" 에서 찾게 한다.
 * (모두 국제 층서 기준 단면(GSSP)에서 절의 시작을 정의하는 종 = 최초 출현 연대가 고정된 종)
 *   ① Declinognathodus noduliferus 바시키르절 기저   323.4  Ma   (H층, y=47)
 *   ② Eoparastaffella simplex      비제절 기저       346.7  Ma   (D층, y=30)
 *   ③ Palmatolepis subperlobata    파멘니안절 기저   372.15 Ma   (C층, y=21)
 *   ④ Ancyrodella rotundiloba      프라스니안절 기저 382.31 Ma   (A층, y=12)
 */
public enum Fossil {

    // 번호는 얕은 곳 → 깊은 곳 순서 (① 이 지표에서 가장 가깝다)
    F1("①", "Declinognathodus noduliferus", "코노돈트", 25, 47, 25, RockLayer.H),
    F2("②", "Eoparastaffella simplex", "방추충", 6, 30, 7, RockLayer.D),
    F3("③", "Palmatolepis subperlobata", "코노돈트", 14, 21, 26, RockLayer.C),
    F4("④", "Ancyrodella rotundiloba", "코노돈트", 24, 12, 6, RockLayer.A);

    public final String mark;
    public final String species;
    public final String group;
    public final int dx, dy, dz;
    /** 실제로 들어 있는 층 (학생에게는 보여주지 않음) */
    public final RockLayer layer;

    Fossil(String mark, String species, String group, int dx, int dy, int dz, RockLayer layer) {
        this.mark = mark;
        this.species = species;
        this.group = group;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.layer = layer;
    }

    public String title() {
        return "화석 " + mark;
    }

    public static Fossil at(int dx, int dy, int dz) {
        for (Fossil f : values()) {
            if (f.dx == dx && f.dy == dy && f.dz == dz) return f;
        }
        return null;
    }
}
