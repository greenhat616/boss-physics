# Boss 弹体审计（M0）

分两部分：**静态审计**来自 `.decompiled/src/necesse` 的源码阅读，已完成；**运行时审计**要进游戏用 `/bp debug` 采集，待完成。

## 1. 结论速览

1. **挂钩点必须是 `Projectile.postInit()`，不能是 `init()`**。boss 弹体在自己的 `init()` 里先调用 `super.init()`，再写 `isSolid = false`（例如 `QueenSpiderSpitProjectile.java:47-50`），所以在 `Projectile.init()` 出口改的值会被子类覆盖。`EntityList.add` 在 `init()` 之后立即调用 `postInit()`（`EntityList.java:206-207`），而且没有任何弹体子类重写 `postInit()`。
2. **"抛物线类"可以靠 `canHitMobs = false` 识别**。这类弹体本身不打人，靠落点生成的事件造成效果（吐液、蛋、巨石、刷怪），保持原版飞越墙体即可，不需要进白名单。
3. **蠕虫类 boss 的身体节也算 boss**：`swampguardianbody`、`swampguardiantail`、`crystaldragonbody` 注册时 `isBossMob = true`（`MobRegistry.java:634-668`）。测试 boss 的弹体 owner 都传的是头部 `mob`，即使从身体节位置发射也是如此。
4. **boss 弹体都是服务端 AI 生成、再发包给客户端的**。客户端在 `applySpawnPacket` 里按 uniqueID 找 owner（`Projectile.java:263`），找不到时 `getOwner()` 之后还会再试。boss 不在客户端已加载区域时 owner 可能是 null，**这点要靠运行时审计确认**（`owner` 一列出现 `UNRESOLVED` 就说明会发生）。
5. **原测试 boss 只有 Crystal Dragon 有可测的直射弹体**。Queen Spider 的弹体全是抛物线；Swamp Guardian 的剃刀原版就会被墙挡（而且原版只在视线畅通时才发射），巨石是抛物线。建议把 M1 的测试 boss 换成 **Evils Protector**（最早期的 boss，直射弹体多）和 **Crystal Dragon**；Swamp Guardian 留给 M3 测巨石爆炸，Queen Spider 用来验证抛物线弹体不受影响。

## 2. 测试 boss 的攻击清单

| Boss | 攻击 | 实现 | 原版 `isSolid` | 分类 | 处理 |
|---|---|---|---|---|---|
| Queen Spider | 吐液 | `QueenSpiderSpitProjectile` → 落点 `QueenSpiderSpitEvent` | false | 抛物线（`canHitMobs = false`） | 不改 |
| Queen Spider | 产卵 | `QueenSpiderEggProjectile` | false | 抛物线 / 刷怪 | 不改 |
| Swamp Guardian | 剃刀 | `SwampRazorProjectile`（穿透 2，反弹 8） | **true** | 直射 | M1 已满足；M2 可加撞墙伤害 |
| Swamp Guardian | 巨石 | `SwampBoulderProjectile` → 落点 `BoulderHitExplosionEvent` | false | 抛物线 | M3：落点爆炸拆墙 |
| Crystal Dragon | 侧舷齐射 | `CrystalDragonShardProjectile`，从每节身体两侧垂直射出 | false | 直射 | **M1 / M2 主要测试对象** |
| Crystal Dragon | 沿途水晶炸弹 | `CrystalBombEvent`（也会生成碎片） | — | 事件 | M3 候选 |
| Crystal Dragon | 激光螺旋 | `CrystalDragonLaserLevelEvent` | — | 激光 | 不在原型范围 |
| Crystal Dragon | 召唤水晶傀儡 | `CrystalGolemSpawnProjectile` | false | 抛物线 / 刷怪 | 不改 |

## 3. 全部 `isSolid = false` 弹体的初步分类

判断方法：`canHitMobs = false` 记为"不直接打人"，再按 `new XxxProjectile(` 的调用位置找使用者。只列 boss 和 boss 事件相关的，玩家武器、套装效果和普通怪略去。

