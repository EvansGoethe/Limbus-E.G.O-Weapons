package me.yisang.limbusweapons.item;

import me.yisang.limbusweapons.event.WeaponEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * 莊嚴哀悼 — 弩式兩段（近乎瞬發上膛）：
 *  第一下右鍵：瞬間上膛（消耗 1 蝴蝶，保持上膛狀態）。
 *  第二下右鍵：發射蝴蝶彈幕。
 * isBlack=true → 8 傷害 + 凋零；false → 4 傷害 + 失明。
 */
public class SolemnLamentItem extends Item {
    public final boolean isBlack;

    public SolemnLamentItem(boolean isBlack, Settings settings) {
        super(settings);
        this.isBlack = isBlack;
    }

    private static boolean isCharged(ItemStack stack) {
        ChargedProjectilesComponent c = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
        return c != null && !c.isEmpty();
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return ActionResult.SUCCESS;
        ServerWorld sw = (ServerWorld) world;

        if (isCharged(stack)) {
            // 第二段：發射
            WeaponEvents.fireSolemnCharged(user, sw, this);
            stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT);
            return ActionResult.SUCCESS;
        }

        // 第一段：瞬間上膛
        ItemStack ammo = WeaponEvents.findButterfly(user);
        if (ammo == null && !user.getAbilities().creativeMode) return ActionResult.FAIL;
        if (ammo != null) ammo.decrement(1);
        stack.set(DataComponentTypes.CHARGED_PROJECTILES,
                ChargedProjectilesComponent.of(new ItemStack(ModItems.BUTTERFLY_QUARTZ)));
        WeaponEvents.onSolemnLamentDraw(user, world);
        return ActionResult.SUCCESS;
    }
}
