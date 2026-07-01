package me.yisang.limbus.status;

import me.yisang.limbus.LimbusEGOWeapons;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limbus 屬性層/計數的中央管理器。
 *
 * 資料完全 in-memory（ConcurrentHashMap），mob unload / 死亡 / 進入 chunk unload 就丟掉；
 * 屬性本來就不用跨 session 保留。
 *
 * 觸發規則：
 *   BLEED     — 帶血者攻擊時消耗 1 count → 對自己造 potency × 0.5 真傷
 *   BURN      — 每 40 tick 週期消耗 1 count → 對自己造 potency 真傷
 *   FRAGILE   — 承傷乘 (1 + potency × 15%) 乘區
 *   POWER     — 出手乘 (1 + potency × 10%) 乘區
 *   SEDUCTION — 受擊消耗 1 count → potency 真傷 + 玩家 SAN -1（SAN 觸底轉憂鬱 ×1.5）
 *   RUPTURE   — 受擊消耗 1 count → potency × 2 真傷（速殺 boss 用高倍率）
 *   TREMOR    — 累積 potency；受擊且 potency ≥ 閾值時「爆發」→ 消耗全部 potency
 *               造 potency × 3 真傷 + 派生灼熱（追加 BURN 5p/3c）
 *   PROTECTION— 承傷乘 (1 - potency × 5%) 乘區（在 FRAGILE 之前套）
 *   HASTE/BIND— potion wrapper：直接轉 Speed / Slowness，不進 states map
 *               amplifier = potency-1，duration = count 秒
 *   CHARGE    — 出手乘 (1 + potency × 3%)，每次出手 -1 count
 *   BREATHING — 出手 min(60%, potency × 5%) 機率爆擊，×1.75 傷害，每次出手 -1 count
 *   POWER 也改為每次出手 -1 count（原本永久留存不合理，同 Limbus 語意調整）
 *
 * DoT 分 4 桶輪流結算，每 10 tick 處理 1/4，攤平負載。
 */
public class StatusManager implements Listener {
    private static final String CUSTOM_DAMAGE_META = "lce_status_true_dmg";
    private static final int BUCKET_COUNT = 4;
    private static final int BUCKET_INTERVAL_TICKS = 10; // 40 tick 一輪
    private static final double BLEED_COEF = 0.5;
    private static final double FRAGILE_PER_POTENCY = 0.15;
    private static final double POWER_PER_POTENCY = 0.10;
    private static final double DEPRESSION_MULT = 1.5;
    private static final double RUPTURE_MULT = 2.0;
    private static final double TREMOR_MULT = 3.0;
    private static final int TREMOR_BURST_THRESHOLD = 5;
    private static final int TREMOR_DERIV_BURN_POTENCY = 5;
    private static final int TREMOR_DERIV_BURN_COUNT = 3;
    private static final double PROTECTION_PER_POTENCY = 0.05;
    private static final double SEDUCTION_SPEED_PER_POTENCY = 0.02;
    private static final double SEDUCTION_SPEED_MAX = 0.5;
    private static final String SEDUCTION_SPEED_MOD_KEY = "seduction_speed";
    private static final double CHARGE_PER_POTENCY = 0.03;              // +3% 攻擊 / potency
    private static final double BREATHING_CRIT_PER_POTENCY = 0.05;      // +5% 爆擊率 / potency
    private static final double BREATHING_CRIT_MAX = 0.60;              // 上限 60% 爆擊率
    private static final double BREATHING_CRIT_MULT = 1.75;             // 爆擊 ×1.75

    private final LimbusEGOWeapons plugin;
    private final SanityManager sanity;
    private final ConcurrentHashMap<UUID, StatusState> states = new ConcurrentHashMap<>();
    private int tickBucket = 0;

