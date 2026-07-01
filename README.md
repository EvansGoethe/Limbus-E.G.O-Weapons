# Limbus E.G.O Weapons — Paper Plugin

*[English version](README_EN.md)*

將邊獄公司（Limbus Company）的 E.G.O 武器與**攻擊屬性 / 理智值系統**帶進 Minecraft 的 Paper 插件。

- **版本**：3.0.1
- **Minecraft 版本**：1.21.4
- **平台**：Paper（需支援 `setItemModel` API）
- **軟相依**：[ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)（可選，攔截莊嚴哀悼周圍他人聽到的原版弓箭聲）
- **資源包**：v.2.16，由外部 ResourcePackManager 合併分發（本插件不主動推送）

---

## Limbus 屬性體系

每個實體身上追蹤 `(potency, count)` 雙軸狀態，potency 是威力、count 是剩餘觸發次數。全 in-memory 追蹤，mob unload / 死亡自動清除，不寫入 NBT。

### 攻擊屬性（Debuff）

| 屬性 | 觸發規則 |
|------|---------|
| 流血 §c | 帶血者**攻擊時**消耗 1 count → 對自己造 potency × 0.5 真傷 |
| 燒傷 §6 | 每 40 tick 週期消耗 1 count → potency 真傷（DoT，分 4 桶輪流結算） |
| 易損 §d | 承傷乘 (1 + potency × 15%) 乘區 |
| 沉淪 §5 | 受擊消耗 1 count → potency 真傷 + 玩家 SAN −1（SAN 觸底轉憂鬱 ×1.5）；potency 越高目標移速越低（−2%/potency, cap −50%） |
| 破裂 §4 | 受擊消耗 1 count → potency × 2 真傷（速殺 boss 用） |
| 震顫 §b | 累積 potency；受擊且 potency ≥ 5 → **爆發**：消耗全部 potency 造 potency × 3 真傷 + 派生灼熱（追加 BURN 5p/3c）|

### 增益減益（Buff / Debuff）

| 屬性 | 效果 | 消耗 |
|------|------|------|
| 強壯 §e | 出手乘 (1 + potency × 10%) | 每次出手 −1 count |
| 守護 §a | 承傷乘 (1 − potency × 5%)，套在易損之前 | 每次受擊觸發計算 |
| 迅捷 §f | Speed potion wrapper：amplifier = potency−1，duration = count 秒 | 由 vanilla potion 自然衰減 |
| 束縛 §8 | Slowness potion wrapper，同上 | 同上 |
| 呼吸法 §3 | 出手 min(60%, potency × 5%) 機率**爆擊 ×1.75** | 每次出手 −1 count |
| 充能 §9 | 出手乘 (1 + potency × 3%) | 每次出手 −1 count |

---

## 理智值系統（SAN）

每位玩家一條 BossBar 常態顯示，`progress = (SAN + 45) / 90`（SAN=0 剛好半滿）。

- **範圍**：−45 ~ +45，預設 0
- **每命中 2 次** +1；**每受擊 2 次** −1；**沉淪消耗**每 count −1
- **脫戰恢復**：脫戰 10s 後，SAN 為負值時每 2s +1 直到 0；正值不動
- **進食回 SAN**：飽食度每上升 1 → SAN +1
- **屬性微調**：SAN 影響 ATTACK_DAMAGE / MOVEMENT_SPEED modifier
  - 攻擊：±0.3% / SAN 點（極值 ±13.5%）
  - 移速：±0.15% / SAN 點（極值 ±6.75%）
- **警戒閾值**
  - SAN < −20：每跨 10 級發 chat + wither ambient 音（下降才響、上升靜默）
  - SAN ≤ −30：**陷入恐慌** — 60 tick 補刷失明 I + 虛弱 I
  - SAN = −45：**理智觸底** — 追加緩速 IV，沉淪傷害轉憂鬱 ×1.5

---

## 武器一覽

### 環指筆刷
> 材質：**下界合金劍** | CustomModelData: 1001 | +8 傷 / −2.4 速

