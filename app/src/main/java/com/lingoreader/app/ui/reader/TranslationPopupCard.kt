package com.lingoreader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingoreader.app.data.model.CollinsSense
import com.lingoreader.app.data.model.ExampleSentence
import com.lingoreader.app.data.model.SenseGroup
import com.lingoreader.app.data.model.TranslationResult

data class TranslationPopupState(
    val query: String,
    val contextSentence: String,
    val isLoading: Boolean = false,
    val result: TranslationResult? = null,
    val isSaved: Boolean = false,
    val isEnrichingOnline: Boolean = false,
    val isTranslatingSentence: Boolean = false,
    val sentenceTranslation: String? = null,
    val isLoadingAi: Boolean = false,
    val aiText: String? = null,
    val aiError: String? = null
)

private enum class DictTab(val label: String) {
    OVERVIEW("全面释义"),
    COLLINS("柯林斯双解"),
    PHRASES("搭配 · 词族"),
    EXAMPLES("双语例句")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslationPopupCard(
    state: TranslationPopupState,
    aiConfigured: Boolean,
    playingAudioTag: String?,
    onDismiss: () -> Unit,
    onSpeak: (text: String, accent: String, tag: String) -> Unit,
    onToggleSave: () -> Unit,
    onEnrichOnline: () -> Unit,
    onTranslateSentence: () -> Unit,
    onRequestAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = state.result
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    var expanded by remember(state.query) { mutableStateOf(false) }
    var activeTab by remember(state.query) { mutableStateOf(DictTab.OVERVIEW) }

    val availableTabs = remember(result) {
        buildList {
            add(DictTab.OVERVIEW)
            if (!result?.collinsSenses.isNullOrEmpty()) add(DictTab.COLLINS)
            if (!result?.phrases.isNullOrEmpty() ||
                !result?.synonyms.isNullOrEmpty() ||
                !result?.relatedWords.isNullOrEmpty()
            ) {
                add(DictTab.PHRASES)
            }
            if (!result?.examples.isNullOrEmpty()) add(DictTab.EXAMPLES)
        }
    }

    LaunchedEffect(availableTabs) {
        if (activeTab !in availableTabs) {
            activeTab = DictTab.OVERVIEW
        }
    }

    val maxHeight = if (expanded) {
        (screenHeightDp * 0.84f).dp.coerceAtLeast(480.dp)
    } else {
        (screenHeightDp * 0.56f).dp.coerceIn(380.dp, 520.dp)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Drag indicator
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(Modifier.height(8.dp))

            // Header: Headword + Expand + Save + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result?.displayWord?.ifBlank { state.query } ?: state.query,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = if (state.query.length > 26) 16.sp else 23.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!result?.formNote.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "词形还原：${result?.formNote}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                // Save button
                Button(
                    onClick = onToggleSave,
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                    colors = if (state.isSaved) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (state.isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (state.isSaved) "已收录" else "记生词",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                // Expand / Collapse height toggle
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                        contentDescription = if (expanded) "收起窗口" else "展开查看全部义项",
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Dual Human-Voice Audio Row: US (美) + UK (英)
            val audioTarget = result?.displayWord?.ifBlank { state.query } ?: state.query
            val usPhonetic = result?.phoneticUs.orEmpty()
            val ukPhonetic = result?.phoneticUk.orEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val usTag = "head_us_${audioTarget.lowercase()}"
                val ukTag = "head_uk_${audioTarget.lowercase()}"

                VoicePill(
                    accentLabel = "美",
                    phonetic = usPhonetic,
                    isPlaying = playingAudioTag == usTag,
                    onClick = { onSpeak(audioTarget, "US", usTag) }
                )
                VoicePill(
                    accentLabel = "英",
                    phonetic = ukPhonetic.ifBlank { usPhonetic },
                    isPlaying = playingAudioTag == ukTag,
                    onClick = { onSpeak(audioTarget, "UK", ukTag) }
                )

                if (state.isEnrichingOnline) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.8.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "补全详尽释义...",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Rich Dictionary Tabs (when multiple sections exist)
            if (availableTabs.size > 1) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableTabs.forEach { tab ->
                        val countLabel = when (tab) {
                            DictTab.COLLINS -> " (${result?.collinsSenses?.size ?: 0})"
                            DictTab.EXAMPLES -> " (${result?.examples?.size ?: 0})"
                            else -> ""
                        }
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            label = {
                                Text(
                                    text = "${tab.label}$countLabel",
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Scrollable dictionary body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "正在查询详尽释义...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (result != null) {
                    when (activeTab) {
                        DictTab.OVERVIEW -> {
                            OverviewTabContent(
                                state = state,
                                result = result,
                                playingAudioTag = playingAudioTag,
                                onSpeak = onSpeak,
                                onTranslateSentence = onTranslateSentence
                            )
                        }
                        DictTab.COLLINS -> {
                            CollinsTabContent(
                                senses = result.collinsSenses,
                                playingAudioTag = playingAudioTag,
                                onSpeak = onSpeak
                            )
                        }
                        DictTab.PHRASES -> {
                            PhrasesAndFamilyTabContent(
                                result = result,
                                playingAudioTag = playingAudioTag,
                                onSpeak = onSpeak
                            )
                        }
                        DictTab.EXAMPLES -> {
                            ExamplesTabContent(
                                examples = result.examples,
                                playingAudioTag = playingAudioTag,
                                onSpeak = onSpeak
                            )
                        }
                    }
                }

                // Footer row: source label + refresh/enrich + AI button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result?.sourceLabel.orEmpty(),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (result != null && !result.isRichLoaded && !state.isEnrichingOnline) {
                            AssistChip(
                                onClick = onEnrichOnline,
                                label = { Text("加载完整释义", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                        if (aiConfigured && state.aiText == null) {
                            AssistChip(
                                onClick = onRequestAi,
                                enabled = !state.isLoadingAi,
                                label = { Text("AI 精讲", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTabContent(
    state: TranslationPopupState,
    result: TranslationResult,
    playingAudioTag: String?,
    onSpeak: (text: String, accent: String, tag: String) -> Unit,
    onTranslateSentence: () -> Unit
) {
    // 1. Exam & Collins tags
    if (result.tags.isNotEmpty() || result.oxford || result.collins > 0) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            result.tags.forEach { tag -> BadgePill(text = tag, highlight = false) }
            if (result.oxford) BadgePill(text = "牛津核心", highlight = true)
            if (result.collins > 0) {
                BadgePill(text = "柯林斯 ${"★".repeat(result.collins.coerceAtMost(5))}", highlight = true)
            }
        }
    }

    // Error banner
    if (!result.error.isNullOrBlank()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ) {
            Text(
                text = result.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // 2. Comprehensive multi-POS Chinese definitions
    if (result.chineseMeaning.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ) {
            Column(
                modifier = Modifier.padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val lines = result.chineseMeaning.lines().filter { it.isNotBlank() }
                lines.forEach { line ->
                    FormattedMeaningLine(line)
                }
            }
        }
    }

    // 3. Word Forms (复数/过去式/分词等)
    if (result.wordForms.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            result.wordForms.forEach { wf ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = Color.Transparent
                ) {
                    Text(
                        text = "${wf.label}: ${wf.value}",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }

    // 4. Structured Multi-Sense Breakdown (expand_ec — shows ALL granular meanings by POS!)
    if (result.senseGroups.isNotEmpty()) {
        SenseGroupsSection(
            groups = result.senseGroups,
            playingAudioTag = playingAudioTag,
            onSpeak = onSpeak
        )
    }

    // 5. Web & Domain Translations (catches slang/tech/extended meanings)
    if (result.webTranslations.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ) {
            Column(modifier = Modifier.padding(11.dp)) {
                Text(
                    text = "扩展 / 专业释义",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = result.webTranslations.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // 6. English-English definitions (when no senseGroups/Collins yet or offline)
    if (result.englishDef.isNotBlank() && result.senseGroups.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = Color.Transparent
        ) {
            Column(modifier = Modifier.padding(11.dp)) {
                Text(
                    text = "英英释义",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = result.englishDef,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic, lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 7. AI Context Explanation
    AnimatedVisibility(visible = state.isLoadingAi || !state.aiText.isNullOrBlank() || !state.aiError.isNullOrBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "AI 语境精讲",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(6.dp))
                when {
                    state.isLoadingAi -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("AI 思考中...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    !state.aiError.isNullOrBlank() -> {
                        Text(state.aiError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    !state.aiText.isNullOrBlank() -> {
                        Text(
                            text = state.aiText,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    // 8. Context Sentence from the Book
    if (state.contextSentence.isNotBlank() && state.contextSentence.length > state.query.length + 6) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = Color.Transparent
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "阅读原句",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val sentTag = "ctx_sent"
                        TextButton(
                            onClick = { onSpeak(state.contextSentence, "US", sentTag) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "朗读原句",
                                modifier = Modifier.size(14.dp),
                                tint = if (playingAudioTag == sentTag) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(3.dp))
                            Text("朗读", fontSize = 12.sp)
                        }
                        if (state.sentenceTranslation == null) {
                            TextButton(
                                onClick = onTranslateSentence,
                                enabled = !state.isTranslatingSentence,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    if (state.isTranslatingSentence) "翻译中..." else "翻译整句",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.contextSentence,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!state.sentenceTranslation.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.sentenceTranslation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SenseGroupsSection(
    groups: List<SenseGroup>,
    playingAudioTag: String?,
    onSpeak: (text: String, accent: String, tag: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "分词性详解",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        groups.forEach { group ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = group.pos,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    group.senses.forEachIndexed { idx, sense ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "${idx + 1}. ",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = buildString {
                                        if (sense.detailPos.isNotBlank() && sense.detailPos != group.pos) {
                                            append("[${sense.detailPos}] ")
                                        }
                                        append(sense.meaning)
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            sense.examples.firstOrNull()?.let { ex ->
                                val tag = "exp_${group.pos}_$idx"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ex.english,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = ex.chinese,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onSpeak(ex.english, "US", tag) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.VolumeUp,
                                            contentDescription = "朗读例句",
                                            modifier = Modifier.size(15.dp),
                                            tint = if (playingAudioTag == tag) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollinsTabContent(
    senses: List<CollinsSense>,
    playingAudioTag: String?,
    onSpeak: (text: String, accent: String, tag: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        senses.forEach { sense ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = sense.index.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                            )
                        }
                        if (sense.pos.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = if (sense.posTips.isNotBlank()) "${sense.pos} · ${sense.posTips}" else sense.pos,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = sense.explanation,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    sense.examples.forEachIndexed { eIdx, ex ->
                        val tag = "col_${sense.index}_$eIdx"
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ex.english,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = ex.chinese,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onSpeak(ex.english, "US", tag) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "朗读例句",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (playingAudioTag == tag) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhrasesAndFamilyTabContent(
    result: TranslationResult,
    playingAudioTag: String?,
    onSpeak: (text: String, accent: String, tag: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (result.phrases.isNotEmpty()) {
            Text(
                text = "常用短语搭配",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.phrases.forEachIndexed { idx, item ->
                        val tag = "phr_$idx"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.phrase,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = item.meaning,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { onSpeak(item.phrase, "US", tag) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "朗读短语",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (playingAudioTag == tag) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (idx < result.phrases.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        if (result.relatedWords.isNotEmpty()) {
            Text(
                text = "同根派生词",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = Color.Transparent
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.relatedWords.forEach { group ->
                        if (group.pos.isNotBlank()) {
                            Text(
                                text = group.pos,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        group.words.forEach { entry ->
                            val tag = "rel_${entry.word}"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.word,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = entry.meaning,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onSpeak(entry.word, "US", tag) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "朗读",
                                        modifier = Modifier.size(15.dp),
                                        tint = if (playingAudioTag == tag) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (result.synonyms.isNotEmpty()) {
            Text(
                text = "同近义词",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                result.synonyms.forEach { syn ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${syn.pos} ${syn.meaning}".trim(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                syn.words.forEach { w ->
                                    Surface(
                                        onClick = { onSpeak(w, "US", "syn_$w") },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = w,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamplesTabContent(
    examples: List<ExampleSentence>,
    playingAudioTag: String?,
    onSpeak: (text: String, accent: String, tag: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        examples.forEachIndexed { idx, ex ->
            val tag = "ex_$idx"
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ex.english,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = ex.chinese,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (ex.source.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "— ${ex.source}",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onSpeak(ex.english, "US", tag) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "朗读例句",
                            modifier = Modifier.size(18.dp),
                            tint = if (playingAudioTag == tag) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicePill(
    accentLabel: String,
    phonetic: String,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isPlaying) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        },
        border = if (isPlaying) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = accentLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isPlaying) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
            )
            if (phonetic.isNotBlank()) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "/$phonetic/",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = "${accentLabel}音真人朗读",
                modifier = Modifier.size(16.dp),
                tint = if (isPlaying) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FormattedMeaningLine(line: String) {
    val match = Regex("""^([a-zA-Z]{1,6}\.(?:&[a-zA-Z]{1,4}\.)?)\s+(.*)$""").matchEntire(line.trim())
    if (match != null) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = match.groupValues[1],
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = match.groupValues[2],
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    } else {
        Text(
            text = line,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BadgePill(text: String, highlight: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = if (highlight) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}
