package com.bshs.geohint.command;

import com.bshs.geohint.GeoHintPlugin;
import com.bshs.geohint.data.DataStore;
import com.bshs.geohint.game.GameManager;
import com.bshs.geohint.model.Region;
import com.bshs.geohint.model.TeamState;
import com.bshs.geohint.ui.GlowMarkers;
import com.bshs.geohint.ui.TitleQueue.Frame;
import com.bshs.geohint.ui.Ui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Commands implements TabExecutor {

    public static final String[] NAMES = {"geohelp", "hint", "answer", "team", "set", "back", "dm",
            "health", "hungry", "pickaxe", "start", "stop", "reset", "geo"};

    private static final List<String> COLORS = List.of("red", "gold", "yellow", "green", "aqua",
            "blue", "light_purple", "white", "dark_green", "dark_aqua", "dark_purple", "gray");

    private final GeoHintPlugin plugin;

    public Commands(GeoHintPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "geohelp": return help(sender);
            case "hint": return withPlayer(sender, p -> plugin.hints().begin(p));
            case "answer": return withPlayer(sender, p -> plugin.answers().open(p));
            case "team": return team(sender, args);
            case "set": return withPlayer(sender, this::setBack);
            case "back": return withPlayer(sender, this::back);
            case "dm": return dm(sender, args);
            case "health": return withPlayer(sender, p -> {
                GameManager.heal(p);
                plugin.bars().flash(p, Ui.t("체력을 회복했습니다", Ui.OK));
            });
            case "hungry": return withPlayer(sender, p -> {
                p.setFoodLevel(20);
                p.setSaturation(20f);
                plugin.bars().flash(p, Ui.t("허기를 채웠습니다", Ui.OK));
            });
            case "pickaxe": return withPlayer(sender, this::tools);
            case "start": return admin(sender, () -> plugin.game().start());
            case "stop": return admin(sender, () -> plugin.game().end("선생님이 종료했습니다"));
            case "reset": return admin(sender, () -> plugin.game().reset());
            case "geo": return geo(sender, args);
            default: return false;
        }
    }

    // ── 공통 ──

    private interface PlayerAction {
        void run(Player p);
    }

    private boolean withPlayer(CommandSender s, PlayerAction action) {
        if (s instanceof Player p) {
            action.run(p);
        } else {
            s.sendMessage(Ui.t("게임 안에서만 쓸 수 있는 명령어입니다.", Ui.BAD));
        }
        return true;
    }

    private boolean admin(CommandSender s, Runnable r) {
        if (!plugin.isAdmin(s)) {
            s.sendMessage(Ui.t("권한이 없습니다.", Ui.BAD));
            return true;
        }
        r.run();
        return true;
    }

    private static int parseTeam(CommandSender s, String raw) {
        try {
            int id = Integer.parseInt(raw);
            if (id >= 1 && id <= DataStore.TEAM_COUNT) return id;
        } catch (NumberFormatException ignored) {
        }
        s.sendMessage(Ui.t("팀 번호는 1~" + DataStore.TEAM_COUNT + " 사이여야 합니다.", Ui.BAD));
        return -1;
    }

    // ── /geohelp ──

    private boolean help(CommandSender s) {
        s.sendMessage(Ui.bold("──── 명령어 ────", Ui.GOLD));
        row(s, "/team <1-5>", "소속 팀 정하기 (가장 먼저!)");
        row(s, "/hint", "시료 분석 · 시료 30개 소모 · 팀당 " + TeamState.MAX_HINTS + "회");
        row(s, "/answer", "답안지 열기");
        row(s, "/set · /back", "복귀 지점 저장 · 이동");
        row(s, "/dm <닉네임> <내용>", "귓속말");
        row(s, "/health · /hungry", "체력 · 허기 회복");
        row(s, "/pickaxe", "곡괭이 · 화석 나침반 · 조사 노트 다시 받기");
        if (plugin.isAdmin(s)) {
            s.sendMessage(Ui.t("── 관리자 ──", Ui.DIM));
            row(s, "/start · /stop · /reset", "시작 · 강제 종료 · 초기화");
            row(s, "/geo setregion <1-5> <색>", "근처 모형을 그 팀 모형으로 등록");
            row(s, "/geo removeregion <1-5>", "등록 해제");
            row(s, "/geo setteam <닉네임> <1-5>", "팀 강제 지정");
            row(s, "/geo wall <1-5|all> [remove]", "모형 옆면을 벽으로 가리기 · 없애기");
            row(s, "/geo list · /geo reload", "등록 현황 · 다시 불러오기");
        }
        return true;
    }

    private static void row(CommandSender s, String cmd, String desc) {
        s.sendMessage(Ui.join(Ui.t(cmd, Ui.WARN), Ui.t("  " + desc, Ui.SUB)));
    }

    // ── /team ──

    private boolean team(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return withPlayer(s, x -> {});
        if (args.length < 1) {
            s.sendMessage(Ui.t("사용법 : /team <1-5>", Ui.BAD));
            return true;
        }
        int id = parseTeam(s, args[0]);
        if (id < 0) return true;
        assignTeam(p, id);
        return true;
    }

    private void assignTeam(Player p, int id) {
        plugin.data().setTeam(p, id);
        plugin.boards().refresh();
        if (plugin.game().isRunning()) {
            plugin.items().giveKit(p, plugin.game().team(id), plugin.data().region(id));
        }
        Region r = plugin.data().region(id);
        plugin.titles().now(p, Frame.of(Ui.bold(id + "팀", r != null ? r.textColor() : Ui.GOLD),
                Ui.t("팀이 설정되었습니다", Ui.SUB), 40, Ui.S_SOFT, 1.0f));
    }

    // ── /set, /back ──

    private void setBack(Player p) {
        plugin.data().state(p).backPoint = p.getLocation();
        plugin.bars().flash(p, Ui.t("복귀 지점을 저장했습니다 · /back 으로 돌아오기", Ui.OK));
    }

    private void back(Player p) {
        Location loc = plugin.data().state(p).backPoint;
        if (loc == null) {
            plugin.bars().flash(p, Ui.t("저장된 복귀 지점이 없습니다 · 먼저 /set", Ui.BAD));
            return;
        }
        p.teleport(loc);
        plugin.bars().flash(p, Ui.t("복귀 지점으로 이동했습니다", Ui.OK));
    }

    // ── /dm ──

    private boolean dm(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(Ui.t("사용법 : /dm <닉네임> <내용>", Ui.BAD));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            s.sendMessage(Ui.t("'" + args[0] + "' 님을 찾을 수 없습니다.", Ui.BAD));
            return true;
        }
        String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String from = s instanceof Player ? s.getName() : "선생님";
        target.sendMessage(Ui.join(Ui.t("[" + from + " → 나] ", NamedColors.DM), Ui.t(msg, Ui.TEXT)));
        s.sendMessage(Ui.join(Ui.t("[나 → " + target.getName() + "] ", NamedColors.DM), Ui.t(msg, Ui.TEXT)));
        if (target != s) Ui.play(target, Ui.S_OTHER, 0.6f, 1.5f);
        plugin.getLogger().info("[DM] " + from + " -> " + target.getName() + " : " + msg);
        return true;
    }

    private static final class NamedColors {
        static final net.kyori.adventure.text.format.TextColor DM =
                net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
    }

    // ── /pickaxe ──

    private void tools(Player p) {
        int id = plugin.data().state(p).team;
        if (!plugin.game().isRunning()) {
            plugin.bars().flash(p, Ui.t("탐사 중에만 받을 수 있습니다", Ui.SUB));
            return;
        }
        plugin.items().giveKit(p, plugin.game().team(id), plugin.data().region(id));
        plugin.bars().flash(p, Ui.t("곡괭이 · 화석 나침반 · 조사 노트를 다시 받았습니다", Ui.OK));
    }

    // ── /geo ──

    private boolean geo(CommandSender s, String[] args) {
        if (!plugin.isAdmin(s)) {
            s.sendMessage(Ui.t("권한이 없습니다.", Ui.BAD));
            return true;
        }
        if (args.length == 0) {
            s.sendMessage(Ui.t(GEO_USAGE, Ui.BAD));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setregion": return setRegion(s, args);
            case "wall": return wall(s, args);
            case "removeregion": {
                if (args.length < 2) {
                    s.sendMessage(Ui.t("사용법 : /geo removeregion <1-5>", Ui.BAD));
                    return true;
                }
                int id = parseTeam(s, args[1]);
                if (id < 0) return true;
                GlowMarkers.remove(id);
                plugin.data().removeRegion(id);
                plugin.boards().refresh();
                s.sendMessage(Ui.t(id + "팀 모형 등록을 해제했습니다.", Ui.OK));
                return true;
            }
            case "setteam": {
                if (args.length < 3) {
                    s.sendMessage(Ui.t("사용법 : /geo setteam <닉네임> <1-5>", Ui.BAD));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    s.sendMessage(Ui.t("'" + args[1] + "' 님을 찾을 수 없습니다.", Ui.BAD));
                    return true;
                }
                int id = parseTeam(s, args[2]);
                if (id < 0) return true;
                assignTeam(target, id);
                s.sendMessage(Ui.t(target.getName() + " → " + id + "팀", Ui.OK));
                return true;
            }
            case "list": {
                s.sendMessage(Ui.bold("──── 팀 모형 ────", Ui.GOLD));
                if (plugin.data().allRegions().isEmpty()) {
                    s.sendMessage(Ui.t("등록된 모형이 없습니다. 모형 근처에서 /geo setregion <1-5> <색>", Ui.SUB));
                }
                for (Region r : plugin.data().allRegions()) {
                    List<String> names = new ArrayList<>();
                    for (Player m : plugin.game().members(r.id)) names.add(m.getName());
                    s.sendMessage(Ui.join(
                            Ui.bold(r.id + "팀", r.textColor()),
                            Ui.t("  원점 " + r.world + " " + r.ox + " " + r.oy + " " + r.oz, Ui.TEXT),
                            Ui.t("  ·  팀원 " + (names.isEmpty() ? "없음" : String.join(", ", names)), Ui.SUB)));
                }
                return true;
            }
            case "reload": {
                plugin.data().load();
                GlowMarkers.respawnAll(plugin, plugin.data().allRegions());
                plugin.boards().refresh();
                s.sendMessage(Ui.t("설정을 다시 불러왔습니다.", Ui.OK));
                return true;
            }
            default:
                s.sendMessage(Ui.t(GEO_USAGE, Ui.BAD));
                return true;
        }
    }

    private static final String GEO_USAGE =
            "사용법 : /geo <setregion|removeregion|wall|setteam|list|reload>";

    /** /geo wall <1-5|all> [remove] — 모형 네 옆면 바깥에 벽을 세워 단면을 가린다 */
    private boolean wall(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(Ui.t("사용법 : /geo wall <1-5|all> [remove]", Ui.BAD));
            return true;
        }
        boolean remove = args.length >= 3 && args[2].equalsIgnoreCase("remove");
        List<Region> targets = new ArrayList<>();
        if (args[1].equalsIgnoreCase("all")) {
            targets.addAll(plugin.data().allRegions());
        } else {
            int id = parseTeam(s, args[1]);
            if (id < 0) return true;
            Region r = plugin.data().region(id);
            if (r != null) targets.add(r);
        }
        if (targets.isEmpty()) {
            s.sendMessage(Ui.t("등록된 모형이 없습니다. 먼저 /geo setregion <1-5> <색>", Ui.BAD));
            return true;
        }
        for (Region r : targets) {
            int n = remove ? r.removeWalls() : r.buildWalls();
            s.sendMessage(Ui.join(Ui.bold(r.id + "팀", r.textColor()),
                    Ui.t(remove ? " 벽면 제거  ·  " + n + "칸" : " 벽면 생성  ·  " + n + "칸", Ui.OK)));
        }
        return true;
    }

    /**
     * 플레이어 근처(가로 ±48, 아래 70 ~ 위 10)에서 원점 마커(강화 심층암, 옆이 기반암)를 찾아
     * 가장 가까운 것을 그 팀 모형으로 등록한다.
     */
    private boolean setRegion(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return withPlayer(s, x -> {});
        if (args.length < 3) {
            s.sendMessage(Ui.t("사용법 : /geo setregion <1-5> <" + String.join("|", COLORS) + ">", Ui.BAD));
            return true;
        }
        int id = parseTeam(s, args[1]);
        if (id < 0) return true;
        String color = args[2];
        if (!Region.isValidColor(color)) {
            s.sendMessage(Ui.t("알 수 없는 색입니다. 예) red, gold, yellow, green, aqua, light_purple", Ui.BAD));
            return true;
        }

        World w = p.getWorld();
        Location loc = p.getLocation();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        int minY = Math.max(w.getMinHeight(), by - 70);
        int maxY = Math.min(w.getMaxHeight() - 1, by + 10);
        Block best = null;
        double bestD = Double.MAX_VALUE;
        for (int x = bx - 48; x <= bx + 48; x++) {
            for (int z = bz - 48; z <= bz + 48; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != Material.REINFORCED_DEEPSLATE) continue;
                    if (b.getRelative(1, 0, 0).getType() != Material.BEDROCK
                            || b.getRelative(0, 0, 1).getType() != Material.BEDROCK) continue;
                    double d = b.getLocation().distanceSquared(loc);
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        if (best == null) {
            s.sendMessage(Ui.t("근처에서 모형 원점(강화 심층암)을 찾지 못했습니다. 모형 바로 옆에서 다시 입력하세요.", Ui.BAD));
            return true;
        }
        Region r = new Region(id, w.getName(), best.getX(), best.getY(), best.getZ(), color);
        plugin.data().putRegion(r);
        GlowMarkers.spawn(plugin, r);
        plugin.boards().refresh();
        s.sendMessage(Ui.join(Ui.bold(id + "팀", r.textColor()),
                Ui.t(" 모형 등록  ·  원점 " + r.ox + " " + r.oy + " " + r.oz, Ui.OK)));
        return true;
    }

    // ── 탭 자동완성 ──

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (name.equals("team") && args.length == 1) {
            out.addAll(List.of("1", "2", "3", "4", "5"));
        } else if (name.equals("geo") && plugin.isAdmin(s)) {
            if (args.length == 1) out.addAll(List.of("setregion", "removeregion", "wall", "setteam", "list", "reload"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("wall")) out.addAll(List.of("all", "1", "2", "3", "4", "5"));
            else if (args.length == 3 && args[0].equalsIgnoreCase("wall")) out.add("remove");
            else if (args.length == 2 && !args[0].equalsIgnoreCase("setteam")) out.addAll(List.of("1", "2", "3", "4", "5"));
            else if (args.length == 2) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            else if (args.length == 3 && args[0].equalsIgnoreCase("setregion")) out.addAll(COLORS);
            else if (args.length == 3 && args[0].equalsIgnoreCase("setteam")) out.addAll(List.of("1", "2", "3", "4", "5"));
        } else if (name.equals("dm") && args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else {
            return List.of();
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