**直射，M1 白名单候选**

| 弹体 | 使用者 |
|---|---|
| `EvilsProtectorAttack1Projectile`、`EvilsProtectorAttack2Projectile`（跟踪） | Evils Protector |
| `CrystalDragonShardProjectile` | Crystal Dragon、`CrystalBombEvent` |
| `AncientVultureProjectile` | Ancient Vulture |
| `FallenWizardWaveProjectile` | Fallen Wizard |
| `ReaperScytheProjectile`（回旋镖） | Reaper |
| `CryoShardProjectile`、`CryoWaveProjectile`、`CryoVolleyProjectile`、`CryoQuakeProjectile` | Cryo Queen |
| `EmpressSlashProjectile`、`EmpressWebBallProjectile` | Spider Empress |
| `CrescentDiscFollowingProjectile`、`StarVeilProjectile` | Moonlight Dancer |
| `AscendedOrbProjectile`、`AscendedBoltProjectile`、`AscendedShardBombProjectile`、`AscendedBeamProjectile` | Ascended Wizard 及其阶段 |
| `MutantHydraGooSprayProjectile` | 变异九头蛇 |
| `SpiritBeamProjectile`、`SmiteBeamProjectile` | 由 LevelEvent 生成，owner 要看运行时数据 |

**不直接打人，保持原版**

`QueenSpiderSpit/Egg`、`SwampBoulder`、`CrystalGolemSpawn`、`SlimeEgg`（Mother Slime）、`EmpressAcid`、`EmpressSlashWarning`、`CryoWarning`、`MutantHydraSpit`、`AscendedGolemSpawn`、`AscendedFracture`、`PhoenixFeather` 等。

注意：
- 带 `Warning` 的弹体是预警标记，绝对不能改成会被墙挡。
- 回旋镖和跟踪弹（`ReaperScythe`、`EvilsProtectorAttack2`、`CrescentDisc`）撞墙后的行为要单独实测：回旋镖撞墙会走 `returnToOwner()`。

## 4. 运行时审计方法

全自动，不需要手动操作游戏：

```bash
tools/autotest.sh [boss1,boss2,...] [每个 boss 的秒数，默认 40]
```

脚本做的事：
1. 构建 mod，在 `build/autotest/data` 新建数据目录（**新存档、新人物**，不碰你自己的存档、设置和 mod 列表）
2. 生成 `modlist.data`：只启用本 mod，创意工坊订阅的 mod 全部禁用
3. 写一份 1280×720 窗口、静音的 `settings.cfg`
4. 以 `-load bptest -character BossTester` 启动客户端，自动创建世界并进入单人模式。原版 `-character` 只在已有其他人物时才会新建，所以 mod 在 `postInit` 里先建好人物（`TestCharacter`）
5. mod 读到 `-Dbossphysics.autotest` 后，由 `AutoTest` 在 `Server.tick()` 里按脚本执行：玩家无敌 → 时间调到午夜 → 在 700 像素外生成 boss → 战斗一半时把玩家传送到 4000 像素外 4 秒再传回 → 移除 boss → 下一个
6. 全部结束后写出 `build/autotest/data/bossphysics-audit.csv` 并退出游戏

画面检查：`pwsh tools/screenshot.ps1` 只截测试客户端窗口（用 `PrintWindow`，窗口被遮挡也能截）。

手动调试可用 `/bp spawnboss <mobid> [距离]` 和 `/bp autotest <start|stop> [boss 列表]`。

CSV 各列：`kind, side, id, class, superclass, owner, ownerIsBoss, isSolid, canBounce, canHitMobs, canBreakObjects, destroysObjects, destroysTiles, toolTier, count`。

## 5. 运行时结果（2026-10-14，两次运行，每个 boss 40 秒）

第二次运行（已修正 Evils Protector 白天逃走的问题）的完整结果。server 行和 client 行**全部一一对应**，这里合并成一行：

