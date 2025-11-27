package com.example.simpledownloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Custom Delete Dialog - Shows options for deleting video
 * Option 1: Delete from list only (keep file)
 * Option 2: Delete from phone storage (delete file + list)
 */
@Composable
fun CustomDeleteDialog(
    onDismissRequest: () -> Unit,
    onDeleteFromListOnly: () -> Unit,
    onDeleteFromPhone: () -> Unit,
    videoTitle: String,
    modifier: Modifier = Modifier
) {
    var selectedOption by remember { mutableStateOf<DeleteOption?>(null) }
    
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Videoyu Sil",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Video title
                Text(
                    text = videoTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                
                // Delete options
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Option 1: Delete from list only
                    DeleteOptionItem(
                        icon = Icons.Default.Delete,
                        title = "Sadece Listeden Sil",
                        description = "Video telefonda kalacak, sadece geçmişten silinecek",
                        isSelected = selectedOption == DeleteOption.LIST_ONLY,
                        onClick = { selectedOption = DeleteOption.LIST_ONLY }
                    )
                    
                    // Option 2: Delete from phone
                    DeleteOptionItem(
                        icon = Icons.Default.DeleteForever,
                        title = "Telefondan Sil",
                        description = "Video hem geçmişten hem de telefon hafızasından silinecek",
                        isSelected = selectedOption == DeleteOption.FROM_PHONE,
                        isDestructive = true,
                        onClick = { selectedOption = DeleteOption.FROM_PHONE }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    // Cancel button
                    TextButton(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("İptal")
                    }
                    
                    // Delete button
                    FilledTonalButton(
                        onClick = {
                            when (selectedOption) {
                                DeleteOption.LIST_ONLY -> onDeleteFromListOnly()
                                DeleteOption.FROM_PHONE -> onDeleteFromPhone()
                                null -> {} // Do nothing if no option selected
                            }
                        },
                        enabled = selectedOption != null,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Sil")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) {
            if (isDestructive) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            }
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        },
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDestructive && isSelected) {
                    MaterialTheme.colorScheme.errorContainer
                } else if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDestructive && isSelected) {
                            MaterialTheme.colorScheme.error
                        } else if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // Selection indicator
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

private enum class DeleteOption {
    LIST_ONLY,
    FROM_PHONE
}
