package me.yisang.limbus.status;

public enum StatusEffect {
    BLEED("流血", "§c"),
    BURN("燒傷", "§6"),
    FRAGILE("易損", "§d"),
    POWER("強壯", "§e"),
    SEDUCTION("沉淪", "§5"),
    RUPTURE("破裂", "§4"),
    TREMOR("震顫", "§b"),
    PROTECTION("守護", "§a"),
    HASTE("迅捷", "§f"),
    BIND("束縛", "§8"),
    BREATHING("呼吸法", "§3"),
    CHARGE("充能", "§9");

    public final String zh;
    public final String color;

    StatusEffect(String zh, String color) {
        this.zh = zh;
        this.color = color;
    }
}