| 弹体 / 爆炸 | owner | ownerIsBoss | isSolid | canHitMobs | 次数（每端） |
|---|---|---|---|---|---|
| `evilsprotector1`（`EvilsProtectorAttack1Projectile`） | evilsprotector | true | false | true | 42 |
| `crystaldragonshard` | crystaldragon | true | false | true | 76 |
| `crystalgolemspawn` | crystaldragon | true | false | false | 6 |
| `crystalgolembeam`（`LaserProjectile`） | crystalgolem | **false** | true | true | 29 |
| `swampboulder` | swampguardian | true | false | false | 28 |
| 爆炸 `boulderhit` | swampguardian | true | — | — | 26（`destroysObjects = false`，`toolTier = 0`） |
| `queenspideregg` | queenspider | true | false | false | 4 |
| `queenspiderspit` | queenspider | true | false | false | 6 |
| `zombiearrow` | zombiearcher | false | true | true | 16（夜晚刷出的普通怪，无关） |

结论：
1. **客户端没有出现 `UNRESOLVED`**，两端的 owner、`isSolid` 完全一致。M1 可以按"owner 是 boss + 弹体在白名单"判断。
   - 局限：这是单人模式。传送离开期间，Swamp Guardian 和 Queen Spider 在第一次运行中自己消失了，所以"boss 在客户端卸载后又发射弹体"这种情况覆盖得不充分。联机实测留到 M1 验收时再做
2. **静态审计的分类都得到了验证**：抛物线 / 刷怪弹体都是 `canHitMobs = false`；直射的 `evilsprotector1`、`crystaldragonshard` 是 `canHitMobs = true` 且 `isSolid = false`，是 M1 的首批白名单
3. **boss 召唤出来的小怪不算 boss**：水晶傀儡的光束 owner 是 `crystalgolem`（`isBoss = false`），本来就会被墙挡（`isSolid = true`）。是否把"boss 召唤物"也纳入 M2 拆墙，留到 M2 决定
4. **`boulderhit` 爆炸已确认 owner 能解析为 swampguardian**，是 M3 的首个目标
5. 40 秒内没有观察到 `EvilsProtectorAttack2Projectile`（第二阶段的跟踪弹）。要覆盖它需要更长的战斗时间，或者先把 boss 打到半血
6. 次数小的差异（第一次运行 `crystalgolembeam` 服务端 40、客户端 37）是测试结束时还没同步到客户端的弹体，不影响结论

## 6. M1 墙盒测试结果（2026-10-14）

布局：玩家站在 7×7 石墙圈中央（内部 5×5），周围 24 格清成草地；boss 在 700 像素外生成，时间为午夜。表中数字为"客户端 / 服务端"计数。

| 组别 | 弹体 | 打墙 | 打中玩家（墙外生成） | 打中玩家（墙圈内生成） |
|---|---|---|---|---|
| 规则关闭，每个 boss 45 秒 | `evilsprotector1` | 0 / 0 | 1 / 5 | 未区分 |
| 规则关闭，每个 boss 45 秒 | `crystaldragonshard` | 0 / 0 | 3 / 6 | 未区分 |
| 规则开启，每个 boss 60 秒 | `evilsprotector1` | 22 / 22 | **0 / 0** | 0 / 0 |
| 规则开启，每个 boss 60 秒 | `evilsprotector2` | 4 / 4 | **0 / 0** | 0 / 0 |
| 规则开启，每个 boss 60 秒 | `crystaldragonshard` | 61 / 61 | **0 / 0** | 4 / 9 |

结论：
1. **规则生效**：墙外生成的白名单弹体全部停在墙上，没有一次打中墙内的玩家；规则关闭时同样的布局会被穿墙打中
2. 剩下的命中全部来自 Crystal Dragon 飞过墙盒上空时、身体节在墙圈内发射的碎片。boss 就在头顶，这种命中本来就挡不住，物理上也说得通，不算缺陷
3. 打墙次数两端完全一致，说明两端的碰撞模拟同步
4. 打中玩家的次数服务端多于客户端：玩家命中由客户端判定后发给服务端，服务端自己也会记录一次，属于统计口径不同，不影响结论
5. 还有几次 `mob` 命中，是夜里刷出来的普通怪物站在弹道上
6. 未覆盖：联机、回旋镖类弹体、会反弹的弹体（首批白名单里的弹体都没有反弹次数）

