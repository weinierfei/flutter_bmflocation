# 本地补丁说明

**基线版本：** flutter_bmflocation 3.8.5（pub.dev）

## 补丁原因

`flutter_bmflocation` 用 `static MethodChannel` + 单例 `MethodChannelManager` 持有通道，`onAttachedToEngine`
无条件用当前引擎的 BinaryMessenger 覆盖单例。

Flywear 的 `workmanager` 后台任务会通过 `FlutterEngine(context)` 默认构造
拉起第二个无头引擎（workmanager ≥ 0.9.0 无 PluginRegistrantCallback 钩子），
`GeneratedPluginRegistrant` 会把全部插件注册进去，包括 `flutter_bmflocation`。
后台引擎销毁后，单例仍指向已 detach 的死 channel → 主引擎百度定位回调永远收不到 →
进程级定位失败，直到 App 重启。

## 改动文件

仅改动一个文件：
`android/src/main/java/com/baidu/flutter_bmflocation/FlutterBmflocationPlugin.java`

核心改动：新增 `ownerMessenger` 静态字段，让"首个 attach 的引擎（主引擎）"成为通道持有者；
后续引擎 attach 时若持有者不同则跳过；持有者引擎 detach 才清理通道和 ownerMessenger。

## 升级指引

升级 `flutter_bmflocation` 时：
1. 用新版源码覆盖本目录（保留 PATCH.md）
2. 将本补丁重新应用到新版 `FlutterBmflocationPlugin.java`（变更点见 PATCH.md）
3. 重新运行 `fvm flutter pub get` 和 `fvm flutter analyze` 验证

## 3.8.5 升级

- 同步官方 Android 定位 SDK 9.7.0 和 `onceLocation` 参数。
- 保留原有多引擎通道持有者补丁；仍不覆盖后台引擎先注册的既有限制。
- Android 单次定位显式设置 `onceLocation: true`；连续定位设置 `false`，`scanspan` 至少为 1000ms。