右鍵對目標造 3.5 傷 + 隨機負面 potion effect + Limbus 隨機屬性池（流血/燒傷/易損/沉淪/破裂/震顫/束縛，排除 buff 三種）1p/3c。1.5 秒內對同目標再次右鍵觸發雙效。

### 擬態
> 材質：**鑽石劍** | CustomModelData: 1006 | +12 傷 / −3.2 速

10% 機率暴擊追加 40~90 隨機傷害；吸取 25% 最終傷害。**暴擊觸發時給自己 3 potency / 4 count 強壯**（下 4 擊 +30% 出手）。

### DaCapo
> 材質：**鐵劍** | CustomModelData: 1007

取消一般攻擊改為連擊（波及 3.5 格內敵人 70% 傷害）：

| 模式 | 機率 | 連擊數 | 單擊傷 | 間隔 |
|------|------|--------|--------|------|
| 普通 | 60%  | 5 擊   | 1.5   | 2 tick |
| 特殊 | 40%  | 3 擊   | 5     | 4 tick |

每音符命中 apply 1p/1c 沉淪，緊接的傷害立即消耗 → 額外真傷。

### 莊嚴哀悼（黑 / 白）
> 材質：**弩** | CMD: 1002 / 1003 | 隱藏「快速上弦 V」

兩段式：右鍵上弦 → 再右鍵發射蝴蝶投射物。冷卻 400ms。
- 黑：命中 8 傷 + 凋零 II（4s） + 沉淪 4p/3c
- 白：命中 4 傷 + 失明（3s） + 沉淪 3p/2c

> ⚠ 玩家背包同時有普通箭與蝴蝶時 vanilla 可能優先普通箭上弦，建議蝴蝶放副手或 hotbar 第一格。

### 生蝶、亡蝶
> 材質：**箭** | CMD: 1004

莊嚴哀悼專用彈藥。普通弓弩無法擊發。

### 聖宣
> 材質：**盾牌** | CMD: 1005

持有時每 5 tick 對半徑 5 格內敵人施加 vanilla 緩慢 II + Limbus 束縛 1p/2c；自身補守護 potency 到上限 3。

### 天退星刀
> 材質：**下界合金劍** | CMD: 1008 | +8 傷 / −2.4 速

居合衝刺，消耗虎標彈（火藥）：

| 操作 | 蓄力 | 傷害 | 額外 |
|------|------|------|------|
| 右鍵（虎標彈） | 1s | 8 | 燃燒 3s + **震顫 5p / 燒傷 4p/3c** |
| 潛行右鍵（猛虎標彈） | 3s | 18 | 燃燒 5s + 凋零 II + **震顫 8p / 燒傷 6p/4c** |

震顫達 5 potency 後任何受擊觸發爆發 → 潛在超額真傷 + 灼熱派生。

**虎標彈 / 猛虎標彈**（GUNPOWDER）為蓄力消耗物。

**插翅虎**（TRIAL_KEY）：右鍵開包 → 1 刀 + 10 猛虎 + 20 虎標，刀進 storage 不進主手。

### 薄暝
> 材質：**下界合金劍** | CMD: 1009 | +10 傷 / −2.8 速 | 互動距離 +1.5

- 30% 真傷 + 瀕死血量倍率（最多 ×2.5）
- 潛行右鍵蓄力 1.5s → 扇形暮光斬（前方 ±55°）：命中每目標 apply **破裂 5p/2c**

**終末鳥**（TRIAL_KEY）：右鍵開包 → 1 把薄暝，需 1 格空位。

### 提比婭
> 材質：**下界合金劍** | CMD: 1010 | +10 傷 / −2.8 速 | 互動距離 +1

Callisto 用自身骨骼鍛造的巨劍。「你越打我，你流越多血。」

- 每擊命中 apply **流血 3p/2c**（本體流血是**攻擊者受自傷** — 對高攻速怪超強）
- **Melody 加成**：讀目標當前流血 potency，每 3p +3%（上限 +30%）當場加傷
- **潛行右鍵蓄力 2s → 解剖斬 Anatomize**（前方扇形 ±60°，5 格範圍）：
  - 每目標 16 基礎傷（65% 常規 + 35% 真傷）
  - Apply 流血 12p/6c → **強制引爆 3 次**（無視需受目標揮砍才觸發的條件）
