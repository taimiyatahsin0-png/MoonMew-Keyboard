package com.moonmew.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BanglaWordSuggestionsTest {
    @Test fun exactPrefixRanksBeforeLongerMatches() {
        val suggestions = BanglaWordSuggestions.suggest("valo")
        assertEquals("ভালো", suggestions.first())
    }

    @Test fun prefixLookupReturnsOnlyMatchingWords() {
        val suggestions = BanglaWordSuggestions.suggest("shubho", 5)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { it in listOf("শুভ", "শুভরাত্রি", "শুভ সকাল") })
    }

    @Test fun duplicateBanglaSpellingsAreCollapsed() {
        val suggestions = BanglaWordSuggestions.suggest("sob", 5)
        assertEquals(suggestions.size, suggestions.distinct().size)
    }

    @Test fun blankPrefixAndZeroLimitReturnNothing() {
        assertTrue(BanglaWordSuggestions.suggest(" ").isEmpty())
        assertTrue(BanglaWordSuggestions.suggest("valo", 0).isEmpty())
    }
}
