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
        // 以「飛行中蝴蝶」扁平模型作為投射物外觀
        ItemStack projectileItem = new ItemStack(Material.PAPER);
        ItemMeta projMeta = projectileItem.getItemMeta();
        if (projMeta != null) {
            projMeta.setItemModel(NamespacedKey.fromString("solemnlament:butterflies_hit"));
            projectileItem.setItemMeta(projMeta);
        }

        org.bukkit.Location spawnLoc = player.getEyeLocation();
        final org.bukkit.util.Vector initialVel = player.getLocation().getDirection().multiply(3.0);
        final boolean isBlack = model.contains("black");

        org.bukkit.entity.ItemDisplay display = player.getWorld().spawn(
                spawnLoc, org.bukkit.entity.ItemDisplay.class, d -> {
            d.setItemStack(projectileItem);
            d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            // 短插值讓 teleport 之間視覺平滑
            d.setInterpolationDuration(1);
            d.setTeleportDuration(1);
            applyVelocityRotation(d, initialVel);
        });
        display.setMetadata("solemn_arrow",
                new FixedMetadataValue(plugin, isBlack ? "black" : "white"));

        new BukkitRunnable() {
            private final org.bukkit.util.Vector vel = initialVel.clone();
            private int ticksAlive = 0;

            @Override
            public void run() {
                if (!display.isValid() || display.isDead()) {
                    this.cancel();
                    return;
                }

                ticksAlive++;
                if (ticksAlive > 100) { // 5 秒後自動移除
                    display.remove();
                    this.cancel();
                    return;
                }

                // 粒子軌跡
                display.getWorld().spawnParticle(Particle.SQUID_INK, display.getLocation(), 2, 0.02, 0.02, 0.02, 0.01);
                display.getWorld().spawnParticle(Particle.WHITE_ASH, display.getLocation(), 4, 0.05, 0.05, 0.05, 0.01);

                // 命中偵測：掃描 0.8 格內的生物
                for (org.bukkit.entity.Entity e : display.getNearbyEntities(0.8, 0.8, 0.8)) {
                    if (!(e instanceof LivingEntity target)) continue;
                    if (e.equals(player)) continue;

                    display.getWorld().playSound(display.getLocation(), "solemnlament:solemn.hit", 1.0f, 1.0f);
                    display.getWorld().spawnParticle(Particle.SQUID_INK, display.getLocation(), 15, 0.1, 0.1, 0.1, 0.05);

                    if (isBlack) {
                        target.damage(8.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
                        if (plugin.getStatusManager() != null) {
                            plugin.getStatusManager().apply(target,
                                    me.yisang.limbus.status.StatusEffect.SINKING, 4, 3, player);
                        }
                    } else {
                        target.damage(4.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        if (plugin.getStatusManager() != null) {
                            plugin.getStatusManager().apply(target,
                                    me.yisang.limbus.status.StatusEffect.SINKING, 3, 2, player);
                        }
                    }

                    display.remove();
                    this.cancel();
                    return;
                }

                // 移動 + 落地偵測：下個位置是固體方塊就爆粒子並消失
                org.bukkit.Location next = display.getLocation().add(vel);
                if (next.getBlock().getType().isSolid()) {
                    display.getWorld().spawnParticle(Particle.SQUID_INK, next, 8, 0.1, 0.1, 0.1, 0.05);
                    display.remove();
                    this.cancel();
                    return;
                }

                display.teleport(next);
                // velocity 常數，旋轉在生成時算一次即可，這裡不再重算避免抖動
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), "solemnlament:solemn.shoot", 0.8f, 1.0f);
    }

    /**
     * 讓 ItemDisplay 的物品「頭朝 velocity 方向、翅膀水平」。
     *
     * generated item 在 FIXED transform 下，貼圖是一片朝 +Z 的 quad、頂端指向 +Y。
     * 我們用 (forward=velocity, up=world+Y) 建立正交基底：
     *   +Z 對齊 forward → 蝴蝶頭朝飛行方向
     *   +Y 對齊世界上 → 翅膀不繞飛行軸自轉（消除 roll 抖動）
     * 垂直射擊時（forward ≈ ±Y）退化，改用世界 +Z 當 up 避免叉積歸零。
     */
    private void applyVelocityRotation(org.bukkit.entity.ItemDisplay display, org.bukkit.util.Vector velocity) {
        if (velocity.lengthSquared() < 1e-6) return;
        org.bukkit.util.Vector d = velocity.clone().normalize();
        org.joml.Vector3f forward = new org.joml.Vector3f((float) d.getX(), (float) d.getY(), (float) d.getZ());

        org.joml.Vector3f worldUp = new org.joml.Vector3f(0, 1, 0);
        if (Math.abs(forward.dot(worldUp)) > 0.999f) worldUp.set(0, 0, 1);

        org.joml.Vector3f right = new org.joml.Vector3f(worldUp).cross(forward).normalize();
        org.joml.Vector3f up = new org.joml.Vector3f(forward).cross(right).normalize();

        // 3x3 rotation：欄向量 = (right, up, forward)，等同「local 座標 → world 座標」
        org.joml.Matrix3f m = new org.joml.Matrix3f(right, up, forward);
        org.joml.Quaternionf q = new org.joml.Quaternionf().setFromNormalized(m);

        org.bukkit.util.Transformation t = display.getTransformation();
        display.setTransformation(new org.bukkit.util.Transformation(
                t.getTranslation(), q, t.getScale(), t.getRightRotation()));
    }

    // ── 聖宣盾牌 Tick 邏輯（由 LimbusEGOWeapons.startShieldTick 每 5 tick 呼叫）──

    public void handleShieldTick(Player player, ItemStack item) {
        player.getWorld().spawnParticle(
                Particle.WHITE_ASH, player.getLocation().add(0, 1, 0),
                8, 0.4, 0.4, 0.4, 0.02);

        // 自身守護：上限 3 potency
        me.yisang.limbus.status.StatusManager sm = plugin.getStatusManager();
        if (sm != null) {
            me.yisang.limbus.status.StatusState s = sm.get(player);
            int cur = s == null ? 0 : s.potency(me.yisang.limbus.status.StatusEffect.PROTECTION);
            if (cur < 3) {
                sm.apply(player, me.yisang.limbus.status.StatusEffect.PROTECTION, 1, 40, player);
            }
        }

        player.getNearbyEntities(5, 5, 5).forEach(e -> {
            if (e instanceof LivingEntity target && !e.equals(player)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                if (sm != null) {
                    sm.apply(target, me.yisang.limbus.status.StatusEffect.BIND, 1, 2, player);
                }
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
        give(player, type, 1);
    }

    public void give(Player player, String type, int amount) {
        if (amount < 1) amount = 1;
        int remaining = amount;
        while (remaining > 0) {
            ItemStack item = createItem(type);
            if (item == null) return;
            int stack = Math.min(remaining, item.getMaxStackSize());
            item.setAmount(stack);
            player.getInventory().addItem(item);
            remaining -= stack;
        }
    }

    /** 為莊嚴哀悼弩附加「快速上弦 V」隱藏附魔（無 tooltip、無閃光）。 */
    private ItemStack applyHiddenQuickChargeV(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.QUICK_CHARGE, 5, true);
            meta.setEnchantmentGlintOverride(false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
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
