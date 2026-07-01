package me.yisang.limbus.status;

import me.yisang.limbus.LimbusEGOWeapons;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 理智值系統：範圍 -45 ~ +45，預設 0。
 * - 每命中 2 次 +1；每受擊 2 次 -1
 * - 脫戰 10s 後，SAN 為負值時每 2s +1 直到 0；正值不動
 * - SAN &lt; -20 時每次變動發 chat 提示 + 音效
 * - 觸底 -45 時，沉淪造成的傷害轉為「憂鬱」（×1.5，仍為真傷）
 * - BossBar 常態顯示：progress = (SAN + 45) / 90，SAN=0 剛好半滿
 */
public class SanityManager {
    private final LimbusEGOWeapons plugin;

    public static final int SAN_MAX = 45;
    public static final int SAN_MIN = -45;
    private static final long OUT_COMBAT_MS = 10_000L;
    private static final int WARN_THRESHOLD = -20;
    private static final int DEBUFF_THRESHOLD = -30;

    // 屬性微調：每 1 點 SAN 的攻擊/速度乘區。刻意小幅度，避免高/低理智過於失衡。
    // 攻擊 ±0.3% / 點 → 極值 ±13.5%；速度 ±0.15% / 點 → 極值 ±6.75%。
    private static final double ATK_PER_SAN = 0.003;
    private static final double SPD_PER_SAN = 0.0015;
    private static final String ATK_MOD_KEY = "san_atk";
    private static final String SPD_MOD_KEY = "san_spd";

    private final Map<UUID, Integer> san = new ConcurrentHashMap<>();
    /** [0]=攻擊命中累計, [1]=受擊累計 */
    private final Map<UUID, int[]> counters = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public SanityManager(LimbusEGOWeapons plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // 每 2s 檢查脫戰恢復
        Bukkit.getScheduler().runTaskTimer(plugin, this::recoveryTick, 40L, 40L);
        for (Player p : Bukkit.getOnlinePlayers()) onJoin(p);
    }

    public void shutdown() {
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
    }

    private void recoveryTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            // 低理智 debuff 定期補刷（讓玩家離開閾值後自然消退）
            if (getSan(p) <= DEBUFF_THRESHOLD) applyLowSanDebuffs(p);

