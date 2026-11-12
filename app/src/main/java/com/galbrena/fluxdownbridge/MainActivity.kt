package com.galbrena.fluxdownbridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        if (BridgePrefs.enabled(this)) BridgeService.start(this)
        setContent {
            FluxBridgeTheme {
                BridgeScreen()
            }
        }
    }
}

private enum class RootState { Idle, Checking, Ok, Failed }

private data class NotificationState(
    val appEnabled: Boolean,
    val channelImportance: Int?,
) {
    val channelCreated: Boolean
        get() = channelImportance != null
    val blocked: Boolean
        get() = !appEnabled || channelImportance == NotificationManager.IMPORTANCE_NONE
}

private fun notificationState(context: Context): NotificationState {
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = manager.getNotificationChannel(BridgeService.notificationChannelId(context))
    return NotificationState(
        appEnabled = manager.areNotificationsEnabled(),
        channelImportance = channel?.importance,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FluxBridgeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(0xFFAFC6FF),
            secondary = Color(0xFFD0BCFF),
            tertiary = Color(0xFFFFB4AB),
        )
        else -> lightColorScheme(
            primary = Color(0xFF2858C5),
            primaryContainer = Color(0xFFDCE1FF),
            secondary = Color(0xFF74558B),
            secondaryContainer = Color(0xFFF2DAFF),
        )
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(30.dp),
            extraLarge = RoundedCornerShape(38.dp),
        ),
        typography = Typography(
            titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
            labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BridgeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val serviceState by BridgeService.runtimeState.collectAsStateWithLifecycle()
    val activity by BridgeActivityState.snapshot.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf(BridgePrefs.token(context)) }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var connectionExpanded by rememberSaveable { mutableStateOf(false) }
    var resetTokenDialog by rememberSaveable { mutableStateOf(false) }
    var quietNotifications by remember { mutableStateOf(BridgePrefs.quietNotifications(context)) }
    var currentNotificationState by remember { mutableStateOf(notificationState(context)) }
    var rootState by remember { mutableStateOf(RootState.Idle) }
    var rootMessage by remember { mutableStateOf("") }

    LifecycleResumeEffect(serviceState) {
        token = BridgePrefs.token(context)
        quietNotifications = BridgePrefs.quietNotifications(context)
        currentNotificationState = notificationState(context)
        onPauseOrDispose {}
    }

    val statusColor by animateColorAsState(
        when (serviceState) {
            BridgeRuntimeState.Running, BridgeRuntimeState.Starting -> MaterialTheme.colorScheme.primary
            is BridgeRuntimeState.Failed -> MaterialTheme.colorScheme.error
            BridgeRuntimeState.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "statusColor",
    )

    fun copyValue(label: String, value: String, message: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun regenerateToken() {
        token = BridgeService.regenerateToken(context)
        tokenVisible = false
        scope.launch { snackbar.showSnackbar("已生成新令牌，请在扩展中同步修改") }
    }

    fun testRoot() {
        if (rootState == RootState.Checking) return
        rootState = RootState.Checking
        rootMessage = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) { RootLauncher.testRoot() }
            rootState = if (result.ok()) RootState.Ok else RootState.Failed
            rootMessage = if (result.ok()) "Root 权限正常" else result.output.ifBlank { "无法获取 Root 权限" }
            snackbar.showSnackbar(rootMessage)
        }
    }

    val background = if (isSystemInDarkTheme()) {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.surface,
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                MaterialTheme.colorScheme.surface,
            )
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
                .padding(innerPadding)
                .imePadding(),
        ) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Header()
                HeroCard(
                    state = serviceState,
                    statusColor = statusColor,
                    activity = activity,
                    onStart = { BridgeService.start(context) },
                    onStop = { BridgeService.stop(context) },
                )
                RootCard(
                    state = rootState,
                    message = rootMessage,
                    onTest = ::testRoot,
                )
                NotificationCard(
                    quiet = quietNotifications,
                    notificationState = currentNotificationState,
                    onQuietChange = {
                        quietNotifications = it
                        BridgePrefs.setQuietNotifications(context, it)
                        BridgeService.refreshNotification(context)
                        currentNotificationState = notificationState(context)
                    },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    },
                )
                ConnectionCard(
                    expanded = connectionExpanded,
                    onToggleExpanded = {
                        connectionExpanded = !connectionExpanded
                        if (!connectionExpanded) tokenVisible = false
                    },
                    token = token,
                    visible = tokenVisible,
                    onToggleVisible = { tokenVisible = !tokenVisible },
                    onCopyAddress = {
                        copyValue("FluxDown Bridge URL", "http://127.0.0.1:17800", "地址已复制")
                    },
                    onCopyToken = { copyValue("FluxDown Bridge Token", token, "令牌已复制") },
                    onRegenerate = { resetTokenDialog = true },
                )
                Text(
                    "本地回环专用 · 不监听局域网 · 无轮询 · 无持续唤醒锁",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }
    }

    if (resetTokenDialog) {
        AlertDialog(
            onDismissRequest = { resetTokenDialog = false },
            title = { Text("重置访问令牌？") },
            text = { Text("现有令牌会立即失效，扩展将无法继续投递。重置后需要把新令牌重新粘贴到扩展中。") },
            confirmButton = {
                TextButton(onClick = {
                    resetTokenDialog = false
                    regenerateToken()
                }) { Text("重置令牌") }
            },
            dismissButton = {
                TextButton(onClick = { resetTokenDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun NotificationCard(
    quiet: Boolean,
    notificationState: NotificationState,
    onQuietChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("通知显示", style = MaterialTheme.typography.titleLarge)
            Text(
                when {
                    notificationState.blocked -> "已在系统中隐藏，服务仍可运行"
                    !notificationState.channelCreated -> "启动服务后可设置通知显示"
                    else -> "系统允许显示通知"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (notificationState.appEnabled && notificationState.channelCreated) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("通知样式", style = MaterialTheme.typography.labelLarge)
                    FilterChip(
                        selected = !quiet,
                        onClick = { if (quiet) onQuietChange(false) },
                        label = { Text("标准") },
                    )
                    FilterChip(
                        selected = quiet,
                        onClick = { if (!quiet) onQuietChange(true) },
                        label = { Text("静默") },
                    )
                }
                Text(
                    "当前渠道：${notificationImportanceLabel(notificationState.channelImportance)}，以系统设置为准",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("管理通知显示")
            }
            Text(
                "使用后台冻结工具时，请将 Bridge 加入不冻结名单。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun notificationImportanceLabel(importance: Int?): String = when (importance) {
    NotificationManager.IMPORTANCE_NONE -> "已关闭"
    NotificationManager.IMPORTANCE_MIN -> "最低重要性"
    NotificationManager.IMPORTANCE_LOW -> "低重要性"
    NotificationManager.IMPORTANCE_DEFAULT -> "默认重要性"
    NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_MAX -> "高重要性"
    else -> "由系统管理"
}

@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "FluxDown",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "Bridge",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Text(
            "手机内的无缝下载桥",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(
    state: BridgeRuntimeState,
    statusColor: Color,
    activity: BridgeActivitySnapshot,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    )
                )
            )
        ) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Surface(shape = RoundedCornerShape(18.dp), color = statusColor) {
                        if (state == BridgeRuntimeState.Starting) {
                            LoadingIndicator(
                                modifier = Modifier.padding(12.dp).size(27.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                when (state) {
                                    is BridgeRuntimeState.Failed -> Icons.Rounded.Error
                                    BridgeRuntimeState.Stopped -> Icons.Rounded.Stop
                                    else -> Icons.Rounded.Download
                                },
                                contentDescription = null,
                                tint = if (state is BridgeRuntimeState.Failed) {
                                    MaterialTheme.colorScheme.onError
                                } else if (state == BridgeRuntimeState.Stopped) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                                modifier = Modifier.padding(12.dp).size(27.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (state) {
                                BridgeRuntimeState.Starting -> "正在开启本地接口"
                                BridgeRuntimeState.Running -> "本地接口已开启"
                                BridgeRuntimeState.Stopped -> "本地接口已停止"
                                is BridgeRuntimeState.Failed -> "本地接口启动失败"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            when (state) {
                                is BridgeRuntimeState.Failed -> state.message
                                BridgeRuntimeState.Stopped -> "启动后可接收浏览器请求"
                                else -> "仅在这台手机上接收请求"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ActivityLine(
                            "最近接口请求",
                            activity.lastRequestAtMillis?.let(::formatActivityTime) ?: "尚未收到请求",
                        )
                        ActivityLine(
                            "最近交接结果",
                            activity.lastResult?.let { "${formatActivityTime(it.atMillis)} · ${dispatchResultLabel(it)}" }
                                ?: "暂无交接记录",
                        )
                    }
                }
                when (state) {
                    BridgeRuntimeState.Stopped -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("启动")
                    }
                    is BridgeRuntimeState.Failed -> Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text("重试")
                        }
                        TextButton(onClick = onStop) { Text("停止") }
                    }
                    else -> OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (state == BridgeRuntimeState.Starting) "取消启动" else "停止")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatActivityTime(atMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(atMillis))

private fun dispatchResultLabel(result: BridgeDispatchResult): String = when (result.outcome) {
    BridgeDispatchOutcome.SENT -> "已交给 FluxDown · ${result.sent} 项"
    BridgeDispatchOutcome.AUTH_FAILED -> "令牌校验失败，请检查扩展配置"
    BridgeDispatchOutcome.INVALID_REQUEST -> "请求无效，未完成交接"
    BridgeDispatchOutcome.LAUNCH_FAILED -> if (result.sent > 0) {
        "已交接 ${result.sent}/${result.total} 项，其余交接失败"
    } else {
        "交接失败，请检查 Root 和 FluxDown"
    }
}

@Composable
private fun ConnectionCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    token: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onCopyAddress: () -> Unit,
    onCopyToken: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggleExpanded)
                .semantics { stateDescription = if (expanded) "已展开" else "已折叠" }
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("连接配置", style = MaterialTheme.typography.titleLarge)
                Text(
                    "地址、访问令牌与首次设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
            )
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("服务器地址", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text("http://127.0.0.1:17800", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = onCopyAddress, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("复制地址")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("访问令牌", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onToggleVisible) {
                        Icon(
                            if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (visible) "隐藏令牌" else "显示令牌",
                        )
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            if (visible) token else token.maskToken(),
                            modifier = Modifier.padding(16.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FilledTonalButton(onClick = onCopyToken, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("复制令牌")
                }
                TextButton(onClick = onRegenerate, modifier = Modifier.align(Alignment.End)) {
                    Text("重置访问令牌")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SetupInstructions()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RootCard(state: RootState, message: String, onTest: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Root 通道", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "用于无确认页直达 FluxDown",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onTest,
                    enabled = state != RootState.Checking,
                ) {
                    if (state == RootState.Checking) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Rounded.Security, contentDescription = null)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text("测试")
                }
            }
            AnimatedVisibility(
                visible = state == RootState.Ok || state == RootState.Failed,
                enter = fadeIn(
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                ) + slideInVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ) { -it / 3 },
                exit = fadeOut(
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                ) + slideOutVertically(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ) { -it / 3 },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (state == RootState.Ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                        contentDescription = null,
                        tint = if (state == RootState.Ok) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupInstructions() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("连接浏览器扩展", style = MaterialTheme.typography.titleMedium)
        Step(1, "打开 FluxDown 扩展的“远程服务器”设置")
        Step(2, "粘贴服务器地址")
        Step(3, "粘贴令牌并测试连接")
        Step(4, "投递模式选择“仅远程 / Always”")
        Step(5, "关闭“FluxDown 自定义协议”")
        Text(
            "首次接管下载时，请允许 KernelSU/Magisk 永久 Root。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                number.toString(),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun String.maskToken(): String {
    if (length <= 18) return "•".repeat(length.coerceAtLeast(12))
    return take(8) + "••••••••••" + takeLast(8)
}
