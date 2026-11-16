package data.plugins;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.impl.campaign.abilities.SensorBurstAbility;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import com.fs.starfarer.api.ModManagerAPI;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.util.*;
import java.time.LocalTime;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

/** Exercises mod code and the installed game's real ability base classes, without an OpenGL window. */
public class CampaignRegressionTest {
    interface Call { Object invoke(Method method, Object[] args) throws Throwable; }
    static int checks;
    static boolean paused, dialog, menu, usable = true, hostile;
    static int scavenges, transponderOff, slowFrames, burnDeactivations;
    static boolean transponderOn = true;
    static boolean hideUI, clockEnabled;
    static CoreUITabId tab;
    static float screenHeight = 1080f;
    static final Map<String, AbilityPlugin> abilities = new HashMap<>();
    static final Map<String, AbilitySpecAPI> specs = new HashMap<>();
    static final Map<String, Object> memory = new HashMap<>();
    static CampaignFleetAPI player;
    static LocationAPI system, hyperspace;

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getName().equals("toString")) return type.getSimpleName();
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            Object result = call.invoke(m, a == null ? new Object[0] : a);
            if (result != null || !m.getReturnType().isPrimitive()) return result;
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == float.class) return 0f;
            if (m.getReturnType() == int.class) return 0;
            if (m.getReturnType() == long.class) return 0L;
            return null;
        });
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    static AbilitySpecAPI spec(String id, String... initialTags) {
        Set<String> tags = new HashSet<>(Arrays.asList(initialTags));
        return proxy(AbilitySpecAPI.class, (m, a) -> switch (m.getName()) {
            case "getId", "getName" -> id;
            case "getTags" -> tags;
            case "hasTag" -> tags.contains(a[0]);
            case "isPositiveTag" -> ((String)a[0]).endsWith("+");
            case "isNegativeTag" -> ((String)a[0]).endsWith("-");
            case "hasOppositeTag" -> {
                String tag = (String)a[0];
                yield tags.contains(tag.substring(0, tag.length()-1) + (tag.endsWith("+") ? "-" : "+"));
            }
            case "getActivationDays", "getDurationDays", "getDeactivationDays" -> 0.1f;
            default -> null;
        });
    }
    static class AutoScavenge extends qolp_AutoScavengeAbility {
        @Override public boolean hostileCHeck() { return hostile; }
    }
    static void setup() {
        specs.put("auto_scavenge", spec("auto_scavenge"));
        specs.put("sensor_burst", spec("sensor_burst", "stealth-", "burn-", "sensors+"));
        specs.put("remote_survey", spec("remote_survey", "stealth-", "burn-", "sensors+"));
        specs.put("sustained_burn", spec("sustained_burn", "burn+", "sensors-"));
        ModManagerAPI manager = proxy(ModManagerAPI.class, (m, a) -> false);
        Global.setSettings(proxy(SettingsAPI.class, (m, a) -> switch (m.getName()) {
            case "getAbilitySpec" -> specs.get(a[0]);
            case "getModManager" -> manager;
            case "getScreenHeight" -> screenHeight;
            case "getMergedJSONForMod" -> new JSONObject("{BetterSensorBurst:true,BetterRemoteSurvey:true,"
                    + "ScavengeAsYouFly:true,EnableClock:" + clockEnabled + ",12HourCLock:false,PartialSurveyAsYouFly:false,Transpoffder:false}");
            default -> null;
        }));
        system = proxy(LocationAPI.class, (m, a) -> m.getName().equals("getFleets") ? List.of() : null);
        hyperspace = proxy(LocationAPI.class, (m, a) -> m.getName().equals("isHyperspace") ? true : null);
        MemoryAPI mem = proxy(MemoryAPI.class, (m, a) -> {
            if (m.getName().equals("set")) memory.put((String)a[0], a[1]);
            if (m.getName().equals("getBoolean")) return Boolean.TRUE.equals(memory.get(a[0]));
            return null;
        });
        MutableFleetStatsAPI stats = proxy(MutableFleetStatsAPI.class, (m, a) ->
                m.getName().equals("getSensorRangeMod") || m.getName().equals("getDetectedRangeMod") ? new StatBonus() : null);
        player = proxy(CampaignFleetAPI.class, (m, a) -> switch (m.getName()) {
            case "getAbility" -> abilities.get(a[0]);
            case "getAbilities" -> abilities;
            case "getContainingLocation" -> system;
            case "getMemoryWithoutUpdate" -> mem;
            case "getStats" -> stats;
            case "setTransponderOn" -> { transponderOn = (boolean)a[0]; yield null; }
            case "goSlowOneFrame" -> { slowFrames++; yield null; }
            default -> null;
        });
        CampaignUIAPI ui = proxy(CampaignUIAPI.class, (m, a) -> switch (m.getName()) {
            case "isShowingDialog" -> dialog;
            case "isShowingMenu" -> menu;
            case "isHideUI" -> hideUI;
            case "getCurrentCoreTab" -> tab;
            default -> null;
        });
        CampaignClockAPI clock = proxy(CampaignClockAPI.class, (m, a) -> m.getName().equals("convertToDays") ? a[0] : null);
        CharacterDataAPI character = proxy(CharacterDataAPI.class, (m, a) -> {
            if (m.getName().equals("removeAbility")) abilities.remove(a[0]);
            if (m.getName().equals("addAbility") && !abilities.containsKey(a[0])) {
                var auto = new AutoScavenge();
                auto.init((String)a[0], player);
                abilities.put((String)a[0], auto);
            }
            return null;
        });
        Global.setSector(proxy(SectorAPI.class, (m, a) -> switch (m.getName()) {
            case "getPlayerFleet" -> player;
            case "getCampaignUI" -> ui;
            case "getClock" -> clock;
            case "getMemoryWithoutUpdate" -> mem;
            case "getCharacterData" -> character;
            case "isPaused" -> paused;
            default -> null;
        }));
        abilities.put("scavenge", proxy(AbilityPlugin.class, (m, a) -> switch (m.getName()) {
            case "isUsable" -> usable;
            case "activate" -> { scavenges++; usable = false; yield null; }
            default -> null;
        }));
        abilities.put("transponder", proxy(AbilityPlugin.class, (m, a) -> {
            if (m.getName().equals("deactivate")) transponderOff++;
            return null;
        }));
    }
    static void testTransponder() {
        var listener = new qolp_TranspoffderListener();
        listener.reportCurrentLocationChanged(system, hyperspace);
        check(!transponderOn && transponderOff == 1, "Hyperspace entry must disable transponder and ability");
        transponderOn = true;
        listener.reportCurrentLocationChanged(hyperspace, system);
        check(transponderOn && transponderOff == 1, "System entry must leave transponder alone");
        listener.reportCurrentLocationChanged(null, hyperspace);
        check(transponderOn, "Load callback without a previous location must be ignored");
        abilities.remove("transponder");
        listener.reportCurrentLocationChanged(system, hyperspace);
        check(!transponderOn, "Missing ability must not prevent fleet transponder from being disabled");
    }
    static void testScavenge() {
        AutoScavenge auto = new AutoScavenge();
        auto.init("auto_scavenge", player);
        abilities.put("auto_scavenge", auto);
        auto.advance(0.1f);
        check(scavenges == 0, "Scavenging starts off");
        qolp_AutoScavengeAbility.setOn(true);
        check(qolp_AutoScavengeAbility.isOn(), "Toggle enables scavenging");
        auto.advance(0.1f);
        check(scavenges == 1, "Usable scavenge activates immediately");
        auto.advance(0.1f);
        check(scavenges == 1, "Cooldown must not reactivate scavenge");
        usable = true; dialog = true; auto.advance(0.1f); dialog = false;
        menu = true; auto.advance(0.1f); menu = false;
        paused = true; auto.advance(0.1f); paused = false;
        auto.advance(0);
        check(scavenges == 1, "Dialogs, menus and pause must not trigger scavenging");
        hostile = true; auto.advance(0.1f);
        check(scavenges == 1, "Nearby hostile must block scavenging");
        hostile = false; auto.advance(1f);
        check(scavenges == 1, "Hostile recheck is throttled");
        auto.advance(1.1f);
        check(scavenges == 2, "Scavenging resumes when safe");
        qolp_AutoScavengeAbility.setOn(false);
        usable = true; auto.advance(3f);
        check(scavenges == 2, "Toggle disables scavenging");
        auto.forceDisable(); auto.advance(0.1f); auto.advance(0.1f);
        check(auto.isUsable(), "Base ability lifecycle must clear force-disable frames");
        abilities.remove("auto_scavenge");
        qolp_AutoScavengeAbility.setOn(true);
        check(!qolp_AutoScavengeAbility.isOn(), "Missing auto-scavenge must be safe");
        check(!new qolp_AutoScavengeAbility().hostileCHeck(), "Empty system has no hostile fleets");
    }
    static void testSensorBurst() throws Exception {
        abilities.clear();
        SensorBurstAbility burst = new SensorBurstAbility();
        burst.init("sensor_burst", player);
        AbilityPlugin burn = proxy(AbilityPlugin.class, (m, a) -> switch (m.getName()) {
            case "getSpec" -> specs.get("sustained_burn");
            case "isActive" -> true;
            case "deactivate", "forceDisable" -> { burnDeactivations++; yield null; }
            default -> null;
        });
        check(!burst.isCompatible(burn), "Vanilla sensor burst conflicts with sustained burn");
        new qolp_modPlugin().onApplicationLoad();
        check(burst.isCompatible(burn), "Patched burst must be compatible with sustained burn");
        check(specs.get("sensor_burst").hasTag("stealth-"), "Detectability penalty must remain");
        abilities.put("sustained_burn", burn);
        abilities.put("sensor_burst", burst);
        burst.activate();
        check(slowFrames > 0, "Real 0.98.5a sensor burst must slow fleet movement");
        check(burnDeactivations == 0, "Real burst activation must preserve sustained burn");
    }
    static void testSaveCycle() {
        var plugin = new qolp_modPlugin();
        plugin.afterGameSave();
        check(!qolp_AutoScavengeAbility.isOn(), "New toggle starts off");
        qolp_AutoScavengeAbility.setOn(true);
        plugin.beforeGameSave();
        check(!abilities.containsKey("auto_scavenge"), "Custom ability is removed before serialization");
        check(Boolean.TRUE.equals(memory.get("$transpoffderAutoScavenge")), "Enabled state is stored in save memory");
        plugin.afterGameSave();
        check(qolp_AutoScavengeAbility.isOn(), "Enabled state is restored after save");
        qolp_AutoScavengeAbility.setOn(false);
        plugin.beforeGameSave(); plugin.afterGameSave();
        check(!qolp_AutoScavengeAbility.isOn(), "Disabled state survives save hooks too");
    }
    static void testClock() {
        check(qolp_clock.formatTime(LocalTime.MIDNIGHT, true).equals("12:00 AM"), "Midnight uses 12 AM");
        check(qolp_clock.formatTime(LocalTime.NOON, true).equals("12:00 PM"), "Noon uses 12 PM");
        check(qolp_clock.formatTime(LocalTime.of(23, 7), true).equals("11:07 PM"), "12-hour minutes are padded");
        check(qolp_clock.formatTime(LocalTime.of(0, 7), false).equals("0:07"), "24-hour midnight formatting");
        final int[] draws = {0};
        final float[] y = {0};
        qolp_clock clock = new qolp_clock(false) {
            @Override protected void drawClock(String value, float height) { draws[0]++; y[0] = height; }
        };
        clock.renderInUICoordsBelowUI(null); clock.renderInUICoordsAboveUIAndTooltips(null);
        check(draws[0] == 0, "Clock only draws on its assigned UI layer");
        paused = true;
        clock.renderInUICoordsAboveUIBelowTooltips(null);
        check(draws[0] == 1 && y[0] == 965f, "Clock renders during pause in UI coordinates");
        paused = false; screenHeight = 1440f;
        clock.renderInUICoordsAboveUIBelowTooltips(null);
        check(draws[0] == 2 && y[0] == 1325f, "Clock responds to resolution changes");
        dialog = true; clock.renderInUICoordsAboveUIBelowTooltips(null); dialog = false;
        menu = true; clock.renderInUICoordsAboveUIBelowTooltips(null); menu = false;
        hideUI = true; clock.renderInUICoordsAboveUIBelowTooltips(null); hideUI = false;
        tab = CoreUITabId.REFIT; clock.renderInUICoordsAboveUIBelowTooltips(null);
        check(draws[0] == 2, "Clock hides for interactions, menus, hidden UI, and refit");
        tab = CoreUITabId.FLEET; clock.renderInUICoordsAboveUIBelowTooltips(null); tab = null;
        check(draws[0] == 3, "Clock is visible on fleet screen");
        SectorAPI original = Global.getSector();
        var listeners = new ArrayList<Object>();
        ListenerManagerAPI manager = proxy(ListenerManagerAPI.class, (m, a) -> {
            if (m.getName().equals("addListener")) {
                check(a.length == 2 && Boolean.TRUE.equals(a[1]), "Clock listener is transient");
                listeners.add(a[0]);
            }
            return null;
        });
        try {
            Global.setSector(proxy(SectorAPI.class, (m, a) -> m.getName().equals("getListenerManager")
                    ? manager : m.invoke(original, a)));
            clockEnabled = true;
            new qolp_modPlugin().onGameLoad(false);
            check(listeners.stream().anyMatch(l -> l instanceof qolp_clock), "Loading a save registers clock renderer");
        } finally { clockEnabled = false; Global.setSector(original); }
    }
    static PlanetAPI planet(float x, SurveyLevel[] level, boolean star) {
        MarketAPI market = level == null ? null : proxy(MarketAPI.class, (m, a) -> switch (m.getName()) {
            case "getSurveyLevel" -> level[0];
            case "setSurveyLevel" -> { level[0] = (SurveyLevel)a[0]; yield null; }
            default -> null;
        });
        return proxy(PlanetAPI.class, (m, a) -> switch (m.getName()) {
            case "getLocation" -> new Vector2f(x, 0);
            case "getMarket" -> market;
            case "isStar" -> star;
            default -> null;
        });
    }
    static void testPartialSurvey() {
        SectorAPI original = Global.getSector();
        SurveyLevel[] unseen = {SurveyLevel.NONE}, seen = {SurveyLevel.SEEN}, distant = {SurveyLevel.NONE},
                full = {SurveyLevel.FULL}, preliminary = {SurveyLevel.PRELIMINARY}, star = {SurveyLevel.NONE};
        var planets = Arrays.asList(planet(50, unseen, false), planet(100, seen, false), planet(101, distant, false),
                planet(20, full, false), planet(20, preliminary, false), planet(20, star, true), planet(20, null, false));
        StarSystemAPI system = proxy(StarSystemAPI.class, (m, a) -> m.getName().equals("getPlanets") ? planets : null);
        CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class, (m, a) -> switch (m.getName()) {
            case "getStarSystem" -> system;
            case "getLocation" -> new Vector2f();
            case "getSensorStrength" -> 100f;
            default -> null;
        });
        try {
            Global.setSector(proxy(SectorAPI.class, (m, a) -> switch (m.getName()) {
                case "getPlayerFleet" -> fleet;
                case "isPaused" -> paused;
                default -> null;
            }));
            var script = new qolp_PartialSurveyScript();
            script.advance(0); paused = true; script.advance(1); paused = false;
            check(unseen[0] == SurveyLevel.NONE, "Survey waits while paused");
            script.advance(1);
            check(unseen[0] == SurveyLevel.PRELIMINARY && seen[0] == SurveyLevel.PRELIMINARY,
                    "Nearby unseen and seen planets get preliminary surveys, including range boundary");
            check(distant[0] == SurveyLevel.NONE, "Out-of-range planets remain untouched");
            check(full[0] == SurveyLevel.FULL && preliminary[0] == SurveyLevel.PRELIMINARY, "Existing surveys are preserved");
            check(star[0] == SurveyLevel.NONE, "Stars are never surveyed");
            Global.setSector(proxy(SectorAPI.class, (m, a) -> null)); script.advance(1);
            check(true, "Missing player fleet is safe");
            Global.setSector(proxy(SectorAPI.class, (m, a) -> m.getName().equals("getPlayerFleet") ? player : null));
            script.advance(1);
            check(true, "Hyperspace without a star system is safe");
        } finally { Global.setSector(original); paused = false; }
    }
    public static void main(String[] args) throws Exception {
        try {
            setup(); testTransponder(); testScavenge(); testSaveCycle(); testSensorBurst(); testClock(); testPartialSurvey();
            System.out.println("PASS: " + checks + " campaign regression checks against installed Starsector API");
        } finally { Global.setSector(null); Global.setSettings(null); }
    }
}
