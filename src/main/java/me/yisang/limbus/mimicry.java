package me.yisang.limbus;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;
import org.bukkit.event.Listener;

public class mimicry implements EGOWeapon, Listener {
    private final LimbusEGOWeapons plugin;

    public mimicry(LimbusEGOWeapons plugin) { this.plugin = plugin; }

    @Override
    public String getId() { return "mimicry"; }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes("&#FF0000擬態"));
            meta.setLore(List.of(plugin.translateHexColorCodes(
                    "&x&F&F&0&0&0&0而那裡有許多聲音齊聲哭喊著同一個字──「主管」")));
            meta.setCustomModelData(1006);
            meta.setUnbreakable(true);
            meta.setItemModel(NamespacedKey.fromString("mimicry:mimicry"));
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(new NamespacedKey(plugin, "mimicry_dmg"),
                            12.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(new NamespacedKey(plugin, "mimicry_spd"),
                            -3.2, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(
                    plugin.getItemIdKey(), PersistentDataType.STRING, "mimicry");
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleMelee(EntityDamageByEntityEvent event, Player attacker) {
        // 修正：先決定是否暴擊並修改傷害值，再計算吸血量
        // 原本的寫法在加暴擊前就抓了 getFinalDamage()，導致暴擊時吸血量低估
        boolean crit = Math.random() < 0.10;
        if (crit) {
            double bonus = 40.0 + (Math.random() * 50.0);
            event.setDamage(event.getDamage() + bonus);
            attacker.getWorld().spawnParticle(
                    Particle.EXPLOSION_EMITTER, event.getEntity().getLocation(), 1);
        }

        // getFinalDamage() 在此時已反映上面可能的暴擊加成，計算結果才正確
        double heal = event.getFinalDamage() * 0.25;
        double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + heal));

        // 暴擊時給自己 3 potency / 4 count 的強壯（下次 4 擊 +30% 出手）
        if (crit && plugin.getStatusManager() != null) {
            plugin.getStatusManager().apply(attacker,
                    me.yisang.limbus.status.StatusEffect.POWER, 3, 4, attacker);
        }
    }
}
