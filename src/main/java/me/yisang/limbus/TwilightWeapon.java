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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 薄暝 Twilight — 終末鳥 E.G.O。
 * 近戰刀（鑽石劍）。忠於原作：
 *  - 瀕死增傷：HP 越低傷害越高（滿血 ×1.0 → 空血 ×2.5，線性）。
 *  - 部分真實傷害：30% 傷害無視盔甲/抗性（直接扣吸收→生命）。
 *  - 超長射程：entity_interaction_range +1.5。
 *  - 潛行右鍵蓄力 1.5 秒 → 暮光斬（前方扇形波，穿透多敵 + 凋零 + 羽毛粒子）。
 */
public class TwilightWeapon implements EGOWeapon, Listener {

    private static final double TRUE_FRACTION = 0.30;   // 30% 真實傷害
    private static final double MAX_LOWHP_BONUS = 1.5;  // 空血額外 +150% → ×2.5
    private static final long SPECIAL_COOLDOWN_MS = 6000L;
    private static final int CHARGE_TICKS = 30;         // 1.5 秒

    private final LimbusEGOWeapons plugin;
    private final Map<UUID, Long> specialCooldown = new HashMap<>();
    private final Set<UUID> charging = new HashSet<>();

    public TwilightWeapon(LimbusEGOWeapons plugin) { this.plugin = plugin; }

    @Override
    public String getId() { return "twilight"; }

