package org.kcmcelia.bookofmormon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.kcmcelia.bookofmormon.MainViewModel
import org.kcmcelia.bookofmormon.R
import org.kcmcelia.bookofmormon.data.BookRef
import org.kcmcelia.bookofmormon.data.Chapter
import org.kcmcelia.bookofmormon.data.ContentManifest
import org.kcmcelia.bookofmormon.data.Progress
import org.kcmcelia.bookofmormon.util.formatDuration
import org.kcmcelia.bookofmormon.util.formatHours
import androidx.compose.ui.res.stringResource

sealed interface Screen {
    data object Home : Screen
    data class Book(val bookIndex: Int) : Screen
    data object Settings : Screen
    data object Battery : Screen
}

@Composable
fun BomApp(viewModel: MainViewModel) {
    BomTheme {
        val progress by viewModel.progress.collectAsStateWithLifecycle()
        val downloadedCount by viewModel.downloadedCount.collectAsStateWithLifecycle()
        val newVersion by viewModel.newVersion.collectAsStateWithLifecycle()
        val message by viewModel.message.collectAsStateWithLifecycle()

        // Mapping then distinctUntilChanged keeps playback ticks from recomposing the list.
        val currentId by remember(viewModel) {
            viewModel.playerState.map { it.mediaId }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = null)

        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        var showPlayer by remember { mutableStateOf(false) }
        var showIntro by remember { mutableStateOf(false) }
        var introDismissed by remember { mutableStateOf(false) }

        LaunchedEffect(progress.introSeen) {
            if (!progress.introSeen && !introDismissed) showIntro = true
        }

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(message) {
            message?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessage()
            }
        }

        BackHandler(enabled = showPlayer || screen != Screen.Home) {
            if (showPlayer) showPlayer = false else screen = Screen.Home
        }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    MiniPlayerBar(viewModel) { showPlayer = true }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (val current = screen) {
                        Screen.Home -> HomeScreen(
                            manifest = viewModel.manifest,
                            progress = progress,
                            downloadedCount = downloadedCount,
                            downloadedIndices = remember(downloadedCount, viewModel.manifest) {
                                viewModel.manifest.items
                                    .filter { viewModel.content.isDownloaded(it) }
                                    .map { it.index }
                                    .toSet()
                            },
                            onResume = viewModel::resume,
                            onPlayChapter = viewModel::playChapter,
                            onOpenBook = { screen = Screen.Book(it) },
                            onDownloadAll = viewModel::downloadAll,
                            onOpenSettings = { screen = Screen.Settings },
                        )

                        is Screen.Book -> BookScreen(
                            book = viewModel.manifest.books.first { it.index == current.bookIndex },
                            chapters = viewModel.manifest.chaptersOf(current.bookIndex),
                            currentId = currentId,
                            downloadedIndices = remember(downloadedCount, viewModel.manifest) {
                                viewModel.manifest.items
                                    .filter { viewModel.content.isDownloaded(it) }
                                    .map { it.index }
                                    .toSet()
                            },
                            onBack = { screen = Screen.Home },
                            onPlay = viewModel::playChapter,
                            onDownloadBook = { viewModel.downloadBook(current.bookIndex) },
                        )

                        Screen.Settings -> SettingsScreen(
                            viewModel = viewModel,
                            newVersion = newVersion,
                            onBack = { screen = Screen.Home },
                            onOpenBattery = { screen = Screen.Battery },
                        )

                        Screen.Battery -> BatteryGuideScreen(onBack = { screen = Screen.Settings })
                    }
                }
            }

            if (showPlayer) {
                PlayerScreen(viewModel = viewModel, onClose = { showPlayer = false })
            }

            if (showIntro) {
                AlertDialog(
                    onDismissRequest = {
                        showIntro = false
                        introDismissed = true
                        viewModel.markIntroSeen()
                    },
                    title = { Text(stringResource(R.string.intro_title)) },
                    text = { Text(stringResource(R.string.intro_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showIntro = false
                            introDismissed = true
                            viewModel.markIntroSeen()
                        }) { Text(stringResource(R.string.intro_start)) }
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------- home

@Composable
private fun HomeScreen(
    manifest: ContentManifest,
    progress: Progress,
    downloadedCount: Int,
    downloadedIndices: Set<Int>,
    onResume: () -> Unit,
    onPlayChapter: (Chapter) -> Unit,
    onOpenBook: (Int) -> Unit,
    onDownloadAll: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val current = progress.index?.let { manifest.chapterAt(it) } ?: manifest.items.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "${manifest.counts.chapters} 章 · ${formatHours(manifest.counts.durationSec)} 小時",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.settings))
                }
            }
        }

        if (current != null) {
            item {
                ContinueCard(
                    chapter = current,
                    positionMs = progress.positionMs,
                    onResume = onResume,
                )
            }
        }

        if (downloadedCount == 0) {
            item { DownloadOfferCard(onDownloadAll = onDownloadAll) }
        }

        item { SectionHeader(stringResource(R.string.home_front_matter)) }

        listItems(manifest.frontMatter, key = { it.index }) { chapter ->
            ChapterRow(
                chapter = chapter,
                downloaded = chapter.index in downloadedIndices,
                onClick = { onPlayChapter(chapter) },
            )
        }

        item {
            SectionHeader(
                stringResource(R.string.home_books) + "  ·  " +
                    stringResource(R.string.download_progress, downloadedCount, manifest.counts.items)
            )
        }

        listItems(manifest.books, key = { it.index }) { book ->
            BookRow(
                book = book,
                downloadedChapters = manifest.chaptersOf(book.index).count { it.index in downloadedIndices },
                onClick = { onOpenBook(book.index) },
            )
        }
    }
}

