package me.yisang.limbus;

import me.yisang.limbus.status.StatusEffect;
import me.yisang.limbus.status.StatusManager;
import me.yisang.limbus.status.StatusState;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 著影揮刀 — Meursault 的居合刀。
 * 每次命中對自己累積 BREATHING（呼吸法）；隨層數提高爆擊機率。
 * 慢速大劍手感：低攻速、高基礎傷、靠呼吸法賭爆擊。
 */
public class ShadowBladesinger implements EGOWeapon, Listener {
    private static final int BREATHING_POTENCY_CAP = 10; // 50% 爆擊率上限（5% × 10）
    private static final int BREATHING_PER_HIT = 1;
    private static final int BREATHING_COUNT_PER_HIT = 4;

    private final LimbusEGOWeapons plugin;

    public ShadowBladesinger(LimbusEGOWeapons plugin) { this.plugin = plugin; }

    @Override
    public String getId() { return "bladesinger"; }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.translateHexColorCodes("&#AEDBFF著影揮刀"));
            meta.setLore(List.of(
                    plugin.translateHexColorCodes("&#D0E7FF望月斬首──就此，氣絕吧。")));
            meta.setCustomModelData(1012);
            meta.setUnbreakable(true);
            meta.setItemModel(NamespacedKey.fromString("shadow_vested_bladesinger:shadow_vested_bladesinger"));
            // 居合手感：高基礎傷、慢攻速
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(new NamespacedKey(plugin, "bladesinger_dmg"),
                            9.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(new NamespacedKey(plugin, "bladesinger_spd"),
                            -2.6, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(
                    plugin.getItemIdKey(), PersistentDataType.STRING, "bladesinger");
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleMelee(EntityDamageByEntityEvent event, Player attacker) {
        StatusManager sm = plugin.getStatusManager();
        if (sm == null) return;
        StatusState s = sm.get(attacker);
        int cur = s == null ? 0 : s.potency(StatusEffect.BREATHING);
        if (cur < BREATHING_POTENCY_CAP) {
            sm.apply(attacker, StatusEffect.BREATHING, BREATHING_PER_HIT, BREATHING_COUNT_PER_HIT, attacker);
        } else {
            sm.refresh(attacker, StatusEffect.BREATHING, BREATHING_COUNT_PER_HIT);
        }
    }
}
