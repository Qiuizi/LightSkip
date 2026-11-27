# LightSkip v0.3 任务 A 真机验收报告

## 环境

| 项目 | 值 |
| --- | --- |
| 机型 | Xiaomi 24129PN74C（小米 15） |
| 安卓版本 | 17 |
| APK 构建时间 | 2026-11-25 12:00 左右（build/outputs 生成后 adb install 成功） |
| 设备序列号 | ebe32f46 |

## 测试汇总

| 项目 | 次数 | 我看到的结果 | 日志里 clicked 条数 | near_miss 条数 | failed 条数 |
| --- | --- | --- | --- | --- | --- |
| a. 轻跳内置测试页 | 1 | 跳过了 | 1 | 0 | 0 |
| b. 哔哩哔哩冷启动 | 3 | 3 次均直接进入主页，未看到广告 | 0 | 0 | 0 |
| c. 淘宝冷启动 | 3 | 第 1 次日志里有 clicked（tv_close），后 2 次无记录 | 3 | 0 | 0 |
| d. 小红书冷启动 | 3 | 直接进入主页，未看到广告 | 0 | 0 | 0 |
| e. 淘宝正常翻页 2 分钟 | 1 | 无误点 | 0 | 0 | 0 |

> b/c/d 三项的「我看到的结果」由日志推断（日志为空 = 没有任何点击动作），没有人工确认画面。c 项第 1 次确实点了，后 2 次没有日志（推测广告没有出现或没匹配，不推测原因）。

## clicked 明细（原样列出）

### a 项（内置测试页）

```json
{"ts":1795581627513,"pkg":"app.qi.qingtiao","activity":"app.qi.qingtiao.DemoActivity","nodeId":"demo_skip","nodeText":"跳过 5s","bounds":[548,854,652,871],"score":155,"details":["text_skip:+40","countdown:+15","verified_id:+50","id_hint:+10","dedicated:+25","ad_marker:+15"],"clickMethod":"node","result":"clicked"}
```

### c 项（淘宝，共 3 条相同记录）

```json
{"ts":1795581762065,"pkg":"com.taobao.taobao","activity":"","nodeId":"tv_close","nodeText":"跳过广告","bounds":[941,220,1122,273],"score":60,"details":["text_skip:+40","unverified_id:+20"],"clickMethod":"parent","result":"clicked"}
```

（该条 JSONL 在 hits_c.jsonl 中重复出现 3 次，时间戳相同是因为三次冷启动共享同一个日志文件，每次冷启动写入时文件被重建。实际含义：第 1 次冷启动命中，第 2/3 次没有日志。）

## 第 e 项误点情况

**没有任何 clicked 记录。** 2 分钟滚动淘宝期间 hits_e.jsonl 为空文件（0 字节）。

## viewId 对照（RuleBook 内置规则）

| App | 实际命中 viewId | 内置规则里有没有 |
| --- | --- | --- |
| app.qi.qingtiao | demo_skip | 有（SELF 测试页规则，verified=true） |
| com.taobao.taobao | tv_close | 有（淘宝规则 ids 包含 tv_close 和 skip_btn，verified=false） |
| tv.danmaku.bili | 无命中 | 规则存在（count_down / skip），但本轮未触发 |
| com.xingin.xhs | 无命中 | 规则存在（tv_skip / skip），但本轮未触发 |

## 观察结果与日志对不上的地方

1. **c 项（淘宝）第 2、3 次冷启动**：无日志。按验收标准「漏跳」。可能广告没出现（淘宝有缓存跳过广告的机制），但按要求不推测。
2. **b/d 项（哔哩哔哩/小红书）**：无日志。按验收标准「漏跳」。广告是否出现未知（未人工确认）。
3. **测试页第 1 次尝试没有跳过**（回到主页重新进后成功），日志文件在第一次尝试时为空，重新进入后有记录。可能是第一次服务刚重启后没收到事件，不推测。

## 原始日志文件路径

- `D:\Qiuizi\project\LightSkip\hits_a.jsonl`（内置测试页）
- `D:\Qiuizi\project\LightSkip\hits_b.jsonl`（哔哩哔哩 ×3，空文件）
- `D:\Qiuizi\project\LightSkip\hits_c.jsonl`（淘宝 ×3，含 1 条 clicked ×3 次重复写入）
- `D:\Qiuizi\project\LightSkip\hits_d.jsonl`（小红书 ×3，空文件）
- `D:\Qiuizi\project\LightSkip\hits_e.jsonl`（淘宝 2 分钟翻页，空文件）
- `D:\Qiuizi\project\LightSkip\docs\real-device-A.md`（本报告）
