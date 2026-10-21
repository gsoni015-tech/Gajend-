package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PdfEntity
import com.example.reader.ExcelDocument
import com.example.reader.OfficeDocumentHelper
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixCard
import com.example.ui.theme.WhiteMixSurface
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelViewerScreen(
    entity: PdfEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var excelDoc by remember { mutableStateOf<ExcelDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    var isListView by remember { mutableStateOf(false) } // Toggle for vertical card list

    LaunchedEffect(entity.path) {
        isLoading = true
        excelDoc = OfficeDocumentHelper.parseExcelDocument(File(entity.path))
        isLoading = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = WhiteMixBackground,
        topBar = {
            Surface(
                color = WhiteMixSurface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp) // Compact 48dp height
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entity.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // View Mode Toggle (Table Grid vs Vertical Card List)
                    IconButton(
                        onClick = { isListView = !isListView },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isListView) Icons.Default.TableChart else Icons.Default.ViewAgenda,
                            contentDescription = if (isListView) "Switch to Table Grid" else "Switch to Vertical List",
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandPrimary)
                }
            } else {
                excelDoc?.let { doc ->
                    val sheetNames = doc.sheets.keys.toList()
                    val currentSheetData = if (sheetNames.isNotEmpty()) doc.sheets[sheetNames[selectedSheetIndex]] else null

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sheet Tabs
                        if (sheetNames.size > 1) {
                            TabRow(
                                selectedTabIndex = selectedSheetIndex,
                                containerColor = WhiteMixSurface,
                                modifier = Modifier.height(40.dp)
                            ) {
                                sheetNames.forEachIndexed { index, name ->
                                    Tab(
                                        selected = selectedSheetIndex == index,
                                        onClick = { selectedSheetIndex = index },
                                        text = { Text(name, fontSize = 12.sp, fontWeight = if (selectedSheetIndex == index) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }
                        }

                        currentSheetData?.let { sheet ->
                            if (isListView) {
                                // Vertical Card List View for mobile ease of reading
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(sheet.rows) { rowIndex, rowCells ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            color = WhiteMixCard,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, WhiteMixBorder),
                                            shadowElevation = 0.5.dp
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "Row #${rowIndex + 1}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandPrimary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                rowCells.forEachIndexed { colIndex, cellVal ->
                                                    val headerName = sheet.headers.getOrNull(colIndex) ?: "Column ${('A' + colIndex)}"
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 2.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = headerName,
                                                            fontSize = 11.sp,
                                                            color = TextSecondary,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Text(
                                                            text = cellVal,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = TextPrimary,
                                                            modifier = Modifier.weight(1.5f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Horizontal & Vertical Table Grid
                                val horizontalScrollState = rememberScrollState()
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(horizontalScrollState)
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        // Header Row
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .background(Color(0xFFF3F4F6))
                                                    .border(0.5.dp, WhiteMixBorder)
                                            ) {
                                                sheet.headers.forEach { header ->
                                                    Box(
                                                        modifier = Modifier
                                                            .width(130.dp)
                                                            .border(0.5.dp, WhiteMixBorder)
                                                            .padding(horizontal = 8.dp, vertical = 10.dp)
                                                    ) {
                                                        Text(
                                                            text = header,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextPrimary
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Data Rows
                                        itemsIndexed(sheet.rows) { rowIndex, row ->
                                            Row(
                                                modifier = Modifier
                                                    .background(if (rowIndex % 2 == 0) WhiteMixSurface else Color(0xFFFAFAFA))
                                                    .border(0.5.dp, WhiteMixBorder)
                                            ) {
                                                row.forEach { cell ->
                                                    Box(
                                                        modifier = Modifier
                                                            .width(130.dp)
                                                            .border(0.5.dp, WhiteMixBorder)
                                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = cell,
                                                            fontSize = 12.sp,
                                                            color = TextPrimary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
