package com.lingoreader.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingoreader.app.AppContainer
import com.lingoreader.app.data.model.AppSettings
import com.lingoreader.app.data.model.OnlineEngine
import com.lingoreader.app.data.model.PageTurnMode
import com.lingoreader.app.data.model.ReaderTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer
) {
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepo.settingsFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var dictWordCount by remember { mutableIntStateOf(container.offlineDictionary.getWordCount()) }

    // Local editable AI form state synced when settings load
    var aiEnabled by remember(settings.aiEnabled) { mutableStateOf(settings.aiEnabled) }
    var aiBaseUrl by remember(settings.aiBaseUrl) { mutableStateOf(settings.aiBaseUrl) }
    var aiApiKey by remember(settings.aiApiKey) { mutableStateOf(settings.aiApiKey) }
    var aiModel by remember(settings.aiModel) { mutableStateOf(settings.aiModel) }
    var showApiKey by remember { mutableStateOf(false) }
    var isTestingAi by remember { mutableStateOf(false) }
    var aiTestResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.offlineDictionary.ensureReady()
        dictWordCount = container.offlineDictionary.getWordCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Offline Dictionary Status
            item {
                SectionCard(title = "离线词典引擎") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (dictWordCount > 0) "ECDICT 离线英汉词库已就绪" else "正在初始化离线词库...",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (dictWordCount > 0) {
                                    "内置 $dictWordCount 条核心词条 + 33,867 条词形还原，断网也可毫秒查词"
                                } else {
                                    "包含中高考、四六级、考研、雅思、托福、GRE 与牛津核心词汇"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Online Phrase/Sentence Translation Engine
            item {
                SectionCard(title = "在线划词 / 划句翻译引擎") {
                    OnlineEngine.entries.forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { container.settingsRepo.updateOnlineEngine(engine) }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.onlineEngine == engine,
                                onClick = {
                                    scope.launch { container.settingsRepo.updateOnlineEngine(engine) }
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(engine.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    engine.desc,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. Optional Custom AI Translation (OpenAI-compatible)
            item {
                SectionCard(title = "自定义 AI 语境翻译（选配）") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("划句优先使用 AI 翻译", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "支持 DeepSeek、通义千问、OpenAI 等兼容接口，结合语境讲解",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = aiEnabled,
                            onCheckedChange = { checked ->
                                aiEnabled = checked
                                scope.launch {
                                    container.settingsRepo.updateAiConfig(checked, aiBaseUrl, aiApiKey, aiModel)
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("快捷预设：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = aiBaseUrl.contains("deepseek", true),
                            onClick = {
                                aiBaseUrl = "https://api.deepseek.com/v1"
                                aiModel = "deepseek-chat"
                            },
                            label = { Text("DeepSeek", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = aiBaseUrl.contains("dashscope", true),
                            onClick = {
                                aiBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
                                aiModel = "qwen-turbo"
                            },
                            label = { Text("通义千问", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = aiBaseUrl.contains("openai.com", true),
                            onClick = {
                                aiBaseUrl = "https://api.openai.com/v1"
                                aiModel = "gpt-4o-mini"
                            },
                            label = { Text("OpenAI", fontSize = 12.sp) }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = aiBaseUrl,
                        onValueChange = { aiBaseUrl = it },
                        label = { Text("API Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aiApiKey,
                        onValueChange = { aiApiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "显示或隐藏 Key"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aiModel,
                        onValueChange = { aiModel = it },
                        label = { Text("模型名称 (Model)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    container.settingsRepo.updateAiConfig(aiEnabled, aiBaseUrl, aiApiKey, aiModel)
                                    snackbarHostState.showSnackbar("AI 翻译配置已保存")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("保存配置")
                        }
                        OutlinedButton(
                            enabled = !isTestingAi && aiApiKey.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    isTestingAi = true
                                    aiTestResult = null
                                    container.settingsRepo.updateAiConfig(aiEnabled, aiBaseUrl, aiApiKey, aiModel)
                                    val tempSettings = settings.copy(
                                        aiEnabled = true,
                                        aiBaseUrl = aiBaseUrl,
                                        aiApiKey = aiApiKey,
                                        aiModel = aiModel
                                    )
                                    val res = container.translationService.requestAiExplanation(
                                        "serendipity",
                                        "Finding that rare bookshop was pure serendipity.",
                                        tempSettings
                                    )
                                    isTestingAi = false
                                    aiTestResult = res.fold(
                                        onSuccess = { "连接成功：\n$it" },
                                        onFailure = { "连接失败：${it.message}" }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingAi) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("测试连接")
                            }
                        }
                    }
                    if (!aiTestResult.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = aiTestResult!!,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // 4. Pronunciation Settings (Real Human Voice + TTS fallback)
            item {
                SectionCard(title = "英语发音朗读（真人语音）") {
                    SettingSwitchRow(
                        title = "优先使用真人原声发音（推荐）",
                        subtitle = "自动获取纯正美式/英式真人 MP3 音频并缓存到本地，断网时自动回退系统 TTS",
                        checked = settings.preferHumanVoice,
                        onCheckedChange = { scope.launch { container.settingsRepo.updatePreferHumanVoice(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = settings.ttsAccent == "US",
                            onClick = {
                                scope.launch { container.settingsRepo.updateTtsSettings("US", settings.ttsSpeed) }
                            },
                            label = { Text("默认美音 (US)") }
                        )
                        FilterChip(
                            selected = settings.ttsAccent == "UK",
                            onClick = {
                                scope.launch { container.settingsRepo.updateTtsSettings("UK", settings.ttsSpeed) }
                            },
                            label = { Text("默认英音 (UK)") }
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                container.ttsManager.speak(
                                    text = "serendipity",
                                    accent = settings.ttsAccent,
                                    speed = settings.ttsSpeed,
                                    preferHumanVoice = settings.preferHumanVoice
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("试听", fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    var speedDraft by remember(settings.ttsSpeed) { mutableFloatStateOf(settings.ttsSpeed) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("语速", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp))
                        Slider(
                            value = speedDraft,
                            onValueChange = { speedDraft = it },
                            onValueChangeFinished = {
                                scope.launch { container.settingsRepo.updateTtsSettings(settings.ttsAccent, speedDraft) }
                            },
                            valueRange = 0.75f..1.35f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("%.2fx".format(speedDraft), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
                    }
                }
            }

            // 5. Reading behavior defaults (Theme, Font Size, Page Turn Mode, Volume Key, Auto Turn, Double Tap)
            item {
                SectionCard(title = "阅读排版与翻页偏好") {
                    Text("默认阅读主题", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderTheme.entries.forEach { theme ->
                            FilterChip(
                                selected = settings.readerTheme == theme,
                                onClick = { scope.launch { container.settingsRepo.updateReaderTheme(theme) } },
                                label = { Text(theme.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    var fontDraft by remember(settings.fontSizeSp) { mutableFloatStateOf(settings.fontSizeSp.toFloat()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("字号", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp))
                        Slider(
                            value = fontDraft,
                            onValueChange = { fontDraft = it },
                            onValueChangeFinished = {
                                scope.launch { container.settingsRepo.updateFontSize(fontDraft.toInt()) }
                            },
                            valueRange = 14f..30f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${fontDraft.toInt()}px", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("翻页动画模式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageTurnMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.pageTurnMode == mode,
                                onClick = { scope.launch { container.settingsRepo.updatePageTurnMode(mode) } },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    var autoTurnDraft by remember(settings.autoTurnIntervalSec) { mutableFloatStateOf(settings.autoTurnIntervalSec.toFloat()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自动翻页", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
                        Slider(
                            value = autoTurnDraft,
                            onValueChange = { autoTurnDraft = it },
                            onValueChangeFinished = {
                                scope.launch { container.settingsRepo.updateAutoTurnIntervalSec(autoTurnDraft.toInt()) }
                            },
                            valueRange = 2f..60f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${autoTurnDraft.toInt()}秒", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(42.dp))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "音量键翻页",
                        subtitle = "阅读时按音量下键下一页，音量上键上一页",
                        checked = settings.volumeKeyPaging,
                        onCheckedChange = { scope.launch { container.settingsRepo.updateVolumeKeyPaging(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "双击单词弹出翻译小窗",
                        subtitle = "双击英文单词查词并提取原句，单击两侧流畅翻页",
                        checked = settings.tapToTranslate,
                        onCheckedChange = { scope.launch { container.settingsRepo.updateTapToTranslate(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "正文中高亮已收藏生词",
                        subtitle = "再次遇到生词本中的单词时显示下划虚线提示",
                        checked = settings.highlightVocab,
                        onCheckedChange = { scope.launch { container.settingsRepo.updateHighlightVocab(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "查词时自动朗读真人发音",
                        subtitle = "每次双击英文单词立即播放真人读音",
                        checked = settings.autoPronounceOnTap,
                        onCheckedChange = { scope.launch { container.settingsRepo.updateAutoPronounce(it) } }
                    )
                }
            }

            // 6. About & Tips
            item {
                SectionCard(title = "使用技巧") {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "• 小说式左右翻页：支持覆盖、仿真、平移、淡入淡出、无动画 5 种模式；支持音量键翻页与定时自动翻页。\n" +
                                "• 单击翻页 / 呼出菜单：单击屏幕左侧 30% 上一页、右侧 30% 下一页、中间 40% 呼出底部工具栏（可快速调字号、切主题、开自动翻页）。\n" +
                                "• 双击单词查词：双击任意英文单词不误触翻页，即刻弹出全义项词典、柯林斯双解与美/英真人原声发音。\n" +
                                "• 长按划词 / 划句：拖选后点击底部「划词翻译」悬浮条或系统菜单「译 翻译」获取整句译文。\n" +
                                "• 单词本复习：内置 SM-2 遗忘曲线记忆卡片与释义四选一测验，支持导出 CSV / Markdown。",
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
