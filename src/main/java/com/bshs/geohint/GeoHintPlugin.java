package com.bshs.geohint;

import com.bshs.geohint.command.Commands;
import com.bshs.geohint.data.DataStore;
import com.bshs.geohint.game.AnswerService;
import com.bshs.geohint.game.FossilService;
import com.bshs.geohint.game.GameManager;
import com.bshs.geohint.game.HintService;
import com.bshs.geohint.item.Items;
import com.bshs.geohint.listener.ChatListener;
import com.bshs.geohint.listener.GuiListener;
import com.bshs.geohint.listener.InteractListener;
import com.bshs.geohint.listener.JoinListener;
import com.bshs.geohint.listener.MiningListener;
import com.bshs.geohint.listener.ProtectListener;
import com.bshs.geohint.ui.ActionBars;
import com.bshs.geohint.ui.AnswerGui;
import com.bshs.geohint.ui.GlowMarkers;
import com.bshs.geohint.ui.Scoreboards;
import com.bshs.geohint.ui.TitleQueue;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class GeoHintPlugin extends JavaPlugin {

    private DataStore data;
    private Items items;
    private TitleQueue titles;
    private ActionBars bars;
    private Scoreboards boards;
    private GameManager game;
    private HintService hints;
    private AnswerService answers;
    private FossilService fossils;

    @Override
    public void onEnable() {
        data = new DataStore(this);
        items = new Items(this);
        titles = new TitleQueue(this);
        bars = new ActionBars(this);
        boards = new Scoreboards(this);
        game = new GameManager(this);
        hints = new HintService(this);
        answers = new AnswerService(this);
        fossils = new FossilService(this);

        Commands commands = new Commands(this);
        for (String name : Commands.NAMES) {
            PluginCommand cmd = getCommand(name);
            if (cmd == null) {
                getLogger().warning("plugin.yml 에 '" + name + "' 명령어가 없습니다");
                continue;
            }
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        Listener[] listeners = {
                new MiningListener(this), new ProtectListener(this), new InteractListener(this),
                new GuiListener(this), new ChatListener(this), new JoinListener(this)};
        for (Listener l : listeners) getServer().getPluginManager().registerEvents(l, this);

        titles.start();
        bars.start();
        JoinListener.startEffectTask(this);
        Bukkit.getScheduler().runTaskTimer(this, AnswerGui::tickAll, 10L, 10L);

        GlowMarkers.respawnAll(this, data.allRegions());
        boards.refresh();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boards.assign(p);
            GameManager.applyNightVision(p);
        }
        getLogger().info("GeoHint 2.0 활성화 - 등록된 팀 모형 " + data.allRegions().size() + "개");
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
    }

    /** 관리자 = 서버 콘솔 또는 OP (geohint.admin 권한) */
    public boolean isAdmin(org.bukkit.command.CommandSender s) {
        return !(s instanceof Player) || s.hasPermission("geohint.admin");
    }

    public DataStore data() { return data; }
    public Items items() { return items; }
    public TitleQueue titles() { return titles; }
    public ActionBars bars() { return bars; }
    public Scoreboards boards() { return boards; }
    public GameManager game() { return game; }
    public HintService hints() { return hints; }
    public AnswerService answers() { return answers; }
    public FossilService fossils() { return fossils; }
}
