package me.yisang.limbus;

import org.bukkit.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LimbusEGOWeapons extends JavaPlugin implements Listener, TabCompleter {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private NamespacedKey ITEM_ID_KEY;

    private final Map<String, EGOWeapon> weaponModules = new HashMap<>();
    private final Map<UUID, Long> solemnCooldowns = new HashMap<>();
    private solemnlament solemn;
    private TiantuiStar tiantui;
    private SoundSuppressor soundSuppressor;

    private static final String PACK_URL  = "https://github.com/EvansGoethe/Limbus-E.G.O-weapon-plugin-ResourcePack/releases/download/2.8/Limbus_E.G.O_Weapons_plugin_ResourcePack.v.2.8.zip";
    private static final String PACK_HASH = "d2cee3b9c8a05fd48716285fd8d73473b016f517";
    private static final java.util.UUID PACK_UUID = java.util.UUID.nameUUIDFromBytes(
            PACK_URL.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4)
                             + Character.digit(hex.charAt(i * 2 + 1), 16));
        }
        return data;
    }

    // ── 公開工具方法 ────────────────────────────────────────────────────────────

    public NamespacedKey getItemIdKey() { return ITEM_ID_KEY; }
    public solemnlament getSolemn() { return solemn; }
    public TiantuiStar getTiantui() { return tiantui; }
    public EGOWeapon getWeaponModule(String id) { return weaponModules.get(id); }

    public boolean hasItemId(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        String value = item.getItemMeta()
                .getPersistentDataContainer()
                .get(ITEM_ID_KEY, PersistentDataType.STRING);
        return id.equals(value);
    }

    // ── 初始化 ──────────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        this.ITEM_ID_KEY = new NamespacedKey(this, "item_id");

        this.solemn = new solemnlament(this);
        mimicry    m  = new mimicry(this);
        dacapo     d  = new dacapo(this);
        ringbrush  r  = new ringbrush(this);
        this.tiantui = new TiantuiStar(this);

        weaponModules.put("mimicry", m);
        weaponModules.put("dacapo",  d);
        weaponModules.put("brush",   r);
        weaponModules.put("tiantui", tiantui);

        registerModule(m);
        registerModule(d);
        registerModule(r);
        registerModule(tiantui);

        startShieldTick();

        getServer().getPluginManager().registerEvents(this, this);

        // 原版弓箭聲音攔截（需 ProtocolLib）
        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            try {
                soundSuppressor = new SoundSuppressor(this);
                soundSuppressor.register();
                getLogger().info("已啟用原版弓箭聲音攔截 (ProtocolLib)。");
            } catch (Throwable t) {
                soundSuppressor = null;
                getLogger().warning("ProtocolLib 聲音攔截初始化失敗：" + t.getMessage());
            }
        } else {
            getLogger().info("未偵測到 ProtocolLib，跳過原版弓箭聲音攔截。");
        }

        if (getCommand("getego") != null) {
            getCommand("getego").setExecutor(this);
            getCommand("getego").setTabCompleter(this);
        }
    }

    private void registerModule(org.bukkit.event.Listener module) {
        getServer().getPluginManager().registerEvents(module, this);
    }

    // ── 聖宣盾牌 Tick ─────────────────────────────────────────────────────────

    private void startShieldTick() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ItemStack offHand  = player.getInventory().getItemInOffHand();
                ItemStack mainHand = player.getInventory().getItemInMainHand();

                if (solemn.hasId(offHand, "solemn_shield")) {
                    solemn.handleShieldTick(player, offHand);
                } else if (solemn.hasId(mainHand, "solemn_shield")) {
                    solemn.handleShieldTick(player, mainHand);
                }
            }
        }, 0L, 5L);
    }

    // ── 資源包推送 ───────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getLogger().info("[ResourcePack] Sending pack to " + player.getName());
        try {
            player.addResourcePack(PACK_UUID, PACK_URL, hexToBytes(PACK_HASH),
                    "Receiving resource pack...", true);
            getLogger().info("[ResourcePack] Sent successfully to " + player.getName());
        } catch (Exception e) {
            getLogger().severe("[ResourcePack] Failed to send: " + e.getMessage());
        }
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_UUID.equals(event.getID())) return;
        getLogger().info("[ResourcePack] " + event.getPlayer().getName()
                + " status: " + event.getStatus().name());
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED) {
            event.getPlayer().kickPlayer("§c請接受資源包以加入伺服器。");
        }
    }

    // ── 莊嚴哀悼射擊（弩兩段式：右鍵上弦 → 再右鍵發射）──
    //
    // 由於底材為 CROSSBOW 並隱藏附魔「快速上弦 V」(QUICK_CHARGE 5)，上弦近乎瞬發。
    // - onWeaponInteract：右鍵時播自訂裝填音，並 mark soundSuppressor 抑制 vanilla 上弦音。
    // - onSolemnCrossbowLoad：確保上弦消耗的是蝴蝶彈藥（避免普通箭被吃進弩）。
    // - onSolemnBowShoot：攔截 vanilla 箭矢、清空弩的 chargedProjectiles，改發蝴蝶彈幕。

    // 右鍵：播放自訂裝填音、提早抑制 vanilla 上弦音。
    @EventHandler(priority = EventPriority.LOWEST)
    public void onWeaponInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !solemn.isSolemnLament(item)) return;

        // 無蝴蝶彈藥 → 不准上弦（含創造、含背包只有普通箭的情況）
        if (!solemn.hasButterflyQuartz(player)) {
            event.setCancelled(true);
            return;
        }

        // 提早 mark：涵蓋整個上弦階段，抑制 vanilla 弩的 loading_start/middle/end 音
        // 1500ms 足以涵蓋無附魔弩的 25 tick 上弦 + 緩衝
        if (soundSuppressor != null) soundSuppressor.mark(player, 1500L);

        int quickLevel = item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.QUICK_CHARGE);
        String loadSound = (quickLevel > 0)
                ? "solemnlament:solemn.quick_load." + Math.min(quickLevel, 3)
                : "solemnlament:solemn.load";
        player.getWorld().playSound(player.getLocation(), loadSound, 0.6f, 1.0f);
        // 不取消事件：讓 vanilla 弩正常進入上弦（舉手）動畫
    }

    // 發射：攔截原版箭矢、清空 chargedProjectiles 讓弩可再次上弦，改發蝴蝶彈幕。
    // 註：若玩家身上同時有蝴蝶與普通箭，vanilla 可能優先選普通箭上弦。
    // 玩家自行管理彈藥順序（建議蝴蝶放副手或 hotbar 第一格）。
    @EventHandler
    public void onSolemnBowShoot(org.bukkit.event.entity.EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();

        // 不是莊嚴哀悼：若用了蝴蝶彈藥則擋下（蝴蝶彈藥專屬莊嚴哀悼）
        if (bow == null || !solemn.isSolemnLament(bow)) {
            if (solemn.isButterfly(event.getConsumable())) event.setCancelled(true);
            return;
        }

        // 攔截：不射出原版箭矢、不損耐久
        event.setCancelled(true);

        // 清空弩的 chargedProjectiles（吃進去的蝴蝶在此消耗），讓弩可立即重新上弦
        if (bow.getItemMeta() instanceof org.bukkit.inventory.meta.CrossbowMeta cbMeta) {
            cbMeta.setChargedProjectiles(java.util.Collections.emptyList());
            bow.setItemMeta(cbMeta);
        }

        long now = System.currentTimeMillis();
        int quickLevel = bow.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.QUICK_CHARGE);
        long cooldown = quickLevel > 0 ? Math.max(400L, 1200L - quickLevel * 300L) : 1200L;
        if (now - solemnCooldowns.getOrDefault(player.getUniqueId(), 0L) < cooldown) return;

        solemnCooldowns.put(player.getUniqueId(), now);

        // 抑制這次射擊在玩家附近的原版弓箭聲音
        if (soundSuppressor != null) soundSuppressor.mark(player);

        ItemMeta meta = bow.getItemMeta();
        String model = (meta != null && meta.getItemModel() != null)
                ? meta.getItemModel().toString() : "";
        solemn.handleShootManual(player, bow, model);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        solemnCooldowns.remove(event.getPlayer().getUniqueId());
    }

    // ── 近戰攻擊 ──────────────────────────────────────────────────────────────

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager().hasMetadata("lsmp_custom_damage")) return;
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;

        for (EGOWeapon ego : weaponModules.values()) {
            if (hasItemId(item, ego.getId())) {
                ego.handleMelee(event, player);
                break;
            }
        }
    }

    // ── 環指筆刷右鍵生物 ──────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof LivingEntity target)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!hasItemId(item, "brush")) return;

        EGOWeapon ego = weaponModules.get("brush");
        if (ego instanceof ringbrush brush) {
            brush.handleInteractEntity(player, target);
        }
    }

    // ── 指令 ─────────────────────────────────────────────────────────────────

    @EventHandler
    public void onAdminGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof WeaponAdminGUI gui)) return;
        event.setCancelled(true);
        if (!gui.isItemSlot(event.getSlot())) return;
        ItemStack item = event.getCurrentItem();
        if (item != null && !item.getType().isAir()) {
            player.getInventory().addItem(item.clone());
            player.sendMessage(translateHexColorCodes("&#FFD700已給予物品。"));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) return true;
        String first = args[0].toLowerCase();

        if ("give".equals(first)) {
            if (!sender.hasPermission("limbus.admin") && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) return true;
            if (args.length < 3) { sender.sendMessage("用法：/getego give <玩家> <武器ID>"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("找不到玩家：" + args[1]); return true; }
            String weaponId = args[2].toLowerCase();
            int amount = 1;
            if (args.length >= 4) {
                try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException ignored) {}
            }
            if ("tiger_mark".equals(weaponId)) {
                target.getInventory().addItem(tiantui.createTigerMark(amount));
            } else if ("savage_tiger_mark".equals(weaponId)) {
                target.getInventory().addItem(tiantui.createSavageTigerMark(amount));
            } else if ("chatuhu".equals(weaponId)) {
                target.getInventory().addItem(tiantui.createChatuhuPack(amount));
            } else if (weaponModules.containsKey(weaponId)) {
                for (int i = 0; i < amount; i++) weaponModules.get(weaponId).give(target);
            } else if (List.of("black", "white", "butterflies", "shield").contains(weaponId)) {
                solemn.give(target, weaponId, amount);
            }
            return true;
        }

        if (!(sender instanceof Player player)) return true;
        if ("admin".equals(first)) {
            if (!player.hasPermission("limbus.admin") && !player.isOp()) return true;
            player.openInventory(new WeaponAdminGUI(this).getInventory());
            return true;
        }
        if ("tiger_mark".equals(first)) {
            player.getInventory().addItem(tiantui.createTigerMark(1));
        } else if ("savage_tiger_mark".equals(first)) {
            player.getInventory().addItem(tiantui.createSavageTigerMark(1));
        } else if ("chatuhu".equals(first)) {
            player.getInventory().addItem(tiantui.createChatuhuPack(1));
        } else if (weaponModules.containsKey(first)) {
            weaponModules.get(first).give(player);
        } else if (List.of("black", "white", "butterflies", "shield").contains(first)) {
            solemn.give(player, first);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(
                    List.of("brush", "black", "white", "butterflies", "shield", "mimicry", "dacapo",
                            "tiantui", "tiger_mark", "savage_tiger_mark", "chatuhu", "admin"));
            return completions.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }

    // ── 顏色代碼工具 ─────────────────────────────────────────────────────────

    public String translateHexColorCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
