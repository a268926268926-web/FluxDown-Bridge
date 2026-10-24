package com.galbrena.fluxdownbridge

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 33)
        }
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
    var token by remember { mutableStateOf(BridgePrefs.token(context)) }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var quietNotifications by remember { mutableStateOf(BridgePrefs.quietNotifications(context)) }
    var currentNotificationState by remember { mutableStateOf(notificationState(context)) }
    var rootState by remember { mutableStateOf(RootState.Idle) }
    var rootMessage by remember { mutableStateOf("") }

    LifecycleResumeEffect(serviceState) {
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

    fun copyToken() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("FluxDown Bridge Token", token))
        scope.launch { snackbar.showSnackbar("令牌已复制") }
    }

    fun startService() {
        BridgeService.start(context)
    }

    fun stopService() {
        BridgeService.stop(context)
    }

    fun regenerateToken() {
        token = BridgeService.regenerateToken(context)
        rootState = RootState.Idle
        rootMessage = ""
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
                Header(serviceState)
                HeroCard(
                    state = serviceState,
                    statusColor = statusColor,
                    onStart = ::startService,
                    onStop = ::stopService,
                )
                SectionLabel("扩展连接")
                TokenCard(
                    token = token,
                    visible = tokenVisible,
                    onToggleVisible = { tokenVisible = !tokenVisible },
                    onCopy = ::copyToken,
                    onRegenerate = ::regenerateToken,
                )
                SectionLabel("应用设置")
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
                        scope.launch {
                            snackbar.showSnackbar(if (it) "已切换为静默通知" else "已恢复标准通知")
                        }
                    },
                    onOpenSettings = {
                        val state = notificationState(context)
                        val appIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val settingsIntent = if (!state.appEnabled || !state.channelCreated) {
                            appIntent
                        } else {
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, BridgeService.notificationChannelId(context))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(settingsIntent) }.onFailure {
                            context.startActivity(appIntent)
                        }
                    },
                )
                SectionLabel("首次设置")
                SetupCard()
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("服务通知", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "标准通知、静默或按系统设置关闭",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                ) {
                    Text("系统设置")
                }
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .toggleable(value = quiet, role = Role.Switch, onValueChange = onQuietChange)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("静默通知", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "降低通知渠道优先级，不改变桥接服务运行",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = quiet, onCheckedChange = null)
                }
            }
            if (notificationState.blocked) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "通知当前已关闭",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "通知可以按需关闭。若出现间歇断连，可能与通知设置有关，可以恢复通知作对比排查。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            } else if (!notificationState.channelCreated) {
                Text(
                    "启动服务后创建通知渠道。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (quiet) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "正在使用最低优先级通知。若出现间歇断连，可以试着恢复标准通知作对比。",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Text(
                "Android 要求前台服务声明一条通知。你仍可在系统设置中选择是否显示；若遇到不稳定，可以恢复应用通知或标准通知渠道作对比。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(state: BridgeRuntimeState) {
    val containerColor = when (state) {
        BridgeRuntimeState.Running, BridgeRuntimeState.Starting -> MaterialTheme.colorScheme.primaryContainer
        is BridgeRuntimeState.Failed -> MaterialTheme.colorScheme.errorContainer
        BridgeRuntimeState.Stopped -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when (state) {
        BridgeRuntimeState.Running, BridgeRuntimeState.Starting -> MaterialTheme.colorScheme.onPrimaryContainer
        is BridgeRuntimeState.Failed -> MaterialTheme.colorScheme.onErrorContainer
        BridgeRuntimeState.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
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
        Surface(
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (state == BridgeRuntimeState.Starting) {
                    LoadingIndicator(modifier = Modifier.size(18.dp), color = contentColor)
                } else {
                    Icon(
                        when (state) {
                            BridgeRuntimeState.Running -> Icons.Rounded.CheckCircle
                            is BridgeRuntimeState.Failed -> Icons.Rounded.Error
                            else -> Icons.Rounded.Stop
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    when (state) {
                        BridgeRuntimeState.Starting -> "启动中"
                        BridgeRuntimeState.Running -> "运行中"
                        BridgeRuntimeState.Stopped -> "已停止"
                        is BridgeRuntimeState.Failed -> "启动失败"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    state: BridgeRuntimeState,
    statusColor: Color,
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
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(12.dp).size(27.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (state) {
                                BridgeRuntimeState.Starting -> "正在启动桥接服务"
                                BridgeRuntimeState.Running -> "桥接服务正在监听"
                                BridgeRuntimeState.Stopped -> "桥接服务未运行"
                                is BridgeRuntimeState.Failed -> "桥接服务启动失败"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "127.0.0.1:17800",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            when (state) {
                                BridgeRuntimeState.Starting -> "正在建立本地监听"
                                BridgeRuntimeState.Running -> "扩展可以直接投递下载"
                                BridgeRuntimeState.Stopped -> "启动后扩展才能连接"
                                is BridgeRuntimeState.Failed -> state.message
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalButton(
                        onClick = onStart,
                        enabled = !state.active,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("启动")
                    }
                    Button(
                        onClick = onStop,
                        enabled = state != BridgeRuntimeState.Stopped,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("停止")
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenCard(
    token: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("访问令牌", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "扩展远程服务器使用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("复制")
                }
                OutlinedButton(
                    onClick = onRegenerate,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("重新生成")
                }
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
private fun SetupCard() {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("连接浏览器扩展", style = MaterialTheme.typography.titleLarge)
            Step(1, "打开 FluxDown 扩展的“远程服务器”设置")
            Step(2, "地址填写 http://127.0.0.1:17800")
            Step(3, "粘贴上方令牌并测试连接")
            Step(4, "投递模式选择“仅远程 / Always”")
            Step(5, "关闭“FluxDown 自定义协议”")
            Text(
                "首次接管下载时，请允许 KernelSU/Magisk 永久 Root。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
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
