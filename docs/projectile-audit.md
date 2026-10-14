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

## 4. 运行时审计步骤（待完成）

1. 构建：`JAVA_HOME=/g/scoop/apps/temurin17-jdk/current ./gradlew runClient`（会带 `-dev -mod build/jar/`）
2. 进一个**单人**世界（单人模式下服务端和客户端在同一个 JVM 里，才能对比两端），打开聊天输入 `/bp debug on`
3. 依次打 Evils Protector、Crystal Dragon、Swamp Guardian、Queen Spider，每个都让 boss 把所有攻击放一遍。期间试着**跑远**，让 boss 离开屏幕后再靠近，以触发 owner 未加载的情况
4. 输入 `/bp debug dump`，结果写到 `%APPDATA%/Necesse/bossphysics-audit.csv`（用 `-datadir` 时写到对应目录），游戏日志里也有每种新组合第一次出现时的 `[BossPhysics] new ...` 行
5. 把 CSV 贴进下面第 5 节，重点看：
   - 同一个弹体的 server 行和 client 行，`owner`、`ownerIsBoss`、`isSolid` 是否一致
   - `owner` 是否出现 `UNRESOLVED`：出现就说明 M1 不能只靠 owner 判断，要退回"只按弹体类判断"
   - 爆炸行的 owner 和 `destroysObjects`

CSV 各列：`kind, side, id, class, superclass, owner, ownerIsBoss, isSolid, canBounce, canHitMobs, canBreakObjects, destroysObjects, destroysTiles, toolTier, count`。

## 5. 运行时结果

（待采集）
