package me.yisang.limbusweapons.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import me.yisang.limbusweapons.item.ModItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

public class EgoCommand {

    private static final Map<String, ItemStack> ITEMS = Map.ofEntries(
            Map.entry("black",       new ItemStack(ModItems.SOLEMN_LAMENT_BLACK)),
            Map.entry("white",       new ItemStack(ModItems.SOLEMN_LAMENT_WHITE)),
            Map.entry("butterflies", new ItemStack(ModItems.BUTTERFLY_QUARTZ, 16)),
            Map.entry("shield",      new ItemStack(ModItems.SOLEMN_SHIELD)),
            Map.entry("mimicry",     new ItemStack(ModItems.MIMICRY)),
            Map.entry("dacapo",      new ItemStack(ModItems.DACAPO)),
            Map.entry("brush",       new ItemStack(ModItems.RING_BRUSH)),
            Map.entry("tiantui",     new ItemStack(ModItems.TIANTUI_STAR)),
            Map.entry("twilight",    new ItemStack(ModItems.TWILIGHT)),
            Map.entry("tiger_mark",  new ItemStack(ModItems.TIGER_MARK, 64)),
            Map.entry("savage_mark", new ItemStack(ModItems.SAVAGE_TIGER_MARK, 64)),
            Map.entry("chatuhu",     new ItemStack(ModItems.CHATUHU)),
            Map.entry("apocalypse",  new ItemStack(ModItems.APOCALYPSE_BIRD))
    );

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                CommandManager.literal("getego")
                    .then(CommandManager.literal("admin")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            player.openHandledScreen(me.yisang.limbusweapons.client.WeaponAdminGUI.create());
                            return 1;
                        }))
                    .then(CommandManager.literal("catalog")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            player.openHandledScreen(me.yisang.limbusweapons.client.WeaponCatalogGUI.create());
                            return 1;
                        }))
                    .then(CommandManager.literal("give")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.argument("item", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                ITEMS.keySet().forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "item");
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

                                ItemStack stack = ITEMS.get(name.toLowerCase());
                                if (stack == null) {
                                    player.sendMessage(Text.literal("§c未知武器：" + name), false);
                                    return 0;
                                }
                                player.getInventory().insertStack(stack.copy());
                                player.sendMessage(Text.literal("§a已給予 " + name), false);
                                return 1;
                            })))
            )
        );
    }
}
