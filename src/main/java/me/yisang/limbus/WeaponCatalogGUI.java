package me.yisang.limbus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 武器圖鑑（唯讀，玩家可開啟瀏覽）。
 * 兩頁籤：
 *  - 全部：所有武器（莊嚴哀悼黑/白、生蝶亡蝶、聖宣、擬態、DaCapo、環指筆刷）
 *  - LCE 研發限定：天退星刀、虎標彈、猛虎標彈、插翅虎、薄暝、終末鳥
 */
public class WeaponCatalogGUI implements InventoryHolder {

    public static final int TAB_ALL = 0;
    public static final int TAB_LCE = 1;
    private static final int TAB_ALL_SLOT = 2;
    private static final int TAB_LCE_SLOT = 6;
    private static final int CLOSE_SLOT = 49;

    private final LimbusEGOWeapons plugin;
    private Inventory inventory;
    private int currentTab;

    public WeaponCatalogGUI(LimbusEGOWeapons plugin, int tab) {
        this.plugin = plugin;
        this.currentTab = tab;
        build();
    }

    private void build() {
        inventory = Bukkit.createInventory(this, 54,
                plugin.translateHexColorCodes("&#FFFFFF武器圖鑑"));

        ItemStack border = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inventory.setItem(i, border);
        for (int i = 45; i < 54; i++) inventory.setItem(i, border);

        // 頁籤
        boolean allActive = currentTab == TAB_ALL;
        boolean lceActive = currentTab == TAB_LCE;
        inventory.setItem(TAB_ALL_SLOT, makeItem(
                allActive ? Material.WHITE_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                "&#FFFFFF" + (allActive ? "&l▶ " : "") + "全部武器"));
        inventory.setItem(TAB_LCE_SLOT, makeItem(
                lceActive ? Material.WHITE_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE,
                "&#FFD700" + (lceActive ? "&l▶ " : "") + "LCE 研發限定"));

        // 內容
        List<ItemStack> items = currentTab == TAB_LCE ? lcePage() : allPage();
        for (int i = 0; i < 36; i++) {
            inventory.setItem(9 + i, i < items.size() ? items.get(i) : border);
        }

        inventory.setItem(CLOSE_SLOT, makeItem(Material.BARRIER, "&#FF5555關閉"));
    }

    private List<ItemStack> allPage() {
        solemnlament s = plugin.getSolemn();
        List<ItemStack> list = new ArrayList<>();
        list.add(plugin.getWeaponModule("brush").createItem());
        list.add(plugin.getWeaponModule("mimicry").createItem());
        list.add(plugin.getWeaponModule("dacapo").createItem());
        list.add(s.createItem("black"));
        list.add(s.createItem("white"));
        list.add(s.createItem("butterflies"));
        list.add(s.createItem("shield"));
        list.add(plugin.getWeaponModule("w_corp_knife").createItem());
        list.add(plugin.getWeaponModule("bladesinger").createItem());
        return list;
    }

    private List<ItemStack> lcePage() {
        TiantuiStar t = plugin.getTiantui();
        TwilightWeapon w = plugin.getTwilight();
        List<ItemStack> list = new ArrayList<>();
        list.add(t.createItem());
        list.add(t.createTigerMark(1));
        list.add(t.createSavageTigerMark(1));
        list.add(t.createChatuhuPack(1));
        list.add(w.createItem());
        list.add(w.createApocalypseBirdPack(1));
        list.add(plugin.getTibia().createItem());
        return list;
    }

    private ItemStack makeItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void switchTab(Player player, int tab) {
        currentTab = tab;
        build();
        player.openInventory(inventory);
    }

    public int getTabForSlot(int slot) {
        if (slot == TAB_ALL_SLOT) return TAB_ALL;
        if (slot == TAB_LCE_SLOT) return TAB_LCE;
        return -1;
    }

    public boolean isCloseSlot(int slot) { return slot == CLOSE_SLOT; }
    public int getCurrentTab() { return currentTab; }

    @Override public Inventory getInventory() { return inventory; }
}
