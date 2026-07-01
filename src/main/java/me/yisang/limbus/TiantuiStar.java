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
import org.bukkit.event.player.PlayerQuitEvent;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 天退星刀 — 居合衝刺。
 * 近戰刀（下界合金劍）。消耗實體子彈（火藥）發動定身蓄力 → 向前衝刺，
 * 衝刺路徑上的敵人受傷（無投射物，子彈僅為助推火藥）。
 *  - 右鍵（虎標彈）：蓄力 1 秒 → 中速中距衝刺，路徑傷 8。
 *  - 潛行右鍵（猛虎標彈）：蓄力 3 秒 → 更快更遠衝刺，路徑傷 18 + 凋零 II。
 *  - 蓄力期間定身（重緩速），受擊中斷且不消耗子彈。
 */
public class TiantuiStar implements EGOWeapon, Listener {

    private final LimbusEGOWeapons plugin;
    private final Map<UUID, Charge> charging = new HashMap<>();
    // 受擊後短暫忽略 onMove 的擊退位移,避免蓄力被打即中斷
    private final Map<UUID, Long> ignoreMoveUntil = new HashMap<>();
    private static final long HIT_IGNORE_MOVE_MS = 500L;

    public TiantuiStar(LimbusEGOWeapons plugin) { this.plugin = plugin; }

    private record Charge(boolean savage, BukkitTask task) {}

    @Override
    public String getId() { return "tiantui_star"; }