## 7. M2 墙盒测试结果（2026-10-14）

布局同第 6 节，墙盒挪到定居点以外。每个 boss 45 秒。表中"造成伤害 / 拆掉 / 被拒绝"是服务端的拆墙结果计数。

| 墙（等级） | 弹体（boss 等级） | 撞墙 | 造成伤害 | 拆掉 | 被拒绝 |
|---|---|---|---|---|---|
| 石墙（0） | `evilsprotector1`（1） | 33 | 31 | 2 | 0 |
| 石墙（0） | `crystaldragonshard`（6） | 18 | 0 | 18 | 0 |
| 深石墙（2） | `evilsprotector1`（1） | 31 | 0 | 0 | **31** |
| 深石墙（2） | `crystaldragonshard`（6） | 22 | 0 | 22 | 0 |

问题与修复：
1. **第一次测试所有撞墙都被跳过**，原因是新世界出生点（长老村子）整片登记为定居点，默认不拆墙。墙盒改为自动放到定居点以外
2. **立即拆墙导致两端不同步**：Crystal Dragon 的撞墙次数服务端 18～22、客户端只有 9～10。服务端的弹体模拟比客户端快，墙先在服务端被拆掉，客户端的同一枚碎片飞到时墙已经不在，于是穿了过去。改为撞墙后延迟 250 毫秒再扣耐久，复测结果：

| 弹体 | 撞墙（客户端 / 服务端） | 拆墙结果 |
|---|---|---|
| `crystaldragonshard` | 21 / 21 | 拆掉 15 面；另有 6 次到点时墙已被前一枚拆掉（`air`），不做处理 |
| `evilsprotector1` | 17 / 17 | 造成伤害 16 次，拆掉 1 面 |

延迟带来的副作用：一面墙在被拆掉前的 250 毫秒内可以多挡几枚弹体。视觉上表现为"墙被连续命中后倒塌"，可以接受。

## 8. 受保护地块与联机测试（2026-10-14）

### 受保护地块

`tools/autotest.sh evilsprotector,crystaldragon 40 walled on stonewall protect`：墙盒的每一格用 `regionManager.setTileProtected` 标为受保护，和 Chieftain 等 boss 场地预设的做法相同。

| 弹体 | 撞墙（客户端 / 服务端） | 造成伤害 | 拆掉 | 被拒绝 |
|---|---|---|---|---|
| `evilsprotector1` | 28 / 28 | 0 | 0 | **28** |
| `crystaldragonshard` | 24 / 24 | 0 | 0 | **24** |

墙照样挡住弹体，但一面都没被打坏。原因是 `doObjectDamage` 在 `!obj.canPlaceOnProtectedLevels && level.isProtected(x, y)` 时把伤害置 0，而墙都不能放在受保护地块上。整关受保护的关卡（`DungeonArenaLevel`、`TempleArenaLevel`、`TrialRoomLevel`、Ascended 虚空）走同一个判断，没有单独测。

### 联机

`tools/autotest-mp.sh evilsprotector,crystaldragon 45 stonewall`：独立服务器（`Server.jar`，单独的 JVM）运行脚本，另一个客户端进程通过 `127.0.0.1:14179` 连进来。

| 弹体 | 撞墙（客户端 / 服务端） | 打中玩家 | 拆墙结果（服务端） |
|---|---|---|---|
| `evilsprotector1` | 31 / 31 | 0 / 0 | 造成伤害 30，拆掉 1 |
| `crystaldragonshard` | 27 / 27 | 1 / 3（全部在墙圈内生成） | 拆掉 19 面墙、1 棵云杉；`air` 6；定居点跳过 1 |