            Long lc = lastCombat.get(p.getUniqueId());
            if (lc == null) continue;
            if (now - lc < OUT_COMBAT_MS) continue;
            int cur = getSan(p);
            if (cur < 0) setSan(p, cur + 1);
        }
    }

    public int getSan(Player p) {
        return san.getOrDefault(p.getUniqueId(), 0);
    }

    public void setSan(Player p, int v) {
        v = Math.max(SAN_MIN, Math.min(SAN_MAX, v));
        int old = getSan(p);
        san.put(p.getUniqueId(), v);
        updateBossBar(p, v);
        applyAttributeModifiers(p, v);
        if (v <= DEBUFF_THRESHOLD) applyLowSanDebuffs(p);

        // 只有「下降」才提示；每跨過一個 -10 區間才響一次
        // Math.floorDiv 對負數用地板除法：-20→-2、-21→-3、-30→-3、-31→-4
        boolean droppedByTen = v < old && Math.floorDiv(v, 10) < Math.floorDiv(old, 10);
        if (droppedByTen && v < WARN_THRESHOLD) {
            p.sendMessage("§5§l⚠ 理智值 §7» §d" + v + " §8/ §7" + SAN_MAX);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.4f, 0.5f);
        }
        if (old > DEBUFF_THRESHOLD && v <= DEBUFF_THRESHOLD) {
            p.sendMessage("§5§l▼ 陷入恐慌 §c你的雙眼與四肢已不再聽從指揮");
        }
        if (old > SAN_MIN && v == SAN_MIN) {
            p.sendMessage("§4§l▼ 理智觸底 §c你已陷入憂鬱");
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.5f);
        }
    }

    /**
     * 根據 SAN 套用 ATTACK_DAMAGE / MOVEMENT_SPEED 微調 modifier。
     * SAN 正 → 微幅增益；SAN 負 → 微幅削弱。
     * 每次呼叫先移除舊 modifier 再加新的，避免多次疊加。
     */
    private void applyAttributeModifiers(Player p, int san) {
        setModifier(p, Attribute.ATTACK_DAMAGE, ATK_MOD_KEY, san * ATK_PER_SAN);
        setModifier(p, Attribute.MOVEMENT_SPEED, SPD_MOD_KEY, san * SPD_PER_SAN);
    }

    private void setModifier(Player p, Attribute attr, String keyName, double amount) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        NamespacedKey key = new NamespacedKey(plugin, keyName);
        inst.getModifiers().stream()
                .filter(m -> key.equals(m.getKey()))
                .findFirst()
                .ifPresent(inst::removeModifier);
        if (amount == 0.0) return;
        inst.addModifier(new AttributeModifier(
                key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    /**
     * SAN &le; -30 時施加失明 I + 虛弱 I；
     * SAN 觸底 -45 時再追加緩速 IV。時效 3 秒，由 recovery/setSan 定期補刷。
     */
    private void applyLowSanDebuffs(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, true, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, false, true));
        if (getSan(p) <= SAN_MIN) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, true, false, true));
        }
    }

    private void clearAttributeModifiers(Player p) {
        setModifier(p, Attribute.ATTACK_DAMAGE, ATK_MOD_KEY, 0.0);
        setModifier(p, Attribute.MOVEMENT_SPEED, SPD_MOD_KEY, 0.0);
    }

    /** 攻擊命中：每 2 次 +1 SAN。 */
    public void onPlayerAttack(Player p) {
        int[] c = counters.computeIfAbsent(p.getUniqueId(), k -> new int[]{0, 0});
        c[0]++;
        lastCombat.put(p.getUniqueId(), System.currentTimeMillis());
        if (c[0] >= 2) {
            c[0] = 0;
            setSan(p, getSan(p) + 1);
        }
    }

    /** 受擊：每 2 次 -1 SAN。 */
    public void onPlayerHurt(Player p) {
        int[] c = counters.computeIfAbsent(p.getUniqueId(), k -> new int[]{0, 0});
        c[1]++;
        lastCombat.put(p.getUniqueId(), System.currentTimeMillis());
        if (c[1] >= 2) {
            c[1] = 0;
            setSan(p, getSan(p) - 1);
        }
    }

    /** 沉淪扣理智：每消耗 1 count 扣 1 SAN。 */
    public void dropSan(Player p, int amount) {
        if (amount <= 0) return;
        setSan(p, getSan(p) - amount);
        lastCombat.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** 是否處於憂鬱狀態（SAN 觸底）。 */
    public boolean isDepressed(LivingEntity e) {
        if (!(e instanceof Player p)) return false;
        return getSan(p) <= SAN_MIN;
    }

    public void onJoin(Player p) {
        san.putIfAbsent(p.getUniqueId(), 0);
        BossBar bar = Bukkit.createBossBar("理智值 0 / " + SAN_MAX, BarColor.BLUE, BarStyle.SEGMENTED_10);
        bar.setProgress(0.5);
        bar.addPlayer(p);
        bars.put(p.getUniqueId(), bar);
        updateBossBar(p, getSan(p));
    }

    public void onQuit(Player p) {
        BossBar b = bars.remove(p.getUniqueId());
        if (b != null) b.removeAll();
        counters.remove(p.getUniqueId());
        lastCombat.remove(p.getUniqueId());
        san.remove(p.getUniqueId());
        clearAttributeModifiers(p);
    }

    private void updateBossBar(Player p, int cur) {
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) return;
        double prog = (cur + 45.0) / 90.0;
        bar.setProgress(Math.max(0.0, Math.min(1.0, prog)));
        bar.setTitle("§f理智值 " + (cur >= 0 ? "§b" : (cur < WARN_THRESHOLD ? "§5" : "§c")) + cur + " §7/ §f" + SAN_MAX);
        if (cur < WARN_THRESHOLD) bar.setColor(BarColor.PURPLE);
        else if (cur < 0) bar.setColor(BarColor.RED);
        else bar.setColor(BarColor.BLUE);
    }
}
