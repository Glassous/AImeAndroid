package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSelectionBottomSheet(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onTakeVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onRecordAudio: () -> Unit,
    onPickPdf: () -> Unit,
    onPickTextFile: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        AttachmentSelectionContent(
            onDismiss = onDismiss,
            onPickImage = onPickImage,
            onTakePhoto = onTakePhoto,
            onPickVideo = onPickVideo,
            onTakeVideo = onTakeVideo,
            onPickAudio = onPickAudio,
            onRecordAudio = onRecordAudio,
            onPickPdf = onPickPdf,
            onPickTextFile = onPickTextFile
        )
    }
}

@Composable
fun AttachmentSelectionContent(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onTakeVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onRecordAudio: () -> Unit,
    onPickPdf: () -> Unit,
    onPickTextFile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "上传附件",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            item {
                AttachmentOptionItem(
                    icon = Icons.Default.Image,
                    text = "本地图片",
                    onClick = {
                        onPickImage()
                        onDismiss()
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                AttachmentOptionItem(
                    icon = Icons.Default.CameraAlt,
                    text = "拍摄照片",
                    onClick = {
                        onTakePhoto()
                        onDismiss()
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                AttachmentOptionItem(
                    icon = Icons.Default.VideoLibrary,
                    text = "本地视频",
                    onClick = {
                        onPickVideo()
                        onDismiss()
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                AttachmentOptionItem(
                    icon = Icons.Default.Videocam,
                    text = "录制视频",
                    onClick = {
                        onTakeVideo()
                        onDismiss()
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                AttachmentOptionItem(
                    icon = Icons.Default.AudioFile,
                    text = "本地音频",
                    onClick = {
                        onPickAudio()
                        onDismiss()
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                AttachmentOptionItem(
                    icon = Icons.Default.Mic,
                    text = "录制音频",
                    onClick = {
                        onRecordAudio()
                        onDismiss()
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                AttachmentOptionItem(
                    icon = Icons.Default.PictureAsPdf,
                    text = "PDF文件",
                    onClick = {
                        onPickPdf()
                        onDismiss()
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                AttachmentOptionItem(
                    icon = Icons.Default.Description,
                    text = "纯文本文件",
                    onClick = {
                        onPickTextFile()
                        onDismiss()
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AttachmentOptionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
