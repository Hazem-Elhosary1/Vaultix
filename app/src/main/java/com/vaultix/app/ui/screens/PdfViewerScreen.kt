package com.vaultix.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.PdfUiState
import com.vaultix.app.ui.viewmodel.PdfViewerViewModel

@Composable
fun PdfViewerScreen(
    fileId: String,
    fileName: String,
    onBack: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(fileId) {
        viewModel.loadPdf(fileId, fileName)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.closeRenderer()
        }
    }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(fileName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                        Text("Encrypted Preview", fontSize = 11.sp, color = VaultSuccess)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = VaultTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is PdfUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = VaultOrange)
                        Spacer(Modifier.height(16.dp))
                        Text("Decrypting secure preview...", color = VaultTextSecondary)
                    }
                }
                is PdfUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(count = state.pageCount) { index ->
                            PdfPageItem(index, viewModel)
                        }
                    }
                }
                is PdfUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = VaultError, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(state.message, color = VaultError, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = VaultSurface)) {
                            Text("Go Back", color = VaultTextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(pageIndex: Int, viewModel: PdfViewerViewModel) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        bitmap = viewModel.getPageBitmap(pageIndex)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White) // PDF pages are usually white
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        } ?: Box(
            modifier = Modifier.fillMaxWidth().height(400.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = VaultOrange, modifier = Modifier.size(24.dp))
        }
    }
}