    // ── 物品 ─────────────────────────────────────────────────────────────────

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes("&#FFD700薄暝"));
            meta.setLore(List.of(
                    plugin.translateHexColorCodes("&7終末將至，黃昏執刃。"),
                    plugin.translateHexColorCodes("&8越接近死亡，斬擊越致命；部分傷害無視防禦。"),
                    plugin.translateHexColorCodes("&8潛行右鍵：蓄力暮光斬（前方扇形波）")));
            meta.setCustomModelData(1009);
            meta.setUnbreakable(true);
            meta.setItemModel(NamespacedKey.fromString("twilight:twilight"));
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(new NamespacedKey(plugin, "twilight_dmg"),
                            9.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(new NamespacedKey(plugin, "twilight_spd"),
                            -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(new NamespacedKey(plugin, "twilight_reach"),
                            1.5, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(
                    plugin.getItemIdKey(), PersistentDataType.STRING, "twilight");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── 薄暝獲取包（終末鳥）───────────────────────────────────────────────────

    public ItemStack createApocalypseBirdPack(int amount) {
        ItemStack item = new ItemStack(Material.TRIAL_KEY, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes("&#FFD700終末鳥"));
            meta.setLore(List.of(
                    plugin.translateHexColorCodes("&7黃昏審判，終末降臨。"),
                    plugin.translateHexColorCodes("&8右鍵開啟以領受薄暝（需 1 格空位，開後消失）")));
            meta.setItemModel(NamespacedKey.fromString("twilight:apocalypse_bird"));
            meta.getPersistentDataContainer().set(
                    plugin.getItemIdKey(), PersistentDataType.STRING, "apocalypse_bird");
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openApocalypseBirdPack(Player player, ItemStack pack) {
        if (countFreeSlots(player) < 1) {
            player.sendActionBar(plugin.translateHexColorCodes("&#FF5555背包需 1 格空位才能開啟"));
            return;
        }
        if (pack.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else pack.setAmount(pack.getAmount() - 1);

        // 薄暝放 storage（slot 9-35），避免進主手
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        int slot = -1;
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) { slot = i; break; }
        }
        if (slot >= 0) inv.setItem(slot, createItem());
        else player.getWorld().dropItemNaturally(player.getLocation(), createItem());

        Location c = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.WHITE_ASH, c, 24, 0.5, 0.6, 0.5, 0.02);
        player.getWorld().spawnParticle(Particle.DUST, c, 12, 0.4, 0.5, 0.4,
                new Particle.DustOptions(Color.fromRGB(0x6C5B9E), 1.4f));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 0.8f, 0.6f);
        player.sendActionBar(plugin.translateHexColorCodes("&#6C5B9E薄暝 &7已領受"));
    }

    private int countFreeSlots(Player player) {
        int free = 0;
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) free++;
        }
        return free;
    }

    // ── 近戰：瀕死增傷 + 部分真實傷害 ───────────────────────────────────────────

    @Override
    public void handleMelee(EntityDamageByEntityEvent event, Player attacker) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        double mult = lowHpMultiplier(attacker);
        double base = event.getDamage();

        // 70% 走一般（受盔甲/抗性影響），整體乘上瀕死倍率
        event.setDamage(base * mult * (1.0 - TRUE_FRACTION));

        // 30% 真實傷害，下一 tick 套用（避開無敵幀；setHealth 無視盔甲/抗性）
        double trueDmg = base * mult * TRUE_FRACTION;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (target.isValid() && !target.isDead()) dealTrueDamage(target, trueDmg, attacker);
        });

        target.getWorld().spawnParticle(Particle.WHITE_ASH,
                target.getLocation().add(0, 1.0, 0), 8, 0.3, 0.4, 0.3, 0.01);
    }

    // ── 潛行右鍵：蓄力暮光斬 ────────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 終末鳥獲取包：右鍵開啟
        if (plugin.hasItemId(item, "apocalypse_bird")) {
            event.setCancelled(true);
            openApocalypseBirdPack(player, item);
            return;
        }

        if (!player.isSneaking()) return;
        if (!plugin.hasItemId(item, "twilight")) return;

        event.setCancelled(true);

        UUID uid = player.getUniqueId();
        if (charging.contains(uid)) return;

        long now = System.currentTimeMillis();
        long cd = specialCooldown.getOrDefault(uid, 0L);
        if (now < cd) {
            player.sendActionBar(plugin.translateHexColorCodes(
                    "&#8E7CC3暮光斬冷卻中…" + ((cd - now) / 1000 + 1) + "s"));
            return;
        }
        charging.add(uid);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.7f, 0.6f);

        // 蓄力：羽毛聚集粒子，1.5 秒後釋放
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!charging.contains(uid)) { cancel(); return; }
                if (!player.isOnline() || player.isDead()
                        || !plugin.hasItemId(player.getInventory().getItemInMainHand(), "twilight")) {
                    charging.remove(uid);
                    cancel();
                    return;
                }
                Location c = player.getLocation().add(0, 1.0, 0);
                player.getWorld().spawnParticle(Particle.WHITE_ASH, c, 6, 0.5, 0.5, 0.5, 0.01);
                player.getWorld().spawnParticle(Particle.DUST, c, 4, 0.4, 0.4, 0.4,
                        new Particle.DustOptions(Color.fromRGB(0x6C5B9E), 1.2f));
                t++;
                if (t >= CHARGE_TICKS) {
                    cancel();
                    charging.remove(uid);
                    specialCooldown.put(uid, System.currentTimeMillis() + SPECIAL_COOLDOWN_MS);
                    twilightSlash(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // 切槽/離線中止暮光斬蓄力(不寫入冷卻)
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

    private void twilightSlash(Player player) {
        double mult = lowHpMultiplier(player);
        double baseDmg = 14.0 * mult;
        double range = 6.0;
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0e-6) look = new Vector(0, 0, 1);
        look.normalize();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.7f);

        // 扇形劍氣：以玩家腰部為中心,沿弧線每 5° 一片 SWEEP_ATTACK,前緣加細塵勾邊
        Location origin = player.getLocation().add(0, 1.1, 0);
        for (double deg = -55; deg <= 55; deg += 5) {
            Vector dir = rotateY(look.clone(), Math.toRadians(deg));
            // 沿弧線 3 段推進,形成有厚度的劍氣扇面
            for (double d = 1.2; d <= range; d += 1.6) {
                Location p = origin.clone().add(dir.clone().multiply(d));
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0, 0, 0, 0);
            }
            // 扇面外緣:一道細灰塵勾邊,強化「刀鋒劃過」的線條感
            Location edge = origin.clone().add(dir.clone().multiply(range));
            player.getWorld().spawnParticle(Particle.DUST, edge, 3, 0.15, 0.15, 0.15, 0,
                    new Particle.DustOptions(Color.fromRGB(0xE6D9FF), 1.0f));
        }

        // 標記為自訂傷害，避免 target.damage 再觸發 handleMelee 重複加成
        player.setMetadata("lsmp_custom_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        try {
            for (Entity e : player.getNearbyEntities(range, range, range)) {
                if (e.equals(player)) continue;
                if (!(e instanceof LivingEntity target)) continue;
                Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                if (to.lengthSquared() < 1.0e-6) continue;
                double angle = look.angle(to.normalize());
                if (angle > Math.toRadians(55)) continue; // 扇形 ±55°

                target.setNoDamageTicks(0); // 清無敵幀,確保常規部分不被吸收
                target.damage(baseDmg * (1.0 - TRUE_FRACTION), player);
                double trueDmg = baseDmg * TRUE_FRACTION;
                if (target.isValid() && !target.isDead()) dealTrueDamage(target, trueDmg, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));

                // Limbus 破裂：受擊時消耗 → potency × 2 真傷
                if (plugin.getStatusManager() != null && target.isValid() && !target.isDead()) {
                    plugin.getStatusManager().apply(target,
                            me.yisang.limbus.status.StatusEffect.RUPTURE, 5, 2, player);
                }
            }
        } finally {
            player.removeMetadata("lsmp_custom_damage", plugin);
        }
    }

    // ── 工具 ─────────────────────────────────────────────────────────────────

    /** 瀕死倍率：滿血 ×1.0 → 空血 ×(1+MAX_LOWHP_BONUS)，線性。 */
    private double lowHpMultiplier(Player player) {
        var maxAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        double frac = Math.max(0.0, Math.min(1.0, player.getHealth() / max));
        return 1.0 + MAX_LOWHP_BONUS * (1.0 - frac);
    }

    /**
     * 真實傷害：先扣吸收再扣生命，無視盔甲與抗性。
     * 若這一擊會致死，先寫入 killer 讓 EntityDeathEvent 能認到玩家（掉落表、經驗、統計）。
     */
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
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0),
                6, 0.2, 0.3, 0.2, 0.05);
    }

    private static Vector rotateY(Vector v, double rad) {
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }
}
