package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LanguageItem
import com.example.i18n.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerBottomSheet(
    title: String,
    selectedCode: String,
    recentCodes: List<String>,
    allowAutoDetect: Boolean,
    onSelect: (LanguageItem) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()

    val filtered = remember(trimmed) {
        if (trimmed.isEmpty()) {
            LanguageItem.ALL_LANGUAGES
        } else {
            LanguageItem.ALL_LANGUAGES.filter {
                it.nameTr.contains(trimmed, ignoreCase = true) ||
                    it.nameEn.contains(trimmed, ignoreCase = true) ||
                    it.nativeName.contains(trimmed, ignoreCase = true) ||
                    it.code.contains(trimmed, ignoreCase = true)
            }
        }
    }

    val recentItems = remember(recentCodes) {
        recentCodes.mapNotNull { code ->
            LanguageItem.ALL_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }

    val isTurkishUi = strings.code == "tr"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.searchLanguageHint) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = strings.searchLanguageHint
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = strings.clearDesc)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (trimmed.isEmpty() && recentItems.isNotEmpty()) {
                Text(
                    text = strings.recentLanguagesLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentItems.forEach { lang ->
                        FilterChip(
                            selected = selectedCode.equals(lang.code, ignoreCase = true),
                            onClick = {
                                onSelect(lang)
                                onDismiss()
                            },
                            label = {
                                Text(
                                    text = if (isTurkishUi) lang.nameTr else lang.nativeName,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (allowAutoDetect && (trimmed.isEmpty() || strings.autoDetectTitle.contains(trimmed, ignoreCase = true) || "auto".contains(trimmed, ignoreCase = true))) {
                    item(key = "lang_auto") {
                        LanguageRowItem(
                            primaryLabel = strings.autoDetectTitle,
                            secondaryLabel = strings.autoDetectSubtitle,
                            code = LanguageItem.AUTO_CODE,
                            isSelected = selectedCode.equals(LanguageItem.AUTO_CODE, ignoreCase = true),
                            selectedDesc = strings.selectedDesc,
                            onClick = {
                                onSelect(LanguageItem.AUTO_DETECT)
                                onDismiss()
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                items(items = filtered, key = { it.code }) { lang ->
                    LanguageRowItem(
                        primaryLabel = if (isTurkishUi) lang.nameTr else lang.nativeName,
                        secondaryLabel = if (isTurkishUi) "${lang.nativeName} • ${lang.code.uppercase()}" else "${lang.nameEn} • ${lang.code.uppercase()}",
                        code = lang.code,
                        isSelected = selectedCode.equals(lang.code, ignoreCase = true),
                        selectedDesc = strings.selectedDesc,
                        onClick = {
                            onSelect(lang)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRowItem(
    primaryLabel: String,
    secondaryLabel: String,
    code: String,
    isSelected: Boolean,
    selectedDesc: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val bg = if (isSelected) {
        cs.primaryContainer.copy(alpha = 0.85f)
    } else {
        cs.surfaceVariant.copy(alpha = 0.40f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .liquidPressBounce(interactionSource, pressedScale = 0.98f)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                width = if (isSelected) 1.3.dp else 1.dp,
                color = if (isSelected) cs.primary.copy(alpha = 0.65f) else cs.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag("lang_item_$code"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondaryLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = selectedDesc,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
