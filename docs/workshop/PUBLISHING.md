# 发布到 Steam 创意工坊

Necesse 没有独立的上传工具，上传在**游戏内**完成：用开发模式启动游戏，然后在「Mod」页面对开发模组点「上传到 Steam 创意工坊」。流程依据反编译的 `SteamDevModProvider` 和 `LoadedDevMod`（游戏 1.3.3）整理，与本仓库的其他 Mod 相同。

## 材料清单

| 文件 | 用途 |
|---|---|
| `src/main/resources/preview.png` | 640×640 封面，打进 jar 的 `resources/preview.png`，上传时自动作为创意工坊封面。**jar 里没有这张图时，游戏不显示上传按钮**（`SteamDevModProvider` 中 `mod.preview != null` 是显示条件） |
| `docs/workshop/description.bbcode` | 创意工坊描述（Steam BBCode，中英双语，约 4000 字符；游戏上传框上限 5000 字符） |
| `docs/workshop/images/00-overview.png` | 1280×720 像素风示意图卡片（与本仓库其他 Mod 的卡片版式相同），作为第一张附加图片 |
| `docs/workshop/images/01-crystaldragon.png` 等 4 张 | 1280×720 游戏截图加中英文说明条，排在示意图卡片之后 |
| `docs/workshop/screenshots/` | 原始游戏截图（没有说明条），也可以直接上传 |
| `tools/make_workshop_images.py` | 生成封面、示意图卡片和带说明条的截图：`python tools/make_workshop_images.py .`（需要 Pillow 和 Windows 自带字体）。说明条里的数字来自拍摄那一次测试的日志 |

### 截图是怎么拍的

都是真实的游戏画面，由自动测试在白天、英文界面、隐藏 HUD 的情况下拍摄：

```bash
AUTOTEST_SCREENSHOTS=3 AUTOTEST_DAYTIME=true AUTOTEST_LANGUAGE=en \
    tools/autotest.sh --mode walled --bosses crystaldragon,swampguardian,cryoqueen,ancientvulture --seconds 40
```

- `AUTOTEST_SCREENSHOTS=3`：每 3 秒调用一次游戏自己的截图功能（`Renderer.takeScreenshot`，直接读帧缓冲），保存在 `build/autotest/data/screenshots/`。从游戏外部截窗口（`tools/screenshot.ps1`）对 OpenGL 窗口可能截到一片空白。
- 拍摄期间隐藏 HUD 和新人物的教程提示（`debug/Screenshots`、`patches/StoryObjectiveDrawPatch`），不拍照时这两个 patch 什么也不做。
- `AUTOTEST_DAYTIME=true`：除 Evils Protector（白天会离开）外都在正午开打。
- 每次都会生成新世界，地形和附近的建筑每次不同。挑图后把原图复制到 `docs/workshop/screenshots/`，然后修改脚本里的 `CAPTIONS`。

挑图时注意：说明条写的内容必须是图上真的发生了的。例如 Crystal Dragon 的光束会穿墙（光束不是弹体，见 README 的已知限制），带光束的截图就不能配"墙挡住攻击"的说明。

## 首次发布

1. **构建**（Gradle 9 需要 JDK 17 以上；`mods/` 里要有 Mod Settings Lib 的 jar，见 README）：
   ```bash
   JAVA_HOME=/g/scoop/apps/temurin17-jdk/current ./gradlew buildModJar
   ```
   确认 `build/jar/` 里**只有一个 jar**。游戏要求开发模组目录中除 `preview.png` 外只能有一个 jar 文件、不能有子目录（`validateDevFolderAndReturnJar`）。

2. **以开发模式启动游戏**（Steam 必须正在运行，并已订阅 Mod Settings Lib）：
   ```bash
   JAVA_HOME=/g/scoop/apps/temurin17-jdk/current ./gradlew runClient
   ```

3. 主菜单进入 **Mod**，选中 **Boss Physics**（显示「从开发文件夹加载」），点 **「上传到 Steam 创意工坊」**。

4. 在弹出的窗口中：
   - 选择 **「创建新项目」**。
   - 勾选「更新描述」，粘贴 `description.bbcode` 的内容。
   - 标签建议勾选 **Tweaks**，可以再加 **New features**。
   - 点「继续」，同意 Steam 创意工坊服务条款，确认内容是你自己创作的。

5. 看到「成功地创建/更新了模组」后，到 Steam 网页上完成其余设置：
   - **必需物品**：「添加/移除必需物品」→ 添加 **Mod Settings Lib**（`3558238376`）。
   - **图片和视频**：按顺序上传 `docs/workshop/images/` 下的 5 张图：先 `00-overview.png`，再 4 张截图。
   - **可见性**：先设为「仅好友」联机测一次，再改成「公开」。
   - 新上传的物品需要接受过 Steam 创意工坊法律协议，否则只有你自己能看到。

6. 记下物品 ID（网址中的 `?id=...`），补到 README 里。

## 发布更新

1. 在 `build.gradle` 中提高 `modVersion`，例如 `1.0` → `1.1`。版本号变化后，服务端和客户端的 Mod 一致性检查会要求所有玩家都更新。
2. 改了默认平衡（`BossPhysicsConfig.defaults()`）的话，先跑 `tools/balance-test.sh`。
3. `./gradlew buildModJar`，然后 `./gradlew runClient`；Mod → Boss Physics → 上传 → **选择已有的项目**。
4. 「更新描述」：如果你在 Steam 网页上改过描述，**取消勾选**，否则会被覆盖。

## 首个版本的更新日志参考

```
1.0
- Whitelisted boss projectiles are stopped by walls and damage them (at most 1/4 of a wall's health per hit)
- Boss explosions break objects; boss summons break doors and walls to reach players
- Tool tiers per boss; settlements, arenas and dungeons protected by default
- 17 bosses, all passing the automated balance test
- In-game settings page (Mod Settings Lib); /bp status, /bp reload
```

## 发布前自检

- [ ] `tools/balance-test.sh` 通过
- [ ] `tools/autotest-mp.sh` 通过（独立服务器 + 客户端，服务器的设置同步到客户端）
- [ ] 主菜单 设置 → Mod 设置 → Boss Physics：每个选项都显示文字（不是 `bossphysics...` id），改一项保存后 cfg 文件跟着变；进入世界后选项变灰
- [ ] 独立服务器的 `mods/` 文件夹里也要放 Mod Settings Lib 的 jar
- [ ] `build/jar/` 中只有一个 jar，jar 内包含 `resources/preview.png`、`resources/locale/*.lang` 和 `mod.info`（其中 `dependencies = [aizsave.modsettingslib]`）
