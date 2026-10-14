# Boss Physics — Roadmap

目标：让 Boss 的攻击遵守基本的物理规则，**会被墙挡住**，同时**部分攻击能打坏墙**。
目标游戏版本：1.3.3（与仓库内其他 mod 一致）。

## 1. 依据：原版已有的机制

以下结论来自 `.decompiled/src/necesse` 的反编译源码，开发时以实际行为为准。

| 机制 | 位置 | 对本 mod 的意义 |
|---|---|---|
| `Projectile.isSolid`（默认为 `true`）：弹体为 solid 时与墙碰撞，被挡住或反弹，并过滤墙后的目标 | `entity/projectile/Projectile.java` `getDistanceMovedBeforeCollision` / `checkCollision` / `perpLineCollidesWithLevel` | 把 boss 弹体改成 solid 即可复用全部原版碰撞逻辑 |
| 约 60 个 boss/怪物弹体在构造函数中写了 `isSolid = false` | 如 `CrystalDragonShardProjectile`、`SwampBoulderProjectile`、`QueenSpiderSpitProjectile`、`Ascended*`、`Cryo*`、`Empress*` | 需要处理的对象 |
| `Projectile.init()` 能拿到 owner，但子类会在自己的 `init()` 里先 `super.init()` 再改写 `isSolid`；`EntityList.add` 紧接着调用 `postInit()`，且没有弹体子类重写它 | `Projectile.java:330`、`EntityList.java:206` | 在 `postInit()` 出口 patch，此时能看到最终的 `isSolid` |
| `Projectile.canBreakObjects` 只在 owner 是玩家时为 true，且只影响 `attackThrough` 物体（草、花、蛋等），**不影响墙** | `Projectile.java:337`、`checkCollision` | 不能靠它拆墙，需要自己调用伤害接口 |
| 弹体撞墙时调用 `onHit(null, LevelObjectHit, x, y, ...)`，`LevelObjectHit` 带 `tileX/tileY` | `Projectile.java:1017` | 拆墙逻辑挂在这里 |
| `EntityManager.doObjectDamage(layer, tileX, tileY, damage, toolTier, attacker, client)`：自带同步、特效、掉落，并检查 `isProtected` 和 `toolTier` | `entity/manager/EntityManager.java:772`、`DamagedObjectEntity.java:405` | 拆墙用的接口；受保护的地块（部分 boss 场地和地牢）自动打不坏 |
| `ExplosionEvent` 支持 `destroysObjects` / `destroysTiles` / `toolTier`；原版所有 boss 爆炸都传 `false` | `entity/levelEvent/explosionEvent/ExplosionEvent.java` | patch 构造函数即可让 boss 爆炸拆墙 |
| 墙的 `toolTier`：木、石、砖墙为 0；地牢墙 1；深石墙 2；黑曜石墙 6；……；黎明、黄昏、远古遗迹等墙为 10 | `engine/registries/ObjectRegistry.java` `registerWallObjects` | 用来设计"哪个 boss 能打坏哪种墙" |
| `Mob.isBoss()` = `MobRegistry.isBossMob(id)` | `Mob.java:4028` | 判断 owner 是不是 boss；蠕虫类 boss 的身体节需要实测 |
| `@ModMethodPatch` / `@ModConstructorPatch`（基于 ByteBuddy advice） | `engine/modLoader/annotations` | 实现方式，参考 `coop-pause/src/main/java/cooppause/patches` |

## 2. 设计原则

1. **只用 patch，不注册新内容**。但弹体移动是客户端和服务端各自模拟的，所以 `clientside = false`，联机时两边都必须装这个 mod。
2. **拆墙只在服务端执行**：`doObjectDamage` 只在 `isServer()` 时调用，同步交给原版。
3. **用白名单**：只改明确列入的弹体类，没列入的保持原版行为。避免"从屏幕外刷出的弹幕全被墙挡住，boss 根本打不到人"。
4. **挡墙和拆墙必须一起开**：只开挡墙的话，玩家造个小掩体就能无伤打 boss。
5. **默认保护定居点**：定居点内默认不拆墙，可以在配置里打开。
6. **尊重原版保护**：受保护的地块（`isProtected`）和 `ToolType.UNBREAKABLE` 的物体不处理。