结论：
1. 联机下两端撞墙次数完全一致，250 毫秒延迟在真实网络连接下同样有效
2. 客户端也能解析 owner：两端白名单都生效，没有出现一端挡住、另一端穿过的情况
3. 联机客户端不运行 `AutoTest`，不知道墙盒的位置，所以墙圈内生成的命中在客户端记为普通的 `player`
4. 碎片打中墙盒外的一棵云杉并把它拆了：树也是 layer 0 的带碰撞物体，`WallDamage` 没有区分墙和树。已记入 ROADMAP M5 B
5. 有 1 次撞击落在定居点范围内（boss 追到了出生点村子附近），按设计跳过

## 9. 平衡性测试（2026-10-14）

框架和判定标准见 `docs/balance.md`。石墙，每场 45 秒，单人模式。

### 基线：M1～M3（召唤物不拆墙），open / box / halfcover

| boss | box 破墙时间 | box 剩余墙 | halfcover 破墙时间 | box 压力 | halfcover 压力 | 结果 |
|---|---|---|---|---|---|---|
| Evils Protector | 40.1 秒 | 23/24 | 31.8 秒 | 0.00 | 1.03 | PASS（靠 60 秒内破墙） |
| Crystal Dragon | 7.1 秒 | 4/24 | 7.2 秒 | 1.00 | 1.33 | PASS |
| Swamp Guardian | 17.3 秒 | 14/24 | 11.4 秒 | 0.90 | 1.02 | PASS |
| Queen Spider | 没破 | 24/24 | 没破 | 1.17 | 1.17 | PASS |

- Evils Protector 在墙盒里一次都没打中玩家，只能靠弹体慢慢拆墙，40 秒才破，接近 60 秒的上限
- Queen Spider 的 56 次命中里有 44 次是"隔墙命中"：她体型大，站在墙外时近战判定能越过墙打到墙圈里的人（`mob:queenspider`）。这不是白名单弹体，不影响判定，但说明大体型 boss 的近战本身就能越墙
- Crystal Dragon 7 秒破墙、45 秒拆掉 20/24 面，墙几乎只是拖延

### M5：召唤物拆墙，加 doorbox

| boss | box 破墙时间 | box 剩余墙 | box 压力 | halfcover 压力 | doorbox | 结果 |
|---|---|---|---|---|---|---|
| Evils Protector | 15.8 秒 | 21/24 | 1.03 | 0.79 | 14.9 秒破墙 | PASS |
| Crystal Dragon | 7.0 秒 | 3/24 | 1.15 | 1.19 | 7.0 秒破墙 | PASS |
| Swamp Guardian | 13.1 秒 | 14/24 | 0.87 | 0.87 | 7.9 秒破墙 | PASS |
| Queen Spider | 21.1 秒 | 23/24 | 0.65 | 1.29 | 没破 | PASS |

- Evils Protector 的 box 压力从 0.00 升到 1.03，破墙时间从 40 秒提前到 16 秒：传送门小怪会拆墙进来（`mob:portalminion` 的命中都发生在破墙之后）
- Queen Spider 的蜘蛛幼崽在 box 里 21 秒拆开一面墙
- 召唤物的标记：`portalminion` 64 只、`spiderhatchling` 12 只、`crystalgolem` 24 只。其中 19 只水晶傀儡离龙的身体节更近，误用了身体节的等级（1），已改为取头部的等级

### 测试框架本身的问题

- **boss 偶尔完全不行动**：一次 Queen Spider 的测试里，4 场（包括空地）她一发弹体、一次攻击都没有，导致误判 FAIL。后来记录了战斗结束时的位置：boss 正好停在生成点（+700, 0），玩家正好在原点，说明整个世界停住了。原因是单人模式默认 `pauseOnFocusLoss = true`，测试窗口一失去焦点（在别的窗口上点了一下）游戏就暂停，而本 mod 的测试脚本挂在 `Server.tick` 上，暂停时照样计时。修复：测试客户端的设置里关掉 `pauseOnFocusLoss` 和 `savePerformanceOnFocusLoss`；日志在世界暂停时打出警告；boss 整场没有任何弹体、没有命中（或提前离场）时换一块场地重打，最多 2 次，仍然如此就判 INVALID
- 每个组合只跑一次，随机性大：同一设置下 Queen Spider 的 box 命中数在 1～61 之间波动。接近阈值的结果需要重跑