- 冷卻 8s，Corpus 護體被動：wither / 燃燒降階

### W公司 匕首（v3.0）
> 材質：**鐵劍** | CMD: 1011 | +4 傷 / −1.6 速

W 公司 3 級清掃人員的匕首。「至終，此為通路。」

- 每擊命中：apply 充能 1p/5c（potency 上限 10 → +30% 攻擊乘區）
- **20% 機率過載**：額外 +1 potency / +1 count 充能（**可突破上限**，最高可疊至 15+）

### 著影揮刀（v3.0）
> 材質：**下界合金劍** | CMD: 1012 | +9 傷 / −2.6 速

Meursault 的居合刀。「望月斬首──就此，氣絕吧。」

- 每擊命中：apply 呼吸法 1p/4c（potency 上限 10 → 50% 爆擊機率）
- **肉斬骨斷**（v3.0.1）：血量 < 3 顆心 + 蹲下 + 右鍵實體 → **原地站定五連斬**
  - 每 4 tick 一刀共 5 刀（20 tick / 1 秒）
  - 每刀 7 基礎傷 + SWEEP_ATTACK 劍氣拉線 + CRIT 命中粒子
  - 玩家強緩速 VII + 每 tick 拉回起始錨點防位移
  - 冷卻 12 秒；ActionBar 顯示「肉斬骨斷」

---

## 指令

| 指令 | 說明 |
|------|------|
| `/getego <weapon_id>` | 取得指定武器（需 `limbus.admin` 或 OP） |
| `/getego give <玩家> <weapon_id> [數量]` | 給予玩家武器（主控台可執行） |
| `/getego admin` | 開啟管理員 GUI（3 排武器分區） |
| `/getego catalog` | 開啟玩家圖鑑（**全部** / **LCE 研發限定** 兩頁籤）|

**Weapon IDs**：`brush`、`mimicry`、`dacapo`、`black`、`white`、`butterflies`、`shield`、`tiantui`、`tiger_mark`、`savage_tiger_mark`、`chatuhu`、`twilight`、`apocalypse_bird`、`tibia`、`w_corp_knife`、`bladesinger`

---

## 聲音方案（莊嚴哀悼）

vanilla 對「射手本人」的攻擊音是**客戶端預測播放**，伺服器封包攔截不到。分層方案：

| 對象 | 處理 | 需求 |
|------|------|------|
| **別人**聽你的莊嚴哀悼弓聲 | ProtocolLib 攔截座標/實體音封包 | 需裝 ProtocolLib（可選） |
| **射手本人**聽自己的預測音 | Fabric 客戶端 mod 攔截 `SoundManager.play` | 玩家自裝 [fabric-1.0.1](https://github.com/EvansGoethe/Limbus-E.G.O-Weapons/releases/tag/fabric-1.0.1) |

---

## 資源包

外部發布，onEnable 非同步同步到 `plugins/LimbusEGOWeapons/resourcepack.zip`（hash 對就跳過），交由 ResourcePackManager 合併分發：

- 來源：[Limbus-E.G.O-weapon-plugin-ResourcePack](https://github.com/EvansGoethe/Limbus-E.G.O-weapon-plugin-ResourcePack)
- 目前版本：**v.2.16**

各武器 `item_model` 與底材已寫在上方一覽，可供外部插件（如 BattlePass）引用。

---

## 安裝方式

1. 將 `.jar` 放入 `plugins/`
2.（可選）安裝 ProtocolLib，啟用莊嚴哀悼周圍的弓聲攔截
3. 啟動伺服器
4.（可選）玩家安裝 [fabric-1.0.1](https://github.com/EvansGoethe/Limbus-E.G.O-Weapons/releases/tag/fabric-1.0.1) 客戶端 mod 消除自身聽到的原版弓箭預測音

---

## Fabric 移植版

本插件已移植至 Fabric 1.21.4，請見 `master` branch。
