package com.vaultix.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.FileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsInputSection(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit
) {
    var tagInput by remember { mutableStateOf("") }
    val suggestedTags = listOf("Work", "Personal", "Finance", "Social", "Shopping")

    Column {
        Text("Tags / Labels", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
        Spacer(Modifier.height(6.dp))
        
        // Flow of active tags
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (tag in tags) {
                Surface(
                    onClick = { onTagsChange(tags - tag) },
                    shape = RoundedCornerShape(8.dp),
                    color = VaultOrange.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, VaultOrange.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(tag, color = VaultOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.Close, null, tint = VaultOrange, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        
        Spacer(Modifier.height(6.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = tagInput,
                onValueChange = { input ->
                    if (input.endsWith(",") || input.endsWith(" ")) {
                        val cleaned = input.trim().replace(",", "").replace(" ", "")
                        if (cleaned.isNotEmpty() && !tags.contains(cleaned)) {
                            onTagsChange(tags + cleaned)
                        }
                        tagInput = ""
                    } else {
                        tagInput = input
                    }
                },
                placeholder = { Text("Add tag (comma/space to add)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultOrange,
                    unfocusedBorderColor = VaultBorder,
                    focusedLabelColor = VaultOrange,
                    cursorColor = VaultOrange,
                    focusedTextColor = VaultTextPrimary,
                    unfocusedTextColor = VaultTextPrimary
                )
            )
            IconButton(
                onClick = {
                    val newTag = tagInput.trim()
                    if (newTag.isNotEmpty() && !tags.contains(newTag)) {
                        onTagsChange(tags + newTag)
                    }
                    tagInput = ""
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(VaultOrange.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Add, null, tint = VaultOrange)
            }
        }
        
        Spacer(Modifier.height(6.dp))
        
        // Suggested Tags
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (sug in suggestedTags) {
                if (!tags.contains(sug)) {
                    Surface(
                        onClick = { onTagsChange(tags + sug) },
                        shape = RoundedCornerShape(8.dp),
                        color = VaultSurface,
                        border = BorderStroke(1.dp, VaultBorder)
                    ) {
                        Text(
                            text = sug,
                            color = VaultTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderSelectSection(
    selectedFolderId: String?,
    onFolderSelect: (String?) -> Unit,
    fileViewModel: FileViewModel = hiltViewModel()
) {
    val folders by fileViewModel.folders.collectAsStateWithLifecycle(emptyList())
    var expanded by remember { mutableStateOf(false) }
    val currentFolderName = folders.find { it.id == selectedFolderId }?.name ?: "No Folder (Root)"

    Column {
        Text("Folder / Custom Category", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
        Spacer(Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VaultTextPrimary
                ),
                border = BorderStroke(1.dp, VaultBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currentFolderName, color = VaultTextPrimary)
                    Icon(Icons.Default.ArrowDropDown, null, tint = VaultOrange)
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(VaultSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("No Folder (Root)", color = VaultTextPrimary) },
                    onClick = {
                        onFolderSelect(null)
                        expanded = false
                    }
                )
                for (folder in folders) {
                    DropdownMenuItem(
                        text = { Text(folder.name, color = VaultTextPrimary) },
                        onClick = {
                            onFolderSelect(folder.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
