package com.vaultix.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultix.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.privacy_policy),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Last Updated
            Text(
                stringResource(R.string.last_updated),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Sections
            PrivacySection(stringResource(R.string.privacy_section_1_title), stringResource(R.string.privacy_section_1_body))
            PrivacySection(stringResource(R.string.privacy_section_2_title), stringResource(R.string.privacy_section_2_body))
            PrivacySection(stringResource(R.string.privacy_section_3_title), stringResource(R.string.privacy_section_3_body))
            PrivacySection(stringResource(R.string.privacy_section_4_title), stringResource(R.string.privacy_section_4_body))
            PrivacySection(stringResource(R.string.privacy_section_5_title), stringResource(R.string.privacy_section_5_body))
            PrivacySection(stringResource(R.string.privacy_section_6_title), stringResource(R.string.privacy_section_6_body))
            PrivacySection(stringResource(R.string.privacy_section_7_title), stringResource(R.string.privacy_section_7_body))
            PrivacySection(stringResource(R.string.privacy_section_8_title), stringResource(R.string.privacy_section_8_body))
            PrivacySection(stringResource(R.string.privacy_section_9_title), stringResource(R.string.privacy_section_9_body))
            PrivacySection(stringResource(R.string.privacy_section_10_title), stringResource(R.string.privacy_section_10_body))

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            body,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
