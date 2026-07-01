package me.yisang.limbus;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ringbrush implements EGOWeapon, Listener {
    private final LimbusEGOWeapons plugin;
    private final Map<UUID, TargetInfo> lastHitTargets = new HashMap<>();
    private final Random random = new Random();

    private final PotionEffectType[] negativeEffects = {
            PotionEffectType.BLINDNESS, PotionEffectType.SLOWNESS,
            PotionEffectType.POISON,    PotionEffectType.WEAKNESS,
            PotionEffectType.WITHER
    };

    /** Limbus 隨機池：全屬性中排除會反手 buff 敵人的（強壯 / 守護 / 迅捷）。 */
    private static final me.yisang.limbus.status.StatusEffect[] LIMBUS_POOL = {
            me.yisang.limbus.status.StatusEffect.BLEED,
            me.yisang.limbus.status.StatusEffect.BURN,
            me.yisang.limbus.status.StatusEffect.FRAGILE,
            me.yisang.limbus.status.StatusEffect.SEDUCTION,
            me.yisang.limbus.status.StatusEffect.RUPTURE,
            me.yisang.limbus.status.StatusEffect.TREMOR,
            me.yisang.limbus.status.StatusEffect.BIND,
    };

    public ringbrush(LimbusEGOWeapons plugin) { this.plugin = plugin; }

    @Override
    public String getId() { return "brush"; }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes("&#FFFFFF環指筆刷"));
            meta.setLore(List.of(plugin.translateHexColorCodes("&#FF9500不及格。")));
            meta.setCustomModelData(1001);
            meta.setUnbreakable(true);
            meta.setItemModel(NamespacedKey.fromString("ringbrush:ring_brush"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(
                    plugin.getItemIdKey(), PersistentDataType.STRING, "brush");
            item.setItemMeta(meta);
        }
        return item;
    }

    public void handleInteractEntity(Player player, LivingEntity target) {
        long now = System.currentTimeMillis();
        lastHitTargets.entrySet().removeIf(e -> now - e.getValue().timeMillis() > 1500);

        UUID playerUUID = player.getUniqueId();

        if (lastHitTargets.containsKey(playerUUID)) {
            TargetInfo info = lastHitTargets.get(playerUUID);
            if (now - info.timeMillis() < 1500 && info.targetUUID().equals(target.getUniqueId())) {
                applyEffect(player, target, 2);
                lastHitTargets.remove(playerUUID);
                return;
            }
        }

        applyEffect(player, target, 1);
        player.setVelocity(player.getLocation().getDirection().multiply(1.2).setY(0.2));
        lastHitTargets.put(playerUUID, new TargetInfo(target.getUniqueId(), now));
    }

    private void applyEffect(Player player, LivingEntity target, int times) {
        for (int i = 0; i < times; i++) {
            target.addPotionEffect(new PotionEffect(
                    negativeEffects[random.nextInt(negativeEffects.length)], 80, 1));
            target.damage(3.5, player);
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 20,
                    new Particle.DustOptions(
                            Color.fromRGB(random.nextInt(255), random.nextInt(100), random.nextInt(100)),
                            1.5f));

            // Limbus 隨機池：一擊 1 potency / 3 count；雙擊命中會兩擊各抽一次
            if (plugin.getStatusManager() != null) {
                me.yisang.limbus.status.StatusEffect pick = LIMBUS_POOL[random.nextInt(LIMBUS_POOL.length)];
                plugin.getStatusManager().apply(target, pick, 1, 3, player);
            }
        }
    }

    private record TargetInfo(UUID targetUUID, long timeMillis) {}
}