@Composable
private fun ContinueCard(chapter: Chapter, positionMs: Long, onResume: () -> Unit) {
    val totalMs = (chapter.durationSec * 1000).toLong().coerceAtLeast(1L)
    val fraction = (positionMs.toFloat() / totalMs).coerceIn(0f, 1f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.home_continue),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = chapter.displayTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = if (positionMs > 0) "${formatDuration(positionMs)} / ${formatDuration(totalMs)}"
                else stringResource(R.string.home_not_started),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            TextButton(onClick = onResume) {
                Text(stringResource(R.string.home_resume), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun DownloadOfferCard(onDownloadAll: () -> Unit) {
    Card {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.download_offer),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.download_wifi_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDownloadAll) {
                Text(stringResource(R.string.download_all))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun ChapterRow(chapter: Chapter, downloaded: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chapter.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${(chapter.durationSec / 60).toInt()} 分鐘",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (downloaded) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}

@Composable
private fun BookRow(book: BookRef, downloadedChapters: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = book.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.home_chapter_count, book.chapters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (downloadedChapters > 0) {
                Text(
                    text = "$downloadedChapters/${book.chapters}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------- book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookScreen(
    book: BookRef,
    chapters: List<Chapter>,
    currentId: Int?,
    downloadedIndices: Set<Int>,
    onBack: () -> Unit,
    onPlay: (Chapter) -> Unit,
    onDownloadBook: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(book.name, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                gridItems(chapters, key = { it.index }) { chapter ->
                    ChapterCell(
                        chapter = chapter,
                        selected = chapter.index == currentId,
                        downloaded = chapter.index in downloadedIndices,
                        onClick = { onPlay(chapter) },
                    )
                }
            }
            TextButton(
                onClick = onDownloadBook,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.download_this_book))
            }
        }
    }
}

@Composable
private fun ChapterCell(
    chapter: Chapter,
    selected: Boolean,
    downloaded: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .height(64.dp)
            .background(background, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (chapter.chapter ?: chapter.index).toString(),
            style = MaterialTheme.typography.titleMedium,
            color = foreground,
        )
        if (downloaded) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(8.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                        CircleShape,
                    )
            )
        }
    }
}

// ---------------------------------------------------------------------------- mini player

@Composable
private fun MiniPlayerBar(viewModel: MainViewModel, onOpen: () -> Unit) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    if (state.mediaId == null) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = viewModel.player::toggle) {
                Text(
                    text = if (state.isPlaying) stringResource(R.string.pause)
                    else stringResource(R.string.play),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