## 3. 里程碑

### M0 项目骨架与调研工具

- [x] 照 `coop-pause` 建 Gradle 项目，modID 为 `a632079.bossphysics`，能 `buildModJar` 并加载（无界面服务器冒烟测试通过，两个 patch 都已生效）
- [x] 调试指令 `/bp debug <on|off|dump|clear>`：记录每种非玩家弹体和爆炸，按"端、类、owner、碰撞标志"去重，`dump` 写出 `bossphysics-audit.csv`（单人模式下两端都能记录）
- [x] 静态审计，结果见 `docs/projectile-audit.md`：
  - 3 个测试 boss 的弹体清单
  - 蠕虫类 boss：弹体 owner 是头部，身体节注册时 `isBoss = true`
  - **子类会在自己的 `init()` 里改写 `isSolid`**，所以挂钩点改为 `Projectile.postInit()`
- [x] 弹体分类：**直射**（应被挡）、**抛物线 / 刷怪**（`canHitMobs = false`，保持原版）、**预警标记**（`*Warning*`，绝对不改）
- [ ] 运行时审计：进游戏用 `/bp debug` 采集 4 个测试 boss 的数据，重点确认客户端是否会出现 owner 为 `UNRESOLVED`

验收：运行时 CSV 覆盖 4 个测试 boss 的全部攻击，并填入审计文档第 5 节。

### M1 墙挡住 boss 弹体

- [ ] patch `Projectile.postInit()` 的出口（复用 M0 的 `ProjectilePostInitPatch`）：满足"owner 不为 null、是 boss、弹体类在白名单"时，设 `isSolid = true`
- [ ] 白名单的形式是"弹体 stringID → 规则"，规则包括：是否 solid、是否允许反弹（默认不反弹，`canBounce = false`）
- [ ] 客户端 owner 为 null 的兜底方案（以 M0 的结论为准）：改为只按弹体类判断，不看 owner
- [ ] 测试 boss 调整为 Evils Protector 和 Crystal Dragon（原来的 3 个里只有 Crystal Dragon 有可测的直射弹体）；Queen Spider 用来验证抛物线弹体不受影响
- [ ] 回旋镖和跟踪弹撞墙后的行为单独验证（如 `ReaperScytheProjectile` 撞墙会返回 boss）

验收：
- 测试 boss 的直射弹体打在墙上会消失，并有撞墙粒子效果
- 墙后的玩家不受伤
- 联机时客户端和服务端表现一致，不出现"看着被墙挡了却还掉血"

### M2 弹体撞墙造成伤害

- [ ] patch `Projectile.onHit` 的入口：服务端且 `mob == null && object != null && rule.breaksWalls` 时，对 `object.tileX/tileY` 调用 `doObjectDamage`
  - 伤害 = 弹体伤害 × `wallDamageMultiplier`（默认 1.0）
  - `toolTier` 取 boss 对应的档位（见第 4 节）
  - attacker = 弹体，client = null
- [ ] 层（layer）：先只处理 layer 0，即墙、门、物件所在层
- [ ] 同一个弹体对同一格只造成一次伤害（复用 `hasHit(tileX, tileY)` / `addHit`）
- [ ] 定居点判断：在定居点范围内且 `damageInSettlements = false` 时跳过
- [ ] 确认拆掉的墙会不会掉落物品，掉落是否合理

验收：测试 boss 能逐步打穿木墙和石墙；打不穿地牢墙和更高级的墙（取决于档位）；受保护的场地墙不受影响；定居点默认不受影响。

### M3 boss 爆炸拆墙