    // ── 物品 ─────────────────────────────────────────────────────────────────

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes("&#E67E22天退星刀"));
            meta.setLore(List.of(
                    plugin.translateHexColorCodes("&7填入虎標彈，蓄勢，化作奔虎。"),
                    plugin.translateHexColorCodes("&8右鍵：虎標彈衝刺　潛行右鍵：猛虎標彈衝刺")));
            meta.setCustomModelData(1008);
            meta.setUnbreakable(true);
            meta.setItemModel(NamespacedKey.fromString("tiantui_star:tiantui_star"));
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(new NamespacedKey(plugin, "tiantui_dmg"),
                            8.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(new NamespacedKey(plugin, "tiantui_spd"),
                            -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(
                    plugin.getItemIdKey(), PersistentDataType.STRING, "tiantui_star");
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createTigerMark(int amount) {
        return buildAmmo(amount, "tiger_mark", "&#E67E22虎標彈",
                "&7助推填充火藥。");
    }

    public ItemStack createSavageTigerMark(int amount) {
        return buildAmmo(amount, "savage_tiger_mark", "&#C0392B猛虎標彈",
                "&7更猛烈的助推火藥。");
    }

    private ItemStack buildAmmo(int amount, String id, String name, String lore) {
        ItemStack item = new ItemStack(Material.GUNPOWDER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes(name));
            meta.setLore(List.of(plugin.translateHexColorCodes(lore)));
            meta.setItemModel(NamespacedKey.fromString("tiantui_star:" + id));
            meta.getPersistentDataContainer().set(
                    plugin.getItemIdKey(), PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── 插翅虎組合包 ───────────────────────────────────────────────────────────

    public ItemStack createChatuhuPack(int amount) {
        ItemStack item = new ItemStack(Material.TRIAL_KEY, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes("&#E67E22插翅虎"));
            meta.setLore(List.of(
                    plugin.translateHexColorCodes("&7天退星刀 + 10 猛虎標彈 + 20 虎標彈"),
                    plugin.translateHexColorCodes("&8右鍵開啟（需 4 格空位，開後消失）")));
            meta.setItemModel(NamespacedKey.fromString("tiantui_star:lei"));
            meta.getPersistentDataContainer().set(
                    plugin.getItemIdKey(), PersistentDataType.STRING, "chatuhu");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── 近戰：命中有生命值的實體才播自訂揮刀音 ────────────────────────────────

    @Override
    public void handleMelee(EntityDamageByEntityEvent event, Player attacker) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        attacker.getWorld().playSound(attacker.getLocation(),
                "tiantui_star:tiantui.slash", 1.0f, 1.0f);
    }

    // ── 右鍵：開始定身蓄力 / 開啟插翅虎組合包 ──────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 插翅虎組合包：右鍵開啟
        if (plugin.hasItemId(item, "chatuhu")) {
            event.setCancelled(true);
            openChatuhuPack(player, item);
            return;
        }

        if (!plugin.hasItemId(item, "tiantui_star")) return;

        event.setCancelled(true);
        if (charging.containsKey(player.getUniqueId())) return; // 已在蓄力

        boolean savage = player.isSneaking();
        String ammoId = savage ? "savage_tiger_mark" : "tiger_mark";
        if (!hasAmmo(player, ammoId)) {
            player.sendActionBar(plugin.translateHexColorCodes(
                    savage ? "&#FF5555缺少猛虎標彈" : "&#FF5555缺少虎標彈"));
            return;
        }
        startCharge(player, savage);
    }

    private void startCharge(Player player, boolean savage) {
        int ticks = savage ? 60 : 20;
        // 定身：重緩速（不顯示圖示/粒子）
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, ticks + 2, 200, false, false, false));

        // 蓄力音效：
        //  - 普通：單次播 charge_tiger
        //  - 猛擊：3 段重疊。t=0 播 1（時長 ~2.0s），t=20（1 播到一半 1.0s）接 2，
        //          t=35（2 播到一半 0.75s 後）接 3。配對 3 秒蓄力。
        if (!savage) {
            player.getWorld().playSound(player.getLocation(),
                    "tiantui_star:tiantui.charge_tiger", 0.9f, 1.0f);
        } else {
            player.getWorld().playSound(player.getLocation(),
                    "tiantui_star:tiantui.charge_savage_1", 0.9f, 1.0f);
        }

        BukkitTask task = new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    charging.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                // 猛擊蓄力的後續段
                if (savage) {
                    if (t == 20) {
                        player.getWorld().playSound(player.getLocation(),
                                "tiantui_star:tiantui.charge_savage_2", 0.9f, 1.0f);
                    } else if (t == 35) {
                        player.getWorld().playSound(player.getLocation(),
                                "tiantui_star:tiantui.charge_savage_3", 0.9f, 1.0f);
                    }
                }
                // 蓄力環狀粒子
                player.getWorld().spawnParticle(savage ? Particle.FLAME : Particle.CRIT,
                        player.getLocation().add(0, 1.0, 0), savage ? 6 : 3,
                        0.4, 0.4, 0.4, 0.01);
                // 蓄力進度條 (每 2 tick 更新一次)
                if (t % 2 == 0) {
                    int filled = (int) Math.round((double) t / ticks * 10);
                    StringBuilder bar = new StringBuilder();
                    for (int i = 0; i < 10; i++) bar.append(i < filled ? '▮' : '▯');
                    player.sendActionBar(plugin.translateHexColorCodes(
                            (savage ? "&#C0392B猛虎標彈 " : "&#E67E22虎標彈 ") + bar));
                }
                t++;
                if (t >= ticks) {
                    cancel();
                    charging.remove(player.getUniqueId());
                    ignoreMoveUntil.remove(player.getUniqueId());
                    fireDash(player, savage);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        charging.put(player.getUniqueId(), new Charge(savage, task));
    }

    // ── 插翅虎開啟邏輯 ──────────────────────────────────────────────────────────

    private void openChatuhuPack(Player player, ItemStack pack) {
        // 需 4 格空位（刀、猛虎標彈、虎標彈、組合包暫存／緩衝）
        if (countFreeSlots(player) < 4) {
            player.sendActionBar(plugin.translateHexColorCodes(
                    "&#FF5555背包需 4 格空位才能開啟插翅虎"));
            return;
        }

        // 消耗 1 個組合包
        if (pack.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            pack.setAmount(pack.getAmount() - 1);
        }

        // 先給彈藥（addItem 預設從 hotbar 0 開始找空位）
        player.getInventory().addItem(createSavageTigerMark(10), createTigerMark(20));

        // 刀放 storage（slot 9-35），避免進主手後立刻觸發蓄力；storage 滿才退而求其次
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        int swordSlot = -1;
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) { swordSlot = i; break; }
        }
        if (swordSlot < 0) {
            int held = inv.getHeldItemSlot();
            for (int i = 0; i < 9; i++) {
                if (i == held) continue;
                ItemStack s = inv.getItem(i);
                if (s == null || s.getType().isAir()) { swordSlot = i; break; }
            }
        }
        if (swordSlot >= 0) inv.setItem(swordSlot, createItem());
        else player.getWorld().dropItemNaturally(player.getLocation(), createItem());

        // 無聲開啟，保留粒子提示
        player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                player.getLocation().add(0, 1.0, 0), 16, 0.4, 0.4, 0.4, 0.02);
        player.sendActionBar(plugin.translateHexColorCodes(
                "&#E67E22插翅虎 &7已開啟 &8(天退星刀 + 10 猛 + 20 普)"));
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

    // ── 中斷條件：改變手持 / 移動 / 猛版放開蹲下；受擊不中斷 ───────────────────

    private void cancelCharge(Player player) {
        Charge c = charging.remove(player.getUniqueId());
        if (c == null) return;
        c.task().cancel();
        ignoreMoveUntil.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.stopSound("tiantui_star:tiantui.charge_tiger");
        player.stopSound("tiantui_star:tiantui.charge_savage_1");
        player.stopSound("tiantui_star:tiantui.charge_savage_2");
        player.stopSound("tiantui_star:tiantui.charge_savage_3");
        player.sendActionBar(plugin.translateHexColorCodes("&#FF5555蓄力中斷"));
    }

    // 改變 hotbar 選中欄位
    @EventHandler
    public void onItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        if (charging.containsKey(event.getPlayer().getUniqueId())) cancelCharge(event.getPlayer());
    }

    // F 鍵交換主副手
    @EventHandler
    public void onSwapHand(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (charging.containsKey(event.getPlayer().getUniqueId())) cancelCharge(event.getPlayer());
    }

    // 猛版蓄力中放開蹲下：取消
    @EventHandler
    public void onToggleSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        Charge c = charging.get(event.getPlayer().getUniqueId());
        if (c == null || !c.savage()) return;
        if (!event.isSneaking()) cancelCharge(event.getPlayer());
    }

    // 水平移動取消（忽略視角、Y 軸掉落／彈跳、以及受擊擊退窗口）
    @EventHandler
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!charging.containsKey(id)) return;
        if (event.getTo() == null) return;
        Long until = ignoreMoveUntil.get(id);
        if (until != null && System.currentTimeMillis() < until) return;
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        if (dx * dx + dz * dz > 0.01) cancelCharge(event.getPlayer());
    }

    // 蓄力中被打:開啟短暫的 onMove 忽略窗口,吸收擊退位移
    @EventHandler
    public void onDamaged(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!charging.containsKey(p.getUniqueId())) return;
        ignoreMoveUntil.put(p.getUniqueId(), System.currentTimeMillis() + HIT_IGNORE_MOVE_MS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Charge c = charging.remove(id);
        if (c != null) c.task().cancel();
        ignoreMoveUntil.remove(id);
    }

    // ── 衝刺 ────────────────────────────────────────────────────────────────────

    private void fireDash(Player player, boolean savage) {
        String ammoId = savage ? "savage_tiger_mark" : "tiger_mark";
        if (!consumeAmmo(player, ammoId)) return; // 安全檢查：彈藥沒了就不衝
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        Vector dir = player.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-6) dir = new Vector(0, 0, 1);
        dir.normalize();

        final double speed = savage ? 1.85 : 1.2;
        final int duration = savage ? 10 : 8;
        final double damage = savage ? 18.0 : 8.0;
        final Vector vel = dir.clone().multiply(speed);
        final Set<UUID> hitOnce = new HashSet<>();
        final boolean[] firstSlash = {false};

        player.getWorld().playSound(player.getLocation(),
                "tiantui_star:tiantui.dash", 1.0f, savage ? 0.85f : 1.0f);

        new BukkitRunnable() {
            int t = 0;
            Location lastLoc = player.getLocation();
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) { cancel(); return; }

                // 撞牆偵測:第一 tick 之後,若水平位移過小視為撞牆,提早結束
                if (t > 0) {
                    double dx = player.getLocation().getX() - lastLoc.getX();
                    double dz = player.getLocation().getZ() - lastLoc.getZ();
                    if (dx * dx + dz * dz < 0.04) { cancel(); return; }
                }
                lastLoc = player.getLocation();

                player.setVelocity(vel);

                Location at = player.getLocation().add(0, 1.0, 0);
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1);
                player.getWorld().spawnParticle(savage ? Particle.FLAME : Particle.CRIT,
                        at, savage ? 8 : 4, 0.3, 0.3, 0.3, 0.02);

                for (Entity ent : player.getNearbyEntities(1.8, 1.5, 1.8)) {
                    if (ent.equals(player)) continue;
                    if (!(ent instanceof LivingEntity target)) continue;
                    if (!hitOnce.add(ent.getUniqueId())) continue;

                    // 第一個碰到的目標：先全力揮砍（玩家劈砍行為，含暴擊/橫掃/附魔）
                    if (!firstSlash[0]) {
                        firstSlash[0] = true;
                        player.attack(target);
                        target.setNoDamageTicks(0); // 解除無敵幀以套用衝刺傷害
                    }

                    // 衝刺傷害（metadata 防止再觸發近戰邏輯）
                    player.setMetadata("lsmp_custom_damage", new FixedMetadataValue(plugin, true));
                    try {
                        target.damage(damage, player);
                        target.setNoDamageTicks(0);
                    } finally {
                        player.removeMetadata("lsmp_custom_damage", plugin);
                    }

                    Vector kb = vel.clone().multiply(0.4);
                    kb.setY(0.25);
                    target.setVelocity(kb);
                    // 燃燒效果：普通 3 秒、猛擊 5 秒
                    target.setFireTicks(Math.max(target.getFireTicks(), savage ? 100 : 60));
                    if (savage) target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));

                    // Limbus 屬性：震顫 + 燒傷（猛擊倍量）
                    if (plugin.getStatusManager() != null) {
                        int tremorP = savage ? 8 : 5;
                        int burnP   = savage ? 6 : 4;
                        int burnC   = savage ? 4 : 3;
                        plugin.getStatusManager().apply(target,
                                me.yisang.limbus.status.StatusEffect.TREMOR, tremorP, 6, player);
                        plugin.getStatusManager().apply(target,
                                me.yisang.limbus.status.StatusEffect.BURN, burnP, burnC, player);
                    }

                    target.getWorld().playSound(target.getLocation(),
                            Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, savage ? 0.7f : 1.0f);
                }

                t++;
                if (t >= duration) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── 彈藥工具 ────────────────────────────────────────────────────────────────

    private boolean hasAmmo(Player player, String id) {
        for (ItemStack i : player.getInventory().getContents()) {
            if (plugin.hasItemId(i, id)) return true;
        }
        return false;
    }

    private boolean consumeAmmo(Player player, String id) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (!plugin.hasItemId(it, id)) continue;
            if (it.getAmount() <= 1) inv.setItem(i, null);
            else it.setAmount(it.getAmount() - 1);
            return true;
        }
        return false;
    }
}
