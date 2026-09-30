# 战斗脚本（本轮伤害脚本）

RenovaAttribute 的 Lua 分两层：

| | 属性公式 `formula` | 战斗脚本 `combat` |
|---|---|---|
| 什么时候执行 | 属性快照重算时（换装备、Buff 变化） | 每一次命中 |
| 能拿到什么 | 本属性的叠加值、其他属性的值 | 攻击者、受击者、本次伤害的全部工作变量 |
| 产出 | 一个静态数字 | 直接修改本轮伤害、取消命中、排队执行效果 |
| 适合 | 力量 → 物理伤害 这类派生属性 | 穿透、闪避、斩杀、反伤、元素抗性 |

公式里用 `math.random` 不会“每次攻击随机”，因为结果会随快照缓存；需要按次随机请写战斗脚本。

## 一次命中的流程

1. **创建会话**：读取双方属性快照，初始化工作变量（见下表）。
2. **计算阶段**：按优先级依次执行所有 `ATTACK` / `DEFENSE` 处理器（内建处理器与脚本在同一队列）。任一处理器调用 `ctx:cancel()` 后，剩余处理器不再执行。
3. **写回**：`damage` 写回事件；被取消则取消事件（闪避）。
4. **结算后阶段**（下一 tick）：先执行计算阶段排队的效果，再按优先级执行 `AFTER_ATTACK` / `AFTER_DEFENSE` 处理器（含内建真伤、吸血），最后执行它们排队的效果。
   - 被我们取消的命中：只执行计算阶段已排队的效果（例如“闪避！”提示），不执行结算后处理器。
   - 被其他插件（如领地保护）取消的命中：丢弃所有效果。

## 配置写法

在 `attributes/custom/*.yml` 的属性定义里加 `combat` 列表，一个属性可以挂多个处理器：

```yaml
internal: armor-penetration
display-name: '护甲穿透'
format: PERCENT
min-value: 0.0
max-value: 1.0
lore-mode: FLAT
lore-percent-value: true
combat:
  - trigger: ATTACK        # ATTACK / DEFENSE / AFTER_ATTACK / AFTER_DEFENSE
    priority: 350          # 必填，越小越先执行
    # run-when-zero: false # 默认只有持有者该属性不为 0 时才执行；true 可写全局规则
    script: |
      ctx:set_defense(ctx:defense() * (1 - value))
```

较长的脚本可以放到文件里：`script-file: scripts/xxx.lua`（路径相对插件目录，必须位于 `scripts/` 下），文件内容写成：

```lua
return function(ctx, value)
  -- ...
end
```

脚本编译失败时 `/ra reload` 整体回滚，控制台会给出文件名/属性键和行号。

| trigger | 执行阶段 | 谁持有属性时执行 | `value` 取自 |
|---|---|---|---|
| `ATTACK` | 计算阶段 | 攻击者 | 攻击者 |
| `DEFENSE` | 计算阶段 | 受击者 | 受击者 |
| `AFTER_ATTACK` | 结算后阶段 | 攻击者 | 攻击者 |
| `AFTER_DEFENSE` | 结算后阶段 | 受击者 | 受击者 |

## 执行顺序与内建处理器

排序规则：先比 `priority`，再让 ATTACK 先于 DEFENSE（AFTER_ATTACK 先于 AFTER_DEFENSE），最后按处理器 id（属性键#序号）。

| 优先级 | 内建处理器 | 作用 |
|---|---|---|
| 100 | `renova:damage-boost` | 伤害 × (1 + damage_boost) |
| 200 | `renova:critical` | 未被强制暴击时按 crit_chance 掷骰；暴击则伤害 × (1 + crit_damage) |
| 400 | `renova:defense` | 伤害 × K / (defense + K)，K 为 `damage.defense-constant` |
| 结算后 100 | `renova:true-damage` | 扣除 true_damage |
| 结算后 200 | `renova:lifesteal` | (实际扣血 + 真伤扣血) × lifesteal |

内建优先级可在 `config.yml` 的 `damage.builtin-priorities` 中调整。脚本想影响某个内建处理器，就要排在它**之前**：例如暴击抵抗要 < 200，穿透要 < 400。

## 工作变量

会话创建时从属性快照初始化，计算阶段内可读写，内建处理器在自己的优先级读取它们。截断（如暴击率限制在 0~1、防御不低于 0）只在内建处理器使用时进行；所有 setter 都拒绝 NaN 和无穷大。

