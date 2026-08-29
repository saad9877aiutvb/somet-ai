package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeBackgroundDark
import com.example.ui.theme.CodeText
import com.example.ui.theme.LatoFontFamily

sealed class MarkdownElement {
    data class Paragraph(val text: AnnotatedString) : MarkdownElement()
    data class Header(val level: Int, val text: AnnotatedString) : MarkdownElement()
    data class BulletItem(val text: AnnotatedString) : MarkdownElement()
    data class NumberedItem(val number: String, val text: AnnotatedString) : MarkdownElement()
    data class CodeBlock(val language: String, val code: String) : MarkdownElement()
    data class Blockquote(val text: AnnotatedString) : MarkdownElement()
    object Divider : MarkdownElement()
}

@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val elements = parseMarkdown(content, textColor)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        elements.forEach { element ->
            when (element) {
                is MarkdownElement.Header -> {
                    val (fontSize, fontWeight) = when (element.level) {
                        1 -> 24.sp to FontWeight.Bold
                        2 -> 20.5.sp to FontWeight.SemiBold
                        else -> 18.sp to FontWeight.Medium
                    }
                    Text(
                        text = element.text,
                        fontSize = fontSize,
                        fontFamily = LatoFontFamily,
                        fontWeight = fontWeight,
                        color = textColor,
                        lineHeight = (fontSize.value * 1.35).sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 3.dp)
                    )
                }

                is MarkdownElement.Paragraph -> {
                    Text(
                        text = element.text,
                        fontSize = 16.5.sp,
                        fontFamily = LatoFontFamily,
                        lineHeight = 25.5.sp,
                        color = textColor,
                        letterSpacing = 0.15.sp
                    )
                }

                is MarkdownElement.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 18.sp,
                            fontFamily = LatoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = element.text,
                            fontSize = 16.5.sp,
                            fontFamily = LatoFontFamily,
                            lineHeight = 25.sp,
                            color = textColor
                        )
                    }
                }

                is MarkdownElement.NumberedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = element.number,
                            fontSize = 15.5.sp,
                            fontFamily = LatoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(26.dp)
                        )
                        Text(
                            text = element.text,
                            fontSize = 16.5.sp,
                            fontFamily = LatoFontFamily,
                            lineHeight = 25.sp,
                            color = textColor
                        )
                    }
                }

                is MarkdownElement.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(26.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = element.text,
                            fontSize = 16.sp,
                            fontFamily = LatoFontFamily,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 24.sp,
                            color = textColor.copy(alpha = 0.85f)
                        )
                    }
                }

                is MarkdownElement.CodeBlock -> {
                    CodeBlockCard(
                        language = element.language,
                        code = element.code
                    )
                }

                MarkdownElement.Divider -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CodeBackgroundDark)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1C1A))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" },
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFA8A29A)
            )

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(code))
                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = Color(0xFFA8A29A),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Code content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Text(
                text = code,
                color = CodeText,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

fun parseMarkdown(text: String, defaultColor: Color): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code Block
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trimStart().startsWith("```")) {
                i++ // Consume closing ```
            }
            elements.add(MarkdownElement.CodeBlock(language, codeLines.joinToString("\n")))
            continue
        }

        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Horizontal Rule
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            elements.add(MarkdownElement.Divider)
            i++
            continue
        }

        // Headers
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6) {
                val headerText = trimmed.substring(level).trim()
                elements.add(MarkdownElement.Header(level, parseInlineFormatting(headerText, defaultColor)))
                i++
                continue
            }
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            val quoteText = trimmed.removePrefix(">").trim()
            elements.add(MarkdownElement.Blockquote(parseInlineFormatting(quoteText, defaultColor)))
            i++
            continue
        }

        // Unordered List
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ")) {
            val itemText = trimmed.substring(2).trim()
            elements.add(MarkdownElement.BulletItem(parseInlineFormatting(itemText, defaultColor)))
            i++
            continue
        }

        // Ordered List
        val orderedListMatch = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)
        if (orderedListMatch != null) {
            val number = "${orderedListMatch.groupValues[1]}."
            val itemText = orderedListMatch.groupValues[2]
            elements.add(MarkdownElement.NumberedItem(number, parseInlineFormatting(itemText, defaultColor)))
            i++
            continue
        }

        // Regular Paragraph
        elements.add(MarkdownElement.Paragraph(parseInlineFormatting(line, defaultColor)))
        i++
    }

    return elements
}

fun parseInlineFormatting(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0

        while (cursor < text.length) {
            // Check for inline code `...`
            if (text[cursor] == '`') {
                val nextBacktick = text.indexOf('`', cursor + 1)
                if (nextBacktick != -1) {
                    val codeContent = text.substring(cursor + 1, nextBacktick)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x22888888),
                            color = Color(0xFFC96442),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.5.sp
                        )
                    ) {
                        append(" $codeContent ")
                    }
                    cursor = nextBacktick + 1
                    continue
                }
            }

            // Check for bold **...** or __...__
            if (cursor + 1 < text.length && text.substring(cursor, cursor + 2) == "**") {
                val nextBold = text.indexOf("**", cursor + 2)
                if (nextBold != -1) {
                    val boldContent = text.substring(cursor + 2, nextBold)
                    withStyle(SpanStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Bold, color = defaultColor)) {
                        append(boldContent)
                    }
                    cursor = nextBold + 2
                    continue
                }
            }

            // Check for italic *...*
            if (text[cursor] == '*' && cursor + 1 < text.length && text[cursor + 1] != ' ') {
                val nextItalic = text.indexOf('*', cursor + 1)
                if (nextItalic != -1) {
                    val italicContent = text.substring(cursor + 1, nextItalic)
                    withStyle(SpanStyle(fontFamily = LatoFontFamily, fontStyle = FontStyle.Italic, color = defaultColor)) {
                        append(italicContent)
                    }
                    cursor = nextItalic + 1
                    continue
                }
            }

            // Normal character
            append(text[cursor])
            cursor++
        }
    }
}
