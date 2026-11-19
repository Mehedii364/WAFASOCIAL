package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.firebase.ImageEncoder
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun WafaAvatar(
    name: String,
    avatarData: String?,
    size: Dp = 42.dp,
    ringActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val decoded = remember(avatarData) { ImageEncoder.decodeDataUriToImageBitmap(avatarData) }
    val ringModifier = if (ringActive) {
        Modifier
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                ),
                shape = CircleShape
            )
            .padding(3.dp)
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    }

    Box(
        modifier = modifier
            .size(size)
            .then(ringModifier)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        when {
            decoded != null -> {
                Image(
                    bitmap = decoded,
                    contentDescription = "$name profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            !avatarData.isNullOrBlank() && (avatarData.startsWith("http://") || avatarData.startsWith("https://")) -> {
                AsyncImage(
                    model = avatarData,
                    contentDescription = "$name profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val initials = remember(name) {
                    name.trim()
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                        .take(2)
                        .joinToString("") { it.first().uppercase() }
                        .ifBlank { "W" }
                }
                Text(
                    text = initials,
                    style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WafaMediaImage(
    mediaData: String,
    contentDescription: String,
    aspectRatio: Float = 16f / 10f,
    modifier: Modifier = Modifier
) {
    val decoded = remember(mediaData) { ImageEncoder.decodeDataUriToImageBitmap(mediaData) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        when {
            decoded != null -> {
                Image(
                    bitmap = decoded,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            mediaData.startsWith("http://") || mediaData.startsWith("https://") -> {
                AsyncImage(
                    model = mediaData,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Rounded.Verified,
        contentDescription = "Verified Wafa Social account",
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = modifier.size(16.dp)
    )
}

@Composable
fun WafaEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                androidx.compose.material3.FilledTonalButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun WafaSkeletonPostCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    val shimmer = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(shimmer))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(130.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
                    Box(Modifier.width(84.dp).height(11.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.75f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(shimmer))
        }
    }
}

@Composable
fun MetricStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun formatRelativeTime(ts: Timestamp?): String {
    if (ts == null) return "Just now"
    val diffMs = System.currentTimeMillis() - ts.toDate().time
    val absSec = abs(diffMs) / 1000L
    return when {
        absSec < 45 -> "Now"
        absSec < 3600 -> "${absSec / 60}m"
        absSec < 86400 -> "${absSec / 3600}h"
        absSec < 604800 -> "${absSec / 86400}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(ts.toDate())
    }
}

fun parseAccentHex(hex: String, fallback: Color = Color(0xFF0F766E)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
