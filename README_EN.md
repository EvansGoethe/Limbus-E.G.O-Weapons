# Limbus E.G.O Weapons — Paper Plugin

*[繁體中文版本](README.md)*

A Paper plugin bringing Limbus Company's E.G.O weapons and their **attack attribute / sanity system** into Minecraft.

- **Version**: 3.0.2
- **Minecraft**: 1.21.4
- **Platform**: Paper (requires `setItemModel` API)
- **Soft-depend**: [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) (optional — suppresses vanilla crossbow sounds heard by nearby players when firing Solemn Lament)
- **Resource Pack**: v.2.16, distributed by an external ResourcePackManager (this plugin does not push directly)

---

## Limbus Attribute System

Each entity tracks a `(potency, count)` two-axis state, where potency is intensity and count is remaining trigger uses. Fully in-memory — mob unload / death auto-clears, no NBT writes.

### Attack Attributes (Debuffs)

| Attribute | Trigger |
|-----------|---------|
| Bleed §c | When the bleeder **attacks**, consumes 1 count → deals potency × 0.5 true damage to itself |
| Burn §6 | Every 40-tick cycle consumes 1 count → potency true damage (DoT, 4-bucket rotation) |
| Fragile §d | Incoming damage × (1 + potency × 15%) multiplier |
| Seduction §5 | On being hit, consumes 1 count → potency true damage + player SAN −1 (turns into Depression ×1.5 at rock bottom); higher potency slows victim (−2%/potency, cap −50%) |
| Rupture §4 | On being hit, consumes 1 count → potency × 2 true damage (boss-melter multiplier) |
| Tremor §b | Accumulates potency; on being hit while potency ≥ 5 → **Burst**: consumes all potency, deals potency × 3 true damage + derives Scorching (adds Burn 5p/3c) |

### Buffs / Debuffs

| Attribute | Effect | Consumption |
|-----------|--------|-------------|
| Power §e | Outgoing damage × (1 + potency × 10%) | −1 count per swing |
| Protection §a | Incoming damage × (1 − potency × 5%), applied before Fragile | Passive — checked each incoming hit |
| Haste §f | Speed potion wrapper: amplifier = potency−1, duration = count seconds | Decays via vanilla potion timer |
| Bind §8 | Slowness potion wrapper, same as above | Same as above |
| Breathing §3 | min(60%, potency × 5%) chance to **crit ×1.75** per swing | −1 count per swing |
| Charge §9 | Outgoing damage × (1 + potency × 3%) | −1 count per swing |

---

## Sanity System (SAN)

Each player has a persistent BossBar: `progress = (SAN + 45) / 90` (SAN=0 = half full).

- **Range**: −45 to +45, default 0
- **Every 2 hits landed**: +1; **every 2 hits taken**: −1; **Seduction consume**: −1 per count
- **Out-of-combat recovery**: after 10 s out of combat, negative SAN regenerates +1 per 2 s until 0; positive does not decay
- **Food restores SAN**: every food-level point gained → SAN +1
- **Attribute tuning**: SAN modulates ATTACK_DAMAGE / MOVEMENT_SPEED via modifiers
  - Attack: ±0.3% per SAN point (extremes: ±13.5%)
  - Speed: ±0.15% per SAN point (extremes: ±6.75%)
- **Warning thresholds**
  - SAN < −20: chat + wither ambient sound each time it crosses a −10 boundary downward (silent on rise)
  - SAN ≤ −30: **Panic** — reapplies Blindness I + Weakness I every 60 ticks
  - SAN = −45: **Rock Bottom** — adds Slowness IV, Seduction damage becomes Depression ×1.5

---

## Weapon Roster

### Ring Brush
> Base: **Netherite Sword** | CMD: 1001 | +8 dmg / −2.4 spd

Right-click a target: 3.5 damage + random negative potion effect + Limbus random pool (Bleed/Burn/Fragile/Seduction/Rupture/Tremor/Bind — buff triad excluded) 1p/3c. A second right-click on the same target within 1.5 s triggers double effect.

