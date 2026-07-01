package me.yisang.limbus.status;

import java.util.EnumMap;
import java.util.Map;

/**
 * 單一實體身上的所有 Limbus 屬性層/計數狀態。
 * 每個 effect 有 (potency, count) 兩軸：
 *   potency：威力（原著左下角）
 *   count  ：剩餘觸發次數 / 回合數（原著右下角）
 * 觸發一次消耗 1 count；count 歸零時該 effect 被移除，potency 一併清除。
 */
public class StatusState {
    private final EnumMap<StatusEffect, int[]> stacks = new EnumMap<>(StatusEffect.class);

    public synchronized void add(StatusEffect e, int potency, int count) {
        int[] c = stacks.computeIfAbsent(e, k -> new int[]{0, 0});
        c[0] += potency;
        c[1] += count;
    }

    public synchronized int potency(StatusEffect e) {
        int[] c = stacks.get(e);
        return c == null ? 0 : c[0];
    }

    public synchronized int count(StatusEffect e) {
        int[] c = stacks.get(e);
        return c == null ? 0 : c[1];
    }

    /** 消耗最多 n 個 count；若歸零則移除該 effect。回傳實際消耗量。 */
    public synchronized int consume(StatusEffect e, int n) {
        int[] c = stacks.get(e);
        if (c == null || c[1] <= 0) return 0;
        int use = Math.min(n, c[1]);
        c[1] -= use;
        if (c[1] <= 0) stacks.remove(e);
        return use;
    }

    public synchronized boolean isEmpty() {
        return stacks.isEmpty();
    }

    /** 快照拷貝，供顯示層唯讀迭代使用。 */
    public synchronized Map<StatusEffect, int[]> snapshot() {
        EnumMap<StatusEffect, int[]> copy = new EnumMap<>(StatusEffect.class);
        for (Map.Entry<StatusEffect, int[]> en : stacks.entrySet()) {
            copy.put(en.getKey(), new int[]{en.getValue()[0], en.getValue()[1]});
        }
        return copy;
    }
}
