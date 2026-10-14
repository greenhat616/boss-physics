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
- [x] 自动测试：`tools/autotest.sh` 用新存档、新人物启动客户端，`AutoTest` 按脚本依次生成 boss 并采集；另有 `/bp spawnboss`、`/bp autotest` 和只截测试窗口的 `tools/screenshot.ps1`
- [x] 运行时审计：4 个测试 boss 都已采集，客户端没有出现 `UNRESOLVED`，两端完全一致（审计文档第 5 节）

验收：已完成。遗留：`EvilsProtectorAttack2Projectile` 没在 40 秒内出现；联机下的 owner 解析留到 M1 验收时实测。

### M1 墙挡住 boss 弹体

- [x] patch `Projectile.postInit()` 的出口（`ProjectilePostInitPatch` → `ProjectileRules.apply`）：发射者是 boss 且弹体在白名单里时，设 `isSolid = true`；`canBounce` 是 protected 字段，用 `@Advice.FieldValue` 写回
- [x] 白名单 `ProjectileRules`：弹体 stringID → `ProjectileRule(solid, allowBounce)`。首批为 `evilsprotector1`、`evilsprotector2`、`crystaldragonshard`，规则都是 `BLOCKED`（挡住、不反弹）
- [x] 发射者为 null 的兜底：只按弹体类判断（M0 实测没出现过）
- [x] `-Dbossphysics.rules=off` 关闭全部规则，用于对照测试
- [x] 自动测试加"墙盒模式"（`tools/autotest.sh <bosses> <秒数> walled <on|off>`）：玩家站在 7×7 石墙圈中央，按弹体统计打墙、打玩家的次数，并区分"在墙圈内生成"的弹体
- [x] 联机实测：独立服务器 + 客户端（`tools/autotest-mp.sh`），两端撞墙次数一致（审计文档第 8 节）
- [ ] 扩充白名单到其他 boss（`docs/projectile-audit.md` 第 3 节的候选），回旋镖和跟踪弹要单独验证撞墙行为

验收（2026-10-14 单人实测，详见审计文档第 6 节）：
- [x] 白名单弹体打到墙上就消失：Evils Protector 22+4 次、Crystal Dragon 61 次全部停在墙上，两端计数一致
- [x] 墙后的玩家不受伤：墙外生成的白名单弹体打中墙盒内玩家的次数为 0（对照组：规则关闭时会穿墙打中）
- [x] 联机一致性的单人部分：客户端和服务端的撞墙次数完全一致

### M2 弹体撞墙造成伤害

- [x] patch `Projectile.onHit` 的入口（`ProjectileOnHitPatch` → `WallDamage`）：服务端、命中的是墙、弹体规则 `breaksWalls` 时，对该格调用 `doObjectDamage`
  - 伤害 = 弹体伤害 × `wallDamageMultiplier`（默认 1.0，`-Dbossphysics.wallDamageMultiplier`）
  - `toolTier` 取 `BossTiers`（暂定：Evils Protector / Queen Spider 1，Swamp Guardian 2，Crystal Dragon 6，其他 1）
  - attacker = 弹体，client = null；只处理 layer 0
- [x] **延迟 250 毫秒再扣墙**（`-Dbossphysics.wallDamageDelayMs`）：服务端的弹体模拟比客户端快，立即拆墙会让客户端的同一枚弹体在墙消失后穿过去。延迟后两端撞墙次数恢复一致
- [x] 同一枚弹体对同一格只造成一次伤害：首批白名单弹体撞墙即消失、不反弹，天然满足；以后加入会反弹的弹体时再处理
- [x] 定居点内默认不拆墙（`-Dbossphysics.damageInSettlements=true` 打开）。实测意外验证了：新世界出生点的长老村子整片都登记为定居点，在那里测试时所有撞墙都被跳过
- [x] 拆掉的墙掉落物品：原版 `onDestroyed` 在 client 为 null 时照样掉落（读代码确认，测试里没有专门检查掉落物）
- [x] 自动测试：墙盒自动挪到定居点以外；可选墙的种类（`tools/autotest.sh ... walled on <墙ID>`）；汇总每种弹体对每种墙"造成伤害 / 拆掉 / 被拒绝"的次数

