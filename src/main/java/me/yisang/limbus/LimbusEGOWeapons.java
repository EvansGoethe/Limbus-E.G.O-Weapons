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

    private static final String PACK_URL  = "https://github.com/EvansGoethe/Limbus-E.G.O-weapon-plugin-ResourcePack/releases/download/Releases/Limbus_E.G.O_Weapons_plugin_ResourcePack.v.10.zip";
    private static final String PACK_HASH = "56d3add97e4d4c549654dbd1ed2fef73f91b324c";
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
        mimicry   m = new mimicry(this);
        dacapo    d = new dacapo(this);
        ringbrush r = new ringbrush(this);

        weaponModules.put("mimicry", m);
        weaponModules.put("dacapo",  d);
        weaponModules.put("brush",   r);

        registerModule(m);
        registerModule(d);
        registerModule(r);

        startShieldTick();

        getServer().getPluginManager().registerEvents(this, this);

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

    // ── 莊嚴哀悼射擊（右鍵 → 裝填音 → 延遲後發射蝴蝶石英）──────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWeaponInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !solemn.isSolemnLament(item)) return;

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        if (now - solemnCooldowns.getOrDefault(player.getUniqueId(), 0L) < 1200) return;

        if (!solemn.hasButterflyQuartz(player) && player.getGameMode() != GameMode.CREATIVE) return;

        int quickLevel = item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.QUICK_CHARGE);
        String loadSound = (quickLevel > 0)
                ? "solemnlament:solemn.quick_load." + Math.min(quickLevel, 3)
                : "solemnlament:solemn.load";
        player.getWorld().playSound(player.getLocation(), loadSound, 0.6f, 1.0f);

        solemnCooldowns.put(player.getUniqueId(), now);

        ItemMeta bowMeta = item.getItemMeta();
        String model = (bowMeta != null && bowMeta.getItemModel() != null)
                ? bowMeta.getItemModel().toString() : "";
        long shootDelay = quickLevel > 0 ? Math.max(5L, 20L - quickLevel * 5L) : 20L;

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!solemn.isSolemnLament(player.getInventory().getItemInMainHand())) return;
            ItemStack quartz = solemn.findButterflyQuartz(player);
            if (quartz == null && player.getGameMode() != GameMode.CREATIVE) return;
            if (quartz != null) quartz.setAmount(quartz.getAmount() - 1);
            solemn.handleShootManual(player, item, model);
        }, shootDelay);
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

    // ── 防止蝴蝶石英誤觸發普通弓弩 ───────────────────────────────────────────

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().name().contains("RIGHT_CLICK")) return;
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand  = player.getInventory().getItemInOffHand();

        if (solemn.isButterfly(offHand) && isNormalWeapon(mainHand)) event.setCancelled(true);
        else if (solemn.isButterfly(mainHand) && isNormalWeapon(offHand)) event.setCancelled(true);
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
            if (weaponModules.containsKey(weaponId)) {
                weaponModules.get(weaponId).give(target);
            } else if (List.of("black", "white", "butterflies", "shield").contains(weaponId)) {
                solemn.give(target, weaponId);
            }
            return true;
        }

        if (!(sender instanceof Player player)) return true;
        if ("admin".equals(first)) {
            if (!player.hasPermission("limbus.admin") && !player.isOp()) return true;
            player.openInventory(new WeaponAdminGUI(this).getInventory());
            return true;
        }
        if (weaponModules.containsKey(first)) {
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
                    List.of("brush", "black", "white", "butterflies", "shield", "mimicry", "dacapo", "admin"));
            return completions.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }

    // ── 私有輔助方法 ─────────────────────────────────────────────────────────

    private boolean isNormalWeapon(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return (type == Material.CROSSBOW || type == Material.BOW) && !solemn.isSolemnLament(item);
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