### 最终一轮（门优先、修好暂停问题之后）

| boss | box 破墙时间 | box 剩余墙 | box 压力 | halfcover 压力 | doorbox：召唤物拆门 / 拆墙 | 结果 |
|---|---|---|---|---|---|---|
| Evils Protector | 17.8 秒 | 21/24 | 0.63 | 0.91 | 2 / 0 | PASS |
| Crystal Dragon | 7.0 秒 | 7/24 | 1.00 | 0.90 | 0 / 0（墙先被碎片打穿） | PASS |
| Swamp Guardian | 12.5 秒 | 13/24 | 0.98 | 1.06 | 0 / 0（没有召唤物） | PASS |
| Queen Spider | 16.7 秒 | 23/24 | 1.00 | 1.26 | 28 / 0 | PASS |

- 门优先生效：改寻路代价前，蜘蛛幼崽在 doorbox 里拆门 3 次、拆墙 22 次；改后两轮分别是 21 / 0 和 28 / 0。半掩体里召唤物的拆除次数始终是 0，都是绕过去的
- 门在 45～60 秒内都没被拆开：原版每格每秒最多吃约 10 点拆除伤害，召唤物断断续续地拆，中间超过 15 秒没被打时门还会回血。召唤物拆门的速度偏慢，留给 M5 C 调整
- Queen Spider 的近战越墙（`mob:queenspider` 的隔墙命中）在 doorbox 里有 41 次，是她压力不降的主要原因

### 联机同步设置

`tools/autotest-mp.sh evilsprotector 40 stonewall 14179 blockProjectiles=false`：只有服务器的设置文件关掉了 `blockProjectiles`。

| 端 | 自己文件里的值 | 弹体 `solid` | 撞墙 | 打中玩家 |
|---|---|---|---|---|
| 服务端 | false | false | 0 | 3 |
| 客户端 | true（默认） | **false** | 0 | 2 |

客户端照服务器的设置模拟，两端一致；客户端的设置文件保持原样。

## 10. 全部 boss 的调查（2026-10-14）

静态调查（反编译源码）加一轮空地实测（每个 boss 40 秒）。

### 能否在地表测试

| 情况 | boss |
|---|---|
| 正常战斗 | evilsprotector（只在夜里）、queenspider、chieftain（缺少场地，部分招式不触发）、swampguardian、ancientvulture、piratecaptain、reaper、cryoqueen、thecursedcrone（缺少场地，井字棋和骷髅阶段不触发）、pestwarden、sageandgrit（要生成 `sageandgrit` 起始怪）、fallenwizard（缺少场地，40 秒内没放弹体）、motherslime、nightswarm（要生成起始怪）、spiderempress、sunlightchampion、moonlightdancer、crystaldragon、mutanthydra、ascendedwizard、thevoid |
| 无法测试 | voidwizard：需要地牢场地，没有目标时会传送回场地的 boss 位置，地表上三次都完全不行动 |

### 攻击类型

**直射、原版穿墙，加入白名单候选**：`ancientvulture`、`vulturehatchling`、`reaperscythe`（回旋镖）、`cryoshard`、`cryovolley`、`cryowave`、`fallenwizardwave`、`empressslash`、`empresswebball`（回旋镖）、`starvail`、`crescentdisc`、`ascendedbolt`、`ascendedorb`、`ascendedshard`、`ascendedslash`、`ascendedbeam`、`mutanthydragoospray`

**原版就被墙挡，只差拆墙**：`swamprazor`（反弹 8 次）、`voidwizardwave`、`voidwizardhoming`、`voidwizardmissile`、`voidwizardclone`、`fallenwizardball`、`fallenwizardscepter`、`chieftainshield`、`spiritskull`

