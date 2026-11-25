package com.lingoreader.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lingoreader.app.data.model.ReviewMode
import com.lingoreader.app.data.model.ReviewScope
import com.lingoreader.app.ui.bookshelf.BookshelfScreen
import com.lingoreader.app.ui.reader.ReaderScreen
import com.lingoreader.app.ui.settings.SettingsScreen
import com.lingoreader.app.ui.theme.LingoReaderTheme
import com.lingoreader.app.ui.vocab.AudioRadioScreen
import com.lingoreader.app.ui.vocab.ReviewScreen
import com.lingoreader.app.ui.vocab.VocabScreen
import com.lingoreader.app.ui.vocab.WordDetailScreen

class MainActivity : ComponentActivity() {

    private val pendingOpenUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            pendingOpenUri.value = intent.data
        }

        val container = (application as LingoReaderApp).container

        setContent {
            LingoReaderTheme {
                LingoReaderRoot(
                    container = container,
                    pendingUri = pendingOpenUri.value,
                    onUriHandled = { pendingOpenUri.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            pendingOpenUri.value = intent.data
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val handler = (application as LingoReaderApp).container.volumeKeyPageHandler
        if (handler != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> if (handler(true)) return true
                KeyEvent.KEYCODE_VOLUME_UP -> if (handler(false)) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val handler = (application as LingoReaderApp).container.volumeKeyPageHandler
        if (handler != null && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val MAIN_TABS = listOf(
    BottomTab("bookshelf", "书架", Icons.Rounded.AutoStories),
    BottomTab("vocab", "单词本", Icons.Rounded.Bookmark),
    BottomTab("settings", "设置", Icons.Rounded.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LingoReaderRoot(
    container: AppContainer,
    pendingUri: Uri?,
    onUriHandled: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val vocabList by container.vocabRepo.words.collectAsState()
    val dueCount = vocabList.count { it.isDue() }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        onUriHandled()
        val imported = container.bookRepo.importFromUri(uri).getOrNull()
        if (imported != null) {
            navController.navigate("reader/${imported.id}?chapter=-1")
        }
    }

    val showBottomBar = currentRoute in MAIN_TABS.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    MAIN_TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.route == "vocab" && dueCount > 0) {
                                    BadgedBox(badge = { Badge { Text(dueCount.coerceAtMost(99).toString()) } }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = if (showBottomBar) {
                Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            } else {
                Modifier
            }
        ) {
            NavHost(
                navController = navController,
                startDestination = "bookshelf"
            ) {
                composable("bookshelf") {
                    BookshelfScreen(
                        container = container,
                        onOpenBook = { bookId ->
                            navController.navigate("reader/$bookId?chapter=-1")
                        }
                    )
                }
                composable("vocab") {
                    VocabScreen(
                        container = container,
                        onStartReview = { mode, reviewScope, bookId, limit ->
                            val encodedBook = Uri.encode(bookId)
                            navController.navigate("review?mode=${mode.id}&scope=${reviewScope.id}&bookId=$encodedBook&limit=$limit")
                        },
                        onOpenWordDetail = { wordId ->
                            navController.navigate("vocab_detail/$wordId")
                        },
                        onOpenAudioRadio = {
                            navController.navigate("audio_radio")
                        }
                    )
                }
                composable("settings") {
                    SettingsScreen(container = container)
                }
                composable(
                    route = "reader/{bookId}?chapter={chapter}",
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType },
                        navArgument("chapter") { type = NavType.IntType; defaultValue = -1 }
                    )
                ) { entry ->
                    val bookId = entry.arguments?.getString("bookId").orEmpty()
                    val chapter = entry.arguments?.getInt("chapter") ?: -1
                    ReaderScreen(
                        bookId = bookId,
                        container = container,
                        onBack = { navController.popBackStack() },
                        initialChapterOverride = chapter,
                        onOpenWordDetail = { wordId ->
                            navController.navigate("vocab_detail/$wordId")
                        }
                    )
                }
                composable(
                    route = "vocab_detail/{wordId}",
                    arguments = listOf(navArgument("wordId") { type = NavType.LongType })
                ) { entry ->
                    val wordId = entry.arguments?.getLong("wordId") ?: 0L
                    WordDetailScreen(
                        wordId = wordId,
                        container = container,
                        onBack = { navController.popBackStack() },
                        onJumpToBook = { bookId, chapterIndex ->
                            navController.navigate("reader/$bookId?chapter=$chapterIndex")
                        }
                    )
                }
                composable(
                    route = "review?mode={mode}&scope={scope}&bookId={bookId}&limit={limit}",
                    arguments = listOf(
                        navArgument("mode") { type = NavType.StringType; defaultValue = "flashcard" },
                        navArgument("scope") { type = NavType.StringType; defaultValue = "due" },
                        navArgument("bookId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("limit") { type = NavType.IntType; defaultValue = 20 }
                    )
                ) { entry ->
                    val mode = ReviewMode.fromId(entry.arguments?.getString("mode"))
                    val reviewScope = ReviewScope.fromId(entry.arguments?.getString("scope"))
                    val bookId = Uri.decode(entry.arguments?.getString("bookId").orEmpty())
                    val limit = entry.arguments?.getInt("limit") ?: 20
                    ReviewScreen(
                        initialMode = mode,
                        reviewScope = reviewScope,
                        bookId = bookId,
                        limit = limit,
                        container = container,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("audio_radio") {
                    AudioRadioScreen(
                        container = container,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
