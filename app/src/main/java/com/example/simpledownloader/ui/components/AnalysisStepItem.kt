package com.example.simpledownloader.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simpledownloader.domain.model.AnalysisStep

/**
 * Premium Analysis Step Item with Animations
 */
@Composable
fun AnalysisStepItem(
    step: AnalysisStep,
    isActive: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    // Pulse animation for active step
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon/Progress Indicator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp)
        ) {
            when {
                isCompleted -> {
                    // Completed - Checkmark with gradient background
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                isActive -> {
                    // Active - Pulsing progress indicator
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(44.dp)
                                .scale(if (isActive) pulseScale else 1f)
                                .alpha(if (isActive) pulseAlpha else 1f),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = getStepEmoji(step),
                            fontSize = 20.sp
                        )
                    }
                }
                else -> {
                    // Waiting - Subtle icon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = getStepEmoji(step),
                                fontSize = 20.sp,
                                modifier = Modifier.alpha(0.5f)
                            )
                        }
                    }
                }
            }
        }
        
        // Step Text
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = step.message,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp
                ),
                color = when {
                    isCompleted -> MaterialTheme.colorScheme.primary
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
            
            if (isActive) {
                Text(
                    text = "In progress...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Get emoji for analysis step
 */
private fun getStepEmoji(step: AnalysisStep): String {
    return when (step) {
        AnalysisStep.VALIDATING -> "🔍"
        AnalysisStep.FETCHING -> "📡"
        AnalysisStep.ANALYZING -> "⚙️"
        AnalysisStep.READY -> "✅"
    }
}

/**
 * Get icon for analysis step (backup if emojis don't display well)
 */
private fun getStepIcon(step: AnalysisStep): ImageVector {
    return when (step) {
        AnalysisStep.VALIDATING -> Icons.Default.Search
        AnalysisStep.FETCHING -> Icons.Default.Refresh
        AnalysisStep.ANALYZING -> Icons.Default.PlayArrow
        AnalysisStep.READY -> Icons.Default.CheckCircle
    }
}
