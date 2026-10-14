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
