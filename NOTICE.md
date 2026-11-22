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

## 内置模型权重

- 内置副本：`app/src/main/assets/models/bundled-model.gguf`（GGUF Q4_K_M 量化），491,400,032 字节，SHA-256 `74A4DA8C9FDBCD15BD1F6D01D621410D31C6FC00986F5EB687824E7B93D7A9DB`。该文件**不纳入源码仓库**（`.gitignore` 忽略 `*.gguf`），仅随 APK 打包。
- 底模：[Qwen/Qwen2.5-0.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct)；GGUF 量化参照官方仓 [Qwen/Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF)。
- 版权：Copyright 2024 Alibaba Cloud (Qwen Team)。
- 许可证：**Apache-2.0**（Qwen2.5 系列中 0.5B 属 Apache-2.0，不在 3B/72B 专有许可之列）。随应用再分发时保留本归属声明与 Apache-2.0 许可文本；量化由 llama.cpp GGUF 工具生成。

## 许可全文索引

- Apache-2.0: http://www.apache.org/licenses/LICENSE-2.0
- MIT: https://opensource.org/licenses/MIT
- EPL-1.0: https://www.eclipse.org/legal/epl-1.0
- SQLCipher 许可说明: https://www.zetetic.net/sqlcipher/license/
