package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.reader.ExcelDocument
import com.example.reader.OfficeDocumentHelper
import com.example.ui.components.AdMobBottomBanner
import com.example.ui.viewmodel.PdfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelViewerScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPdf by viewModel.currentPdf.collectAsStateWithLifecycle()

    var excelDoc by remember { mutableStateOf<ExcelDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var activeSheetIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var selectedCellCoords by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (row, col)
    var selectedCellValue by remember { mutableStateOf("") }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(currentPdf) {
        val pdf = currentPdf
        if (pdf != null) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val file = File(pdf.path)
                excelDoc = OfficeDocumentHelper.parseExcel(file)
            }
            isLoading = false
        }
    }

    val horizontalScrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF10B981)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = currentPdf?.title ?: "Excel Spreadsheet",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Excel Spreadsheet Viewer",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(Icons.Default.Search, contentDescription = "Search in Sheet")
                            }
                            IconButton(onClick = { currentPdf?.let { viewModel.sharePdf(context, it) } }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    // Search row if active
                    if (isSearchActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Find cell data in sheet...", fontSize = 13.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        }
                    }

                    // Sheets Tab Row
                    excelDoc?.let { doc ->
                        if (doc.sheets.size > 1) {
                            ScrollableTabRow(
                                selectedTabIndex = activeSheetIndex,
                                edgePadding = 16.dp,
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                doc.sheets.forEachIndexed { index, sheet ->
                                    Tab(
                                        selected = activeSheetIndex == index,
                                        onClick = {
                                            activeSheetIndex = index
                                            selectedCellCoords = null
                                            selectedCellValue = ""
                                        },
                                        text = { Text(sheet.sheetName, fontWeight = if (activeSheetIndex == index) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cell Value / Formula Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val coordStr = selectedCellCoords?.let { (r, c) ->
                        val colLetter = ('A'.code + c).toChar()
                        "$colLetter${r + 1}"
                    } ?: "fx"

                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = coordStr,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = if (selectedCellValue.isNotBlank()) selectedCellValue else "Tap any cell to view and inspect data",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selectedCellValue.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Main Table View
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val currentSheet = excelDoc?.sheets?.getOrNull(activeSheetIndex)
                if (currentSheet == null || (currentSheet.headers.isEmpty() && currentSheet.rows.isEmpty())) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No spreadsheet data available in this file", color = Color.Gray)
                    }
                } else {
                    val filteredRows = if (searchQuery.isBlank()) {
                        currentSheet.rows
                    } else {
                        currentSheet.rows.filter { row ->
                            row.any { cell -> cell.contains(searchQuery, ignoreCase = true) }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // Horizontal scrollable table container
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .testTag("excel_table_grid")
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                // Sticky Header Row
                                item {
                                    Row(
                                        modifier = Modifier
                                            .background(Color(0xFFE2E8F0))
                                            .border(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        // Row # corner cell
                                        Box(
                                            modifier = Modifier
                                                .size(width = 44.dp, height = 40.dp)
                                                .border(0.5.dp, Color(0xFFCBD5E1)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("#", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF475569))
                                        }

                                        // Column Headers
                                        currentSheet.headers.forEachIndexed { colIndex, header ->
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 140.dp, height = 40.dp)
                                                    .border(0.5.dp, Color(0xFFCBD5E1))
                                                    .padding(horizontal = 8.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = header,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF0F172A),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                // Table Rows
                                itemsIndexed(filteredRows) { rowIndex, rowData ->
                                    val isEven = rowIndex % 2 == 0
                                    val rowBg = if (isEven) Color(0xFFFFFFFF) else Color(0xFFF8FAFC)

                                    Row(
                                        modifier = Modifier
                                            .background(rowBg)
                                            .border(0.5.dp, Color(0xFFE2E8F0))
                                    ) {
                                        // Row Index Number
                                        Box(
                                            modifier = Modifier
                                                .size(width = 44.dp, height = 42.dp)
                                                .background(Color(0xFFF1F5F9))
                                                .border(0.5.dp, Color(0xFFCBD5E1)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${rowIndex + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF64748B)
                                            )
                                        }

                                        // Row Cells
                                        currentSheet.headers.indices.forEach { colIndex ->
                                            val cellText = rowData.getOrNull(colIndex) ?: ""
                                            val isSelected = selectedCellCoords?.first == rowIndex && selectedCellCoords?.second == colIndex
                                            val isSearchMatch = searchQuery.isNotBlank() && cellText.contains(searchQuery, ignoreCase = true)

                                            val cellBorderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0)
                                            val cellBg = when {
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                isSearchMatch -> Color(0xFFFEF08A)
                                                else -> Color.Transparent
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(width = 140.dp, height = 42.dp)
                                                    .background(cellBg)
                                                    .border(if (isSelected) 1.5.dp else 0.5.dp, cellBorderColor)
                                                    .clickable {
                                                        selectedCellCoords = Pair(rowIndex, colIndex)
                                                        selectedCellValue = cellText
                                                    }
                                                    .padding(horizontal = 8.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = cellText,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E293B),
                                                    fontWeight = if (isSelected || isSearchMatch) FontWeight.Bold else FontWeight.Normal
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

            // Stats Footer Bar
            excelDoc?.sheets?.getOrNull(activeSheetIndex)?.let { sheet ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${sheet.totalRows} Rows • ${sheet.totalColumns} Columns",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Sheet: ${sheet.sheetName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // AdMob Bottom Banner
            AdMobBottomBanner()
        }
    }
}
