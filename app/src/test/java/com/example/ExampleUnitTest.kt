package com.example

import com.example.ads.AdManager
import com.example.ui.theme.AppFonts
import com.example.util.KrutiDevConverter
import org.junit.Assert.*
import org.junit.Test

/**
 * Local unit tests for document font resolution, Kruti Dev detection, and AdManager safeguards.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun adManager_usesExactUnitIdsAnd25SecondCooldown() {
    assertEquals("ca-app-pub-9247143643125748/5366325906", AdManager.BANNER_AD_UNIT_ID)
    assertEquals("ca-app-pub-9247143643125748/3927267520", AdManager.INTERSTITIAL_AD_UNIT_ID)
    assertEquals("ca-app-pub-9247143643125748/5688861742", AdManager.REWARDED_AD_UNIT_ID)
    assertFalse(AdManager.USE_PRODUCTION_ADS_ALWAYS)
    assertEquals(25_000L, AdManager.INTERSTITIAL_COOLDOWN_MS)
    assertEquals(15_000L, AdManager.DOCUMENT_READING_AD_DELAY_MS)

    AdManager.resetForTesting()
    assertTrue(AdManager.canShowInterstitial(100_000L))
    assertEquals(0L, AdManager.remainingInterstitialCooldownMs(100_000L))

    AdManager.setLastInterstitialShownTimestampForTesting(100_000L)
    assertFalse(AdManager.canShowInterstitial(110_000L))
    assertEquals(15_000L, AdManager.remainingInterstitialCooldownMs(110_000L))
    assertFalse(AdManager.canShowInterstitial(124_999L))
    assertEquals(1L, AdManager.remainingInterstitialCooldownMs(124_999L))
    assertTrue(AdManager.canShowInterstitial(125_000L))
    assertEquals(0L, AdManager.remainingInterstitialCooldownMs(125_000L))
    AdManager.resetForTesting()
  }

  @Test
  fun adManager_resolvesTestAdsInDebugAndProductionAdsInRelease() {
    assertEquals(
      AdManager.TEST_BANNER_AD_UNIT_ID,
      AdManager.resolveAdUnitId(
        isDebug = true,
        productionId = AdManager.BANNER_AD_UNIT_ID,
        testId = AdManager.TEST_BANNER_AD_UNIT_ID
      )
    )
    assertEquals(
      AdManager.BANNER_AD_UNIT_ID,
      AdManager.resolveAdUnitId(
        isDebug = false,
        productionId = AdManager.BANNER_AD_UNIT_ID,
        testId = AdManager.TEST_BANNER_AD_UNIT_ID
      )
    )
    assertEquals(
      AdManager.TEST_INTERSTITIAL_AD_UNIT_ID,
      AdManager.resolveAdUnitId(
        isDebug = true,
        productionId = AdManager.INTERSTITIAL_AD_UNIT_ID,
        testId = AdManager.TEST_INTERSTITIAL_AD_UNIT_ID
      )
    )
    assertEquals(
      AdManager.INTERSTITIAL_AD_UNIT_ID,
      AdManager.resolveAdUnitId(
        isDebug = false,
        productionId = AdManager.INTERSTITIAL_AD_UNIT_ID,
        testId = AdManager.TEST_INTERSTITIAL_AD_UNIT_ID
      )
    )
    assertEquals(
      AdManager.TEST_REWARDED_AD_UNIT_ID,
      AdManager.resolveAdUnitId(
        isDebug = true,
        productionId = AdManager.REWARDED_AD_UNIT_ID,
        testId = AdManager.TEST_REWARDED_AD_UNIT_ID
      )
    )
    assertEquals(
      AdManager.REWARDED_AD_UNIT_ID,
      AdManager.resolveAdUnitId(
        isDebug = false,
        productionId = AdManager.REWARDED_AD_UNIT_ID,
        testId = AdManager.TEST_REWARDED_AD_UNIT_ID
      )
    )
    assertEquals(
      AdManager.BANNER_AD_UNIT_ID,
      AdManager.resolveAdUnitId(
        isDebug = true,
        productionId = AdManager.BANNER_AD_UNIT_ID,
        testId = AdManager.TEST_BANNER_AD_UNIT_ID,
        forceProduction = true
      )
    )
  }

  @Test
  fun adManager_retryBackoffAndLaunchInterstitialGuards() {
    assertEquals("Internal Error (0)", AdManager.describeAdErrorCode(0))
    assertEquals("Invalid Request (1)", AdManager.describeAdErrorCode(1))
    assertEquals("Network Error (2)", AdManager.describeAdErrorCode(2))
    assertEquals("No Ad Fill (3)", AdManager.describeAdErrorCode(3))
    assertEquals("Invalid App ID (8)", AdManager.describeAdErrorCode(8))
    assertEquals("Error 99", AdManager.describeAdErrorCode(99))

    assertEquals(5_000L, AdManager.retryDelayForAttempt(0))
    assertEquals(15_000L, AdManager.retryDelayForAttempt(1))
    assertEquals(30_000L, AdManager.retryDelayForAttempt(2))
    assertEquals(30_000L, AdManager.retryDelayForAttempt(99))

    assertTrue(
      AdManager.shouldTriggerLaunchInterstitial(
        isHomeActive = true,
        hasAttemptedLaunch = false,
        hasNavigatedFromHome = false,
        isAdReady = true,
        isShowingAd = false,
        canShowByCooldown = true
      )
    )
    assertFalse(
      AdManager.shouldTriggerLaunchInterstitial(
        isHomeActive = false,
        hasAttemptedLaunch = false,
        hasNavigatedFromHome = false,
        isAdReady = true,
        isShowingAd = false,
        canShowByCooldown = true
      )
    )
    assertFalse(
      AdManager.shouldTriggerLaunchInterstitial(
        isHomeActive = true,
        hasAttemptedLaunch = true,
        hasNavigatedFromHome = false,
        isAdReady = true,
        isShowingAd = false,
        canShowByCooldown = true
      )
    )
    assertFalse(
      AdManager.shouldTriggerLaunchInterstitial(
        isHomeActive = true,
        hasAttemptedLaunch = false,
        hasNavigatedFromHome = true,
        isAdReady = true,
        isShowingAd = false,
        canShowByCooldown = true
      )
    )
    assertFalse(
      AdManager.shouldTriggerLaunchInterstitial(
        isHomeActive = true,
        hasAttemptedLaunch = false,
        hasNavigatedFromHome = false,
        isAdReady = false,
        isShowingAd = false,
        canShowByCooldown = true
      )
    )
  }

  @Test
  fun resolveFromWordFontName_mapsCommonWordAndHindiFonts() {
    assertEquals(AppFonts.SupportedFont.KRUTI_DEV, AppFonts.resolveFromWordFontName("Kruti Dev 010"))
    assertEquals(AppFonts.SupportedFont.KRUTI_DEV, AppFonts.resolveFromWordFontName("DevLys 010"))
    assertEquals(AppFonts.SupportedFont.HINDI_NOTO, AppFonts.resolveFromWordFontName("Mangal"))
    assertEquals(AppFonts.SupportedFont.HINDI_TIRO, AppFonts.resolveFromWordFontName("Kokila"))
    assertEquals(AppFonts.SupportedFont.ENGLISH_OPEN_SANS, AppFonts.resolveFromWordFontName("Calibri"))
    assertEquals(AppFonts.SupportedFont.ENGLISH_NOTO_SERIF, AppFonts.resolveFromWordFontName("Times New Roman"))
    assertEquals(AppFonts.SupportedFont.ENGLISH_MONO, AppFonts.resolveFromWordFontName("Courier New"))
  }

  @Test
  fun isLikelyKrutiDev_detectsLegacyKrutiAndRejectsUnicodeAndEnglish() {
    assertTrue(KrutiDevConverter.isLikelyKrutiDev("lsok esa çfrfyfi fnukad"))
    assertTrue(KrutiDevConverter.isLikelyKrutiDev("dk;kZy; vkns'k fnukad 12/11/2026"))
    assertFalse(KrutiDevConverter.isLikelyKrutiDev("नमस्ते भारत कार्यालय आदेश"))
    assertFalse(KrutiDevConverter.isLikelyKrutiDev("This is a regular English office document."))
  }

  @Test
  fun detectFontForText_prioritizesScriptAndKrutiDetectionOverGenericDefaults() {
    assertEquals(
      AppFonts.SupportedFont.HINDI_NOTO,
      AppFonts.detectFontForText("कार्यालय आदेश", declaredFontName = "Calibri", isKrutiLikely = false)
    )
    assertEquals(
      AppFonts.SupportedFont.KRUTI_DEV,
      AppFonts.detectFontForText("dk;kZy; vkns'k", declaredFontName = "Calibri", isKrutiLikely = true)
    )
    assertEquals(
      AppFonts.SupportedFont.KRUTI_DEV,
      AppFonts.detectFontForText("jkT; ljdkj", declaredFontName = "Kruti Dev 010", isKrutiLikely = true)
    )
  }

  @Test
  fun sortEntities_sortsByNameDateAndSizeInBothDirectionsWithTieBreakers() {
    val alphaSmallOld = com.example.data.local.PdfEntity(
      path = "/docs/alpha.pdf",
      title = "alpha.pdf",
      uriString = "file:///docs/alpha.pdf",
      sizeBytes = 100L,
      lastModifiedTimestamp = 1_000L,
      lastOpenedTimestamp = 5_000L
    )
    val betaLargeNew = com.example.data.local.PdfEntity(
      path = "/docs/Beta.pdf",
      title = "Beta.pdf",
      uriString = "file:///docs/Beta.pdf",
      sizeBytes = 5_000L,
      lastModifiedTimestamp = 3_000L,
      lastOpenedTimestamp = 2_000L
    )
    val gammaMediumMid = com.example.data.local.PdfEntity(
      path = "/docs/gamma.pdf",
      title = "gamma.pdf",
      uriString = "file:///docs/gamma.pdf",
      sizeBytes = 1_000L,
      lastModifiedTimestamp = 2_000L,
      lastOpenedTimestamp = 9_000L
    )
    val input = listOf(gammaMediumMid, alphaSmallOld, betaLargeNew)

    assertEquals(
      listOf("alpha.pdf", "Beta.pdf", "gamma.pdf"),
      com.example.ui.viewmodel.SortMode.sortEntities(input, com.example.ui.viewmodel.SortMode.NAME_ASC).map { it.title }
    )
    assertEquals(
      listOf("gamma.pdf", "Beta.pdf", "alpha.pdf"),
      com.example.ui.viewmodel.SortMode.sortEntities(input, com.example.ui.viewmodel.SortMode.NAME_DESC).map { it.title }
    )
    assertEquals(
      listOf("Beta.pdf", "gamma.pdf", "alpha.pdf"),
      com.example.ui.viewmodel.SortMode.sortEntities(input, com.example.ui.viewmodel.SortMode.DATE_DESC).map { it.title }
    )
    assertEquals(
      listOf("alpha.pdf", "gamma.pdf", "Beta.pdf"),
      com.example.ui.viewmodel.SortMode.sortEntities(input, com.example.ui.viewmodel.SortMode.DATE_ASC).map { it.title }
    )
    assertEquals(
      listOf("Beta.pdf", "gamma.pdf", "alpha.pdf"),
      com.example.ui.viewmodel.SortMode.sortEntities(input, com.example.ui.viewmodel.SortMode.SIZE_DESC).map { it.title }
    )
    assertEquals(
      listOf("alpha.pdf", "gamma.pdf", "Beta.pdf"),
      com.example.ui.viewmodel.SortMode.sortEntities(input, com.example.ui.viewmodel.SortMode.SIZE_ASC).map { it.title }
    )
    // Recent tab uses lastOpenedTimestamp for DATE_DESC
    assertEquals(
      listOf("gamma.pdf", "alpha.pdf", "Beta.pdf"),
      com.example.ui.viewmodel.SortMode.sortEntities(input, com.example.ui.viewmodel.SortMode.DATE_DESC, isRecentTab = true).map { it.title }
    )
  }

  @Test
  fun filterRecentEntities_excludesUnopenedOrdersDescendingAndFiltersPdfs() {
    val neverOpenedPdf = com.example.data.local.PdfEntity(
      path = "/docs/unopened.pdf",
      title = "unopened.pdf",
      uriString = "file:///docs/unopened.pdf",
      sizeBytes = 100L,
      lastModifiedTimestamp = 10_000L,
      lastOpenedTimestamp = 0L
    )
    val olderPdf = com.example.data.local.PdfEntity(
      path = "/docs/invoice.pdf",
      title = "invoice.pdf",
      uriString = "file:///docs/invoice.pdf",
      sizeBytes = 200L,
      lastModifiedTimestamp = 2_000L,
      lastOpenedTimestamp = 4_000L
    )
    val recentWord = com.example.data.local.PdfEntity(
      path = "/docs/notes.docx",
      title = "notes.docx",
      uriString = "file:///docs/notes.docx",
      sizeBytes = 300L,
      lastModifiedTimestamp = 3_000L,
      lastOpenedTimestamp = 7_000L
    )
    val newestPdf = com.example.data.local.PdfEntity(
      path = "/docs/manual.pdf",
      title = "manual.pdf",
      uriString = "file:///docs/manual.pdf",
      sizeBytes = 400L,
      lastModifiedTimestamp = 1_000L,
      lastOpenedTimestamp = 9_000L
    )
    val items = listOf(neverOpenedPdf, olderPdf, recentWord, newestPdf)

    assertEquals(
      listOf("manual.pdf", "notes.docx", "invoice.pdf"),
      com.example.ui.viewmodel.PdfViewModel.filterRecentEntities(
        items = items,
        filterMode = com.example.ui.viewmodel.RecentFilterMode.ALL
      ).map { it.title }
    )

    assertEquals(
      listOf("manual.pdf", "invoice.pdf"),
      com.example.ui.viewmodel.PdfViewModel.filterRecentEntities(
        items = items,
        filterMode = com.example.ui.viewmodel.RecentFilterMode.PDF
      ).map { it.title }
    )

    assertEquals(
      listOf("invoice.pdf"),
      com.example.ui.viewmodel.PdfViewModel.filterRecentEntities(
        items = items,
        filterMode = com.example.ui.viewmodel.RecentFilterMode.PDF,
        query = "inv"
      ).map { it.title }
    )
  }

  @Test
  fun cloudModels_driveIdentityUtf8TruncationAndMimeResolutionWorkSafely() {
    val entity = com.example.data.local.PdfEntity(
      path = "/storage/emulated/0/Download/Agreement.pdf",
      title = "Agreement.pdf",
      uriString = "file:///storage/emulated/0/Download/Agreement.pdf",
      sizeBytes = 42_500L,
      lastModifiedTimestamp = 1_700_000_000L,
      folderName = "Download"
    )
    val id1 = com.example.data.cloud.CloudDocumentIdentity.buildDocId(entity)
    val id2 = com.example.data.cloud.CloudDocumentIdentity.buildDocId("agreement.pdf", 42_500L)
    assertEquals(id1, id2)
    assertTrue(id1.matches(Regex("^doc_[a-f0-9]{40}$")))

    val model = com.example.data.cloud.CloudDocument(
      id = id1,
      userId = "uid_123",
      title = "Agreement.pdf",
      documentType = "PDF",
      sizeBytes = 42_500L,
      contentHash = id1.removePrefix("doc_"),
      storagePath = "drive://1AbCdEfGhIjK",
      syncState = "SYNCED",
      localPath = entity.path,
      folderName = "Download",
      driveFileId = "1AbCdEfGhIjK"
    )
    assertEquals("1AbCdEfGhIjK", model.resolvedDriveFileId)

    val fallbackModel = model.copy(driveFileId = "")
    assertEquals("1AbCdEfGhIjK", fallbackModel.resolvedDriveFileId)

    // Multi-byte UTF-8 folder names must stay safely under Google Drive's appProperties byte cap
    val longHindiFolder = "दस्तावेज़_" + "क".repeat(60)
    val truncated = com.example.data.cloud.CloudDocumentIdentity.truncateUtf8ForAppProperty(longHindiFolder, maxBytes = 80)
    assertTrue(truncated.toByteArray(Charsets.UTF_8).size <= 80)

    assertEquals(
      "application/pdf",
      com.example.data.cloud.GoogleDriveRepository.mimeTypeForTitle("Contract.PDF")
    )
    assertTrue(
      com.example.data.cloud.GoogleDriveRepository.parseDriveIsoTimestamp("2026-11-21T06:16:43.000Z") > 0L
    )

    assertEquals(
      com.example.data.cloud.CloudSyncState.METADATA_ONLY,
      com.example.data.cloud.CloudSyncState.fromRaw("UNKNOWN_STATE")
    )
    assertEquals(
      com.example.data.cloud.CloudSyncState.SYNCED,
      com.example.data.cloud.CloudSyncState.fromRaw("SYNCED")
    )
  }

  @Test
  fun pdfZoomMath_preventsExcessiveZoomOutAndSplitsVerticalPanForPageScrolling() {
    // Minimum resting zoom must never shrink below 1.0f (fit-to-width)
    assertEquals(1.0f, com.example.ui.components.PdfZoomMath.clampScaleResting(0.35f), 0.0001f)
    assertEquals(1.0f, com.example.ui.components.PdfZoomMath.clampScaleResting(0.99f), 0.0001f)
    assertEquals(2.5f, com.example.ui.components.PdfZoomMath.clampScaleResting(2.5f), 0.0001f)
    assertEquals(4.0f, com.example.ui.components.PdfZoomMath.clampScaleResting(5.5f), 0.0001f)

    // Elastic pinch only allows slight temporary rubber-band down to 0.90f before snapping back to 1.0f
    assertEquals(0.90f, com.example.ui.components.PdfZoomMath.clampScaleDuringPinch(0.35f), 0.0001f)
    assertEquals(4.32f, com.example.ui.components.PdfZoomMath.clampScaleDuringPinch(6.0f), 0.001f)

    // Offsets at scale <= 1.0f remain strictly centered (0, 0)
    val unzoomed = com.example.ui.components.PdfZoomMath.clampOffsets(
      scale = 0.92f,
      viewWidth = 1000f,
      viewHeight = 2000f,
      rawX = 350f,
      rawY = -420f
    )
    assertEquals(0f, unzoomed.first, 0.0001f)
    assertEquals(0f, unzoomed.second, 0.0001f)

    // Double-tap near top-left corner at 2.5x clamps cleanly to +maxX, +maxY
    val dtCorner = com.example.ui.components.PdfZoomMath.computeDoubleTapTargetOffsets(
      targetScale = 2.5f,
      tapX = 0f,
      tapY = 0f,
      viewWidth = 1000f,
      viewHeight = 2000f
    )
    assertEquals(750f, dtCorner.first, 0.01f)
    assertEquals(1500f, dtCorner.second, 0.01f)

    // Vertical pan + page scroll delegation at 2.0x (maxY = 1000f):
    // When at -900f and dragging UP by -300f, offsetY absorbs -100f (to -1000f) and delegates -200f to LazyColumn!
    val splitForward = com.example.ui.components.PdfZoomMath.splitVerticalPanAndScroll(
      currentOffsetY = -900f,
      panDeltaY = -300f,
      maxY = 1000f
    )
    assertEquals(-1000f, splitForward.newOffsetY, 0.01f)
    assertEquals(-200f, splitForward.leftoverPanDeltaY, 0.01f)

    // When already at -1000f bottom overhang and continuing UP, 100% of drag goes straight to next pages
    val splitContinuousNext = com.example.ui.components.PdfZoomMath.splitVerticalPanAndScroll(
      currentOffsetY = -1000f,
      panDeltaY = -250f,
      maxY = 1000f
    )
    assertEquals(-1000f, splitContinuousNext.newOffsetY, 0.01f)
    assertEquals(-250f, splitContinuousNext.leftoverPanDeltaY, 0.01f)

    // Dragging DOWN from -1000f by +2200f pans overhang to +1000f (2000px) and sends +200px to previous pages
    val splitBackward = com.example.ui.components.PdfZoomMath.splitVerticalPanAndScroll(
      currentOffsetY = -1000f,
      panDeltaY = 2200f,
      maxY = 1000f
    )
    assertEquals(1000f, splitBackward.newOffsetY, 0.01f)
    assertEquals(200f, splitBackward.leftoverPanDeltaY, 0.01f)
  }

  @Test
  fun appUpdateManager_validatesVersionComparisonHttpsUrlsAndApkHeader() {
    assertTrue(com.example.update.AppUpdateManager.isUpdateAvailable(1L, 2L))
    assertFalse(com.example.update.AppUpdateManager.isUpdateAvailable(2L, 2L))
    assertFalse(com.example.update.AppUpdateManager.isUpdateAvailable(3L, 2L))

    assertTrue(com.example.update.AppUpdateManager.isValidHttpsApkUrl("https://github.com/user/repo/releases/download/v2/app.apk"))
    assertFalse(com.example.update.AppUpdateManager.isValidHttpsApkUrl("http://example.com/app.apk"))
    assertFalse(com.example.update.AppUpdateManager.isValidHttpsApkUrl("https://127.0.0.1/app.apk"))
    assertFalse(com.example.update.AppUpdateManager.isValidHttpsApkUrl("https://192.168.1.10/app.apk"))
    assertFalse(com.example.update.AppUpdateManager.isValidHttpsApkUrl("https://localhost/app.apk"))
    assertFalse(com.example.update.AppUpdateManager.isValidHttpsApkUrl("file:///sdcard/app.apk"))

    assertTrue(com.example.update.AppUpdateManager.isValidApkHeader(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00), 5))
    assertFalse(com.example.update.AppUpdateManager.isValidApkHeader("<!DOCTYPE html>".toByteArray(), 15))
    assertFalse(com.example.update.AppUpdateManager.isValidApkHeader(byteArrayOf(0x50, 0x4B), 2))

    val parsed = com.example.update.AppUpdateManager.parseReleaseMap(
      mapOf(
        "latestVersionCode" to 2,
        "latestVersionName" to "1.1",
        "apkUrl" to "https://example.com/narayani-v1.1.apk",
        "releaseNotes" to "Zoom & Cloud Sync improvements",
        "forceUpdate" to false
      )
    )
    assertNotNull(parsed)
    assertEquals(2L, parsed!!.latestVersionCode)
    assertEquals("1.1", parsed.latestVersionName)

    assertNull(
      com.example.update.AppUpdateManager.parseReleaseMap(
        mapOf(
          "latestVersionCode" to 2,
          "latestVersionName" to "1.1",
          "apkUrl" to "http://insecure.example/app.apk"
        )
      )
    )
  }
}
