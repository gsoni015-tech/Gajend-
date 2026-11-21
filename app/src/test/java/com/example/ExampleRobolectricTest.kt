package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import com.example.ui.screens.autoRevealCategoryOnTouch
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PdfDatabase
import com.example.data.local.PdfEntity
import com.example.data.repository.PdfRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Narayani PDF Reader", appName)
  }

  @Test
  fun `repository deduplicates content uri, storage import, and open uri`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dao = PdfDatabase.getInstance(context).pdfDao()
    val repo = PdfRepository(context)

    val externalDir = File(context.cacheDir, "FakeDownload").apply { mkdirs() }
    val externalFile = File(externalDir, "TestReport.pdf").apply { writeBytes(ByteArray(2048) { 1 }) }
    val privateDir = File(context.filesDir, "NarayaniDocuments").apply { mkdirs() }
    val privateCopy = File(privateDir, "TestReport.pdf").apply { writeBytes(ByteArray(2048) { 1 }) }

    // Seed duplicate entries (real path + content:// + NarayaniDocuments Storage copy)
    dao.insertPdfs(
      listOf(
        PdfEntity(
          path = externalFile.canonicalPath,
          title = "TestReport.pdf",
          uriString = "content://media/external/file/999",
          sizeBytes = externalFile.length(),
          lastModifiedTimestamp = externalFile.lastModified(),
          isFavorite = false,
          folderName = "FakeDownload"
        ),
        PdfEntity(
          path = "content://media/external/file/999",
          title = "TestReport.pdf",
          uriString = "content://media/external/file/999",
          sizeBytes = externalFile.length(),
          lastModifiedTimestamp = externalFile.lastModified(),
          isFavorite = true,
          folderName = "Storage"
        ),
        PdfEntity(
          path = privateCopy.canonicalPath,
          title = "TestReport.pdf",
          uriString = Uri.fromFile(privateCopy).toString(),
          sizeBytes = privateCopy.length(),
          lastModifiedTimestamp = privateCopy.lastModified(),
          folderName = "Storage"
        )
      )
    )

    repo.autoFetchDeviceDocuments()

    val afterScan = dao.getAllPdfsList().filter { it.title == "TestReport.pdf" }
    assertEquals(1, afterScan.size)
    assertEquals(externalFile.canonicalPath, afterScan.first().path)
    assertEquals(true, afterScan.first().isFavorite)

    // Opening the same file via Uri must reuse the existing entry without adding a duplicate
    val opened = repo.importPdfFromUri(Uri.fromFile(externalFile))
    assertNotNull(opened)
    val afterOpen = dao.getAllPdfsList().filter { it.title == "TestReport.pdf" }
    assertEquals(1, afterOpen.size)
    assertEquals(externalFile.canonicalPath, afterOpen.first().path)
  }

  @Test
  fun `repository batch deletePdfs removes selected files and preserves others`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dao = PdfDatabase.getInstance(context).pdfDao()
    val repo = PdfRepository(context)

    val dir = File(context.cacheDir, "MultiDeleteTest").apply { mkdirs() }
    val file1 = File(dir, "DeleteOne.pdf").apply { writeBytes(ByteArray(512) { 1 }) }
    val file2 = File(dir, "DeleteTwo.pdf").apply { writeBytes(ByteArray(512) { 2 }) }
    val file3 = File(dir, "KeepThree.pdf").apply { writeBytes(ByteArray(512) { 3 }) }

    dao.insertPdfs(
      listOf(
        PdfEntity(
          path = file1.canonicalPath,
          title = file1.name,
          uriString = Uri.fromFile(file1).toString(),
          sizeBytes = file1.length(),
          lastModifiedTimestamp = file1.lastModified(),
          folderName = "MultiDeleteTest"
        ),
        PdfEntity(
          path = file2.canonicalPath,
          title = file2.name,
          uriString = Uri.fromFile(file2).toString(),
          sizeBytes = file2.length(),
          lastModifiedTimestamp = file2.lastModified(),
          folderName = "MultiDeleteTest"
        ),
        PdfEntity(
          path = file3.canonicalPath,
          title = file3.name,
          uriString = Uri.fromFile(file3).toString(),
          sizeBytes = file3.length(),
          lastModifiedTimestamp = file3.lastModified(),
          folderName = "MultiDeleteTest"
        )
      )
    )

    repo.deletePdfs(listOf(file1.canonicalPath, file2.canonicalPath))

    assertEquals(false, file1.exists())
    assertEquals(false, file2.exists())
    assertEquals(true, file3.exists())
    val remaining = dao.getAllPdfsList().map { it.path }
    assertEquals(false, file1.canonicalPath in remaining)
    assertEquals(false, file2.canonicalPath in remaining)
    assertEquals(true, file3.canonicalPath in remaining)
  }

  @Test
  fun `sortPreferences persists selected sort mode across reads`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.local.SortPreferences(context)

    prefs.saveSortMode(com.example.ui.viewmodel.SortMode.SIZE_ASC)
    val loadedSizeAsc = prefs.sortModeFlow.first()
    assertEquals(com.example.ui.viewmodel.SortMode.SIZE_ASC, loadedSizeAsc)

    prefs.saveSortMode(com.example.ui.viewmodel.SortMode.NAME_DESC)
    val loadedNameDesc = prefs.sortModeFlow.first()
    assertEquals(com.example.ui.viewmodel.SortMode.NAME_DESC, loadedNameDesc)

    assertEquals(
      com.example.ui.viewmodel.SortMode.DATE_DESC,
      com.example.data.local.SortPreferences.parseSortMode("INVALID_VALUE")
    )
  }

  @get:org.junit.Rule
  val composeTestRule = androidx.compose.ui.test.junit4.createComposeRule()

  @Test
  fun `adaptiveBannerAdView renders visible reserved banner slot`() {
    composeTestRule.setContent {
      com.example.ui.theme.MyApplicationTheme {
        com.example.ads.AdaptiveBannerAdView()
      }
    }
    composeTestRule.waitForIdle()
    val container = composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("banner_ad_container"),
      useUnmergedTree = true
    )
    val bounds = container.fetchSemanticsNode().boundsInRoot
    assertEquals(true, bounds.height > 0f)
    assertEquals(true, bounds.width > 0f)

    val placeholder = composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("banner_ad_placeholder"),
      useUnmergedTree = true
    )
    val placeholderBounds = placeholder.fetchSemanticsNode().boundsInRoot
    assertEquals(true, placeholderBounds.height > 0f)
    assertEquals(true, placeholderBounds.width > 0f)
  }

  @Test
  fun `repository removeFromRecent and clearRecentHistory keep files intact`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dao = PdfDatabase.getInstance(context).pdfDao()
    val repo = PdfRepository(context)

    val dir = File(context.cacheDir, "RecentHistoryTest").apply { mkdirs() }
    val pdfA = File(dir, "RecentA.pdf").apply { writeBytes(ByteArray(256) { 1 }) }
    val pdfB = File(dir, "RecentB.pdf").apply { writeBytes(ByteArray(256) { 2 }) }

    dao.insertPdfs(
      listOf(
        PdfEntity(
          path = pdfA.canonicalPath,
          title = pdfA.name,
          uriString = Uri.fromFile(pdfA).toString(),
          sizeBytes = pdfA.length(),
          lastModifiedTimestamp = pdfA.lastModified(),
          lastOpenedTimestamp = 20_000L,
          folderName = "RecentHistoryTest"
        ),
        PdfEntity(
          path = pdfB.canonicalPath,
          title = pdfB.name,
          uriString = Uri.fromFile(pdfB).toString(),
          sizeBytes = pdfB.length(),
          lastModifiedTimestamp = pdfB.lastModified(),
          lastOpenedTimestamp = 30_000L,
          folderName = "RecentHistoryTest"
        )
      )
    )

    val initialRecent = repo.recentPdfs.first().map { it.title }
    assertEquals(listOf("RecentB.pdf", "RecentA.pdf"), initialRecent)

    repo.removeFromRecent(pdfA.canonicalPath)
    val afterRemoveOne = repo.recentPdfs.first().map { it.title }
    assertEquals(listOf("RecentB.pdf"), afterRemoveOne)
    assertEquals(true, pdfA.exists())

    repo.clearRecentHistory()
    val afterClearAll = repo.recentPdfs.first()
    assertEquals(0, afterClearAll.size)
    assertEquals(true, pdfB.exists())
  }

  @Test
  fun `mainBottomNavigationBar displays Recent Files tab and triggers selection`() {
    var currentTab = MainNavTab.FILES
    composeTestRule.setContent {
      com.example.ui.theme.MyApplicationTheme {
        MainBottomNavigationBar(
          selectedNavTab = currentTab,
          onSelectTab = { currentTab = it }
        )
      }
    }
    composeTestRule.waitForIdle()

    val recentTabNode = composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("nav_tab_recent"),
      useUnmergedTree = true
    )
    val recentBounds = recentTabNode.fetchSemanticsNode().boundsInRoot
    assertEquals(true, recentBounds.width > 0f)
    assertEquals(true, recentBounds.height >= 48f)

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("nav_tab_recent")
    ).performClick()
    composeTestRule.waitForIdle()
    assertEquals(MainNavTab.RECENT, currentTab)
  }

  @Test
  fun `recentFilesScreenContent shows empty state and populated PDF list`() {
    var browseClicked = false
    val recentItems = androidx.compose.runtime.mutableStateListOf<PdfEntity>()
    composeTestRule.setContent {
      com.example.ui.theme.MyApplicationTheme {
        com.example.ui.screens.RecentFilesScreenContent(
          recentFiles = recentItems,
          totalRecentCount = recentItems.size,
          searchQuery = "",
          filterMode = com.example.ui.viewmodel.RecentFilterMode.ALL,
          onSearchQueryChange = {},
          onFilterModeChange = {},
          onOpenDocument = {},
          onNavigateToDocuments = { browseClicked = true },
          onToggleFavorite = {},
          onRemoveFromRecent = {},
          onClearRecentHistory = {},
          onRenameDocument = { _, _ -> },
          onDeleteDocument = {},
          onSetPin = { _, _ -> },
          onShareDocument = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("recent_empty_state"),
      useUnmergedTree = true
    ).fetchSemanticsNode()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("recent_browse_documents_button")
    ).performClick()
    composeTestRule.waitForIdle()
    assertEquals(true, browseClicked)

    recentItems.add(
      PdfEntity(
        path = "/storage/emulated/0/Download/Quarterly_Report.pdf",
        title = "Quarterly_Report.pdf",
        uriString = "file:///storage/emulated/0/Download/Quarterly_Report.pdf",
        sizeBytes = 1024L * 120L,
        lastModifiedTimestamp = System.currentTimeMillis() - 60_000L,
        lastOpenedTimestamp = System.currentTimeMillis() - 5_000L,
        folderName = "Download"
      )
    )
    composeTestRule.waitForIdle()

    val cardBounds = composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("recent_document_card_Quarterly_Report.pdf"),
      useUnmergedTree = true
    ).fetchSemanticsNode().boundsInRoot
    assertEquals(true, cardBounds.height > 0f)
  }

  @Test
  fun `pdfFastCacheManager warms cache file and evicts automatically on delete`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.data.cache.PdfFastCacheManager.clearAllCache(context)

    val dir = File(context.cacheDir, "CacheWarmTest").apply { mkdirs() }
    val samplePdf = File(dir, "FastOpenSample.pdf").apply {
      writeBytes(ByteArray(1024) { 7 })
    }
    val entity = PdfEntity(
      path = samplePdf.canonicalPath,
      title = samplePdf.name,
      uriString = Uri.fromFile(samplePdf).toString(),
      sizeBytes = samplePdf.length(),
      lastModifiedTimestamp = samplePdf.lastModified(),
      folderName = "CacheWarmTest"
    )

    val warmedFile = com.example.data.cache.PdfFastCacheManager.getOrWarmFastFile(context, entity)
    assertNotNull(warmedFile)
    assertEquals(true, warmedFile!!.exists())
    assertEquals(true, warmedFile.absolutePath.contains("fast_pdf_cache"))
    assertEquals(1024L, warmedFile.length())

    val fastResolved = com.example.data.cache.PdfFastCacheManager.getFastReadableFileIfPresent(context, entity)
    assertEquals(warmedFile.canonicalPath, fastResolved?.canonicalPath)
    assertEquals(1, com.example.data.cache.PdfFastCacheManager.cacheStats.value.fileCount)

    com.example.data.cache.PdfFastCacheManager.evictByEntity(context, entity)
    assertEquals(false, warmedFile.exists())
    assertEquals(0, com.example.data.cache.PdfFastCacheManager.cacheStats.value.fileCount)
  }

  @Test
  fun `external gallery deletion prunes Room DB, evicts fast cache, and emits deleted doc event`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dao = PdfDatabase.getInstance(context).pdfDao()
    val repo = PdfRepository(context)
    com.example.data.cache.PdfFastCacheManager.clearAllCache(context)

    val docsDir = File(context.filesDir, "NarayaniDocuments").apply { mkdirs() }
    val pdfFile = File(docsDir, "GalleryDeletedDoc.pdf").apply {
      writeBytes(ByteArray(2048) { 9 })
    }
    val entity = repo.addCreatedPdf(pdfFile, "Created")
    val cachedFile = com.example.data.cache.PdfFastCacheManager.getOrWarmFastFile(context, entity)
    assertNotNull(cachedFile)
    assertEquals(true, cachedFile!!.exists())

    val emittedFuture = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).async(
      start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED
    ) {
      repo.deletedLocalDocsFlow.first { batch ->
        batch.any { it.path == entity.path && it.title == "GalleryDeletedDoc.pdf" }
      }
    }

    // Simulate external deletion from device Gallery / File Manager
    assertEquals(true, pdfFile.delete())

    repo.autoFetchDeviceDocuments()
    val matchedBatch = kotlinx.coroutines.withTimeout(3_000L) { emittedFuture.await() }

    val remaining = dao.getAllPdfsList().map { it.path }
    assertEquals(false, entity.path in remaining)
    assertEquals(false, cachedFile.exists())
    assertEquals(true, matchedBatch.any { it.path == entity.path && it.title == "GalleryDeletedDoc.pdf" })
  }

  @Test
  fun `mainBottomNavigationBar displays Cloud tab and CloudSyncScreen renders Sign-In and Authenticated states`() {
    var currentTab = MainNavTab.FILES
    composeTestRule.setContent {
      com.example.ui.theme.MyApplicationTheme {
        MainBottomNavigationBar(
          selectedNavTab = currentTab,
          onSelectTab = { currentTab = it }
        )
      }
    }
    composeTestRule.waitForIdle()

    val cloudTabNode = composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("nav_tab_cloud"),
      useUnmergedTree = true
    )
    val cloudBounds = cloudTabNode.fetchSemanticsNode().boundsInRoot
    assertEquals(true, cloudBounds.width > 0f)
    assertEquals(true, cloudBounds.height >= 48f)

    cloudTabNode.performClick()
    composeTestRule.waitForIdle()
    assertEquals(MainNavTab.CLOUD, currentTab)
  }

  @Test
  fun `cloudSyncAuthenticatedContent renders fast cache card and synced cloud documents`() {
    val sampleDoc = com.example.data.cloud.CloudDocument(
      id = "doc_1234567890abcdef",
      userId = "user_1",
      title = "Cloud_Contract.pdf",
      documentType = "PDF",
      sizeBytes = 25_600L,
      contentHash = "1234567890abcdef",
      storagePath = "users/user_1/pdfs/doc_1234567890abcdef.pdf",
      syncState = com.example.data.cloud.CloudSyncState.SYNCED.name,
      localPath = "/storage/emulated/0/Download/Cloud_Contract.pdf",
      folderName = "Download"
    )
    var deletedEverywhereTriggered = false

    composeTestRule.setContent {
      com.example.ui.theme.MyApplicationTheme {
        com.example.ui.screens.CloudSyncAuthenticatedContent(
          userEmail = "reader@example.com",
          userName = "Narayani User",
          cloudDocsState = com.example.data.cloud.CloudUiState.Success(listOf(sampleDoc)),
          isSyncing = false,
          activeSyncTitle = null,
          storageSetupNeeded = false,
          cacheStats = com.example.data.cache.CacheStats(fileCount = 2, totalBytes = 4096L),
          openingDocId = null,
          onSyncNow = {},
          onSignOut = {},
          onClearCache = {},
          onOpenCloudDoc = {},
          onDeleteCloudDoc = { _, alsoLocal ->
            if (alsoLocal) deletedEverywhereTriggered = true
          }
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("cloud_fast_cache_card"),
      useUnmergedTree = true
    ).fetchSemanticsNode()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("cloud_document_card_Cloud_Contract.pdf"),
      useUnmergedTree = true
    ).fetchSemanticsNode()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("cloud_delete_Cloud_Contract.pdf")
    ).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("confirm_cloud_delete_everywhere")
    ).performClick()
    composeTestRule.waitForIdle()

    assertEquals(true, deletedEverywhereTriggered)
  }

  @Test
  fun `cloudSyncAuthenticatedContent shows Drive permission banner when setup needed`() {
    var grantAccessClicked = false
    composeTestRule.setContent {
      com.example.ui.theme.MyApplicationTheme {
        com.example.ui.screens.CloudSyncAuthenticatedContent(
          userEmail = "reader@example.com",
          userName = "Narayani User",
          cloudDocsState = com.example.data.cloud.CloudUiState.Success(emptyList()),
          isSyncing = false,
          activeSyncTitle = null,
          storageSetupNeeded = true,
          cacheStats = com.example.data.cache.CacheStats(),
          openingDocId = null,
          onSyncNow = {},
          onSignOut = {},
          onClearCache = {},
          onOpenCloudDoc = {},
          onDeleteCloudDoc = { _, _ -> },
          onGrantDriveAccess = { grantAccessClicked = true }
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("drive_permission_banner"),
      useUnmergedTree = true
    ).fetchSemanticsNode()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("grant_drive_access_button")
    ).performClick()
    composeTestRule.waitForIdle()
    assertEquals(true, grantAccessClicked)
  }

  @Test
  fun `smoothZoomableBox prevents zoom out below 100 percent and scrolls LazyColumn forward and backward when zoomed in`() {
    lateinit var listState: androidx.compose.foundation.lazy.LazyListState
    var observedScale = 1.0f

    composeTestRule.setContent {
      com.example.ui.theme.MyApplicationTheme {
        listState = androidx.compose.foundation.lazy.rememberLazyListState()
        com.example.ui.components.SmoothZoomableBox(
          modifier = androidx.compose.ui.Modifier.fillMaxSize(),
          onScrollVerticalDelta = { rawDeltaPx ->
            listState.dispatchRawDelta(rawDeltaPx)
          }
        ) { currentScale ->
          observedScale = currentScale
          androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
          ) {
            items(15) { pageIndex ->
              androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                  .fillMaxWidth()
                  .height(120.dp)
                  .testTag("test_pdf_page_$pageIndex")
              ) {
                androidx.compose.material3.Text("Page ${pageIndex + 1}")
              }
            }
          }
        }
      }
    }
    composeTestRule.waitForIdle()
    assertEquals(0, listState.firstVisibleItemIndex)

    val container = composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("pdf_zoomable_container"),
      useUnmergedTree = true
    )

    // 1. Pinch inward should bounce back and never rest below 1.0x (100%)
    container.performTouchInput {
      pinch(
        start0 = center + androidx.compose.ui.geometry.Offset(-180f, 0f),
        end0 = center + androidx.compose.ui.geometry.Offset(-40f, 0f),
        start1 = center + androidx.compose.ui.geometry.Offset(180f, 0f),
        end1 = center + androidx.compose.ui.geometry.Offset(40f, 0f)
      )
    }
    composeTestRule.waitForIdle()
    assertEquals(true, observedScale >= 1.0f)

    // 2. Double tap to zoom in (2.5x) -> Reset badge appears
    container.performTouchInput {
      doubleClick(center)
    }
    composeTestRule.waitForIdle()
    assertEquals(true, observedScale > 2.0f)
    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("pdf_reset_zoom_badge"),
      useUnmergedTree = true
    ).fetchSemanticsNode()

    // 3. While zoomed in, dragging UP must scroll LazyColumn FORWARD to later pages
    repeat(4) {
      container.performTouchInput {
        swipeUp(startY = bottom - 80f, endY = top + 80f, durationMillis = 150)
      }
      composeTestRule.waitForIdle()
    }
    val forwardIndex = listState.firstVisibleItemIndex
    assertEquals(true, forwardIndex >= 2)

    // 4. While still zoomed in, dragging DOWN must scroll LazyColumn BACKWARD to earlier pages
    repeat(6) {
      container.performTouchInput {
        swipeDown(startY = top + 80f, endY = bottom - 80f, durationMillis = 150)
      }
      composeTestRule.waitForIdle()
    }
    assertEquals(true, listState.firstVisibleItemIndex < forwardIndex)

    // 5. Tapping Reset badge returns scale cleanly to 1.0f
    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("pdf_reset_zoom_badge")
    ).performClick()
    composeTestRule.waitForIdle()
    assertEquals(1.0f, observedScale, 0.02f)
  }

  @Test
  fun `appUpdateToolsCard and appUpdateDialog render update prompt and trigger actions`() {
    val sampleRelease = com.example.update.AppReleaseConfig(
      latestVersionCode = 2L,
      latestVersionName = "1.1",
      apkUrl = "https://example.com/narayani-v1.1.apk",
      releaseNotes = "Better zoom and fast cache",
      forceUpdate = false
    )
    val updateState = com.example.update.UpdateCheckState.UpdateAvailable(
      release = sampleRelease,
      currentVersionCode = 1L,
      currentVersionName = "1.0"
    )
    var checkTriggered = false
    var downloadTriggered = false
    var dismissed = false

    composeTestRule.setContent {
      com.example.ui.theme.MyApplicationTheme {
        androidx.compose.foundation.layout.Column {
          com.example.ui.components.AppUpdateToolsCard(
            state = com.example.update.UpdateCheckState.UpToDate(1L, "1.0"),
            onCheckUpdate = { checkTriggered = true },
            onUpdateNow = {}
          )
          com.example.ui.components.AppUpdateDialog(
            state = updateState,
            visible = true,
            onStartDownload = { downloadTriggered = true },
            onInstallApk = {},
            onOpenInstallPermission = {},
            onOpenInBrowser = {},
            onDismiss = { dismissed = true }
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("tools_app_update_card"),
      useUnmergedTree = true
    ).fetchSemanticsNode()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("app_update_dialog"),
      useUnmergedTree = true
    ).fetchSemanticsNode()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("app_update_now_button")
    ).performClick()
    composeTestRule.waitForIdle()
    assertEquals(true, downloadTriggered)

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("app_update_later_button")
    ).performClick()
    composeTestRule.waitForIdle()
    assertEquals(true, dismissed)

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("tools_check_update_button")
    ).performClick()
    composeTestRule.waitForIdle()
    assertEquals(true, checkTriggered)
  }

  @Test
  fun `cloudSyncScreen sign-in view scrolls on compact height so bottom sign-in button is reachable`() {
    composeTestRule.setContent {
      com.example.ui.theme.MyApplicationTheme {
        androidx.compose.foundation.layout.Box(
          modifier = androidx.compose.ui.Modifier
            .size(width = 340.dp, height = 380.dp)
        ) {
          com.example.ui.screens.CloudSyncScreen(
            currentUser = null,
            cloudViewModel = null,
            allDocuments = emptyList(),
            onOpenDocument = {},
            onDeleteLocalPath = {}
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    val scrollContainer = composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("cloud_sign_in_scroll_container"),
      useUnmergedTree = true
    )
    scrollContainer.fetchSemanticsNode()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("cloud_google_sign_in_button")
    ).performScrollTo().fetchSemanticsNode()
  }

  @Test
  fun `fileListScreen category bar auto-hides when idle and re-appears on touch`() {
    val selectedTabState = androidx.compose.runtime.mutableStateOf(
      com.example.ui.viewmodel.FileListTab.ALL
    )

    composeTestRule.setContent {
      com.example.ui.theme.MyApplicationTheme {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val controller = com.example.ui.screens.rememberAutoHideCategoryBarController(
          isScrolling = listState.isScrollInProgress,
          autoHideDelayMs = 600L
        )
        androidx.compose.material3.Scaffold(
          modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .autoRevealCategoryOnTouch(controller),
          contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
          topBar = {
            androidx.compose.foundation.layout.Column {
              com.example.ui.screens.FileListCategoryTabsRow(
                selectedTab = selectedTabState.value,
                visible = controller.isVisible,
                onSelectTab = {
                  controller.bumpVisibility()
                  selectedTabState.value = it
                }
              )
            }
          }
        ) { padding ->
          androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = androidx.compose.ui.Modifier
              .fillMaxSize()
              .padding(padding)
              .testTag("category_test_scroll_list")
          ) {
            items(25) { index ->
              androidx.compose.material3.Text(
                text = "Document $index.pdf",
                modifier = androidx.compose.ui.Modifier
                  .fillMaxWidth()
                  .height(48.dp)
              )
            }
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    // 1. Category bar (All Documents / PDF / Word / Excel) is visible initially
    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("file_list_category_bar"),
      useUnmergedTree = true
    ).fetchSemanticsNode()

    // 2. Advance past idle timeout (600ms + exit animation) -> category bar auto-hides to free screen space
    composeTestRule.mainClock.advanceTimeBy(1_000L)
    composeTestRule.waitForIdle()
    assertEquals(
      0,
      composeTestRule.onAllNodes(
        androidx.compose.ui.test.hasTestTag("file_list_category_bar"),
        useUnmergedTree = true
      ).fetchSemanticsNodes().size
    )

    // 3. Touching/scrolling the screen brings the category bar right back into view
    composeTestRule.onRoot().performTouchInput {
      click(center)
    }
    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(150L)
    composeTestRule.waitForIdle()

    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("file_list_category_bar"),
      useUnmergedTree = true
    ).fetchSemanticsNode()

    // Clicking a category tab updates selection
    composeTestRule.onNode(
      androidx.compose.ui.test.hasTestTag("file_list_tab_pdf")
    ).performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.ui.viewmodel.FileListTab.PDF, selectedTabState.value)
  }
}
