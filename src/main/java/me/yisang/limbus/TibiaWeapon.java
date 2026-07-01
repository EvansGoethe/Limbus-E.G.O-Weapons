package me.yisang.limbus;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import me.yisang.limbus.status.StatusEffect;
import me.yisang.limbus.status.StatusManager;
import me.yisang.limbus.status.StatusState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 鴻璐·脛骨 Tibia — Callisto 用自身軀體鍛造的巨劍。
 *
 * 忠於原作三大機制：
 *  1. 命中疊層 Bleed（用 POISON 模擬，每擊 +2 層，上限 10 層 / 8 秒）。
 *  2. Tibia's Melody：對已流血的目標，每 2 層 +3% 傷害，上限 +30%。
 *  3. 潛行右鍵蓄力 2 秒 → Anatomize：前方 5 格扇形斬，命中 +6 層 Bleed 並附加真實傷害。
 *
 * 被動：Corpus 抗性 — 持有主手時，減少 Wither/Poison/Fire 的觸發效果（每 20 tick 掃描並降級）。
 */
public class TibiaWeapon implements EGOWeapon, Listener {

    // ── Limbus BLEED 屬性參數 ──
    private static final int BLEED_HIT_POTENCY = 3;             // 普攻 +3 potency
    private static final int BLEED_HIT_COUNT   = 2;             // 普攻 +2 count
    private static final int SLASH_BLEED_POTENCY = 12;          // Anatomize +12 potency
    private static final int SLASH_BLEED_COUNT   = 6;           // Anatomize +6 count
    private static final int SLASH_FORCE_TRIGGER = 3;           // Anatomize 強制引爆 3 次
    private static final double MELODY_PER_3POTENCY = 0.03;     // 每 3 potency +3%
    private static final double MELODY_MAX_BONUS    = 0.30;

    private static final double TRUE_FRACTION_SLASH = 0.35;
    private static final long SPECIAL_COOLDOWN_MS = 8000L;
    private static final int CHARGE_TICKS = 40;
    private static final double SLASH_RANGE = 5.0;
    private static final double SLASH_BASE = 16.0;

    private final LimbusEGOWeapons plugin;
    private final Map<UUID, Long> specialCooldown = new HashMap<>();
    private final Set<UUID> charging = new HashSet<>();

    public TibiaWeapon(LimbusEGOWeapons plugin) {
        this.plugin = plugin;
        startPassiveTick();
    }

    @Override
    public String getId() { return "tibia"; }