验收（2026-10-14 单人实测，详见审计文档第 7 节）：
- [x] 能逐步打穿石墙：Evils Protector 每 4～5 发拆一面，Crystal Dragon 一发一面
- [x] 等级不够打不动：Evils Protector（1 级）对深石墙（2 级）31 次全部被拒绝；Crystal Dragon（6 级）照样能拆
- [x] 受保护地块、定居点不受影响：两者都已实测，受保护地块上的撞墙全部被拒绝（审计文档第 8 节）
- [x] 联机实测：拆墙结果同步，客户端没有出现弹体穿过已拆的墙

### M3 boss 爆炸拆墙

- [x] `ExplosionEvent.init()` 出口（`ExplosionEventInitPatch` → `ExplosionRules`）：仅服务端；owner 是 boss、爆炸 ID 在 `EXPLOSIONS` 里、原本不破坏物体时，设 `destroysObjects = true`，`toolTier` 取 boss 的工具等级。之后由原版 `serverTick` 对范围内的物体调用 `doExplosionDamage`（受保护地块、掉落、同步都走原版）。`destroysTiles` 保持 false
- [x] 对物体的伤害可以单独调倍率：patch `getTotalObjectDamage` 的出口，只作用于本 mod 打开的爆炸（`explosionWallDamageMultiplier`，默认 1.0）
- [x] 定居点：爆心和范围四角任一处在定居点内就不打开
- [x] 首批白名单只有 `boulderhit`（Swamp Guardian 的巨石）。其他候选：`captaincannonballexplosion`（Pirate Captain）、`voidwizardexplosion`（Void Wizard），等对应 boss 跑过平衡性测试再加。Ascended 的炸弹原版就会拆墙（等级 10），Sunlight Champion 的超新星半径 650 像素，不考虑
- [x] 自动测试改为统计墙体：每个服务端 tick 数一次墙圈还剩几面，记录第一次破墙的时间（爆炸拆墙不经过本 mod 的代码，只能这样统计）

验收（2026-10-14，平衡性测试基线，石墙，45 秒）：
- [x] Swamp Guardian 的巨石能炸掉墙：全封闭墙盒 17.3 秒后破墙，45 秒内拆掉 10/24 面；半掩体 11.4 秒破墙，拆掉 6/19 面
- 巨石爆炸半径只有 80 像素，玩家站在 5×5 墙盒正中时，落点要偏向墙才炸得到；玩家被爆炸击退到墙边后拆得更快

### M4 配置与发布准备

- [x] 设置文件 `cfg/mods/a632079.bossphysics.cfg`：用原版的 `ModSettings`（`BossPhysicsSettings`），游戏第一次启动时按默认值生成，每项带注释。白名单、等级表都在文件里（`PROJECTILES`、`EXPLOSIONS`、`SUMMONS`、`BOSS_TOOL_TIERS`）。原来的 `-Dbossphysics.*` 系统属性全部改为设置项
- [x] **没有接入 Mod Settings Lib**：设置里大部分是列表和表格，库的界面放不下；也省掉一个前置。以后如果要在游戏里改开关，再单独评估
- [x] 联机同步（`PacketBossPhysicsConfig`）：客户端也要模拟弹体，哪些弹体被墙挡住必须两端一致。服务器在玩家加入时和 `/bp reload` 后发送完整设置；客户端只在内存里使用，不写回自己的文件；在本机启动世界时换回本机文件的设置
- [x] `/bp reload` 重新读取文件并同步；`/bp status` 显示当前设置
- [x] README、创意工坊描述 `docs/workshop/description.bbcode`（中英文）
- [x] 测试脚本改为读设置：`tools/autotest.sh --set key=value` / `--config 文件`
- [ ] 预览图、介绍图和上传（发布前再做）

验收：
- [x] 联机时客户端用服务器的设置：独立服务器设 `blockProjectiles = false`、客户端文件保持默认，客户端的弹体同样是 `solid=false`，没有撞墙；客户端文件没被服务器的值覆盖（`tools/autotest-mp.sh ... blockProjectiles=false`，2026-10-14）
- [ ] `/bp reload` 在运行中改设置后同步给客户端：和加入时走同一个数据包，但没有单独实测

### M5 平衡性调整

原型（M1～M4）跑通后再做，目标是"躲在墙后"不能成为无代价的打法，同时 boss 战不被墙体机制搞得拖沓。

**A. boss 召唤物攻击墙后的玩家**（2026-10-14 提出）

目前 boss 召唤出来的小怪（水晶傀儡、蜘蛛幼崽、Evils Protector 的传送门小怪等）不算 boss，不受白名单影响，也不会主动拆墙。目标行为：

