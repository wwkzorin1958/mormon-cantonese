package org.kcmcelia.bookofmormon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kcmcelia.bookofmormon.MainViewModel
import org.kcmcelia.bookofmormon.R
import org.kcmcelia.bookofmormon.util.formatDuration

@Composable
fun PlayerScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = if (dragging) dragFraction else (state.positionMs.toFloat() / duration)
    val shownPosition = if (dragging) (dragFraction * duration).toLong() else state.positionMs

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.back)) }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = "${formatDuration(shownPosition)} / ${formatDuration(duration)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Slider(
                value = fraction.coerceIn(0f, 1f),
                onValueChange = {
                    dragging = true
                    dragFraction = it
                },
                onValueChangeFinished = {
                    viewModel.player.seekTo((dragFraction * duration).toLong())
                    dragging = false
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { viewModel.player.seekBy(-30_000L) }) {
                    Text("−30 秒")
                }

                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp),
                ) {
                    TextButton(
                        onClick = viewModel.player::toggle,
                        modifier = Modifier.height(72.dp).fillMaxWidth(0.4f),
                    ) {
                        Text(
                            text = if (state.isPlaying) stringResource(R.string.pause)
                            else stringResource(R.string.play),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                OutlinedButton(onClick = { viewModel.player.seekBy(30_000L) }) {
                    Text("+30 秒")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                OutlinedButton(
                    onClick = viewModel.player::previous,
                    enabled = state.hasPrevious,
                ) { Text(stringResource(R.string.prev_chapter)) }

                OutlinedButton(
                    onClick = viewModel.player::next,
                    enabled = state.hasNext,
                ) { Text(stringResource(R.string.next_chapter)) }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.speed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                    val selected = kotlin.math.abs(state.speed - speed) < 0.01f
                    SpeedChip(
                        label = "${speed}×",
                        selected = selected,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.player.setSpeed(speed) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepChip("15", Modifier.weight(1f)) { viewModel.player.setSleepTimer(15) }
                SleepChip("30", Modifier.weight(1f)) { viewModel.player.setSleepTimer(30) }
                SleepChip("60", Modifier.weight(1f)) { viewModel.player.setSleepTimer(60) }
                SleepChip(stringResource(R.string.sleep_off), Modifier.weight(1f)) {
                    viewModel.player.setSleepTimer(null)
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpeedChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label, color = MaterialTheme.colorScheme.primary)
        }
    } else {
        TextButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun SleepChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
}
