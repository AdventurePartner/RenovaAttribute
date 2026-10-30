# 多版本兼容

一个插件 jar 同时支持：

- **服务端**：Spigot、Paper、Purpur，**1.20.1 及以上**（含 1.21.x）。
- **Java**：字节码为 Java 17。1.20.1–1.20.4 跑在 Java 17 上，1.20.5 起跑在 Java 21 上，两者都能加载。
- **MythicMobs**（可选）：**5.6 及以上**。本插件用到的 Mythic 接口从 5.6.2 到 5.13.0 签名完全一致，没有按版本区分的代码。

启动时控制台会打印一行平台选择结果，排查问题时先看这一行：

```
Platform: Paper 1.21.10-R0.1-SNAPSHOT | modifiers=keyed+transient, damage=damage-source, equipment=paper-event, entities=paper, text=adventure
```

## 工程结构

| 模块 | 编译依赖 | 内容 |
|---|---|---|
| `core` | spigot-api 1.20.1、Mythic 5.6.2、AyCore | 全部业务逻辑，以及每项能力的"基线实现"（任何受支持的服务端都能跑） |
| `compat-modern` | spigot-api 1.21.10 | 比 1.20.1 新、但 Spigot 也有的 API |
| `compat-paper` | paper-api 1.21.10 | Paper 专有 API |
| 根工程 | — | 只负责用 shadow 把三个模块合成一个 jar |

core 只能看到最低版本的 API，误用新版或 Paper 专有 API 会直接编译失败。需要新 API 的代码放进 compat 模块，并在 `core/.../platform/Platform.kt` 里登记一个探测条件。探测按"某个类或方法是否存在"判断，不解析版本号。只有探测通过，才会按类名加载 compat 里的实现类，所以旧服务端永远不会链接到它没有的 API。

## 各项能力在不同服务端上的实现

| 能力 | 新实现（满足条件时） | 基线实现 |
|---|---|---|
| 修饰符标识 `modifiers` 前半 | `keyed`：1.21+（Spigot 与 Paper） | `uuid`：由 key 派生的固定 UUID |
| 修饰符写入 `modifiers` 后半 | `transient`：有 `addTransientModifier` 的服务端（Paper 全系） | `persistent`：会随实体存档，见下方注意事项 |
| 战斗脚本 `dealDamage` | `damage-source`：1.20.4+ | `legacy`：`damage(amount, attacker)`，同样记击杀归属 |
| 装备变化检测 | `paper-event`：Paper 1.21.4+ | `polling`：物品栏、换手、拾取、右键穿甲、发射器穿甲等事件即时触发，另加 `equipment-lore.poll-interval-ticks` 轮询兜底 |
| 实体进出世界 | `paper`：Paper 全系 | `bukkit`：生成事件与区块实体加载/卸载事件，另每 30 秒清理一次已消失实体的缓存 |
| Lore 与 ActionBar 文本 | `adventure`：Paper 全系 | `legacy`：旧版字符串 lore、BungeeCord 组件发送 ActionBar |

原版属性（最大生命、移速）一律按注册表 key 查找：1.21.3 把 `GENERIC_*` 常量改了名，`Attribute` 也从 enum 变成了接口。

## 注意事项

- **Spigot 上的属性修饰符是持久化的**。本插件的最大生命、移速修饰符会写进实体存档。正常停服或卸载插件时，已加载的实体会被清理。如果直接删掉插件 jar 再开服，离线玩家身上可能残留修饰符。关闭 `vanilla-sync` 的某一项后，实体再次加载时会自动删除对应的残留修饰符。
- **基线装备检测**下，其他插件通过 API 直接替换装备时，最多延迟一个 `poll-interval-ticks`（默认 20 tick）才会刷新。玩家自己的操作不受影响。
- core 里**不要调用 `Attribute` 等"1.20.1 时是 class/enum、之后改成接口"的类型上的方法**，否则新版服务端会抛 `IncompatibleClassChangeError`。这类对象在 core 里只能作为参数传回服务端。