### Mimicry
> Base: **Diamond Sword** | CMD: 1006 | +12 dmg / −3.2 spd

10% crit chance for 40–90 bonus damage; heals 25% of final damage dealt. **On crit: self gains 3 potency / 4 count Power** (next 4 swings +30% outgoing).

### DaCapo
> Base: **Iron Sword** | CMD: 1007

Replaces normal attack with a note combo (splashes to entities within 3.5 blocks at 70% damage):

| Mode | Chance | Notes | Per-note damage | Interval |
|------|--------|-------|-----------------|----------|
| Normal | 60% | 5 | 1.5 | 2 tick |
| Special | 40% | 3 | 5 | 4 tick |

Each note-hit applies 1p/1c Seduction; the immediately following damage consumes 1 count → extra true damage.

### Solemn Lament (Black / White)
> Base: **Crossbow** | CMD: 1002 / 1003 | Hidden "Quick Charge V"

Two-stage: right-click to load → right-click to fire butterfly projectile. Cooldown 400 ms.
- Black: 8 damage + Wither II (4 s) + Seduction 4p/3c on hit
- White: 4 damage + Blindness (3 s) + Seduction 3p/2c on hit

> ⚠ If both regular arrows and butterflies are in inventory, vanilla may pick regular arrows first. Put butterflies in offhand or hotbar slot 1.

### Butterflies (Living / Dying)
> Base: **Arrow** | CMD: 1004

Solemn Lament's exclusive ammo. Regular bows/crossbows cannot fire them.

### Solemn Declaration
> Base: **Shield** | CMD: 1005

While held, every 5 ticks applies vanilla Slowness II + Limbus Bind 1p/2c to hostiles within 5 blocks; self replenishes Protection potency up to cap 3.

### Tiantui Star Sword
> Base: **Netherite Sword** | CMD: 1008 | +8 dmg / −2.4 spd

Iaijutsu dash, consumes Tiger Mark (gunpowder):

| Action | Charge | Damage | Extra |
|--------|--------|--------|-------|
| Right-click (Tiger Mark) | 1s | 8 | Fire 3s + **Tremor 5p / Burn 4p/3c** |
| Sneak + right-click (Savage Tiger Mark) | 3s | 18 | Fire 5s + Wither II + **Tremor 8p / Burn 6p/4c** |

Once Tremor reaches 5 potency, any incoming hit triggers Burst → potentially massive true damage + Scorching derivative.

**Tiger Mark / Savage Tiger Mark** (GUNPOWDER) are the charge consumables.

**Winged Tiger** (TRIAL_KEY): right-click to unpack → 1 sword + 10 savage marks + 20 marks. The sword goes into storage, not main hand.

### Twilight
> Base: **Netherite Sword** | CMD: 1009 | +10 dmg / −2.8 spd | +1.5 interaction range

- 30% true damage + low-HP multiplier (up to ×2.5)
- Sneak + right-click charges for 1.5s → fan-shaped Twilight Slash (±55° forward): each target gets **Rupture 5p/2c**

**Apocalypse Bird** (TRIAL_KEY): right-click → 1 Twilight (requires 1 empty slot).

### Tibia
> Base: **Netherite Sword** | CMD: 1010 | +10 dmg / −2.8 spd | +1 interaction range

Callisto's greatsword forged from her own bones. "The more you attack, the more you bleed."

- Each melee hit applies **Bleed 3p/2c** (Bleed hurts the *attacker* when they swing — devastating against high attack-speed mobs)
- **Melody bonus**: reads target's current Bleed potency, +3% per 3p (cap +30%), added on the hit itself
- **Sneak + right-click charges for 2s → Anatomize** (fan ±60°, 5-block range):
  - Each target takes 16 base damage (65% regular + 35% true)
  - Applies Bleed 12p/6c → **force-triggers Bleed 3 times** (bypasses the "target must attack" condition)