    // ── 物品 ─────────────────────────────────────────────────────────────────

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes("&#8B0000提比婭"));
            meta.setLore(List.of(
                    plugin.translateHexColorCodes("&7聽見了嗎？那由提比婭的一對肱骨與二十四根肋骨奏響的旋律！")));
            meta.setCustomModelData(1010);
            meta.setUnbreakable(true);
            meta.setItemModel(NamespacedKey.fromString("tibia:tibia"));
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(new NamespacedKey(plugin, "tibia_dmg"),
                            10.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(new NamespacedKey(plugin, "tibia_spd"),
                            -2.8, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(new NamespacedKey(plugin, "tibia_reach"),
                            1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(
                    plugin.getItemIdKey(), PersistentDataType.STRING, "tibia");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── 近戰：疊 Bleed + 高流血增傷 ─────────────────────────────────────────────

    @Override
    public void handleMelee(EntityDamageByEntityEvent event, Player attacker) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        double bonus = melodyBonus(target);
        event.setDamage(event.getDamage() * (1.0 + bonus));

        StatusManager sm = plugin.getStatusManager();
        if (sm != null) {
            sm.apply(target, StatusEffect.BLEED, BLEED_HIT_POTENCY, BLEED_HIT_COUNT, attacker);
        }

        target.getWorld().spawnParticle(Particle.DUST,
                target.getLocation().add(0, 1.0, 0), 8, 0.3, 0.4, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(0x8B0000), 1.2f));
        if (bonus > 0) {
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.5f);
        }

        // Melody 加成即時提示：讓玩家看見流血 potency 換來的 %
        int bleedPot = sm == null || sm.get(target) == null ? 0 : sm.get(target).potency(StatusEffect.BLEED);
        attacker.sendActionBar(plugin.translateHexColorCodes(
                "&#8B0000提比婭之旋律 &#FFFFFF+" + (int) Math.round(bonus * 100) + "% &7│ &#FF5555流血 &#FFFFFF" + bleedPot));
    }

    /** Melody 加成：讀目標身上的 BLEED potency，每 3 potency +3%，上限 30%。 */
    private double melodyBonus(LivingEntity target) {
        StatusManager sm = plugin.getStatusManager();
        if (sm == null) return 0.0;
        StatusState s = sm.get(target);
        if (s == null) return 0.0;
        int potency = s.potency(StatusEffect.BLEED);
        return Math.min(MELODY_MAX_BONUS, Math.floor(potency / 3.0) * MELODY_PER_3POTENCY);
    }

    // ── 潛行右鍵：蓄力解剖斬 ────────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!plugin.hasItemId(item, "tibia")) return;
        if (!player.isSneaking()) return;

        event.setCancelled(true);

        UUID uid = player.getUniqueId();
        if (charging.contains(uid)) return;

        long now = System.currentTimeMillis();
        long cd = specialCooldown.getOrDefault(uid, 0L);
        if (now < cd) {
            player.sendActionBar(plugin.translateHexColorCodes(
                    "&#8B0000解剖斬冷卻中…" + ((cd - now) / 1000 + 1) + "s"));
            return;
        }
        charging.add(uid);

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.8f);

        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!charging.contains(uid)) { cancel(); return; }
                if (!player.isOnline() || player.isDead()
                        || !plugin.hasItemId(player.getInventory().getItemInMainHand(), "tibia")) {
                    charging.remove(uid);
                    cancel();
                    return;
                }
                Location c = player.getLocation().add(0, 1.0, 0);
                player.getWorld().spawnParticle(Particle.DUST, c, 6, 0.5, 0.5, 0.5, 0,
                        new Particle.DustOptions(Color.fromRGB(0x8B0000), 1.3f));
                player.getWorld().spawnParticle(Particle.CRIMSON_SPORE, c, 3, 0.4, 0.4, 0.4, 0.01);
                t++;
                // 蓄力進度條（每 tick 更新）
                player.sendActionBar(plugin.translateHexColorCodes(
                        "&#8B0000◆ 解剖斬蓄力 " + chargeBar(t, CHARGE_TICKS)));
                if (t >= CHARGE_TICKS) {
                    cancel();
                    charging.remove(uid);
                    specialCooldown.put(uid, System.currentTimeMillis() + SPECIAL_COOLDOWN_MS);
                    anatomize(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void onItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        charging.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSwapHand(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        charging.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        charging.remove(id);
        specialCooldown.remove(id);
    }

    private void anatomize(Player player) {
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0e-6) look = new Vector(0, 0, 1);
        look.normalize();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.6f);

        // 扇形血刃粒子
        Location origin = player.getLocation().add(0, 1.1, 0);
        for (double deg = -60; deg <= 60; deg += 6) {
            Vector dir = rotateY(look.clone(), Math.toRadians(deg));
            for (double d = 1.0; d <= SLASH_RANGE; d += 1.2) {
                Location p = origin.clone().add(dir.clone().multiply(d));
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0, 0, 0, 0);
                if (d + 1.2 > SLASH_RANGE) {
                    player.getWorld().spawnParticle(Particle.DUST, p, 3, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(0x8B0000), 1.4f));
                }
            }
        }

        int hitCount = 0;
        int totalBleedApplied = 0;
        player.setMetadata("lsmp_custom_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        try {
            for (Entity e : player.getNearbyEntities(SLASH_RANGE, SLASH_RANGE, SLASH_RANGE)) {
                if (e.equals(player)) continue;
                if (!(e instanceof LivingEntity target)) continue;
                Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                if (to.lengthSquared() < 1.0e-6) continue;
                double angle = look.angle(to.normalize());
                if (angle > Math.toRadians(60)) continue;

                // Melody 加成：本次結算前的 BLEED potency
                double bonus = melodyBonus(target);
                double dmg = SLASH_BASE * (1.0 + bonus);

                target.setNoDamageTicks(0);
                target.damage(dmg * (1.0 - TRUE_FRACTION_SLASH), player);
                if (target.isValid() && !target.isDead()) {
                    dealTrueDamage(target, dmg * TRUE_FRACTION_SLASH, player);
                }

                // 大幅疊 Limbus BLEED 並強制引爆
                StatusManager sm = plugin.getStatusManager();
                if (sm != null && target.isValid() && !target.isDead()) {
                    sm.apply(target, StatusEffect.BLEED, SLASH_BLEED_POTENCY, SLASH_BLEED_COUNT, player);
                    sm.triggerBleed(target, player, SLASH_FORCE_TRIGGER);
                    totalBleedApplied += SLASH_BLEED_POTENCY;
                }
                hitCount++;
            }
        } finally {
            player.removeMetadata("lsmp_custom_damage", plugin);
        }

        // 結算 ActionBar：命中數 + 疊加流血總量
        if (hitCount == 0) {
            player.sendActionBar(plugin.translateHexColorCodes("&#8B0000◆ 解剖斬 &7未命中"));
        } else {
            player.sendActionBar(plugin.translateHexColorCodes(
                    "&#8B0000◆ 解剖斬 &#FFFFFF命中 " + hitCount + " &7│ &#FF5555流血 +" + totalBleedApplied
                            + " &7│ &#FFAA00引爆 " + SLASH_FORCE_TRIGGER + " 次"));
        }
    }

    /** 蓄力進度條：▰▰▱▱▱ 40% 這種樣式，共 10 格。 */
    private String chargeBar(int cur, int max) {
        int filled = Math.min(10, cur * 10 / Math.max(1, max));
        StringBuilder sb = new StringBuilder("&#FFFFFF");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "▰" : "&#555555▱&#FFFFFF");
        sb.append(" ").append(cur * 100 / Math.max(1, max)).append("%");
        return sb.toString();
    }

    // ── 被動：Corpus 護體（減弱火/毒/凋零 debuff）───────────────────────────────

    private void startPassiveTick() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack main = p.getInventory().getItemInMainHand();
                if (!plugin.hasItemId(main, "tibia")) continue;

                // 凋零 / 燃燒：降階（POISON 不再是 bleed 載體，交回 vanilla 處理）
                weaken(p, PotionEffectType.WITHER);
                if (p.getFireTicks() > 20) p.setFireTicks(Math.max(20, p.getFireTicks() / 2));
            }
        }, 20L, 20L);
    }

    private void weaken(Player p, PotionEffectType type) {
        PotionEffect eff = p.getPotionEffect(type);
        if (eff == null) return;
        int newAmp = eff.getAmplifier() / 2;
        int newDur = Math.max(20, eff.getDuration() - 40);
        p.removePotionEffect(type);
        if (newAmp >= 0) {
            p.addPotionEffect(new PotionEffect(type, newDur, newAmp, eff.isAmbient(),
                    eff.hasParticles(), eff.hasIcon()));
        }
    }

    // ── 工具 ─────────────────────────────────────────────────────────────────

    private void dealTrueDamage(LivingEntity target, double dmg, Player attacker) {
        if (dmg <= 0) return;
        double absorb = target.getAbsorptionAmount();
        if (absorb > 0) {
            double used = Math.min(absorb, dmg);
            target.setAbsorptionAmount(absorb - used);
            dmg -= used;
        }
        if (dmg <= 0) return;
        double newHp = Math.max(0.0, target.getHealth() - dmg);
        if (newHp <= 0.0 && attacker != null) {
            try { target.setKiller(attacker); } catch (Throwable ignored) {}
        }
        target.setHealth(newHp);
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                target.getLocation().add(0, 1.0, 0), 4, 0.2, 0.3, 0.2, 0.05);
    }

    private static Vector rotateY(Vector v, double rad) {
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }
}
