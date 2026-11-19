package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.ImageEncoder
import com.example.data.model.Community
import com.example.data.model.PostType
import com.example.ui.components.WafaMediaImage
import com.example.ui.viewmodel.AiAssistantUiState
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.WafaSocialViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatePostScreen(
    viewModel: WafaSocialViewModel,
    aiViewModel: AiAssistantViewModel,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val draftText by viewModel.composerDraftText.collectAsStateWithLifecycle()
    val myCommunities by viewModel.myCommunities.collectAsStateWithLifecycle()
    val aiState by aiViewModel.uiState.collectAsStateWithLifecycle()

    var selectedType by remember { mutableStateOf(PostType.TEXT) }
    var visibility by remember { mutableStateOf("public") }
    var selectedCommunity by remember { mutableStateOf<Community?>(null) }
    var mediaData by remember { mutableStateOf<String?>(null) }
    var mediaAspect by remember { mutableStateOf("16:9") }
    var isCompressing by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    val pollOptions = remember { mutableStateListOf("", "") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isCompressing = true
            scope.launch {
                val encoded = ImageEncoder.uriToCompressedDataUri(
                    context = context,
                    uri = uri,
                    maxDimensionPx = 640,
                    maxChars = 220_000
                )
                isCompressing = false
                if (encoded != null) {
                    mediaData = encoded
                    if (selectedType == PostType.TEXT) selectedType = PostType.PHOTO
                } else {
                    viewModel.emitToast("Image could not be compressed within cloud upload limits.")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Create Content", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Share posts, photos, polls, questions, or reels",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    isPublishing = true
                    viewModel.publishPost(
                        content = draftText,
                        postType = selectedType.wireValue,
                        mediaData = mediaData,
                        mediaAspect = if (selectedType == PostType.REEL) "9:16" else mediaAspect,
                        pollOptions = pollOptions.toList(),
                        visibility = visibility,
                        community = selectedCommunity
                    ) {
                        isPublishing = false
                        mediaData = null
                        selectedCommunity = null
                        aiViewModel.resetState()
                        onPublished()
                    }
                    isPublishing = false
                },
                enabled = draftText.trim().isNotEmpty() && !isPublishing && !isCompressing,
                modifier = Modifier.testTag("publish_post_button")
            ) {
                Text(if (isPublishing) "Publishing..." else "Publish")
            }
        }

        // Post Type Selector
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PostType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.label) },
                    modifier = Modifier.testTag("composer_type_${type.wireValue.lowercase()}")
                )
            }
        }

        // Main Composer Field
        OutlinedTextField(
            value = draftText,
            onValueChange = { if (it.length <= 2000) viewModel.updateComposerDraft(it) },
            label = {
                Text(
                    when (selectedType) {
                        PostType.QUESTION -> "Ask the Wafa Social community..."
                        PostType.POLL -> "Write your poll question..."
                        PostType.REEL -> "Describe your Reel & add #hashtags..."
                        else -> "What would you like to share? Add #hashtags..."
                    }
                )
            },
            minLines = 4,
            maxLines = 8,
            supportingText = { Text("${draftText.length} / 2000") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("composer_content_input")
        )

        // Photo Picker / Reel Visual Card
        if (selectedType == PostType.PHOTO || selectedType == PostType.REEL || selectedType == PostType.TEXT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !isCompressing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_photo_button")
                ) {
                    Icon(
                        imageVector = if (selectedType == PostType.REEL) Icons.Rounded.VideoLibrary else Icons.Rounded.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isCompressing -> "Optimizing photo..."
                            mediaData != null -> "Change Attached Visual"
                            selectedType == PostType.REEL -> "Pick Vertical Reel Cover"
                            else -> "Attach Photo (Zero-Permission Picker)"
                        }
                    )
                }
                if (mediaData != null) {
                    IconButton(onClick = { mediaData = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove attached photo")
                    }
                }
            }
            if (selectedType == PostType.REEL) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Reel cards publish immediately to the swipeable vertical Reels feed. Dedicated adaptive HLS video transcoding requires an external media server / CDN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            if (!mediaData.isNullOrBlank()) {
                WafaMediaImage(
                    mediaData = mediaData!!,
                    contentDescription = "Selected post attachment preview",
                    aspectRatio = if (selectedType == PostType.REEL) 3f / 4f else 16f / 10f
                )
            }
        }

        // Poll Options Builder
        if (selectedType == PostType.POLL) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Poll Options (2–4 choices)", style = MaterialTheme.typography.titleSmall)
                    pollOptions.forEachIndexed { idx, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { if (it.length <= 80) pollOptions[idx] = it },
                            label = { Text("Option ${idx + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pollOptions.size < 4) {
                            TextButton(onClick = { pollOptions.add("") }) { Text("+ Add Option") }
                        }
                        if (pollOptions.size > 2) {
                            TextButton(onClick = { pollOptions.removeAt(pollOptions.lastIndex) }) { Text("Remove Last") }
                        }
                    }
                }
            }
        }

        // Audience & Community Target
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = visibility == "public",
                onClick = { visibility = "public" },
                leadingIcon = { Icon(Icons.Rounded.Public, null, modifier = Modifier.size(16.dp)) },
                label = { Text("Public") }
            )
            FilterChip(
                selected = visibility == "private",
                onClick = { visibility = "private" },
                leadingIcon = { Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(16.dp)) },
                label = { Text("Only Me") }
            )
        }

        if (myCommunities.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Post to Community (optional)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedCommunity == null,
                        onClick = { selectedCommunity = null },
                        label = { Text("Main Feed") }
                    )
                    myCommunities.forEach { comm ->
                        FilterChip(
                            selected = selectedCommunity?.id == comm.id,
                            onClick = { selectedCommunity = comm },
                            label = { Text(comm.name) }
                        )
                    }
                }
            }
        }

        // Built-in AI Writing Assistant (Firebase AI Logic — never auto-publishes!)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Gemini Writing Assistant", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Polishes copy & suggests hashtags · You always confirm before posting",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = { aiViewModel.runStructuredDraft(draftText) },
                    enabled = draftText.trim().isNotEmpty() && aiState !is AiAssistantUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("composer_ai_polish_btn")
                ) {
                    if (aiState is AiAssistantUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Generating suggestion...")
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Polish Draft & Suggest Hashtags")
                    }
                }

                when (val s = aiState) {
                    is AiAssistantUiState.Error -> {
                        Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    is AiAssistantUiState.StructuredSuccess -> {
                        val suggestion = s.suggestion
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(suggestion.polishedText, style = MaterialTheme.typography.bodyMedium)
                                if (suggestion.hashtags.isNotEmpty()) {
                                    Text(
                                        suggestion.hashtags.joinToString(" ") { "#$it" },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (suggestion.tips.isNotBlank()) {
                                    Text(
                                        "Tip: ${suggestion.tips}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val tagSuffix = if (suggestion.hashtags.isNotEmpty()) {
                                                "\n\n" + suggestion.hashtags.joinToString(" ") { "#$it" }
                                            } else ""
                                            viewModel.updateComposerDraft((suggestion.polishedText + tagSuffix).take(2000))
                                            aiViewModel.resetState()
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Apply to Draft")
                                    }
                                    TextButton(onClick = { aiViewModel.resetState() }) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
