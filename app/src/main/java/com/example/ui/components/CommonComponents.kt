package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.*

fun sanitizeImageUrl(
    url: String?,
    fallbackUrl: String = "https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=1200&q=80"
): String {
    if (url.isNullOrBlank()) return fallbackUrl
    val trimmed = url.trim()
    return when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "https://")
        !trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("content://") && !trimmed.startsWith("file://") -> "https://$trimmed"
        else -> trimmed
    }
}

@Composable
fun rememberTravelImageRequest(
    url: String?,
    fallbackUrl: String = "https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=1200&q=80"
): ImageRequest {
    val context = LocalContext.current
    val cleanUrl = remember(url, fallbackUrl) { sanitizeImageUrl(url, fallbackUrl) }
    return remember(cleanUrl) {
        ImageRequest.Builder(context)
            .data(cleanUrl)
            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0 TripMate/1.0")
            .crossfade(true)
            .build()
    }
}

@Composable
fun TravelBadge(
    text: String,
    containerColor: Color = TravelNavyContainer,
    contentColor: Color = TravelNavyDark,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(12.dp))
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BudgetProgressBar(
    totalBudget: Double,
    spentBudget: Double,
    modifier: Modifier = Modifier
) {
    val progress = if (totalBudget > 0) (spentBudget / totalBudget).toFloat().coerceIn(0f, 1f) else 0f
    val remaining = (totalBudget - spentBudget).coerceAtLeast(0.0)
    val isOverBudget = spentBudget > totalBudget

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Total Trip Budget",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = "₹${totalBudget.toInt()}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isOverBudget) "Over Budget" else "Remaining Balance",
                    fontSize = 12.sp,
                    color = if (isOverBudget) TravelWarningRed else TravelForestGreen
                )
                Text(
                    text = "₹${remaining.toInt()}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOverBudget) TravelWarningRed else TravelForestGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isOverBudget) TravelWarningRed else TravelTerracotta,
            trackColor = TravelBorderSubtle,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Committed: ₹${spentBudget.toInt()} (${(progress * 100).toInt()}%)",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Text(
                text = if (isOverBudget) "Exceeded by ₹${(spentBudget - totalBudget).toInt()}" else "Healthy runway",
                fontSize = 11.sp,
                color = if (isOverBudget) TravelWarningRed else TextMuted
            )
        }
    }
}

@Composable
fun AiSuggestionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = TravelSurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, TravelBorderSubtle),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = TravelAmberAccent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun DemoModeBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFEF3C7),
        contentColor = Color(0xFF92400E),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(11.dp))
            Text(
                text = "DEMO DATA • API Ready",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LiveConnectedBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFDCFCE7),
        contentColor = Color(0xFF166534),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(11.dp))
            Text(
                text = "LIVE DATA • Real-Time Research",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun travelTextFieldColors(
    containerColor: Color = TravelSurfaceCard,
    unfocusedBorderColor: Color = TravelBorderSubtle,
    focusedBorderColor: Color = TravelNavyPrimary
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF0F172A),
    unfocusedTextColor = Color(0xFF0F172A),
    disabledTextColor = Color(0xFF475569),
    errorTextColor = Color(0xFFDC2626),
    cursorColor = Color(0xFF1E3A5F),
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    disabledContainerColor = containerColor,
    focusedBorderColor = focusedBorderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    focusedLabelColor = TravelNavyPrimary,
    unfocusedLabelColor = Color(0xFF475569),
    focusedPlaceholderColor = Color(0xFF94A3B8),
    unfocusedPlaceholderColor = Color(0xFF94A3B8),
    focusedSupportingTextColor = Color(0xFF475569),
    unfocusedSupportingTextColor = Color(0xFF475569),
    focusedPrefixColor = Color(0xFF0F172A),
    unfocusedPrefixColor = Color(0xFF0F172A),
    focusedSuffixColor = Color(0xFF0F172A),
    unfocusedSuffixColor = Color(0xFF0F172A)
)

/**
 * Universally styled travel input field ensuring typed text is always crisp and visible (never white).
 */
@Composable
fun TravelAppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    containerColor: Color = TravelSurfaceCard
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        shape = shape,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color(0xFF0F172A),
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        ),
        colors = travelTextFieldColors(containerColor = containerColor)
    )
}

/**
 * Modal dialog displayed when researching / synthesizing a destination via Gemini AI.
 * Shows an animated loading state and destination name as requested.
 */
@Composable
fun DestinationGeneratingDialog(
    destinationName: String
) {
    AlertDialog(
        onDismissRequest = { /* Cannot dismiss while generating */ },
        confirmButton = {},
        dismissButton = null,
        shape = RoundedCornerShape(24.dp),
        containerColor = TravelSurfaceCard,
        modifier = Modifier.testTag("destination_generating_dialog"),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(TravelNavyContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = TravelNavyPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Generating",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TravelNavyPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (destinationName.isNotBlank()) {
                        "TripMate AI is researching destination guides, authentic landmarks & travel insights for $destinationName..."
                    } else {
                        "TripMate AI is researching destination guides, landmarks & travel insights..."
                    },
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = TravelTerracotta,
                    trackColor = TravelNavyContainer
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = TravelAmberAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Powered by Gemini AI & Real Travel Knowledge",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    )
}

