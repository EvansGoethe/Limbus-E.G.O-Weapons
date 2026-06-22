package me.yisang.limbus;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class solemnlament {
    private final LimbusEGOWeapons plugin;
    private final NamespacedKey ITEM_ID_KEY;

    public solemnlament(LimbusEGOWeapons plugin) {
        this.plugin = plugin;
        this.ITEM_ID_KEY = plugin.getItemIdKey();
    }

    // ── 識別工具 ─────────────────────────────────────────────────────────────

    public boolean hasId(ItemStack item, String id) {
        return plugin.hasItemId(item, id);
    }

    public boolean isButterfly(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) return false;
        return hasId(item, "butterfly");
    }

    public boolean isSolemnLament(ItemStack item) {
        return hasId(item, "solemn_lament");
    }

    public boolean hasButterflyQuartz(Player player) {
        return findButterflyQuartz(player) != null;
    }

    public ItemStack findButterflyQuartz(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isButterfly(item)) return item;
        }
        return null;
    }

    // ── 射擊邏輯 ──────────────────────────────────────────────────────────────

    public void handleShootManual(Player player, ItemStack bow, String model) {
        // 以蝴蝶石英外觀作為投射物實體
        ItemStack projectileItem = new ItemStack(Material.QUARTZ);
        ItemMeta projMeta = projectileItem.getItemMeta();
        if (projMeta != null) {
            projMeta.setItemModel(NamespacedKey.fromString("solemnlament:butterflies"));
            projMeta.setCustomModelData(1004);
            projectileItem.setItemMeta(projMeta);
        }

        Item thrown = player.getWorld().dropItem(player.getEyeLocation(), projectileItem);
        thrown.setPickupDelay(Integer.MAX_VALUE);
        thrown.setVelocity(player.getLocation().getDirection().multiply(3.0));

        boolean isBlack = model.contains("black");
        thrown.setMetadata("solemn_arrow", new FixedMetadataValue(plugin, isBlack ? "black" : "white"));

        new BukkitRunnable() {
            private int ticksAlive = 0;
            @Override
            public void run() {
                if (!thrown.isValid() || thrown.isDead()) {
                    this.cancel();
                    return;
                }

                ticksAlive++;
                if (ticksAlive > 100) { // 5 秒後自動移除
                    thrown.remove();
                    this.cancel();
                    return;
                }

                // 粒子軌跡
                thrown.getWorld().spawnParticle(Particle.SQUID_INK, thrown.getLocation(), 2, 0.02, 0.02, 0.02, 0.01);
                thrown.getWorld().spawnParticle(Particle.WHITE_ASH, thrown.getLocation(), 4, 0.05, 0.05, 0.05, 0.01);

                // 落地時爆出粒子並移除
                if (thrown.isOnGround()) {
                    thrown.getWorld().spawnParticle(Particle.SQUID_INK, thrown.getLocation(), 8, 0.1, 0.1, 0.1, 0.05);
                    thrown.remove();
                    this.cancel();
                    return;
                }

                // 命中偵測：掃描 0.8 格內的生物
                for (Entity e : thrown.getNearbyEntities(0.8, 0.8, 0.8)) {
                    if (!(e instanceof LivingEntity target)) continue;
                    if (e.equals(player)) continue;

                    thrown.getWorld().playSound(thrown.getLocation(), "solemnlament:solemn.hit", 1.0f, 1.0f);
                    thrown.getWorld().spawnParticle(Particle.SQUID_INK, thrown.getLocation(), 15, 0.1, 0.1, 0.1, 0.05);

                    if (isBlack) {
                        target.damage(8.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
                    } else {
                        target.damage(4.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    }

                    thrown.remove();
                    this.cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), "solemnlament:solemn.shoot", 0.8f, 1.0f);
    }

    // ── 聖宣盾牌 Tick 邏輯（由 LimbusEGOWeapons.startShieldTick 每 5 tick 呼叫）──

    public void handleShieldTick(Player player, ItemStack item) {
        player.getWorld().spawnParticle(
                Particle.WHITE_ASH, player.getLocation().add(0, 1, 0),
                8, 0.4, 0.4, 0.4, 0.02);
        player.getNearbyEntities(5, 5, 5).forEach(e -> {
            if (e instanceof LivingEntity target && !e.equals(player)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
        });
    }

    // ── 給予物品 ──────────────────────────────────────────────────────────────

    public ItemStack createItem(String type) {
        return switch (type.toLowerCase()) {
            case "black"       -> applyHiddenQuickChargeV(buildItem(Material.CROSSBOW, 1002,
                    "&#333333&l莊嚴哀悼",
                    "&x&F&F&F&F&F&F人&x&D&1&D&1&D&1死&x&A&3&A&3&A&3後&x&7&4&7&4&7&4會&x&4&6&4&6&4&6去&x&7&4&7&4&7&4往&x&A&3&A&3&A&3何&x&D&1&D&1&D&1方&x&F&F&F&F&F&F？",
                    "solemnlament:solemn_lament_black", "solemn_lament"));
            case "white"       -> applyHiddenQuickChargeV(buildItem(Material.CROSSBOW, 1003,
                    "&#FFFFFF&l莊嚴哀悼",
                    "&x&F&F&F&F&F&F人&x&D&1&D&1&D&1死&x&A&3&A&3&A&3後&x&7&4&7&4&7&4會&x&4&6&4&6&4&6去&x&7&4&7&4&7&4往&x&A&3&A&3&A&3何&x&D&1&D&1&D&1方&x&F&F&F&F&F&F？",
                    "solemnlament:solemn_lament_white", "solemn_lament"));
            case "butterflies" -> buildItem(Material.ARROW,  1004,
                    "&#FFFFFF生&#D8D8D8蝶&#B1B1B1、&#8A8A8A亡&#636363蝶",
                    "&x&F&F&F&F&F&F人&x&D&1&D&1&D&1死&x&A&3&A&3&A&3後&x&7&4&7&4&7&4會&x&4&6&4&6&4&6去&x&7&4&7&4&7&4往&x&A&3&A&3&A&3何&x&D&1&D&1&D&1方&x&F&F&F&F&F&F？",
                    "solemnlament:butterflies", "butterfly");
            case "shield"      -> buildItem(Material.SHIELD,  1005,
                    "&#FFFFFF&l聖宣",
                    "&x&F&F&F&F&F&F人&x&D&1&D&1&D&1死&x&A&3&A&3&A&3後&x&7&4&7&4&7&4會&x&4&6&4&6&4&6去&x&7&4&7&4&7&4往&x&A&3&A&3&A&3何&x&D&1&D&1&D&1方&x&F&F&F&F&F&F？",
                    "solemnlament:solemn_lament_shield", "solemn_shield");
            default -> null;
        };
    }

    public void give(Player player, String type) {
        ItemStack item = createItem(type);
        if (item != null) player.getInventory().addItem(item);
    }

    /** 為莊嚴哀悼弩附加「快速上弦 V」隱藏附魔（無 tooltip、無閃光）。 */
    private ItemStack applyHiddenQuickChargeV(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.QUICK_CHARGE, 5, true);
            meta.setEnchantmentGlintOverride(false);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildItem(Material material, int cmdData,
                                String name, String lore, String model, String id) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes(name));
            meta.setLore(List.of(plugin.translateHexColorCodes(lore)));
            meta.setCustomModelData(cmdData);
            meta.setItemModel(NamespacedKey.fromString(model));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(ITEM_ID_KEY, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }
}
