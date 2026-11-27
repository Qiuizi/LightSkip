<div align="center">

# 轻跳 · LightSkip

**零联网权限、纯本地运行、约 130 KB 的轻量级 Android 开屏广告自动跳过工具**

[![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026–35)-3DDC84?logo=android&logoColor=white)](#环境与构建)
[![Permissions](https://img.shields.io/badge/INTERNET-None-10B981)](#隐私与安全边界)
[![Size](https://img.shields.io/badge/Debug%20APK-~131%20KB-111827)](#核心特性)
[![Tests](https://img.shields.io/badge/Unit%20Tests-99%20Passing-2563EB)](#测试与真机验证)
[![Status](https://img.shields.io/badge/Visibility-Private%20Alpha-6B7280)](#免责声明)

</div>

---

## 为什么做「轻跳」

市面上的开屏跳过工具要么需要联网拉取订阅、体积动辄几十 MB，要么配置门槛太高，不适合直接发给家人和朋友用。

**轻跳（LightSkip）** 坚持极简与克制：

- **安装包里没有 `INTERNET` 权限**：从系统层断绝任何联网、上报或后台下载可能，完全离线运行。
- **零第三方运行时依赖**：纯 Java + Android 原生控件构建，整个 Debug APK 仅约 **131 KB**。
- **默认白名单 + 敏感应用硬保护**：只在你勾选的应用里工作，微信、支付宝、QQ、各大银行、小米钱包、身份验证器、系统安全与输入法默认排除、永不点击。
- **开箱即用**：内置 25 个常用应用规则 + 评分启发式识别 + 免悬浮窗按键录制，同时按手机品牌自动匹配保活引导。

---

## 核心特性

| 模块 | 实现要点 |
| --- | --- |
| **极速识别引擎** | 新窗口冷启动前 `2.5s` 启用高频窗口（`notificationTimeout=0`、`25ms` 内容节流 + `50ms` 短轮询兜底）；已验证规则走 `findAccessibilityNodeInfosByViewId` 直查，跳过全树遍历；支持 `preferGesture` 直发手势点击。 |
| **多维防误点评分** | `SkipScorer` 综合文案特征、倒计时、已验证 ID、广告标记与屏幕角落位置打分，仅在窗口观察期内对唯一高置信度目标触发一次点击；淘宝信息流连续滑动实测 0 误点。 |
| **多机型保活适配** | `RomProfile` + `Device` 自动识别 **小米 / HyperOS、华为 / HarmonyOS、荣耀 / MagicOS、OPPO·一加·真我 / ColorOS、vivo·iQOO / OriginOS、三星 / OneUI、原生 Android**，按机型展示对应步骤并做 `resolveActivity` 预检跳转，失败自动回退标准系统设置页。 |
| **零卡顿应用列表** | 原生 `ListView` + `ViewHolder` + 图标 `LruCache` + 搜索防抖；小米 15 连续快速滑动 6 次 `gfxinfo` 实测 **388 帧仅 1 帧卡顿（0.26%）**，P50 帧耗 `5ms`。 |
| **免悬浮窗录制** | 无需申请悬浮窗权限，5 秒内即可针对未覆盖的应用录制自定义跳过按钮。 |

---

## 隐私与安全边界

轻跳只在 Manifest 中声明以下两项权限：

- `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`：用于一键引导加入电池优化白名单，防止无障碍服务在锁屏后被系统清理。
- `android.permission.QUERY_ALL_PACKAGES`：用于在「生效范围」页面列出本机已安装应用，供机主勾选。

**明确不申请**：`INTERNET`、`SYSTEM_ALERT_WINDOW`（悬浮窗）、读写外部存储、定位、通讯录、设备识别码。所有开关状态、勾选名单与录制规则仅保存在本机 `SharedPreferences` 与应用私有目录中。

---

## 技术边界说明

轻跳基于 Android 官方 `AccessibilityService`（无障碍服务）实现，**不要求 Root，也不使用本地 VPN 拦截网络**：

- 开屏广告在应用启动后由目标应用自行渲染，纯无障碍方案在物理上无法做到 0 毫秒不闪现；轻跳的目标是将「按钮出现 → 自动点掉」的自身响应延迟压到最低。
- 仅用于个人与亲友小范围自用测试，不上架应用商店、不做任何商业化。

---

## 环境与构建

- **JDK**：17（源码兼容 Java 8）
- **Android SDK**：`compileSdk 35` / `targetSdk 35` / `minSdk 26`（Android 8.0+）

```bash
# 1. 克隆仓库（本地构建需在根目录准备 local.properties 指向本机 Android SDK）
git clone git@github.com:Qiuizi/LightSkip.git
cd LightSkip

# 2. 运行全部本地单元测试（99 项）
./gradlew testDebugUnitTest      # Windows 使用 gradlew.bat testDebugUnitTest

# 3. 构建 Debug APK（输出至 app/build/outputs/apk/debug/app-debug.apk）
./gradlew assembleDebug          # Windows 使用 gradlew.bat assembleDebug
```

安装到已连接的 Android 设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 首次使用（3 步）

1. **安装并打开轻跳**：在首页点击「去开启」授予无障碍服务权限（Android 13+ 侧载安装若开关灰显，请先在应用信息右上角允许「受限设置」）。
2. **配置后台保活**：点击「后台保活」，按页面自动识别出的当前手机品牌步骤完成电池无限制 / 自启动设置。
3. **确认生效范围**：进入「生效范围」，点击「一键勾选推荐应用」即可；或用内置测试页直接验证跳过与防误点效果。

---

## 项目结构

```text
app/src/main/java/app/qi/qingtiao/
├── SkipService.java        # 无障碍服务主循环、高频窗口与极速通道
├── SkipScorer.java         # 多维打分器与防误点判定
├── RuleBook.java           # 内置应用规则与 preferGesture / verified 标记
├── WindowSession.java      # 开屏窗口生命周期与观察期管理
├── Scope.java              # 白名单/全选范围持久化与自动清洗
├── ScopePolicy.java        # 敏感应用、银行、钱包、输入法保护策略
├── RomProfile.java         # 7 大安卓品牌 ROM 自动识别与引导文案
├── Device.java             # 厂商设置页 resolveActivity 预检与降级跳转
├── HitLog.java             # 本地命中与诊断日志（JSONL）
├── MainActivity.java       # 首页主控台与实时状态
├── PageActivity.java       # 生效范围 / 录制规则 / 权限与保活 / 关于页
└── Ui.java                 # 原生控件样式、状态栏 insets 与视觉排版
```

---

## 测试与真机验证

- **单元测试**：`testDebugUnitTest` 共 **99 项全部通过**（覆盖打分引擎、窗口防重、多机型 ROM 识别、`Scope` 清洗与敏感包保护、规则验证等）。
- **真机验证（小米 15 · Android 15 / HyperOS）**：
  - 实测成功自动跳过：QQ 音乐、哔哩哔哩（`count_down`）、知乎（`btn_skip`）、百度网盘（`vilon_close_text`）。
  - 防误点实测：淘宝冷启动及信息流连续滑动 0 误点。
  - 性能实测：应用列表连续快速滑动 6 次，卡顿率 **0.26%**（1 / 388 帧）。

---

## 免责声明

本项目仅供个人学习 Android 无障碍服务机制及亲友小范围自用体验，不面向公众分发，不包含任何商业用途。部分开源规则标识参考了社区公开资料，匹配与点击引擎均为独立实现。