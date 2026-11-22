# 第三方组件与许可清单（NOTICE）

本项目主体采用 Apache-2.0（见 [`LICENSE`](LICENSE)）。下列第三方组件以其各自许可条款被引用或编译进本应用；再分发本应用时须相应保留其许可与归属声明。

## 原生 / 底层库

| 组件 | 版本 / 来源 | 用途 | 许可 |
|---|---|---|---|
| llama.cpp / ggml | 外部源码目录（构建期引入，不进仓库），编译为 `libminepanel-llama.so` | 端侧 GGUF 前向推理 | MIT — Copyright (c) 2023-2026 The ggml authors |
| SQLCipher for Android | `net.zetetic:sqlcipher-android:4.6.1` | 本地数据库加密 | SQLCipher BSD-style 许可；底层 SQLite 为公有领域 |
| Okio | `com.squareup.okio:okio-jvm:3.4.0`（DataStore 传递） | 本地文件 / 字节缓冲 IO | Apache-2.0 |
| Guava ListenableFuture | `com.google.guava:listenablefuture:1.0`（Core 传递） | Future 回调接口 | Apache-2.0 |

## 平台与语言栈

| 组件 | 版本 | 用途 | 许可 |
|---|---|---|---|
| AndroidX Core / Activity / Compose (BOM) / Material3 / Lifecycle / Room / Work / DataStore | 见 [`gradle/libs.versions.toml`](gradle/libs.versions.toml) | 本地 UI、生命周期、本地存储、后台调度、本地键值配置 | Apache-2.0 |
| Kotlin 语言与 Compose 编译器 | 2.0.21 | 语言与 UI 编译器 | Apache-2.0 |
| KSP | 2.0.21-1.0.25 | Room 注解处理 | Apache-2.0 |

## 测试依赖（不进入 release 运行时类路径）

| 组件 | 用途 | 许可 |
|---|---|---|
| JUnit | JVM 单元测试 | EPL-1.0 |
| MockK | 模拟依赖 | Apache-2.0 |
| AndroidX Test / Espresso / Compose UI Test | instrumented 测试 | Apache-2.0 |

## 内置模型权重（合规待办）

- 基线 GGUF 权重位于 `app/src/main/assets/models/bundled-model.gguf`，**未纳入源码仓库**（`.gitignore` 忽略 `*.gguf`）。
- **权重的上游来源、作者与许可证目前尚未登记确认**。在发布任何内置该权重的 APK / 分发包之前，必须：
  1. 核实权重许可证允许随应用再分发；
  2. 在本节补登权重名称、来源链接、作者与许可证；
  3. 按权重许可证要求保留归属与声明文本。
- 若权重许可不允许再分发，应改为：首次运行由用户自行放置本地 GGUF，或通过单独的许可渠道分发权重文件。

## 许可全文索引

- Apache-2.0: http://www.apache.org/licenses/LICENSE-2.0
- MIT: https://opensource.org/licenses/MIT
- EPL-1.0: https://www.eclipse.org/legal/epl-1.0
- SQLCipher 许可说明: https://www.zetetic.net/sqlcipher/license/
