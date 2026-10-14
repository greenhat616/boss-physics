# Boss Physics

Necesse 1.3.3 Mod：让 boss 的攻击遵守基本的物理规则。墙能挡住 boss 的直射弹体，但 boss 也会把墙打穿。

## 效果

| 机制 | 说明 |
|---|---|
| 墙挡弹体 | 列入白名单的 boss 直射弹体（Evils Protector 的弹幕、Crystal Dragon 的碎片等）撞墙即停，打不到墙后的人。抛物线类（蜘蛛吐液、蛋、巨石）照样飞过墙，在落点生效 |
| 弹体拆墙 | 这些弹体撞墙时对墙造成伤害（弹体伤害 × 倍率），能逐步打穿；被拆的墙照常掉落物品 |
| 爆炸拆墙 | 列入白名单的 boss 爆炸（Swamp Guardian 的巨石落地等）会像炸弹一样破坏范围内的物体 |
| 召唤物拆墙 | boss 的召唤物（传送门小怪、蜘蛛幼崽、水晶傀儡）够不到玩家时：有路就绕过去，被围住就先破门、再拆墙 |
| 工具等级 | 每个 boss 有自己的工具等级，打不动等级更高的墙。默认：Evils Protector、Queen Spider 1（木、石、砖墙和地牢墙），Swamp Guardian 2（再加深石墙），Crystal Dragon 6（再加黑曜石墙） |
| 保护 | 定居点内默认不拆墙；受保护的地块（boss 场地、地牢）和原版不可破坏的物体始终打不坏 |

## 设置

保存在 `cfg/mods/a632079.bossphysics.cfg`（游戏第一次启动时按默认值生成，每项都有注释）。

| 键 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 总开关，false 时完全恢复原版行为 |
| `blockProjectiles` | true | 墙挡住 `PROJECTILES` 里的弹体 |
| `projectilesBreakWalls` | true | 这些弹体撞墙时造成伤害 |
| `explosionsBreakWalls` | true | `EXPLOSIONS` 里的爆炸破坏物体 |
| `summonsBreakWalls` | true | `SUMMONS` 里的召唤物破门拆墙 |
| `damageInSettlements` | false | 定居点内是否也拆墙 |
| `wallDamageMultiplier` | 1.0 | 弹体对墙的伤害 = 弹体伤害 × 此值 |
| `explosionWallDamageMultiplier` | 1.0 | 爆炸对物体的伤害 = 爆炸伤害 × 此值 |
| `wallDamageDelayMs` | 250 | 弹体撞墙后延迟多久才扣墙的耐久，让联机客户端先看到撞墙（不建议改） |
| `defaultBossToolTier` | 1.0 | 没有单独设置的 boss 用的工具等级 |
| `BOSS_TOOL_TIERS` | 见上 | boss 的 mob ID = 工具等级 |
| `PROJECTILES` | 3 种 | 弹体 ID = `BLOCKED`（挡住并拆墙）/ `SHIELDED`（只挡不拆）/ `BOUNCE`（反弹并拆墙）/ `VANILLA`（原版） |
| `EXPLOSIONS` | `boulderhit` | 爆炸事件 ID 列表 |
| `SUMMONS` | 3 种 | 召唤物的 mob ID 列表 |

修改后在游戏里输入 `/bp reload` 立即生效（独立服务器控制台可省略 `/`）。联机时以服务器的设置为准：服务器在玩家加入时和 `/bp reload` 后把设置发给所有客户端，客户端自己的文件只在单人游戏和自己主持世界时使用。

往白名单里加新的 boss 攻击之前，请先跑平衡性测试（见下文）。没测过的攻击可能会让 boss 根本打不到人，或者轻易把基地拆光。

## 指令

都需要 ADMIN 权限。

| 指令 | 作用 |
|---|---|
| `/bp status` | 显示当前生效的设置 |
| `/bp reload` | 重新读取设置文件，并同步给所有客户端 |
| `/bp debug <on\|off\|dump\|clear>` | 记录 boss 弹体和爆炸，`dump` 写出 `bossphysics-audit.csv` |
| `/bp spawnboss <mobid> [距离]` | 在身边生成一个 boss |
| `/bp autotest <start\|walled\|balance\|stop> [boss1,boss2,...]` | 脚本化的 boss 战测试；`balance` 会写出平衡性报告 |

## 构建与测试

```bash
# 需要 JDK 17+ 来运行 Gradle（字节码目标为 Java 8）
./gradlew buildModJar                      # 输出 build/jar/BossPhysics-1.3.3-<版本>.jar

# 以下脚本都用临时的存档、人物和设置启动游戏，不会动你自己的存档；需要 Steam 在运行
tools/balance-test.sh [bosses] [秒数]     # 平衡性测试，报告在 build/balance/
tools/autotest.sh --mode walled ...        # 单人墙盒测试，选项见脚本开头
tools/autotest-mp.sh [bosses] [秒数]      # 独立服务器 + 客户端的联机测试
```

所有玩家都需要安装本 Mod（`clientside = false`）：客户端也要模拟弹体，两端对"哪些弹体被墙挡住"的判断必须一致。

## 文档

- [docs/ROADMAP.md](docs/ROADMAP.md)：设计、里程碑和待办
- [docs/balance.md](docs/balance.md)：平衡性测试的布局、统计口径和判定标准
- [docs/projectile-audit.md](docs/projectile-audit.md)：弹体审计和各阶段的测试结果

## 已知限制

- 激光、冲锋、地面 AOE 等攻击不走弹体碰撞，仍然穿墙
- 爆炸伤害生物时不检查墙（原版行为）
- 蠕虫类、飞行类 boss 的本体会越过墙
- 召唤物遇到打不动的墙（等级不够、受保护地块、定居点）会一直撞着它
- 远程召唤物不会专门瞄准墙开火，只会像近战一样走过去拆
