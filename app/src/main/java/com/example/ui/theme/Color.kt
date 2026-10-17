package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// White Mix Theme Background & Surfaces
val WhiteMixBackground = Color(0xFFF8FAFC) // Clean white mix canvas with subtle soft slate mist
val WhiteMixSurface = Color(0xFFFFFFFF)    // Crisp pure white for cards, top bar, modals
val WhiteMixSurfaceVariant = Color(0xFFF1F5F9) // Subtle soft slate container
val WhiteMixCard = Color(0xFFFFFFFF)       // Pure white cards
val WhiteMixBorder = Color(0xFFE2E8F0)     // Refined crisp border
val WhiteMixBorderSubtle = Color(0xFFEDF2F7)

// Text Colors (High contrast on White Mix)
val TextPrimary = Color(0xFF0F172A)        // Slate 900 - sharp, highly readable
val TextSecondary = Color(0xFF475569)      // Slate 600 - secondary descriptions
val TextTertiary = Color(0xFF94A3B8)       // Slate 400 - hints and footers

// Buttons & Accents designed to complement the White Mix background:
// Primary: Deep vibrant Royal Blue that contrasts with high elegance on white
val BrandPrimary = Color(0xFF1D4ED8)       // Royal Blue 700
val BrandPrimaryHover = Color(0xFF1E40AF)  // Royal Blue 800
val BrandPrimaryContainer = Color(0xFFEFF6FF) // Soft tinted blue pill container
val BrandOnPrimaryContainer = Color(0xFF1E40AF)

// Secondary: PDF Crimson accent for PDF badges, delete actions & alerts
val BrandCrimson = Color(0xFFDC2626)
val BrandCrimsonContainer = Color(0xFFFEF2F2)
val BrandOnCrimsonContainer = Color(0xFF991B1B)

// Accent Amber for Ad badges & star ratings
val AdBadgeAmber = Color(0xFFF59E0B)
val AdBadgeAmberContainer = Color(0xFFFEF3C7)

// Dark Theme Fallbacks (if user toggles in night reader)
val DarkSurface = Color(0xFF121418)
val DarkBackground = Color(0xFF0F1115)
val DarkCard = Color(0xFF1E222A)
val DarkBorder = Color(0xFF2E3440)
