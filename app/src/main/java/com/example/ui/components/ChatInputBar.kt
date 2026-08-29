package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AVAILABLE_AI_MODELS
import com.example.data.preferences.AiModelInfo

/**
 * MessageInputBar - Rounded rectangle "pill" style container input area.
 * Specifications:
 * - Shape: rounded rectangle "pill" style container (26dp corner radius)
 * - Horizontal margin from screen edges: 14dp
 * - Bottom margin: 8dp with IME & Navigation bar insets
 * - Left Model Switcher: Displays current model ("PWA 2.0") with quick popup menu
 * - Text input field: Placeholder "Message Sonet AI...", 16sp regular font, 22sp line height, auto-expanding
 * - Right send/stop button: 38dp circular button, 19dp icon (arrow up / stop square), smooth 180ms animated state transitions
 * - Smooth 200ms height animation as text wraps to new lines
 */
@Composable
fun MessageInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
    selectedModel: String = "mini_flash",
    onModelSelect: (String) -> Unit = {}
) {
    val hasText = inputText.isNotBlank()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showModelMenu by remember { mutableStateOf(false) }

    val currentModel = AVAILABLE_AI_MODELS.find {
        it.key == selectedModel ||
                (selectedModel == "mili_flash" && it.key == "mini_flash") ||
                (selectedModel == "C" && it.key == "pwa_2_0")
    } ?: AVAILABLE_AI_MODELS.first()

    val handleSend = {
        if (hasText) {
            keyboardController?.hide()
            focusManager.clearFocus()
            onSend()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp, top = 4.dp)
    ) {
        // Rounded rectangle "pill" style container
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(26.dp)
                )
                .animateContentSize(animationSpec = tween(200))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp, max = 150.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Model Switch Button Inside Input Area Bubble
                Box(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 2.dp)
                ) {
                    val isFlash = currentModel.key == "mini_flash" || currentModel.key == "mili_flash"
                    val modelIcon = if (isFlash) Icons.Filled.Bolt else Icons.Filled.AutoAwesome

                    Surface(
                        onClick = { showModelMenu = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .height(36.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .testTag("model_switch_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = modelIcon,
                                contentDescription = "Switch AI Model",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentModel.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Open model menu",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false },
                        properties = androidx.compose.ui.window.PopupProperties(
                            focusable = true,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true
                        )
                    ) {
                        AVAILABLE_AI_MODELS.forEach { modelInfo ->
                            val isSelected = modelInfo.key == selectedModel ||
                                    (selectedModel == "mili_flash" && modelInfo.key == "mini_flash") ||
                                    (selectedModel == "C" && modelInfo.key == "pwa_2_0")

                            val itemIcon = if (modelInfo.key == "mini_flash" || modelInfo.key == "mili_flash") {
                                Icons.Filled.Bolt
                            } else {
                                Icons.Filled.AutoAwesome
                            }

                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = modelInfo.displayName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.5.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isSelected) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Active",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = modelInfo.description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                            maxLines = 1
                                        )
                                    }
                                },
                                onClick = {
                                    onModelSelect(modelInfo.key)
                                    showModelMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Check else itemIcon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text input field (center, flexible width, auto-expanding)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 134.dp)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Message Somet AI...",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        )
                    }

                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { handleSend() },
                            onDone = { handleSend() }
                        ),
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_input_text_field")
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right send/stop button (38dp diameter, circular button)
                AnimatedContent(
                    targetState = isGenerating,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) + scaleIn(animationSpec = tween(180))) togetherWith
                                (fadeOut(animationSpec = tween(180)) + scaleOut(animationSpec = tween(180)))
                    },
                    label = "send_stop_action"
                ) { generating ->
                    if (generating) {
                        // c) Generating state: Stop button with small square icon
                        IconButton(
                            onClick = onStop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .testTag("stop_generation_button"),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop Response",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    } else {
                        // a) Disabled state (reduced opacity ~30%, non-interactive)
                        // b) Enabled state (full opacity, interactive)
                        val sendAlpha by animateFloatAsState(
                            targetValue = if (hasText) 1f else 0.3f,
                            animationSpec = tween(180),
                            label = "send_button_alpha"
                        )

                        IconButton(
                            onClick = { handleSend() },
                            enabled = hasText,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .alpha(sendAlpha)
                                .testTag("send_message_button"),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "Send Message",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Backward compatibility alias for any existing callers
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
    selectedModel: String = "mini_flash",
    onModelSelect: (String) -> Unit = {}
) {
    MessageInputBar(
        inputText = inputText,
        onInputChange = onInputChange,
        onSend = onSend,
        onStop = onStop,
        isGenerating = isGenerating,
        modifier = modifier,
        selectedModel = selectedModel,
        onModelSelect = onModelSelect
    )
}
