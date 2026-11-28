package com.moonmew.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {
    @Test fun categoriesAndTabsStayInSync() {
        assertEquals(EmojiCatalog.categories.size, EmojiCatalog.tabs.size)
        assertTrue(EmojiCatalog.categories.size >= 12)
        assertTrue(EmojiCatalog.categories.all { it.isNotEmpty() })
    }

    @Test fun pickerIncludesUsefulModernEmojiAndFlags() {
        val all = EmojiCatalog.categories.flatten().toSet()
        assertTrue(all.contains("🩷"))
        assertTrue(all.contains("🫶"))
        assertTrue(all.contains("🫠"))
        assertTrue(all.contains("🐈‍⬛"))
        assertTrue(all.contains("🏳️‍🌈"))
        assertTrue(all.contains("🇧🇩"))
        assertTrue(all.contains("👋🏿"))
    }

    @Test fun categoriesContainNoBlankEmojiEntries() {
        assertFalse(EmojiCatalog.categories.flatten().any { it.isBlank() })
    }
}
