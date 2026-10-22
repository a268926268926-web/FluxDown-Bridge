package com.galbrena.fluxdownbridge

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var serviceEnabled by remember { mutableStateOf(BridgePrefs.enabled(context)) }
    var token by remember { mutableStateOf(BridgePrefs.token(context)) }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var quietNotifications by remember { mutableStateOf(BridgePrefs.quietNotifications(context)) }
    var rootState by remember { mutableStateOf(RootState.Idle) }
    var rootMessage by remember { mutableStateOf("") }

    val statusColor by animateColorAsState(
        if (serviceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "statusColor",
    )

    fun copyToken() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("FluxDown Bridge Token", token))
        scope.launch { snackbar.showSnackbar("令牌已复制") }
    }

    fun startService() {
        BridgePrefs.setEnabled(context, true)
        BridgeService.start(context)
        serviceEnabled = true
        scope.launch { snackbar.showSnackbar("桥接服务已启动") }
    }

    fun stopService() {
        BridgeService.stop(context)
        serviceEnabled = false
        scope.launch { snackbar.showSnackbar("桥接服务已停止") }
    }

    fun regenerateToken() {
        val wasRunning = serviceEnabled
        if (wasRunning) BridgeService.stop(context)
        token = BridgePrefs.regenerateToken(context)
        rootState = RootState.Idle
        rootMessage = ""
        if (wasRunning) {
            scope.launch {
                delay(350)
                BridgeService.start(context)
                snackbar.showSnackbar("已生成新令牌，请在扩展中同步修改")
            }
        } else {
            scope.launch { snackbar.showSnackbar("已生成新令牌") }
        }
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
                Header(serviceEnabled)
                HeroCard(
                    enabled = serviceEnabled,
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
                NotificationCard(
                    quiet = quietNotifications,
                    onQuietChange = {
                        quietNotifications = it
                        BridgePrefs.setQuietNotifications(context, it)
                        BridgeService.refreshNotification(context)
                        scope.launch {
                            snackbar.showSnackbar(if (it) "已切换为静默通知" else "已恢复标准通知")
                        }
                    },
                    onOpenSettings = {
                        val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, BridgeService.notificationChannelId(context))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(channelIntent) }.onFailure {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                )
                RootCard(
                    state = rootState,
                    message = rootMessage,
                    onTest = ::testRoot,
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
                        "系统 Monet 色图标与静默显示",
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
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
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
                    Switch(checked = quiet, onCheckedChange = onQuietChange)
                }
            }
            Text(
                "Android 要求前台服务保留一条通知，App 无法完全删除。若需要彻底关闭，请使用“系统设置”把本应用或通知渠道设为关闭。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Header(enabled: Boolean) {
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
            color = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ) {
            Row(
                Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                )
                Text(
                    if (enabled) "运行中" else "已停止",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    enabled: Boolean,
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
                    Column {
                        Text(
                            if (enabled) "桥接服务正在监听" else "桥接服务未运行",
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
                            if (enabled) "扩展可以直接投递下载" else "启动后扩展才能连接",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalButton(
                        onClick = onStart,
                        enabled = !enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("启动")
                    }
                    Button(
                        onClick = onStop,
                        enabled = enabled,
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
