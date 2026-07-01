package me.yisang.limbusweapons.client;

import me.yisang.limbusweapons.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class WeaponCatalogGUI {

    public static SimpleNamedScreenHandlerFactory create() {
        return new SimpleNamedScreenHandlerFactory((syncId, playerInventory, player) -> {
            SimpleInventory inventory = new SimpleInventory(54);
            
            inventory.setStack(0, new ItemStack(ModItems.MIMICRY));
            inventory.setStack(1, new ItemStack(ModItems.DACAPO));
            inventory.setStack(2, new ItemStack(ModItems.RING_BRUSH));
            inventory.setStack(3, new ItemStack(ModItems.SOLEMN_LAMENT_BLACK));
            inventory.setStack(4, new ItemStack(ModItems.SOLEMN_LAMENT_WHITE));
            inventory.setStack(5, new ItemStack(ModItems.SOLEMN_SHIELD));
            inventory.setStack(6, new ItemStack(ModItems.TIANTUI_STAR));
            inventory.setStack(7, new ItemStack(ModItems.TWILIGHT));

            return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6) {
                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
                    if (slotIndex >= 0 && slotIndex < 54) {
                        // 圖鑑模式不允許拿出物品
                        return;
                    }
                    super.onSlotClick(slotIndex, button, actionType, player);
                }
                
                @Override
                public ItemStack quickMove(PlayerEntity player, int index) {
                    return ItemStack.EMPTY;
                }
            };
        }, Text.literal("Limbus E.G.O Weapons - 圖鑑"));
    }
}