    public StatusManager(LimbusEGOWeapons plugin, SanityManager sanity) {
        this.plugin = plugin;
        this.sanity = sanity;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::burnTick, BUCKET_INTERVAL_TICKS, BUCKET_INTERVAL_TICKS);
    }

    /** 對外 API：施加屬性（無 source，適合系統派生或自身效果）。 */
    public void apply(LivingEntity target, StatusEffect effect, int potency, int count) {
        apply(target, effect, potency, count, null);
    }

    /** 對外 API：施加屬性，並讓 source（施術者）看到 ActionBar 反饋。 */
    public void apply(LivingEntity target, StatusEffect effect, int potency, int count, Player source) {
        if (target == null || !target.isValid() || potency <= 0 || count <= 0) return;

        // HASTE / BIND 是 potion wrapper，不進 states map
        if (effect == StatusEffect.HASTE || effect == StatusEffect.BIND) {
            applyPotionWrapper(target, effect, potency, count);
            showEffectApplied(target, effect, potency, count, source);
            return;
        }

        StatusState s = states.computeIfAbsent(target.getUniqueId(), k -> new StatusState());
        s.add(effect, potency, count);
        if (effect == StatusEffect.SEDUCTION) syncSeductionSpeed(target, s);
        showEffectApplied(target, effect, potency, count, source);
    }

    private void applyPotionWrapper(LivingEntity target, StatusEffect effect, int potency, int count) {
        PotionEffectType type = effect == StatusEffect.HASTE ? PotionEffectType.SPEED : PotionEffectType.SLOWNESS;
        int amplifier = Math.max(0, potency - 1);
        int duration = count * 20; // count 表秒數
        target.addPotionEffect(new PotionEffect(type, duration, amplifier, true, true, true));
    }

    public StatusState get(LivingEntity e) {
        return states.get(e.getUniqueId());
    }

    /**
     * 續 count（延長現存效果持續時間），不改 potency。若該效果不在或 potency ≤ 0 則 no-op。
     * 用於 potency 已達上限時只要 refresh 生效時間的場景（例：W公司匕首持續攻擊維持充能）。
     */
    public void refresh(LivingEntity target, StatusEffect effect, int addCount) {
        if (target == null || addCount <= 0) return;
        StatusState s = states.get(target.getUniqueId());
        if (s == null || s.potency(effect) <= 0) return;
        s.add(effect, 0, addCount);
    }

    /**
     * 強制引爆流血：不需受目標主動攻擊即可觸發，最多 times 次。
     * 提比婭 Anatomize 用來解決「呆怪不揮砍不觸發」問題。
     */
    public void triggerBleed(LivingEntity target, Player source, int times) {
        if (target == null || times <= 0) return;
        StatusState s = states.get(target.getUniqueId());
        if (s == null) return;
        int potency = s.potency(StatusEffect.BLEED);
        if (potency <= 0) return;
        int consumed = s.consume(StatusEffect.BLEED, times);
        if (consumed <= 0) return;
        double dmg = potency * BLEED_COEF * consumed;
        scheduleTrueDamage(target, source, dmg, StatusEffect.BLEED);
        if (s.isEmpty()) states.remove(target.getUniqueId());
    }

    // ── DoT tick（燒傷） ───────────────────────────────────────────────

    private void burnTick() {
        int bucket = tickBucket;
        tickBucket = (tickBucket + 1) % BUCKET_COUNT;

        // 分桶：以 UUID hashCode mod BUCKET_COUNT 決定歸屬
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, StatusState> en : states.entrySet()) {
            if ((en.getKey().hashCode() & 0x7fffffff) % BUCKET_COUNT != bucket) continue;

            org.bukkit.entity.Entity ent = Bukkit.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isValid() || le.isDead()) {
                toRemove.add(en.getKey());
                continue;
            }

            StatusState s = en.getValue();
            int potency = s.potency(StatusEffect.BURN);
            if (potency > 0 && s.consume(StatusEffect.BURN, 1) > 0) {
                dealTrueDamage(le, null, potency, StatusEffect.BURN);
            }
            if (s.isEmpty()) toRemove.add(en.getKey());
        }
        for (UUID id : toRemove) states.remove(id);
    }

    // ── 傷害事件中央處理 ────────────────────────────────────────────

    /**
     * HIGH 優先度：跑在 mimicry / solemn 等武器模組（NORMAL）之後，
     * 讓武器改完 damage 再套 POWER / FRAGILE 乘區。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // 忽略本系統自己造成的真傷，避免遞迴
        if (event.getDamager().hasMetadata(CUSTOM_DAMAGE_META)) return;

        LivingEntity attacker = event.getDamager() instanceof LivingEntity la ? la : null;
        LivingEntity victim = event.getEntity() instanceof LivingEntity lv ? lv : null;

        StatusState atkS = attacker == null ? null : states.get(attacker.getUniqueId());
        StatusState vicS = victim == null ? null : states.get(victim.getUniqueId());

        // POWER / CHARGE：攻擊者輸出乘區；BREATHING：機率爆擊。每次出手 -1 count。
        if (attacker != null && atkS != null) {
            int power = atkS.potency(StatusEffect.POWER);
            int charge = atkS.potency(StatusEffect.CHARGE);
            int breathing = atkS.potency(StatusEffect.BREATHING);
            double mult = 1.0;
            if (power > 0) mult *= (1.0 + power * POWER_PER_POTENCY);
            if (charge > 0) mult *= (1.0 + charge * CHARGE_PER_POTENCY);
            boolean crit = false;
            if (breathing > 0) {
                double chance = Math.min(BREATHING_CRIT_MAX, breathing * BREATHING_CRIT_PER_POTENCY);
                if (Math.random() < chance) {
                    crit = true;
                    mult *= BREATHING_CRIT_MULT;
                }
            }
            if (mult != 1.0) event.setDamage(event.getDamage() * mult);
            if (crit && attacker instanceof Player pa) {
                sendActionBar(pa, "§3§l✦ 呼吸法爆擊 §7» §f×" + BREATHING_CRIT_MULT);
            }
            // 每次出手消耗 1 count（Limbus buff 語意：每回合／每次拼點自然衰減）
            if (power > 0) atkS.consume(StatusEffect.POWER, 1);
            if (charge > 0) atkS.consume(StatusEffect.CHARGE, 1);
            if (breathing > 0) atkS.consume(StatusEffect.BREATHING, 1);
        }

        // PROTECTION：受害者減傷乘區（先於 FRAGILE，讓易損不會被完全抵消）
        if (victim != null && vicS != null) {
            int prot = vicS.potency(StatusEffect.PROTECTION);
            if (prot > 0) event.setDamage(event.getDamage() * Math.max(0.0, 1.0 - prot * PROTECTION_PER_POTENCY));
        }

        // FRAGILE：受害者承傷乘區
        if (victim != null && vicS != null) {
            int f = vicS.potency(StatusEffect.FRAGILE);
            if (f > 0) event.setDamage(event.getDamage() * (1.0 + f * FRAGILE_PER_POTENCY));
        }

        // SAN 計數（僅玩家）
        if (attacker instanceof Player pa) sanity.onPlayerAttack(pa);
        if (victim instanceof Player pv) sanity.onPlayerHurt(pv);

        // BLEED：帶血者「攻擊時」對自己扣血
        if (attacker != null && atkS != null) {
            int bleedPotency = atkS.potency(StatusEffect.BLEED);
            if (bleedPotency > 0 && atkS.consume(StatusEffect.BLEED, 1) > 0) {
                double dmg = bleedPotency * BLEED_COEF;
                scheduleTrueDamage(attacker, null, dmg, StatusEffect.BLEED);
            }
        }

        // SEDUCTION：受擊消耗
        if (victim != null && vicS != null) {
            int sedPotency = vicS.potency(StatusEffect.SEDUCTION);
            if (sedPotency > 0 && vicS.consume(StatusEffect.SEDUCTION, 1) > 0) {
                boolean depressed = sanity.isDepressed(victim);
                double dmg = sedPotency * (depressed ? DEPRESSION_MULT : 1.0);
                scheduleTrueDamage(victim,
                        attacker instanceof Player ? (Player) attacker : null,
                        dmg,
                        depressed ? null : StatusEffect.SEDUCTION);
                if (victim instanceof Player pv) sanity.dropSan(pv, 1);
                syncSeductionSpeed(victim, vicS);
            }
        }

        // RUPTURE：受擊消耗 1 count → potency × 2 真傷
        if (victim != null && vicS != null) {
            int rupPotency = vicS.potency(StatusEffect.RUPTURE);
            if (rupPotency > 0 && vicS.consume(StatusEffect.RUPTURE, 1) > 0) {
                double dmg = rupPotency * RUPTURE_MULT;
                scheduleTrueDamage(victim,
                        attacker instanceof Player ? (Player) attacker : null,
                        dmg, StatusEffect.RUPTURE);
            }
        }

        // TREMOR：受擊且 potency 達閾值 → 爆發（消耗全部 + 派生灼熱）
        if (victim != null && vicS != null) {
            int tremorPotency = vicS.potency(StatusEffect.TREMOR);
            if (tremorPotency >= TREMOR_BURST_THRESHOLD) {
                int cnt = vicS.count(StatusEffect.TREMOR);
                vicS.consume(StatusEffect.TREMOR, cnt);
                double dmg = tremorPotency * TREMOR_MULT;
                Player src = attacker instanceof Player ? (Player) attacker : null;
                scheduleTrueDamage(victim, src, dmg, StatusEffect.TREMOR);
                // 派生灼熱：追加 BURN
                if (victim.isValid() && !victim.isDead()) {
                    apply(victim, StatusEffect.BURN, TREMOR_DERIV_BURN_POTENCY, TREMOR_DERIV_BURN_COUNT);
                }
                if (src != null) {
                    sendActionBar(src, "§b§l⚡ 震顫爆發 §f" + tremorPotency + " §7→ §6灼熱派生");
                }
            }
        }

        // 清理空狀態
        if (atkS != null && atkS.isEmpty()) states.remove(attacker.getUniqueId());
        if (vicS != null && vicS.isEmpty()) states.remove(victim.getUniqueId());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity le = event.getEntity();
        // 清 SEDUCTION 移速 modifier（玩家 attribute 會跨復活保留，要顯式移除）
        syncSeductionSpeed(le, null);
        states.remove(le.getUniqueId());
    }
    // 非死亡的 despawn / chunk unload 由 burnTick 掃描時 isValid 檢查順帶清掉。

    // ── 傷害施加（避免遞迴） ────────────────────────────────────────

    private void scheduleTrueDamage(LivingEntity target, Player source, double amount, StatusEffect labelOrNullForDepression) {
        Bukkit.getScheduler().runTask(plugin, () -> dealTrueDamage(target, source, amount, labelOrNullForDepression));
    }

    /** null label = 憂鬱傷害。 */
    private void dealTrueDamage(LivingEntity target, Player source, double amount, StatusEffect label) {
        if (target == null || !target.isValid() || target.isDead()) return;
        target.setMetadata(CUSTOM_DAMAGE_META, new FixedMetadataValue(plugin, true));
        try {
            target.damage(amount);
        } finally {
            target.removeMetadata(CUSTOM_DAMAGE_META, plugin);
        }
        showDamage(target, source, amount, label);
    }

    // ── 顯示 ────────────────────────────────────────────────────────

    private void showEffectApplied(LivingEntity target, StatusEffect e, int potency, int count, Player source) {
        String txt = e.color + "▲ " + e.zh + " §f" + potency + " §7/ §f" + count;
        if (target instanceof Player p) sendActionBar(p, txt);
        if (source != null && source.isOnline() && !source.equals(target)) sendActionBar(source, txt);
    }

    /**
     * 沉淪移速：讀當前 SEDUCTION potency，套 MULTIPLY_SCALAR_1 modifier
     * 到 MOVEMENT_SPEED。-2% / potency，上限 -50%。potency 歸零時清 modifier。
     */
    private void syncSeductionSpeed(LivingEntity target, StatusState s) {
        AttributeInstance inst = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (inst == null) return;
        NamespacedKey key = new NamespacedKey(plugin, SEDUCTION_SPEED_MOD_KEY);
        inst.getModifiers().stream()
                .filter(m -> key.equals(m.getKey()))
                .findFirst()
                .ifPresent(inst::removeModifier);
        int p = s == null ? 0 : s.potency(StatusEffect.SEDUCTION);
        if (p <= 0) return;
        double amount = -Math.min(SEDUCTION_SPEED_MAX, p * SEDUCTION_SPEED_PER_POTENCY);
        inst.addModifier(new AttributeModifier(
                key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    private void showDamage(LivingEntity target, Player source, double amount, StatusEffect label) {
        String tag = label == null ? "§4憂鬱" : (label.color + label.zh);
        String txt = tag + " §7» §f" + String.format("%.1f", amount);
        if (target instanceof Player p) sendActionBar(p, "§c-" + String.format("%.1f", amount) + " " + tag);
        if (source != null && source.isOnline()) sendActionBar(source, txt);
    }

    private void sendActionBar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }
}
