package app.minepanel.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 一条第三方组件归属与许可记录。 */
private data class LicenseEntry(
    val name: String,
    val license: String,
    val copyright: String?,
    val source: String?,
    val note: String
)

private val licenseEntries = listOf(
    LicenseEntry(
        name = "Qwen2.5-0.5B-Instruct（内置 GGUF Q4_K_M 量化副本）",
        license = "Apache-2.0",
        copyright = "Copyright 2024 Alibaba Cloud (Qwen Team)",
        source = "huggingface.co/Qwen/Qwen2.5-0.5B-Instruct",
        note = "端侧本地模型，用于回顾草案与聊天续写；不联网调用云端接口。"
    ),
    LicenseEntry(
        name = "llama.cpp / ggml",
        license = "MIT",
        copyright = "Copyright (c) 2023-2026 The ggml authors",
        source = "外部源码编译为 libminepanel-llama.so",
        note = "端侧 GGUF 前向推理引擎。"
    ),
    LicenseEntry(
        name = "SQLCipher for Android 4.6.1",
        license = "SQLCipher BSD-style",
        copyright = null,
        source = "net.zetetic:sqlcipher-android",
        note = "本地数据库加密；底层 SQLite 为公有领域。"
    ),
    LicenseEntry(
        name = "AndroidX 套件",
        license = "Apache-2.0",
        copyright = null,
        source = "core / activity / compose / material3 / lifecycle / room / work / datastore",
        note = "本地 UI、存储与后台调度。"
    ),
    LicenseEntry(
        name = "Kotlin 语言与 Compose 编译器",
        license = "Apache-2.0",
        copyright = null,
        source = "org.jetbrains.kotlin",
        note = "语言与 UI 编译器。"
    ),
    LicenseEntry(
        name = "Okio 3.4.0",
        license = "Apache-2.0",
        copyright = null,
        source = "com.squareup.okio（DataStore 传递依赖）",
        note = "本地文件与字节缓冲 IO，不含 HTTP 客户端。"
    ),
    LicenseEntry(
        name = "Guava ListenableFuture 1.0",
        license = "Apache-2.0",
        copyright = null,
        source = "com.google.guava（Core 传递依赖）",
        note = "仅 Future 回调接口。"
    )
)

/** 应用内开源许可与第三方归属页；清单与源码仓 NOTICE.md 对齐。 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
                Text(
                    text = "开源许可",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(licenseEntries, key = { it.name }) { entry ->
                    LicenseCard(entry)
                }

                item {
                    Text(
                        text = "本应用主体采用 Apache-2.0；许可全文与完整归属见随包 assets/open-source/ 目录及源码仓 NOTICE.md。测试期依赖（JUnit、MockK、AndroidX Test/Espresso）不进入 release 运行时类路径。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseCard(entry: LicenseEntry) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    entry.license,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            entry.copyright?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry.source?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(entry.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
