package me.yisang.limbusweapons.client;

import me.yisang.limbusweapons.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class WeaponAdminGUI {

    public static SimpleNamedScreenHandlerFactory create() {
        return new SimpleNamedScreenHandlerFactory((syncId, playerInventory, player) -> {
            SimpleInventory inventory = new SimpleInventory(36);
            
            // 放入所有武器
            inventory.setStack(0, new ItemStack(ModItems.MIMICRY));
            inventory.setStack(1, new ItemStack(ModItems.DACAPO));
            inventory.setStack(2, new ItemStack(ModItems.RING_BRUSH));
            inventory.setStack(3, new ItemStack(ModItems.SOLEMN_LAMENT_BLACK));
            inventory.setStack(4, new ItemStack(ModItems.SOLEMN_LAMENT_WHITE));
            inventory.setStack(5, new ItemStack(ModItems.SOLEMN_SHIELD));
            inventory.setStack(6, new ItemStack(ModItems.BUTTERFLY_QUARTZ, 64));
            inventory.setStack(7, new ItemStack(ModItems.TIANTUI_STAR));
            inventory.setStack(8, new ItemStack(ModItems.TIGER_MARK, 64));
            inventory.setStack(9, new ItemStack(ModItems.SAVAGE_TIGER_MARK, 64));
            inventory.setStack(10, new ItemStack(ModItems.CHATUHU, 16));
            inventory.setStack(11, new ItemStack(ModItems.TWILIGHT));
            inventory.setStack(12, new ItemStack(ModItems.APOCALYPSE_BIRD, 16));

            return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, playerInventory, inventory, 4) {
                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
                    if (slotIndex >= 0 && slotIndex < 36) {
                        ItemStack clickedStack = this.getInventory().getStack(slotIndex);
                        if (!clickedStack.isEmpty()) {
                            // 給予玩家點擊的物品
                            player.getInventory().offerOrDrop(clickedStack.copy());
                            player.sendMessage(Text.literal("§a已給予 " + clickedStack.getName().getString()), false);
                        }
                        // 取消事件
                        return;
                    }
                    super.onSlotClick(slotIndex, button, actionType, player);
                }
                
                @Override
                public ItemStack quickMove(PlayerEntity player, int index) {
                    return ItemStack.EMPTY;
                }
            };
        }, Text.literal("Limbus E.G.O Weapons - 管理員"));
    }
}
