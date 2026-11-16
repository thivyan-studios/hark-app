package com.thivyanstudios.hark.ui.screens

import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.data.local.TranscriptionEntity
import com.thivyanstudios.hark.ui.theme.GlassActionBar
import com.thivyanstudios.hark.ui.theme.GlassActionButton
import com.thivyanstudios.hark.ui.theme.HarkText
import com.thivyanstudios.hark.ui.theme.LocalBackdropBlur
import com.thivyanstudios.hark.ui.theme.glassSurface
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<TranscriptionEntity>,
    onDeleteAll: () -> Unit,
    onDeleteById: (Long) -> Unit,
    onShare: (String) -> Unit,
    onEdit: (id: Long, text: String) -> Unit,
    hapticFeedbackEnabled: Boolean = false,
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    // Id of the transcript open in the editor (saveable so rotation keeps it open)
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editingItem = history.firstOrNull { it.id == editingId }
    val copy = rememberCopyToClipboard()

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                HarkText(
                    text = stringResource(R.string.history_delete_all_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                HarkText(
                    text = stringResource(R.string.history_delete_all_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAll()
                        showDeleteAllDialog = false
                    }
                ) {
                    HarkText(
                        text = stringResource(R.string.history_delete_all_confirm),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    HarkText(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (editingItem != null) {
        TranscriptEditorDialog(
            item = editingItem,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            onSave = { text -> onEdit(editingItem.id, text) },
            onCopy = copy,
            onShare = onShare,
            onDelete = {
                onDeleteById(editingItem.id)
                editingId = null
            },
            onClose = { editingId = null },
        )
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.cd_delete_all_history),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (history.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HarkText(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(history, key = { it.id }) { item ->
                        HistoryItem(
                            item = item,
                            onOpen = { editingId = item.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    item: TranscriptionEntity,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape, elevation = 4.dp)
            .clickable(onClickLabel = "Edit transcript", onClick = onOpen)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateChip(item.timestamp)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DateChip(timestamp: Long) {
    val date = remember(timestamp) {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm", Locale.getDefault()))
    }
    Text(
        text = date,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}

/**
 * Full-screen glass editor for one transcript. The app behind it is blurred (Android 12+)
 * and dimmed. Edits are saved when you tap Done or close the editor; a blank edit is ignored
 * so a transcript can't be wiped by accident.
 */
@Composable
private fun TranscriptEditorDialog(
    item: TranscriptionEntity,
    onSave: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    hapticFeedbackEnabled: Boolean,
) {
    var text by rememberSaveable(item.id) { mutableStateOf(item.text) }
    val changed = text != item.text
    val closeAndSave = {
        if (changed && text.isNotBlank()) onSave(text)
        onClose()
    }

    Dialog(
        onDismissRequest = closeAndSave,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BlurAppBehind()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxSize()
                    // Translucent over the blurred app; nearly solid where blur isn't available
                    .glassSurface(
                        RoundedCornerShape(28.dp),
                        elevation = 24.dp,
                        paneAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.8f else 0.96f,
                    )
                    .padding(20.dp)
            ) {
                // Header: what's being edited, and Done
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Edit transcript",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        DateChip(item.timestamp)
                    }
                    FilledTonalButton(onClick = closeAndSave, enabled = text.isNotBlank()) {
                        Text(if (changed) "Save" else "Done", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // The editor, with the same floating glass pill as Transcribe over its bottom
                // edge. The text area is the pill's blur source.
                val editorHaze = rememberHazeState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(editorHaze)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                            // Bottom room so the last lines can sit clear of the pill
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
                    )

                    GlassActionBar(
                        hazeState = editorHaze,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                    ) {
                        GlassActionButton(
                            icon = Icons.Rounded.ContentCopy, label = "Copy",
                            onClick = { onCopy(text) }, hapticFeedbackEnabled = hapticFeedbackEnabled,
                        )
                        GlassActionButton(
                            icon = Icons.Rounded.Share, label = "Share",
                            tint = MaterialTheme.colorScheme.tertiary,
                            onClick = { onShare(text) }, hapticFeedbackEnabled = hapticFeedbackEnabled,
                        )
                        if (changed) {
                            GlassActionButton(
                                icon = Icons.AutoMirrored.Rounded.Undo, label = "Undo edits",
                                tint = MaterialTheme.colorScheme.secondary,
                                onClick = { text = item.text }, hapticFeedbackEnabled = hapticFeedbackEnabled,
                            )
                        }
                        GlassActionButton(
                            icon = Icons.Rounded.Delete, label = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = onDelete, hapticFeedbackEnabled = hapticFeedbackEnabled,
                        )
                    }
                }
            }
        }
    }
}

/** Blurs (Android 12+) and dims the app behind the dialog while it's open. */
@Composable
private fun BlurAppBehind() {
    val setBackdropBlur = LocalBackdropBlur.current
    DisposableEffect(Unit) {
        setBackdropBlur(true)
        onDispose { setBackdropBlur(false) }
    }
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0.35f)
    }
}

/** Copies text, with a short confirmation on Android versions that don't show their own. */
@Composable
private fun rememberCopyToClipboard(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboard, context) {
        { text: String ->
            clipboard.setText(AnnotatedString(text))
            // Android 13+ shows its own clipboard confirmation
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
