# 平衡性测试

所有**默认设置**里的 boss 条目（弹体白名单 `PROJECTILES`、爆炸 `EXPLOSIONS`、召唤物 `SUMMONS`、工具等级 `BOSS_TOOL_TIERS`）都必须通过这里的测试，才能进入 `BossPhysicsConfig.defaults()`。把机制扩展到新的 boss 时，先在测试配置里加上条目，跑测试，全部通过再写进默认值。

## 怎么跑

```bash
tools/balance-test.sh [boss1,boss2,...] [每场秒数] [autotest.sh 的其他选项...]
# 例：用自己的配置文件测两个新 boss
tools/balance-test.sh voidwizard,piratecaptain 45 --config my.cfg
# 例：只改一个数值
tools/balance-test.sh swampguardian 45 --set explosionWallDamageMultiplier=2.0
```

- 每个 boss 依次打 4 场，每场换一块新场地（向东移 64 格，避开定居点），时间固定在午夜，boss 在玩家东边 700 像素处生成
- 玩家有调试无敌（只挡致命伤害），所以命中次数和伤害都照常记录
- 报告写到 `build/balance/<时间>.md`，日志里也有 `balance <boss>: PASS/FAIL`
- 判定阈值可以用 `-Dbossphysics.balance.<名称>=<值>` 覆盖（见下表），但默认设置必须按默认阈值通过

## 场地布局

| 布局 | 形状 | 用途 |
|---|---|---|
| `open` | 清空的 49×49 草地，没有墙 | 基准：boss 在空地上每分钟能打中玩家几次 |
| `box` | 7×7 石墙圈，内部 5×5，玩家在中央 | 全封闭掩体 |
| `halfcover` | 同上，但去掉朝西（背对 boss）的一面 | 半掩体：有路可绕 |
| `doorbox` | 全封闭，西面正中是一扇门 | 召唤物应先破门 |

## 统计口径

- **玩家命中**：在 `Mob.isServerHit` 统计，涵盖弹体、爆炸、近战、技能事件。按来源（`projectile:<id>`、`explosion:<id>`、`mob:<id>`、`event:<id>`）分别计数
- **隔墙命中**：墙还完整时（`box`、`doorbox` 在第一面墙或门被拆之前），来源在墙圈外的命中。弹体看生成位置，爆炸看爆心，近战看攻击者位置；技能事件无法判断，不计入
- **破墙时间**：第一面墙或门消失的时刻（每个服务端 tick 检查一次）
- **压力**：某布局每分钟玩家命中数 ÷ `open` 布局每分钟玩家命中数

## 判定标准

| 检查 | 适用布局 | 通过条件 | 阈值名（默认值） | 目的 |
|---|---|---|---|---|
| `walls-stop-whitelist` | box、doorbox | 白名单弹体的隔墙命中 = 0 | — | 正确性：列入白名单的弹体必须被墙挡住 |
| `cover-holds` | box | 墙没破，或第一次破墙不早于阈值 | `minBreachSeconds`（5） | 墙要有掩护作用，不能一碰就碎 |
| `hiding-not-free` | box | 破墙不晚于阈值，**或**压力不低于阈值 | `maxBreachSeconds`（60）、`minBoxPressure`（0.25） | 躲进墙里不能变成无伤打法 |
| `client-server-sync` | box | 客户端和服务端的撞墙次数相差不超过阈值（至少容许 2 次） | `maxSyncDifference`（0.10） | 两端碰撞模拟一致；独立服务器测试时为 N/A |
| `halfcover-reachable` | halfcover | 压力不低于阈值（没有 open 场时：命中 > 0） | `minHalfCoverPressure`（0.25） | 半掩体不能挡住 boss |
| `fight-valid` | 全部 | boss 没有提前离开（死亡、白天离场、脱战） | — | 结果可信 |
| `hits-through-walls` | box、doorbox | 仅信息 | — | 列出所有隔墙命中的来源，是扩充白名单的候选 |
| `door-first` | doorbox | 仅信息 | — | 先破的是门还是墙 |

boss 的所有检查都不是 FAIL 或 INVALID，才算通过。

## 已知局限

- 只测 boss 打玩家，不测玩家打 boss（玩家站着不动）。boss 的实际战斗节奏会因玩家走位不同
- 激光、地面 AOE 等不在白名单范围内的攻击会穿墙（ROADMAP 第 5 节），它们出现在 `hits-through-walls` 里，但不影响判定
- 蠕虫类、飞行类 boss 的本体会越过墙，它们的近战命中发生在墙圈内，不算隔墙命中
- 每个组合只跑一次，随机性较大；接近阈值的结果应该重跑确认