- [ ] patch `ExplosionEvent.init()` 的出口（复用 M0 的 `ExplosionEventInitPatch`，`ownerMob` 在这里才解析）：owner 是 boss，且该爆炸类在白名单里时，设 `destroysObjects = true`、`toolTier = boss 档位`；`destroysTiles` 默认保持 false
- [ ] 候选爆炸：`CaptainCannonBallExplosionEvent`、`BoulderHitExplosionEvent`（Swamp Guardian 的巨石）、`VoidWizardExplosionEvent` 等，以 M0 审计为准
- [ ] 同样遵守定居点开关

验收：Swamp Guardian 的巨石落地后，能炸掉范围内的低级墙。

### M4 配置与发布准备

- [ ] 配置文件 `cfg/mods/a632079.bossphysics.cfg`，并接入 Mod Settings Lib（与 coop-pause 一致）
- [ ] 指令 `/bp reload` 重新读取配置；`/bp status` 显示当前开关
- [ ] 写 README，以及创意工坊的 `description.bbcode`

验收：所有开关都能在不重启的情况下生效（至少独立服务器上 `/bp reload` 可用）。

## 4. 配置项（草案）

| 键 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 总开关 |
| `blockProjectiles` | true | M1：墙挡住白名单弹体 |
| `projectilesBreakWalls` | true | M2：弹体撞墙造成伤害 |
| `explosionsBreakWalls` | true | M3：boss 爆炸拆墙 |
| `wallDamageMultiplier` | 1.0 | 弹体对墙伤害的倍率 |
| `damageInSettlements` | false | 定居点内是否拆墙 |
| `defaultBossToolTier` | 1.0 | 没有单独配置的 boss 用的档位：能打坏 0 级墙（木、石、砖）和地牢墙 |
| `bossToolTiers` | 见下 | 按 boss stringID 单独设档位 |
| `projectileRules` | 见 M1 | 弹体白名单 |

`bossToolTiers` 的初始建议：前期 boss 为 1，中期为 2～6，后期为 10。M2 实测后再调整。

## 5. 不在原型范围内（后续再评估）

| 项 | 原因 |
|---|---|
| 爆炸伤害生物时被墙挡住（检查视线） | 需要改 `ExplosionEvent` 的生物命中逻辑，平衡影响大 |
| 激光类（`laserProjectile/*`）在墙上截断 | 激光不走碰撞逻辑，需要逐个类改长度和渲染 |
| 冲锋、地面 AOE 等 `mobAbilityLevelEvent`（78 个类） | 各自实现命中判定，工作量与覆盖范围成正比 |
| boss 本体被墙挡住或撞穿墙 | 飞行和蠕虫类 boss 的 AI 不走寻路，强行加碰撞容易卡死 |

## 6. 风险

| 风险 | 应对 |
|---|---|
| 客户端初始化弹体时拿不到 owner，两边 `isSolid` 不一致 | M0 实测；不行就改为只按弹体类判断 |
| 某些攻击依赖穿墙（boss 在墙外、弹幕从屏幕外刷出），改了之后 boss 打不到人 | 用白名单逐个验证，默认不改 |
| boss 在定居点附近召唤，拆掉基地 | `damageInSettlements` 默认关闭；用档位限制能拆的墙 |
| 游戏更新后 patch 的方法签名变化 | patch 尽量少（预计 3 个：`Projectile.postInit`、`Projectile.onHit`、`ExplosionEvent.init`），更新后先跑 M0 的调试指令回归 |

## 7. 已确认的决定（2026-10-14）

- mod 名称和 ID：`Boss Physics` / `a632079.bossphysics`，目录 `boss-physics`
- 定居点内默认不拆墙（`damageInSettlements = false`），可以在配置里打开
- 抛物线类弹体（蜘蛛吐液、蛋、部分巨石）飞越墙体，只在落点生效；只有直射类弹体会被墙挡住
- 被拆的墙掉落物品，保持原版接口的默认行为（M2 时确认 client 为 null 时确实会掉）