**不改**：抛物线和落点生成类（`queenspideregg/spit`、`swampboulder`、`slimeegg`、`crystalgolemspawn`、`ascendedgolemspawn`、`ascendedshardbomb`、`mutanthydraspit`、`spiritbeam`），预警标记（`cryowarning*`、`empressslashwarning`），贴地类（`cryoquake*`、`empressacid`、`earthspikesprojectile`）；`captaincannonball` 第一阶段是会被墙挡的实心弹，第二阶段（飞船）是抛射，保持原版

**激光和光束**：`fallenwizardbeam` 和 The Void 的三种光束原版就检查墙；`crystaldragonlaser` 不检查，仍然穿墙

**爆炸**：可加入 `captaincannonballexplosion`（半径 100）、`voidwizardexplosion`（55）、`cursedcronespiritbeamsexplosion`（65）、`voidrainexplosion`（75）；`supernovaexplosion` 半径 650，会夷平一大片，不加；`ascendedbombexplosion` 原版就拆墙（等级 10）

### 召唤物

| 召唤物 | 召唤者 | 类型 | 拆墙寻路是否有用 |
|---|---|---|---|
| `portalminion` | Evils Protector | 近战，地面 | 有用 |
| `spiderhatchling` | Queen Spider | 近战，地面 | 有用 |
| `warriorslime` | Mother Slime | 近战，地面 | 有用 |
| `cursedcronespiritghoul` | The Cursed Crone | 近战，地面 | 有用 |
| `crystalgolem`、`ascendedgolem` | Crystal Dragon、Ascended Wizard | 远程（光束会被墙挡，开火前检查视线），地面 | 有用：走到墙边拆 |
| `piraterecruit`、`pirateparrot` | Pirate Captain | 远程（子弹会被墙挡，检查视线）/ 近战，地面 | 原版海盗忽略这个 buff，需要额外 patch |
| `vulturehatchling` | Ancient Vulture | 飞行，接触伤害，远程弹体穿墙 | 无用（飞行） |
| `reaperspirit`、`spirittornado`、`fallendragon`、`nightswarmbat`、`sunlightgauntlet`、`ascendedbat`、`ascendedgauntlet` | 各 boss | 飞行或无碰撞 | 无用 |
| `voidwizardclone` | Void Wizard | 不移动，远程 | 无用；弹体本来就被墙挡 |

结论：没有需要"朝墙开火"AI 的召唤物（见 ROADMAP M5 A）。

## 11. 扩展到全部 boss 的平衡性调整（2026-10-14）

### 第一轮：直接加入全部候选（石墙，每场 45 秒）

21 个能在地表测试的 boss 里 14 个通过，7 个失败：

| boss | 失败原因 | 处理 |
|---|---|---|
| Chieftain | 墙盒里完全不行动（地面 boss 进不来，地刺发射前检查视线） | 场地 boss，原版场地受保护，移出默认设置 |
| The Cursed Crone、Fallen Wizard | 离开自己的场地后连空地上都几乎不打 | 同上 |
| Pirate Captain | 玩家躲在墙后时不发起攻击（原版视线判定），半掩体、带门墙盒里都不行动 | 需要改 AI，移出默认设置；设置里可手动加 |
| Spider Empress、Ascended Wizard、The Void | 第一面墙 4.9 / 2.1 / 1.9 秒就破（要求 ≥ 5 秒） | 见下 |

还发现两个问题：
- **家具被打坏**：后期 boss 的弹体打碎了附近房子里的床、书架、箱子、工作台。原因是拆墙逻辑对弹体撞到的任何物体都生效。加了按类别的开关 `BREAKABLE_OBJECTS`，在 `EntityManager.doObjectDamage` 统一过滤，弹体、爆炸、召唤物三条路径都管住。最初默认只开墙、门、窗、栅栏、树、石头（复测中一个工作台、一个海盗宝箱被打中，伤害都被拒绝）；后来按用户意见改为默认全开，否则家具和箱子会成为打不坏的掩体。被打坏的容器会掉出全部物品（原版 `getCombinedDroppedItems` 包含物品栏）
- 测试框架把"墙后的 boss 什么也做不了"当成了"空场"去重试。改为只在空地布局重试，墙后的不行动是有效结果