| 变量 | 初始值 |
|---|---|
| `damage` | 原版伤害 + 物理/法术伤害属性（属性部分乘横扫系数和冷却系数；`use-vanilla-base-damage: false` 时不加原版伤害） |
| `damage_boost` | 攻击者 `damage-boost` |
| `crit_chance` / `crit_damage` | 攻击者 `critical-chance` / `critical-damage`（`critical-hits: false` 时暴击率为 0） |
| `defense` | 受击者物理或法术防御（按伤害类型） |
| `true_damage` | 攻击者 `true-damage`（`true-damage: false` 时为 0） |
| `lifesteal` | 攻击者 `lifesteal`（`lifesteal: false` 时为 0） |

**乘区约定**：想和同类加成**加算**，就改工作变量，例如 `ctx:set_damage_boost(ctx:damage_boost() + value)`（排在 100 之前）；想作为**独立乘区**，就用 `ctx:mul_damage(...)`。

**`damage` 的含义**：它是写回给 Bukkit 的基础伤害。`damage.vanilla-reduction: KEEP`（默认）时，原版护甲、抗性效果、保护附魔之后还会再减一次；`IGNORE_ARMOR` 去掉原版护甲；`IGNORE_ALL` 去掉护甲、抗性和保护（保留盾牌格挡与伤害吸收）。其他插件如果在我们之后再调用 `setDamage(double)`，原版护甲减伤会被重新计算回来。

## `ctx` API

脚本参数：`ctx`（本轮伤害）与 `value`（持有者身上本属性的值）。`ctx` 是只读对象，`ctx.xxx = 1` 会报错，修改请用方法。

### 只读字段

| 字段 | 说明 |
|---|---|
| `ctx.attacker` / `ctx.defender` | 实体代理（见下） |
| `ctx.type` | `'physical'` 或 `'magic'` |
| `ctx.cause` | Bukkit DamageCause 名称，如 `'ENTITY_ATTACK'` |
| `ctx.source` | `'vanilla'` 或 `'mythic'` |
| `ctx.projectile` | 是否弹射物命中 |
| `ctx.original_damage` | 进入流水线前的原始伤害 |
| `ctx.element` | Mythic damage 的 element 参数原样字符串；原版伤害为 nil |
| `ctx.ignores_armor` | Mythic damage 是否 `ignoreArmor=true` |
| `ctx.attack_cooldown` | 玩家近战的蓄力（0~1），其他情况为 1 |
| `ctx.tick` | 命中时的服务器 tick |
| `ctx.stage` | `'calculate'` 或 `'settle'` |
| `ctx.final_damage` | 结算后阶段：受击者因普通伤害实际损失的生命；计算阶段为 nil |

### 修改本轮伤害（仅计算阶段，结算后阶段调用会报错）

| 方法 | 说明 |
|---|---|
| `ctx:damage()` / `ctx:set_damage(v)` / `ctx:add_damage(v)` / `ctx:mul_damage(m)` | 伤害 |
| `ctx:damage_boost()` / `ctx:set_damage_boost(v)` | 增伤（100 之前修改才生效） |
| `ctx:crit_chance()` / `ctx:set_crit_chance(v)` | 暴击率（200 之前修改才生效） |
| `ctx:crit_damage()` / `ctx:set_crit_damage(v)` | 暴击伤害（200 之前） |
| `ctx:is_crit()` / `ctx:set_crit(bool)` | 200 之前设为 true = 强制暴击；200 之后读取 = 本次是否暴击 |
| `ctx:defense()` / `ctx:set_defense(v)` | 防御（400 之前） |
| `ctx:true_damage()` / `ctx:set_true_damage(v)` / `ctx:add_true_damage(v)` | 真实伤害 |
| `ctx:lifesteal()` / `ctx:set_lifesteal(v)` | 吸血比例 |
| `ctx:cancel()` / `ctx:is_cancelled()` | 取消本次命中（闪避），后续计算处理器不再执行 |

### 共享变量与工具

| 方法 | 说明 |
|---|---|
| `ctx:set(name, v)` / `ctx:get(name, default)` | 同一次命中内跨脚本传值；只允许数字、布尔、字符串，`nil` 删除。建议命名为 `属性名.变量名` |
| `ctx:chance(p)` | 以概率 p 返回 true |
| `ctx:random(a, b)` | [a, b) 内的随机小数 |
| `ctx:cooldown(entity, name, ticks)` | 冷却已结束则返回 true 并重新计时，否则返回 false。按实体存储，实体死亡/移除时清空。`name` 在所有脚本间共享，请带上属性名 |
| `ctx:has_damage_tag(tag)` | Mythic 伤害是否带某个 damage tag（不区分大小写） |

### 效果（排队执行，两个阶段都可调用）

效果不会在脚本执行途中生效，而是在当前阶段所有处理器执行完后统一执行，所以不会出现“脚本执行到一半又触发另一次伤害”。

