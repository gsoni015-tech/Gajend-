package com.example.pdf

import androidx.compose.ui.graphics.Color
import java.io.File

data class DrawingPoint(
    val x: Float,
    val y: Float
)

data class DrawingStroke(
    val points: List<DrawingPoint>,
    val color: Long,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false
)

data class TextAnnotation(
    val id: String,
    val text: String,
    val xPercent: Float,
    val yPercent: Float,
    val color: Long,
    val fontSizePt: Float = 16f,
    val backgroundColor: Long? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val fontFamilyId: String = "DEFAULT"
)

data class SignatureAnnotation(
    val id: String,
    val strokes: List<DrawingStroke>,
    val xPercent: Float,
    val yPercent: Float,
    val scale: Float = 1.0f
)

enum class StampType(val title: String, val color: Long) {
    APPROVED("APPROVED • स्वीकृत", 0xFF16A34A),
    REJECTED("REJECTED • अस्वीकृत", 0xFFDC2626),
    CONFIDENTIAL("CONFIDENTIAL • गोपनीय", 0xFF9333EA),
    DRAFT("DRAFT • प्रारूप", 0xFF4B5563),
    URGENT("URGENT • तुरंत", 0xFFEA580C),
    OFFICIAL("OFFICIAL SEAL • शासकीय", 0xFF1E3A8A),
    VERIFIED("VERIFIED • सत्यापित", 0xFF0D9488),
    CUSTOM("CUSTOM • कस्टम", 0xFF1F2937)
}

data class StampAnnotation(
    val id: String,
    val stampType: StampType,
    val customText: String = "",
    val xPercent: Float,
    val yPercent: Float,
    val rotationAngle: Float = -15f
)

data class WatermarkConfig(
    val text: String,
    val color: Long = 0xFF9CA3AF,
    val opacity: Float = 0.25f,
    val angle: Float = -45f,
    val applyToAllPages: Boolean = true
)

data class PageEditState(
    val pageId: String,
    val originalPageIndex: Int,
    val rotationDegrees: Int = 0,
    val strokes: List<DrawingStroke> = emptyList(),
    val textAnnotations: List<TextAnnotation> = emptyList(),
    val signatureAnnotations: List<SignatureAnnotation> = emptyList(),
    val stampAnnotations: List<StampAnnotation> = emptyList(),
    val watermark: WatermarkConfig? = null
)