### 第二轮：每发伤害上限

`maxWallDamageFraction = 0.25`：一发弹体最多扣墙耐久的 25%（墙都是 100 耐久，即最多 25 点），一面墙至少挨 4 下。

| boss | 破墙时间（无上限 → 有上限） |
|---|---|
| Spider Empress | 4.9 → 13.7 秒 |
| Ascended Wizard | 2.1 → 6.0 秒 |
| The Void | 1.9 → 4.4 秒，仍未通过 |
| Moonlight Dancer | 5.6 → 13.7 秒 |
| Crystal Dragon | 7.0 → 18.1 秒 |

另外 Spider Empress 出现 1 次白名单弹体隔墙命中：`empresswebball` 是回旋镖，原版在回程时把它改成不碰墙，于是穿墙飞回来。修复：白名单回旋镖回程保持碰墙，回程撞墙直接消失（`ProjectileReturnPatch`、`ProjectileRules.endReturningAtWall`）。

The Void 每秒约 5 发光弹打墙，单靠上限不够，加了按 boss 的对墙伤害倍率 `BOSS_WALL_DAMAGE`，The Void 设为 0.5。

### 最终一轮：17 个 boss 全部通过

| boss | box 命中 | box 剩余墙 | 破墙时间 | doorbox |
|---|---|---|---|---|
| Evils Protector | 35 | 21/24 | 13.2 秒 | 门先破（召唤物打门 5 下） |
| Queen Spider | 66 | 23/24 | 13.7 秒 | 门先破（5 下） |
| Swamp Guardian | 50 | 15/24 | 6.1 秒 | 墙先破（巨石） |
| Ancient Vulture | 14 | 23/24 | 20.6 秒 | 墙先破 |
| Reaper | 33 | 24/24 | 没破 | 没破 |
| Cryo Queen | 6 | 18/24 | 28.3 秒 | 墙先破 |
| Pest Warden | 33 | 24/24 | 没破 | 没破 |
| Sage & Grit | 15 | 24/24 | 没破 | 没破 |
| Mother Slime | 40 | 23/24 | 30.0 秒 | 门先破（5 下） |
| Night Swarm | 14 | 24/24 | 没破 | 没破 |
| Spider Empress | 44 | 13/24 | 9.3 秒 | 墙先破 |
| Sunlight Champion | 72 | 24/24 | 没破 | 没破 |
| Moonlight Dancer | 10 | 17/24 | 11.9 秒 | 墙先破 |
| Crystal Dragon | 22 | 23/24 | 18.3 秒 | 没破 |
| Mutant Hydra | 64 | 24/24 | 没破 | 没破 |
| Ascended Wizard | 8 | 14/24 | 5.5 秒 | 墙先破 |
| The Void | 40 | 0/24 | 18.4 秒 | 门先破（被弹体打破） |

- "没破"的 boss 靠本体越墙或近战够到人维持压力：Reaper、Pest Warden、Sage & Grit、Night Swarm、Sunlight Champion 是飞行或蠕虫类，Mutant Hydra 的头部近战能越过墙
- 接近阈值：Ascended Wizard 5.5 秒、Swamp Guardian 6.1 秒（5 秒为下限），重跑可能失败，留给下一轮调整
- Cryo Queen 的 box 压力 0.22，低于 0.25，靠 60 秒内破墙通过

### 容器和家具默认可破坏（2026-10-14）

用储物箱代替石墙围一圈（`tools/autotest.sh --layouts box --wall storagebox`），每个 boss 40 秒：

| 设置 | Evils Protector | Crystal Dragon |
|---|---|---|
| 默认（含 `containers`） | 伤害 18 次、打坏 1 个，10.1 秒破开 | 伤害 22 次、打坏 2 个，18.1 秒破开 |
| 去掉 `containers` | 23 次全部拒绝，没破 | 19 次全部拒绝，没破 |

去掉 `containers` 时有 1 次白名单弹体打中箱圈里的玩家：储物箱的碰撞箱比整格小，箱子围成的圈本身有缝，不是本 mod 的问题。