| 情况 | 召唤物行为 |
|---|---|
| 只是半个掩体，有路可绕 | 绕过去打，不拆墙 |
| 被完全围住，召唤物有近战能力且有门 | 优先破门 |
| 被完全围住，召唤物有近战能力但没有门 | 拆墙 |
| 召唤物有远程能力，视线被墙挡住 | 优先射击挡在中间的墙 |

- [x] 识别"boss 召唤物"（`SummonRules`）：`Mob.init()` 出口记下 mob ID 在 `SUMMONS` 里的新怪物，下一个服务端 tick 检查 64 格内有没有 boss，有才算召唤物（同种怪物的自然刷新不算），在定居点里出生的也不算。默认 `portalminion`、`spiderhatchling`、`crystalgolem`
- [x] 给召唤物加隐藏 buff `bossphysicssummon`（`CAN_BREAK_OBJECTS`），复用原版袭击者的拆墙寻路：路径可以穿过墙和门，撞上时每秒打一下（10 点）。原版每格每秒最多吃约 10 点拆除伤害，多只召唤物同时拆同一格不会更快
- [x] 原版拆除无视工具等级，`MobPathBreakDownPatch` 补上：墙的等级高于召唤者 boss 的等级、或在定居点内（`damageInSettlements` 关闭时）就不拆。蠕虫 boss 的身体节召唤时取头部的等级
- [x] 门优先：原版的拆除代价是门 40、墙"耐久 × 2.5"（石墙 250），寻路每格代价约 1。实测召唤物仍然常常拆墙，所以对召唤物改为门 20、墙"耐久 × 10"（`MobPathBreakDownCostPatch`）
- [x] 半掩体：不需要单独判断。寻路能绕过去时，绕路的代价远小于拆墙
- [x] 自动测试加两种布局：`halfcover`（去掉背对 boss 的一面）、`doorbox`（背对 boss 一面的正中是门）；统计召唤物的拆除命中（门 / 墙 / 被拒绝）
- [ ] 远程召唤物：目前只是和近战一样走过去拆。水晶傀儡的光束是激光，本来就穿墙。专门"朝挡路的墙开火"的 AI 节点暂不做，等有不穿墙的远程召唤物时再评估

**B. 平衡性测试框架**（2026-10-14 实现，详见 `docs/balance.md`）

- [x] `tools/balance-test.sh`：每个 boss 依次打 open / box / halfcover / doorbox 四场，`BalanceReport` 按判定标准给出 PASS/FAIL，写出报告
- [x] 规则：默认设置里的每个 boss 条目都必须通过；把机制扩展到新 boss 时，先在测试配置里加条目、跑测试，通过后再写进默认值
- [x] boss 整场不行动或提前离开时，换场地重打（最多 2 次）
- [x] 首批 4 个 boss 全部通过（审计文档第 9 节，最终一轮 2026-10-14 19:09）
- [ ] 把白名单扩展到其他 boss（候选见审计文档第 3 节），每个都要先过测试
- [ ] 每个组合多跑几次取中位数，降低随机性

**C. 数值与边界情况**

- [ ] 墙伤害倍率：目前 = 弹体伤害 × 1.0，Crystal Dragon 的碎片（115）一发拆一面石墙（耐久 100），Evils Protector（19～25）要 4～5 发。根据实测手感调整，或者改成按 boss 单独设倍率
- [ ] boss 工具等级表 `BOSS_TOOL_TIERS`：目前是暂定值，要按 boss 进度排完整
- [ ] 墙体完全打不坏（等级不够、受保护地块）时，boss 会一直打不到人，要不要给 boss 别的应对（例如让召唤物处理，或者原版的逃跑计时照常生效）
- [ ] boss 从头顶飞过时在墙内生成的弹体能打中人（M1 实测的现象），确认是否接受
- [ ] 白名单弹体会被树、石头等所有带碰撞箱的物体挡住，森林里打 boss 是否变得太容易；目前它们也会被弹体打坏（联机测试中碎片拆了一棵云杉），要决定只拆墙、门，还是所有物体都拆
- [ ] 定居点保护：定居点按"区域"登记，范围比肉眼看到的大（新世界出生点的长老村子整片都算），确认这个粒度是否合适

## 4. 配置项

已实现，见 README 的"设置"一节。下表是最初的草案，保留作对照。

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
