package io.github.julystar.musicapp.plugin.runtime

data class PluginScriptBundle(
    val pluginId: String,
    val source: String,
    val filename: String,
    val sourceHash: String,
)

data class PluginRuntimeDescriptor(
    val pluginId: String,
    val pluginName: String,
    val pluginVersionCode: Long,
    val pluginUpdatedAt: Long,
    val entryFile: String,
    val includeDirs: List<String>,
    val directory: String,
)

data class PluginRuntimeSettings(
    val appName: String = "Tide Player",
    val packageName: String = "io.github.julystar.musicapp",
    val appVersionName: String,
    val appVersionCode: Long = 0,
    val cacheDirectory: String,
    val memoryLimitBytes: Long = 64L * 1024 * 1024,
    val stackLimitBytes: Long = 2L * 1024 * 1024,
    val defaultTimeoutMs: Long = 15_000,
    val loadTimeoutMs: Long = 10_000,
    val manualOperationTimeoutMs: Long = 30_000,
    val allowHttp: Boolean = false,
    val allowHttps: Boolean = true,
    val allowPrivateNetwork: Boolean = false,
    val maxHttpRequestBytes: Long = 4L * 1024 * 1024,
    val maxHttpResponseBytes: Long = 16L * 1024 * 1024,
    val maxPluginCacheBytes: Long = 4L * 1024 * 1024,
    val maxInflateBytes: Long = 16L * 1024 * 1024,
)

data class PluginRuntimeCacheKey(
    val pluginId: String,
    val pluginVersionCode: Long,
    val pluginUpdatedAt: Long,
    val scriptSourceHash: String,
)