- 8-second cooldown; Corpus passive: reduces Wither / Fire amplifier

### W Corp Knife (v3.0)
> Base: **Iron Sword** | CMD: 1011 | +4 dmg / −1.6 spd

Dagger of a W Corp Tier-3 Cleaner. "In the end, this is the passage."

- Each hit: applies Charge 1p/5c (potency cap 10 → +30% outgoing multiplier)
- **20% Overload chance**: extra +1 potency / +1 count Charge (**bypasses cap**, can stack up to 15+)

### Shadow Vested Bladesinger (v3.0)
> Base: **Netherite Sword** | CMD: 1012 | +9 dmg / −2.6 spd

Meursault's iaijutsu blade. "Behead beneath the moon — and thus, expire."

- Each hit: applies Breathing 1p/4c (potency cap 10 → 50% crit chance)
- **Flesh-Slash Bone-Snap** (v3.0.1): HP < 3 hearts + sneak + right-click entity → **rooted 5-hit combo**
  - One slash every 4 ticks × 5 (20 ticks / 1 second total)
  - Each slash: 7 base damage + SWEEP_ATTACK line particle + CRIT hit particle
  - Player receives strong Slowness VII + teleport-anchored each tick to prevent displacement
  - 12-second cooldown; ActionBar displays "肉斬骨斷" (Flesh-Slash Bone-Snap)

---

## Commands

| Command | Description |
|---------|-------------|
| `/getego <weapon_id>` | Give yourself a weapon (requires `limbus.admin` or OP) |
| `/getego give <player> <weapon_id> [amount]` | Give weapon to player (console-usable) |
| `/getego admin` | Open the admin GUI (3-row layout) |
| `/getego catalog` | Open the player catalog (**All Weapons** / **LCE R&D Exclusive** tabs) |

**Weapon IDs**: `brush`, `mimicry`, `dacapo`, `black`, `white`, `butterflies`, `shield`, `tiantui`, `tiger_mark`, `savage_tiger_mark`, `chatuhu`, `twilight`, `apocalypse_bird`, `tibia`, `w_corp_knife`, `bladesinger`

---

## Sound Strategy (Solemn Lament)

Vanilla crossbow sounds heard by the *shooter themselves* are client-side predicted — the server never sends that packet, so it can't be intercepted server-side. The plugin uses a layered approach:

| Target | Handling | Requirement |
|--------|----------|-------------|
| **Others** hearing your Solemn Lament crossbow | ProtocolLib intercepts world/entity sound packets | ProtocolLib installed (optional) |
| **The shooter** hearing their own predicted sound | Fabric client mod intercepts `SoundManager.play` | Player installs [fabric-1.0.1](https://github.com/EvansGoethe/Limbus-E.G.O-Weapons/releases/tag/fabric-1.0.1) |

---

## Resource Pack

Distributed externally. On enable, syncs asynchronously to `plugins/LimbusEGOWeapons/resourcepack.zip` (skipped if hash matches), then handed off to ResourcePackManager for merged distribution.

- Source: [Limbus-E.G.O-weapon-plugin-ResourcePack](https://github.com/EvansGoethe/Limbus-E.G.O-weapon-plugin-ResourcePack)
- Current version: **v.2.16**

Each weapon's `item_model` and base material is listed above and can be referenced by external plugins (e.g. BattlePass).

---

## Installation

1. Drop the `.jar` into `plugins/`
2. (Optional) Install ProtocolLib to enable Solemn Lament ambient sound suppression for nearby players
3. Start the server
4. (Optional) Players install the [fabric-1.0.1](https://github.com/EvansGoethe/Limbus-E.G.O-Weapons/releases/tag/fabric-1.0.1) client mod to suppress their own predicted crossbow sounds

---

## Fabric Port

A Fabric 1.21.4 port is maintained on the `master` branch.
