package me.yisang.limbus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class WeaponAdminGUI implements InventoryHolder {

    private final Inventory inventory;

    // Slot layout (36-slot / 4 rows):
    // Row 0: all filler
    // Row 1: [filler, brush, mimicry, dacapo, filler, black, white, butterflies, shield]
    // Row 2: [filler, tiantui_star, tiger_mark, savage_tiger_mark, filler...]
    // Row 3: all filler
    static final int[] ITEM_SLOTS = {10, 11, 12, 14, 15, 16, 17, 19, 20, 21, 22};

    public WeaponAdminGUI(LimbusEGOWeapons plugin) {
        inventory = Bukkit.createInventory(this, 36, "§6✦ 武器管理");

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int i = 0; i < 36; i++) inventory.setItem(i, filler.clone());

        solemnlament solemn = plugin.getSolemn();
        TiantuiStar tiantui = plugin.getTiantui();

        inventory.setItem(10, plugin.getWeaponModule("brush").createItem());
        inventory.setItem(11, plugin.getWeaponModule("mimicry").createItem());
        inventory.setItem(12, plugin.getWeaponModule("dacapo").createItem());
        // slot 13 stays as filler separator
        inventory.setItem(14, solemn.createItem("black"));
        inventory.setItem(15, solemn.createItem("white"));
        inventory.setItem(16, solemn.createItem("butterflies"));
        inventory.setItem(17, solemn.createItem("shield"));

        // Row 2 — 天退星系列
        inventory.setItem(19, tiantui.createItem());
        inventory.setItem(20, tiantui.createTigerMark(1));
        inventory.setItem(21, tiantui.createSavageTigerMark(1));
        inventory.setItem(22, tiantui.createChatuhuPack(1));
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public boolean isItemSlot(int slot) {
        for (int s : ITEM_SLOTS) if (s == slot) return true;
        return false;
    }
}
