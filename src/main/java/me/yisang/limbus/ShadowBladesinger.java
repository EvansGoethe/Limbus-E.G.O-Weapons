package me.yisang.limbus;

import me.yisang.limbus.status.StatusEffect;
import me.yisang.limbus.status.StatusManager;
import me.yisang.limbus.status.StatusState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 著影揮刀 — Meursault 的居合刀。
 * 每次命中對自己累積 POISE（呼吸法）；隨層數提高爆擊機率。
 * 慢速大劍手感：低攻速、高基礎傷、靠呼吸法賭爆擊。
 */
public class ShadowBladesinger implements EGOWeapon, Listener {
    private static final int POISE_POTENCY_CAP = 10; // 50% 爆擊率上限（5% × 10）
    private static final int POISE_PER_HIT = 1;
    private static final int POISE_COUNT_PER_HIT = 4;

    // 肉斬骨斷
    private static final double LOW_HP_THRESHOLD = 6.0;        // 3 顆心
    private static final int SLASH_COUNT = 5;                  // 五連斬
    private static final int SLASH_INTERVAL_TICKS = 4;         // 每 4 tick 一刀，共 20 tick 打完
    private static final double SLASH_DAMAGE = 7.0;            // 每刀基礎
    private static final long SLASH_COOLDOWN_MS = 12_000L;

    private final LimbusEGOWeapons plugin;
    private final Map<UUID, Long> slashCooldown = new HashMap<>();

    public ShadowBladesinger(LimbusEGOWeapons plugin) { this.plugin = plugin; }

    @Override
    public String getId() { return "bladesinger"; }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes("&#AEDBFF著影揮刀"));
            meta.setLore(List.of(
                    plugin.translateHexColorCodes("&#D0E7FF望月斬首──就此，氣絕吧。")));
            meta.setCustomModelData(1012);
            meta.setUnbreakable(true);
            meta.setItemModel(NamespacedKey.fromString("shadow_vested_bladesinger:shadow_vested_bladesinger"));
            // 居合手感：高基礎傷、慢攻速
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(new NamespacedKey(plugin, "bladesinger_dmg"),
                            9.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(new NamespacedKey(plugin, "bladesinger_spd"),
                            -2.6, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(
                    plugin.getItemIdKey(), PersistentDataType.STRING, "bladesinger");
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleMelee(EntityDamageByEntityEvent event, Player attacker) {
        StatusManager sm = plugin.getStatusManager();
        if (sm == null) return;
        StatusState s = sm.get(attacker);
        int cur = s == null ? 0 : s.potency(StatusEffect.POISE);
        if (cur < POISE_POTENCY_CAP) {
            sm.apply(attacker, StatusEffect.POISE, POISE_PER_HIT, POISE_COUNT_PER_HIT, attacker);
        } else {
            sm.refresh(attacker, StatusEffect.POISE, POISE_COUNT_PER_HIT);
        }
    }

    // ── 肉斬骨斷 五連斬（低血蹲下右鍵實體觸發） ──────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!plugin.hasItemId(player.getInventory().getItemInMainHand(), "bladesinger")) return;
        if (!(event.getRightClicked() instanceof LivingEntity target)) return;
        if (!player.isSneaking()) return;
        if (player.getHealth() >= LOW_HP_THRESHOLD) return;

        event.setCancelled(true);

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cd = slashCooldown.getOrDefault(uid, 0L);
        if (now < cd) {
            player.sendActionBar(plugin.translateHexColorCodes(
                    "&#AEDBFF肉斬骨斷冷卻中…" + ((cd - now) / 1000 + 1) + "s"));
            return;
        }
        slashCooldown.put(uid, now + SLASH_COOLDOWN_MS);

        player.sendActionBar(plugin.translateHexColorCodes("&#AEDBFF&l✦ 肉斬骨斷"));
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.6f);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.7f);
        // 站定：強緩速 + 每 tick 拉回原位
        int totalTicks = SLASH_INTERVAL_TICKS * SLASH_COUNT + 4;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, totalTicks, 6, true, false, true));
        final Location anchor = player.getLocation();

        new BukkitRunnable() {
            int hit = 0;
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) { cancel(); return; }
                if (!plugin.hasItemId(player.getInventory().getItemInMainHand(), "bladesinger")) {
                    cancel(); return;
                }
                if (!target.isValid() || target.isDead()) { cancel(); return; }

                // 定身：把玩家釘在 anchor
                player.setVelocity(new Vector(0, 0, 0));
                Location p = player.getLocation();
                if (p.distanceSquared(anchor) > 0.09) {
                    Location keep = anchor.clone();
                    keep.setYaw(p.getYaw());
                    keep.setPitch(p.getPitch());
                    player.teleport(keep);
                }

                // 揮斬劍氣：從玩家腰部向目標方向拉一道 SWEEP_ATTACK
                Location origin = anchor.clone().add(0, 1.1, 0);
                Vector dir = target.getLocation().toVector().subtract(anchor.toVector()).normalize();
                for (double d = 0.6; d <= 3.5; d += 0.35) {
                    Location at = origin.clone().add(dir.clone().multiply(d));
                    player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1);
                }
                player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0),
                        6, 0.3, 0.3, 0.3, 0.1);

                // 攻擊：標記為自訂傷害以便跑正常結算（POISE 爆擊可觸發）
                player.setMetadata("lsmp_custom_damage", new FixedMetadataValue(plugin, true));
                try {
                    target.damage(SLASH_DAMAGE, player);
                    target.setNoDamageTicks(0);
                } finally {
                    player.removeMetadata("lsmp_custom_damage", plugin);
                }
                player.getWorld().playSound(target.getLocation(),
                        Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.3f + hit * 0.05f);

                hit++;
                if (hit >= SLASH_COUNT) cancel();
            }
        }.runTaskTimer(plugin, 0L, SLASH_INTERVAL_TICKS);
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        slashCooldown.remove(event.getPlayer().getUniqueId());
    }
}