| 方法 | 说明 |
|---|---|
| `ctx:heal(entity, amount)` | 治疗，不超过最大生命 |
| `ctx:deal_damage(from, to, amount)` | 造成无视原版护甲的伤害，带击杀归属；**不会再次进入属性结算**，所以反伤互打不会无限递归 |
| `ctx:add_buff(entity, key, mode, value, ticks[, tag])` | 临时属性，`mode` 为 BASE / FLAT / PERCENT / MULTIPLY；同 tag 覆盖旧 Buff（默认 tag 为 `combat:<处理器id>:<属性键>`）。每次添加都会重算一次快照，高频命中请配合冷却 |
| `ctx:message(entity, text)` / `ctx:actionbar(entity, text)` | 聊天栏 / 动作栏消息，支持 `&` 颜色码 |
| `ctx:cast_skill(caster, skill[, target])` | 释放 MythicMobs 技能（需要启用 MythicMobs 集成）。粒子、音效、药水等表现建议都交给 Mythic 技能 |

`entity` 参数只能是 `ctx.attacker` 或 `ctx.defender`。

### 实体代理（`ctx.attacker` / `ctx.defender`）

| 方法 | 说明 |
|---|---|
| `:attr(key)` | 本次命中时的属性快照值；键不存在会报错。`'physical-damage'` 等价于 `'renova:physical-damage'` |
| `:health()` / `:max_health()` | 实时生命 / 最大生命 |
| `:is_dead()` / `:is_valid()` | 实时状态 |
| `:is_player()` / `:type()` / `:name()` / `:uuid()` | `type()` 形如 `'minecraft:zombie'` |
| `:has_tag(tag)` | 是否有该 scoreboard tag |

## 注意事项

- **不要在文件顶层保存可变状态**。`script-file` 只编译一次，顶层的 `local t = {}` 会一直存活并不断变大；需要冷却请用 `ctx:cooldown`，需要层数可以用 Buff。
- 脚本不能定义全局变量、不能使用 `require` / `io` / `os` / `load` / `getmetatable` / `setmetatable`。
- 每个处理器单次最多执行 `lua.max-instructions-per-combat-script`（默认 20000）条指令，超出即中止。
- 单个处理器出错时默认记录日志（同一处理器每分钟最多一次）并跳过；`damage.script-error-policy: VANILLA` 则让整次命中退回原版伤害。
- 横扫攻击每个目标都会完整计算属性伤害，可用 `damage.sweep-ratio` 降低；`damage.scale-by-attack-cooldown: true` 让属性伤害随攻击冷却缩放，避免狂点。
- Mythic 技能设了 `ignoreArmor=true` 时，`mythicmobs.ignore-armor-bypasses-defense: true` 可同时跳过内建属性防御。
- 摔落、火焰、爆炸等没有攻击实体的伤害目前不进入流水线。

## 调试

`/ra debug damage [玩家]`（需要 `renovaattribute.admin`）开关某个玩家的伤害追踪。该玩家参与的每次命中都会向你输出：原始伤害、初始伤害、每个处理器的优先级、`value`、它改变了哪些工作变量、耗时，以及最终结果（实际扣血、真伤扣血）。脚本报错也会显示在对应行。

## 其他插件注册处理器

```java
RenovaApi.getCombat().registerHandler(new CombatHandler() {
    public String getId() { return "myplugin:boss-shield"; }
    public CombatTrigger getTrigger() { return CombatTrigger.DEFENSE; }
    public int getPriority() { return 450; }
    public AttributeKey getAttribute() { return null; }      // null = 每次都执行，value 为 0
    public boolean getRunWhenZero() { return true; }
    public void handle(DamageSession session, double value) {
        if (session.getDefender().hasTag("boss")) {
            session.setDamage(Math.min(session.getDamage(), 50));
        }
    }
});
```

通过 API 注册的处理器在 `/ra reload` 后仍然保留，id 不能与内建或脚本处理器重复。

## 示例

`attributes/custom/` 下附带以下示例，去掉 `.example` 后缀即可启用：

| 文件 | 效果 | 要点 |
|---|---|---|
| `armor-penetration.yml.example` | 按比例无视防御 | ATTACK 350，排在内建防御前 |
| `dodge.yml.example` | 几率闪避 | DEFENSE 50，`cancel` + 动作栏提示 |
| `critical-resistance.yml.example` | 降低攻击者暴击率 | DEFENSE 190，排在内建暴击前 |
| `execute.yml.example` | 目标低血量时增伤 | ATTACK 150，独立乘区 |
| `thorns.yml.example` | 反伤 | AFTER_DEFENSE，`final_damage` + 冷却 + `deal_damage` |
| `fire-resistance.yml.example` | 火焰元素抗性 | 读取 `ctx.element` |
| `pvp-reduction.yml.example` | 受到玩家攻击时减伤 | `ctx.attacker:is_player()` |
