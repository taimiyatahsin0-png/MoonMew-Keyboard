package com.moonmew.keyboard

import android.content.Context
import android.content.ComponentCallbacks2
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.os.Bundle
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.os.SystemClock
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo

class KeyboardSurface(context: Context, private val listener: Listener) : View(context) {
    interface Listener { fun onKey(key: String) }
    enum class Mode { ENGLISH, BANGLA_PHONETIC, BANGLA_DIRECT }
    private data class Key(val label: String, val value: String, val weight: Float = 1f, val special: Boolean = false, val longPressValue: String? = null)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // One tiny reusable tiled fiber texture; allocated lazily only for plush themes.
    private var furTextureBitmap: Bitmap? = null
    private var furTextureShader: BitmapShader? = null
    private var backgroundGradient: android.graphics.LinearGradient? = null
    private var backgroundGradientWidth = -1
    private var backgroundGradientHeight = -1
    private var backgroundGradientTop = 0
    private var backgroundGradientBottom = 0
    private data class CachedKeyGradient(val bounds: RectF, val topColor: Int, val bottomColor: Int, val shader: android.graphics.LinearGradient)
    private val keyGradientCache = mutableListOf<CachedKeyGradient>()
    // Reused draw rectangles avoid allocating temporary RectF objects for every key on every frame.
    private val shadowRect = RectF()
    private val highlightRect = RectF()
    private val pawOvalRect = RectF()
    // Immutable normalized decoration points are created once, not on every redraw.
    private val starPoints = arrayOf(0.08f to 0.12f, 0.22f to 0.05f, 0.78f to 0.09f, 0.93f to 0.22f, 0.13f to 0.86f, 0.88f to 0.82f)
    private val candyAccentPoints = arrayOf(0.08f to 0.08f, 0.27f to 0.07f, 0.73f to 0.07f, 0.92f to 0.08f, 0.05f to 0.92f, 0.95f to 0.92f)
    private val pawPrintPoints = arrayOf(0.16f to 0.18f, 0.84f to 0.18f, 0.10f to 0.78f, 0.90f to 0.78f)
    private val keyTypeface = Typeface.create("sans-serif-rounded", Typeface.NORMAL)
    private val preferences = context.getSharedPreferences("moonmew_settings", Context.MODE_PRIVATE)
    private var themeId = preferences.getInt("theme_id", if (preferences.getBoolean("rose_theme", true)) 0 else 1)
    private var keySound = preferences.getBoolean("key_sound", false)
    private var keyHaptics = preferences.getBoolean("key_haptics", true)
    private var reduceAnimations = preferences.getBoolean("reduce_animations", false)
    private var mode = Mode.ENGLISH
    private var shifted = false
    private var symbols = false
    private var emojiMode = false
    private var emojiCategory = 0
    private var emojiPage = 0
    private var clipboardMode = false
    private var moreMenu = false
    private var brandSparkleUntil = 0L
    private var clipboardItems = emptyList<String>()
    private var downKeyIndex = -1
    private var downSuggestionIndex = -1
    private var downTime = 0L
    private var lastShiftTapAt = 0L
    private var spaceCursorGesture = false
    private var spaceCursorMoved = false
    private var cursorMoveRemainder = 0f
    private var deleteRepeatRunnable: Runnable? = null
    private var deleteRepeatStartedAt = 0L
    private var deleteRepeatFired = false
    private val swipePath = StringBuilder()
    private var lastSwipeKeyIndex = -1
    private var swipeTravel = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var swipeDetected = false
    private var swipeGestureEligible = false
    private var multiTouchCancelled = false
    private var swipeEnabled = true
    private var passwordField = false
    private var numericInput = false
    private var numericDecimal = false
    private var numericSigned = false
    private var editorAction = EditorInfo.IME_ACTION_NONE
    private var symbolsPage = 0
    private var composition = ""
    private var preview = ""
    private var compositionPreviewText = ""
    private var suggestions = emptyList<String>()
    private val bounds = mutableListOf<RectF>()
    private val suggestionBounds = mutableListOf<RectF>()
    private val rows = mutableListOf<List<Key>>()
    private val flatKeys = mutableListOf<Key>()

    // Keep hit-test rectangles across frames; rebuild() clears them when key structure changes.
    private fun backgroundShader(): android.graphics.LinearGradient {
        val topColor = backgroundTopColor()
        val bottomColor = backgroundBottomColor()
        val cached = backgroundGradient
        if (cached == null || backgroundGradientWidth != width || backgroundGradientHeight != height ||
            backgroundGradientTop != topColor || backgroundGradientBottom != bottomColor) {
            return android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), topColor, bottomColor,
                android.graphics.Shader.TileMode.CLAMP).also {
                backgroundGradient = it
                backgroundGradientWidth = width
                backgroundGradientHeight = height
                backgroundGradientTop = topColor
                backgroundGradientBottom = bottomColor
            }
        }
        return cached
    }

    private fun keyShader(index: Int, rect: RectF, topColor: Int, bottomColor: Int): android.graphics.LinearGradient {
        while (keyGradientCache.size <= index) {
            keyGradientCache.add(CachedKeyGradient(RectF(), 0, 0,
                android.graphics.LinearGradient(0f, 0f, 0f, 1f, 0, 0, android.graphics.Shader.TileMode.CLAMP)))
        }
        val cached = keyGradientCache[index]
        if (cached.topColor == topColor && cached.bottomColor == bottomColor && cached.bounds == rect) return cached.shader
        val boundsCopy = RectF(rect)
        val shader = android.graphics.LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
            topColor, bottomColor, android.graphics.Shader.TileMode.CLAMP)
        keyGradientCache[index] = CachedKeyGradient(boundsCopy, topColor, bottomColor, shader)
        return shader
    }

    private fun obtainRect(pool: MutableList<RectF>, index: Int): RectF {
        while (pool.size <= index) pool.add(RectF())
        return pool[index]
    }

    private fun trimRectPool(pool: MutableList<RectF>, used: Int) {
        while (pool.size > used) pool.removeAt(pool.lastIndex)
    }

    // Virtual IDs include a layout generation so a delayed TalkBack action can
    // never target a different key after the keyboard or suggestion row changes.
    private companion object {
        const val ACCESSIBILITY_ID_STRIDE = 4096
    }
    private var accessibilityGeneration = 0
    private fun advanceAccessibilityGeneration() {
        accessibilityGeneration = if (accessibilityGeneration >= Int.MAX_VALUE / ACCESSIBILITY_ID_STRIDE - 1) {
            0
        } else {
            accessibilityGeneration + 1
        }
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }
    private fun accessibilityNodeId(localIndex: Int): Int =
        accessibilityGeneration * ACCESSIBILITY_ID_STRIDE + localIndex

    private val keyboardAccessibilityProvider = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            if (virtualViewId == HOST_VIEW_ID) {
                return AccessibilityNodeInfo.obtain(this@KeyboardSurface).apply {
                    className = KeyboardSurface::class.java.name
                    packageName = context.packageName
                    contentDescription = "MoonMew keyboard"
                    setSource(this@KeyboardSurface)
                    val parentView = this@KeyboardSurface.parent
                    if (parentView is View) setParent(parentView)
                    for (index in bounds.indices) addChild(this@KeyboardSurface, accessibilityNodeId(index))
                    for (index in suggestionBounds.indices) {
                        addChild(this@KeyboardSurface, accessibilityNodeId(flatKeys.size + index))
                    }
                }
            }
            if (virtualViewId < 0 || virtualViewId / ACCESSIBILITY_ID_STRIDE != accessibilityGeneration) return null
            val localIndex = virtualViewId % ACCESSIBILITY_ID_STRIDE
            val suggestionIndex = localIndex - flatKeys.size
            val isSuggestion = suggestionIndex >= 0
            val rect = if (isSuggestion) suggestionBounds.getOrNull(suggestionIndex) else bounds.getOrNull(localIndex)
            if (rect == null) return null
            val key = if (isSuggestion) null else flatKeys.getOrNull(localIndex)
            val label = if (isSuggestion) suggestions.getOrNull(suggestionIndex) else key?.let { accessibilityLabel(it) }
            return AccessibilityNodeInfo.obtain().apply {
                setSource(this@KeyboardSurface, virtualViewId)
                setParent(this@KeyboardSurface)
                packageName = context.packageName
                className = "android.widget.Button"
                text = label ?: "Keyboard key"
                contentDescription = if (isSuggestion) "Suggestion: ${label ?: "word"}" else label ?: "Keyboard key"
                isEnabled = true
                isFocusable = true
                isClickable = true
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                if (key?.longPressValue != null) {
                    isLongClickable = true
                    addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK)
                }
                val nodeBounds = Rect()
                rect.roundOut(nodeBounds)
                setBoundsInParent(nodeBounds)
            }
        }

        override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (virtualViewId == HOST_VIEW_ID) {
                return this@KeyboardSurface.performAccessibilityAction(action, arguments)
            }
            if (action != AccessibilityNodeInfo.ACTION_CLICK &&
                action != AccessibilityNodeInfo.ACTION_LONG_CLICK) return false
            if (virtualViewId < 0 || virtualViewId / ACCESSIBILITY_ID_STRIDE != accessibilityGeneration) return false
            val localIndex = virtualViewId % ACCESSIBILITY_ID_STRIDE
            if (localIndex >= flatKeys.size) {
                if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) return false
                val word = suggestions.getOrNull(localIndex - flatKeys.size) ?: return false
                listener.onKey("SUGGEST:$word")
            } else {
                val key = flatKeys.getOrNull(localIndex) ?: return false
                if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
                    val alternate = key.longPressValue ?: return false
                    listener.onKey("LONG:$alternate")
                } else {
                    listener.onKey(key.value)
                }
            }
            return true
        }
    }

    init {
        isClickable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        setBackgroundColor(backgroundBottomColor())
        applyHeight()
        rebuild()
    }

    private fun accessibilityLabel(key: Key): String = when (key.value) {
        "DELETE" -> "Backspace"
        "SPACE" -> "Space"
        "ENTER" -> when (key.label) {
            "Go" -> "Go action"
            "Search" -> "Search action"
            "Send" -> "Send action"
            "Next" -> "Next field"
            "Done" -> "Done action"
            "Prev" -> "Previous field"
            else -> "Enter"
        }
        "SHIFT" -> if (shifted) "Shift, uppercase" else "Shift, lowercase"
        "MODE" -> if (mode == Mode.BANGLA_DIRECT) "Switch to English" else "Switch language"
        "SYMBOLS", "SYMBOLS_MORE" -> "Symbols"
        "EMOJI" -> "Open emoji picker"
        "CLIP" -> "Open clipboard history"
        "EMOJI_BACK" -> "Return to keyboard"
        "CLIP_CLOSE" -> "Close clipboard history"
        "CLIP_CLEAR" -> "Clear clipboard history"
        "ABC" -> "Return to letters"
        "TOOLBAR_BRAND" -> "MoonMew keyboard tools"
        else -> key.label
    }

    private fun configuredHeightDp(): Int =
        preferences.getInt("keyboard_height_dp", 210).coerceIn(140, 280)

    private fun applyHeight() {
        val params = layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(configuredHeightDp().toFloat()).toInt()
        )
        params.height = dp(configuredHeightDp().toFloat()).toInt()
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams = params
        requestLayout()
    }

    // IME parents on some Android builds measure their input view at almost the
    // full display height. Do not let that turn the keyboard into a full-screen view.
    // A resize changes every key's hitbox. Clear old geometry before another touch
    // can be dispatched; onDraw will repopulate the pools using the new dimensions.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            cancelDeleteRepeat()
            lastShiftTapAt = 0L
            // A finger may still be down while Android resizes the IME. Cancel that
            // gesture too: onDraw can repopulate the same indices before ACTION_UP.
            multiTouchCancelled = true
            downKeyIndex = -1
            downSuggestionIndex = -1
            resetSwipeTracking()
            bounds.clear()
            suggestionBounds.clear()
            advanceAccessibilityGeneration()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val displayHeightPx = resources.displayMetrics.heightPixels
        val screenCapPx = (displayHeightPx * 0.44f).toInt().coerceAtLeast(dp(120f).toInt())
        val parentCapPx = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            screenCapPx
        } else {
            MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        }
        val desiredPx = dp(configuredHeightDp().toFloat()).toInt()
        val measuredHeight = minOf(desiredPx, screenCapPx, parentCapPx).coerceAtLeast(1)
        val measuredWidth = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            resources.displayMetrics.widthPixels.coerceAtLeast(suggestedMinimumWidth)
        } else {
            MeasureSpec.getSize(widthMeasureSpec)
        }
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    fun refreshPreferences() {
        themeId = preferences.getInt("theme_id", if (preferences.getBoolean("rose_theme", true)) 0 else 1).coerceIn(0, 6)
        keySound = preferences.getBoolean("key_sound", false)
        keyHaptics = preferences.getBoolean("key_haptics", true)
        reduceAnimations = preferences.getBoolean("reduce_animations", false)
        if (themeId < 5) releaseFurTexture()
        // If clipboard history was switched off in Settings while this IME was alive,
        // immediately discard the in-memory panel contents as well as the saved data.
        if (!preferences.getBoolean("clipboard_history_enabled", false)) {
            clipboardItems = emptyList()
            clipboardMode = false
        }
        setBackgroundColor(backgroundBottomColor())
        applyHeight()
        rebuild()
        invalidate()
    }

    private fun backgroundTopColor() = when (themeId) {
        1 -> 0xFF172B43.toInt()
        2 -> 0xFF30213F.toInt()
        3 -> 0xFFFFD9EC.toInt()
        4 -> 0xFF24213D.toInt()
        5 -> 0xFF17151B.toInt()
        6 -> 0xFFD8C2E8.toInt()
        else -> 0xFFFFEAF1.toInt()
    }
    private fun backgroundBottomColor() = when (themeId) {
        1 -> 0xFF0B1422.toInt()
        2 -> 0xFF171020.toInt()
        3 -> 0xFFFFB9D9.toInt()
        4 -> 0xFF111329.toInt()
        5 -> 0xFF09090F.toInt()
        6 -> 0xFFF4D7E8.toInt()
        else -> 0xFFF7C9DA.toInt()
    }
    private fun accentColor() = when (themeId) {
        1 -> 0xFFB9DFFF.toInt()
        2 -> 0xFFE0C8FF.toInt()
        3 -> 0xFFFFFFFF.toInt()
        4 -> 0xFFB9A7FF.toInt()
        5 -> 0xFFF4DCE6.toInt()
        6 -> 0xFFC7A9E2.toInt()
        else -> 0xFFFF8FB9.toInt()
    }
    private fun keyColor(special: Boolean, modeKey: Boolean = false) = when (themeId) {
        1 -> if (modeKey) 0xFF416B8B.toInt() else if (special) 0xFF2B405B.toInt() else 0xFF1D2B3E.toInt()
        2 -> if (modeKey) 0xFF795A99.toInt() else if (special) 0xFF49365F.toInt() else 0xFF302541.toInt()
        3 -> if (modeKey) 0xFFFF83BA.toInt() else if (special) 0xFFFFC4E0.toInt() else 0xFFFFF8FC.toInt()
        4 -> if (modeKey) 0xFF7866C8.toInt() else if (special) 0xFF39365D.toInt() else 0xFF292743.toInt()
        5 -> if (modeKey) 0xFF9A667A.toInt() else if (special) 0xFF39313D.toInt() else 0xFF211F29.toInt()
        6 -> if (modeKey) 0xFFE9AFCB.toInt() else if (special) 0xFFDCC8EC.toInt() else 0xFFFFF8FC.toInt()
        else -> if (modeKey) 0xFFFFA9C8.toInt() else if (special) 0xFFFFD5E3.toInt() else 0xFFFFF8F5.toInt()
    }
    private fun keyTextColor() = when (themeId) {
        1 -> 0xFFEAF5FF.toInt()
        2 -> 0xFFF4EAFF.toInt()
        3 -> 0xFF713B60.toInt()
        4 -> 0xFFF0ECFF.toInt()
        5 -> 0xFFF7EAF0.toInt()
        6 -> 0xFF49334E.toInt()
        else -> 0xFF805365.toInt()
    }

    private fun releaseFurTexture() {
        furTextureShader = null
        furTextureBitmap?.recycle()
        furTextureBitmap = null
    }

    /** Release optional rendering caches when Android reports memory pressure. */
    fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        releaseFurTexture()
        backgroundGradient = null
        keyGradientCache.clear()
        invalidate()
    }

    private fun furShader(): BitmapShader {
        furTextureShader?.let { return it }
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val tileCanvas = Canvas(bitmap)
        val fiberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        // Deterministic, sparse fibers: no random work or allocations during drawing.
        for (i in 0 until 18) {
            val x = ((i * 7 + 3) % 23).toFloat()
            val y = ((i * 11 + 5) % 23).toFloat()
            fiberPaint.color = if (i % 3 == 0) 0x55FFFFFF else 0x332B1D35
            tileCanvas.drawLine(x, y, x + if (i % 2 == 0) 2.5f else -1.5f, y - 3f, fiberPaint)
        }
        furTextureBitmap = bitmap
        return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also { furTextureShader = it }
    }

    fun setSwipeEnabled(value: Boolean) { swipeEnabled = value; if (!value) resetSwipeTracking() }
    fun setNumericInput(value: Boolean, decimal: Boolean = false, signed: Boolean = false) {
        numericInput = value
        numericDecimal = decimal
        numericSigned = signed
        rebuild()
        invalidate()
    }
    fun setEditorAction(value: Int) { editorAction = EditorActionPolicy.keyboardAction(value); rebuild(); invalidate() }
    private fun editorActionLabel(): String = when (editorAction) {
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_DONE -> "Done"
        EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
        else -> "↵"
    }
    fun setPasswordField(value: Boolean) {
        passwordField = value
        if (value) {
            // Private fields must never retain clipboard content or an open tools panel.
            clipboardMode = false
            moreMenu = false
            clipboardItems = emptyList()
        }
        rebuild()
        invalidate()
    }
    private fun resetSwipeTracking() { swipePath.setLength(0); lastSwipeKeyIndex = -1; swipeTravel = 0f; swipeDetected = false; swipeGestureEligible = false }
    fun setMode(value: Mode) { mode = value; lastShiftTapAt = 0L; symbols = false; emojiMode = false; clipboardMode = false; symbolsPage = 0; rebuild(); invalidate() }
    fun setShifted(value: Boolean) { shifted = value; rebuild(); invalidate() }
    fun setSymbols(value: Boolean) { symbols = value; emojiMode = false; clipboardMode = false; symbolsPage = 0; rebuild(); invalidate() }
    fun setEmoji(value: Boolean) { emojiMode = value; if (value) { emojiCategory = 0; emojiPage = 0 }; clipboardMode = false; symbols = false; rebuild(); invalidate() }
    fun setEmojiCategory(value: Int) { emojiCategory = value.coerceIn(0, EmojiCatalog.categories.lastIndex); emojiPage = 0; if (emojiMode) { rebuild(); invalidate() } }
    fun moveEmojiPage(delta: Int) { if (emojiMode) { emojiPage = (emojiPage + delta).coerceAtLeast(0); rebuild(); invalidate() } }
    fun setClipboard(value: Boolean) { clipboardMode = value; moreMenu = false; emojiMode = false; symbols = false; rebuild(); invalidate() }
    fun setMoreMenu(value: Boolean) { moreMenu = value && !passwordField; clipboardMode = false; emojiMode = false; symbols = false; rebuild(); invalidate() }
    fun playBrandSparkle() { brandSparkleUntil = SystemClock.elapsedRealtime() + 720L; invalidate() }
    fun setClipboardItems(value: List<String>) { clipboardItems = value.take(5); if (clipboardMode) rebuild(); invalidate() }
    fun nextSymbolsPage() { if (symbols) { symbolsPage = 1 - symbolsPage; rebuild(); invalidate() } }
    private fun updateCompositionPreviewText() {
        compositionPreviewText = if (composition.isEmpty()) "" else "$composition  →  $preview"
    }
    fun setComposition(value: String) {
        val wasShowingComposition = composition.isNotEmpty()
        composition = value
        updateCompositionPreviewText()
        // The preview text itself is not a virtual node. Keep stable key/suggestion
        // IDs while typing; only invalidate geometry when the preview row appears
        // or disappears and changes the key layout.
        if (wasShowingComposition != value.isNotEmpty()) {
            suggestionBounds.clear()
            advanceAccessibilityGeneration()
        }
        invalidate()
    }
    fun setPreview(value: String) {
        preview = value
        updateCompositionPreviewText()
        invalidate()
    }
    fun setSuggestions(value: List<String>) {
        val nextSuggestions = value.take(3)
        if (suggestions != nextSuggestions) {
            suggestions = nextSuggestions
            suggestionBounds.clear()
            // Suggestion labels/actions changed, so delayed accessibility actions
            // must not resolve against the newly displayed words.
            advanceAccessibilityGeneration()
        }
        invalidate()
    }

    private fun rebuild() {
        cancelDeleteRepeat()
        // A layout rebuild invalidates the geometry captured by an in-flight touch.
        // Cancel it so ACTION_UP cannot activate a different key at the same index.
        multiTouchCancelled = true
        downKeyIndex = -1
        downSuggestionIndex = -1
        resetSwipeTracking()
        advanceAccessibilityGeneration()
        rows.clear()
        flatKeys.clear()
        // Old hitboxes must not point at a newly rebuilt key list before the next draw.
        bounds.clear()
        suggestionBounds.clear()
        keyGradientCache.clear()
        if (moreMenu) {
            rows.add(listOf(Key("📋 Clipboard", "MORE_CLIP", 1.5f, true), Key("ABC", "MORE_CLOSE", 1f, true)))
        } else if (clipboardMode) {
            if (clipboardItems.isEmpty()) {
                val enabled = preferences.getBoolean("clipboard_history_enabled", false)
                rows.add(listOf(Key(
                    if (enabled) "No saved clips" else "Enable in MoonMew settings",
                    if (enabled) "CLIP_EMPTY" else "CLIP_NOTICE",
                    1f, true
                )))
                rows.add(listOf(Key("ABC", "CLIP_CLOSE", 1f, true)))
            } else {
                clipboardItems.forEachIndexed { index, item ->
                    rows.add(listOf(Key(item.replace("\n", " ").take(32), "CLIP_ITEM:$index", 1f, true)))
                }
                rows.add(listOf(Key("Clear history", "CLIP_CLEAR", 1.4f, true), Key("ABC", "CLIP_CLOSE", 1f, true)))
            }
        } else if (emojiMode) {
            val categories = EmojiCatalog.categories
            rows.add(EmojiCatalog.tabs.mapIndexed { index, label ->
                Key(label, "EMOJI_CAT:$index", 1f, true)
            })
            val emojiRows = categories[emojiCategory].chunked(8)
            val pageWindow = EmojiPagination.window(emojiRows.size, emojiPage)
            emojiPage = pageWindow.pageIndex
            emojiRows.subList(pageWindow.startRow, pageWindow.endRowExclusive).forEach { line ->
                rows.add(line.map { emoji -> Key(emoji, emoji) })
            }
            rows.add(listOf(
                Key("‹", "EMOJI_PAGE_PREV", 1f, true),
                Key("${emojiPage + 1}/${pageWindow.pageCount}", "EMOJI_PAGE_STATUS", 1f, true),
                Key("›", "EMOJI_PAGE_NEXT", 1f, true)
            ))
            rows.add(listOf(Key("ABC", "EMOJI_BACK", 1.2f, true), Key("⌫", "DELETE", 1.2f, true), Key("Space", "SPACE", 3.6f, true)))
        } else if (numericInput && !symbols) {
            // Dedicated number-pad layout for numeric editors; avoids showing a
            // full QWERTY board where only numeric input is accepted.
            listOf("123", "456", "789").forEach { line ->
                rows.add(line.map { ch -> Key(ch.toString(), ch.toString()) })
            }
            val bottom = mutableListOf<Key>()
            if (numericSigned) bottom.add(Key("−", "-", 1f, true))
            bottom.add(Key("0", "0"))
            if (numericDecimal) bottom.add(Key(".", "."))
            bottom.add(Key("⌫", "DELETE", 1.2f, true))
            bottom.add(Key(editorActionLabel(), "ENTER", 1.2f, true))
            rows.add(bottom)
        } else if (symbols) {
            val lines = if (symbolsPage == 0)
                listOf("1234567890", "!@#$%^&*()", "-_=+[]{}<>", "?/;:,.।")
            else
                listOf("1234567890", "`£€¥₹¢©®", "±×÷√∞≠≤≥", "ঁংঃ্৽ৎড়ঢ়য়")
            lines.forEach { line -> rows.add(line.map { ch ->
                    val alternate = when (ch) {
                        'ড' -> "ড়"; 'ঢ' -> "ঢ়"; 'য' -> "য়"; 'ত' -> "ৎ"
                        else -> null
                    }
                    Key(ch.toString(), ch.toString(), longPressValue = alternate)
                }) }
            rows.add(listOf(Key("ABC", "ABC", 1.2f, true), Key("☾", "MODE", 1.1f, true),
                Key("⌫", "DELETE", 1.2f, true)))
            rows.add(listOf(Key(if (symbolsPage == 0) "=\\<" else "123", "SYMBOLS_MORE", 1.1f, true),
                Key(",", ",", .7f), Key("English / বাংলা", "SPACE", 4.6f, true),
                Key(".", ".", .7f), Key(editorActionLabel(), "ENTER", 1.1f, true)))
        } else if (mode == Mode.BANGLA_DIRECT) {
            // Dedicated compact Direct Bangla layout: four character rows and one action row.
            // Independent vowels stay on tap; their matching vowel signs are available on long press.
            val vowels = listOf(
                "অ" to null, "আ" to "া", "ই" to "ি", "ঈ" to "ী", "উ" to "ু",
                "ঊ" to "ূ", "ঋ" to "ৃ", "এ" to "ে", "ঐ" to "ৈ", "ও" to "ো", "ঔ" to "ৌ"
            )
            rows.add(vowels.map { (label, alternate) ->
                Key(label, label, longPressValue = alternate)
            })
            listOf("কখগঘঙচছজঝঞ", "টঠডঢণতথদধন", "পফবভমযরলশষসহ").forEach { line ->
                rows.add(line.map { ch ->
                    val alternate = when (ch) {
                        'ড' -> "ড়"; 'ঢ' -> "ঢ়"; 'য' -> "য়"; 'ত' -> "ৎ"
                        else -> null
                    }
                    Key(ch.toString(), ch.toString(), longPressValue = alternate)
                })
            }
            rows.add(listOf(
                Key("?123", "SYMBOLS", 1.05f, true),
                Key("EN", "MODE", 0.9f, true),
                Key("ং", "ং", 0.8f),
                Key("ঃ", "ঃ", 0.8f),
                Key("⌫", "DELETE", 1.0f, true),
                Key("Space", "SPACE", 2.8f, true),
                Key(editorActionLabel(), "ENTER", 0.9f, true)
            ))
        } else {
            // A compact action strip keeps common tools reachable without adding
            // another full-height row of oversized controls.
            rows.add(buildList {
                add(Key("🐱 MoonMew", "TOOLBAR_BRAND", 2.1f, true))
                add(Key("😊", "EMOJI", 0.9f, true))
                if (!passwordField) add(Key("⋯", "MORE", 0.8f, true))
                add(Key("বাংলা", "MODE", 1.1f, true))
            })
            rows.add("1234567890".map { ch ->
                Key(ch.toString(), ch.toString(), longPressValue = NumberLongPressPolicy.alternateFor(ch))
            })
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { line ->
                rows.add(line.map { ch ->
                    val label = if (shifted) ch.uppercaseChar().toString() else ch.toString()
                    Key(label, ch.toString(), longPressValue = longPressFor(ch))
                })
            }
            rows.add(listOf(Key("⇧", "SHIFT", 1.2f, true),
                Key("EN", "MODE", 1.4f, true), Key("⌫", "DELETE", 1.2f, true)))
            rows.add(listOf(
                Key("?123", "SYMBOLS", 1.1f, true), Key(",", ",", .7f),
                Key("English / বাংলা", "SPACE", 4.2f, true),
                Key(".", ".", .7f), Key(editorActionLabel(), "ENTER", 1.1f, true)
            ))
        }
        rows.forEach { flatKeys.addAll(it) }
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = keyboardAccessibilityProvider

    private fun cancelDeleteRepeat() {
        deleteRepeatRunnable?.let(::removeCallbacks)
        deleteRepeatRunnable = null
    }

    private fun startDeleteRepeat(index: Int) {
        if (flatKeys.getOrNull(index)?.value != "DELETE") return
        cancelDeleteRepeat()
        deleteRepeatStartedAt = SystemClock.elapsedRealtime()
        deleteRepeatFired = false
        val repeat = object : Runnable {
            override fun run() {
                if (downKeyIndex != index || multiTouchCancelled ||
                    bounds.getOrNull(index)?.contains(lastTouchX, lastTouchY) != true
                ) return
                deleteRepeatFired = true
                listener.onKey("DELETE")
                val heldFor = SystemClock.elapsedRealtime() - deleteRepeatStartedAt
                postDelayed(this, if (heldFor >= 1_200L) 48L else 88L)
            }
        }
        deleteRepeatRunnable = repeat
        postDelayed(repeat, 420L)
    }

    override fun onDetachedFromWindow() {
        cancelDeleteRepeat()
        releaseFurTexture()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rows.isEmpty()) return
        paint.shader = backgroundShader()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        if (!reduceAnimations) {
            paint.color = accentColor()
            paint.alpha = 58
            starPoints.forEachIndexed { index, point ->
                canvas.drawCircle(width * point.first, height * point.second, dp(if (index % 2 == 0) 1.4f else 1f), paint)
            }
            paint.alpha = 255
        }
        // Decorative candy dots and tiny bow-like accents for the kawaii palettes.
        if (!reduceAnimations && (themeId == 0 || themeId == 3)) {
            candyAccentPoints.forEachIndexed { index, point ->
                val cx = width * point.first
                val cy = height * point.second
                paint.color = if (index % 2 == 0) 0xFFFF8FB9.toInt() else 0xFFFFFFFF.toInt()
                paint.alpha = 185
                canvas.drawCircle(cx - dp(2f), cy, dp(2.4f), paint)
                canvas.drawCircle(cx + dp(2f), cy, dp(2.4f), paint)
                canvas.drawCircle(cx, cy, dp(1.5f), paint)
            }
            // Tiny paw-print motifs add a recognisable kitty detail without
            // covering keys or competing with the labels.
            pawPrintPoints.forEachIndexed { index, point ->
                val cx = width * point.first
                val cy = height * point.second
                val paw = if (index % 2 == 0) 0xFFFF8FB9.toInt() else 0xFFFFFFFF.toInt()
                paint.color = paw
                paint.alpha = 115
                val r = dp(2.2f)
                canvas.drawCircle(cx - r * 1.35f, cy - r * 1.15f, r, paint)
                canvas.drawCircle(cx, cy - r * 1.65f, r, paint)
                canvas.drawCircle(cx + r * 1.35f, cy - r * 1.15f, r, paint)
                pawOvalRect.set(cx - r * 1.5f, cy - r * 0.4f, cx + r * 1.5f, cy + r * 1.65f)
                canvas.drawOval(pawOvalRect, paint)
            }
            paint.alpha = 255
        }
        if (!reduceAnimations) {
            val now = SystemClock.elapsedRealtime()
            if (now < brandSparkleUntil) {
                val progress = 1f - (brandSparkleUntil - now) / 720f
                paint.color = 0xFFFFF4A8.toInt()
                paint.alpha = (220 * (1f - progress * 0.45f)).toInt().coerceIn(0, 255)
                val brandIndex = flatKeys.indexOfFirst { it.value == "TOOLBAR_BRAND" }
                val brandBounds = bounds.getOrNull(brandIndex)
                val cx = brandBounds?.centerX() ?: dp(70f)
                val cy = brandBounds?.centerY() ?: dp(13f)
                for (i in 0 until 7) {
                    val angle = i * (Math.PI * 2.0 / 7.0) + progress * 1.8
                    val radius = dp(8f + progress * 16f)
                    canvas.drawCircle(cx + kotlin.math.cos(angle).toFloat() * radius,
                        cy + kotlin.math.sin(angle).toFloat() * radius, dp(1.5f), paint)
                }
                paint.alpha = 255
                postInvalidateDelayed(32L)
            }
            val moonX = width - dp(18f)
            val moonY = dp(12f)
            canvas.drawCircle(moonX, moonY, dp(6f), paint)
            paint.color = backgroundTopColor()
            canvas.drawCircle(moonX + dp(3f), moonY - dp(2f), dp(5.5f), paint)
        }
        val pad = dp(5f)
        // A slim kawaii character ribbon gives the pink theme a plush themed-keyboard feel
        // while keeping the typing area compact.
        val ribbonHeight = if (themeId == 0 || themeId == 3) dp(19f) else dp(7f)
        val top = ribbonHeight
        val previewHeight = if (composition.isNotEmpty()) dp(49f) else dp(5f)
        if ((themeId == 0 || themeId == 3) && !emojiMode && !clipboardMode && !moreMenu) {
            val ribbon = RectF(pad, dp(2f), width - pad, ribbonHeight - dp(2f))
            paint.color = 0x66FFFFFF
            canvas.drawRoundRect(ribbon, dp(9f), dp(9f), paint)
            paint.color = 0xFFFFB5CF.toInt()
            paint.textSize = dp(10f)
            paint.typeface = keyTypeface
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🐰   ✦   🎀   ✧   🐰   ✦   🎀", width / 2f, ribbon.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
        }
        var suggestionRectCount = 0
        if (composition.isNotEmpty()) {
            paint.color = accentColor()
            paint.textSize = dp(12f)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(compositionPreviewText, pad * 2, top + dp(15f), paint)
            val shownCount = minOf(suggestions.size, 3)
            if (shownCount > 0) {
                val gap = dp(5f)
                val itemWidth = (width - pad * 2 - gap * (shownCount - 1)) / shownCount
                for (index in 0 until shownCount) {
                    val word = suggestions[index]
                    val left = pad + index * (itemWidth + gap)
                    val rect = obtainRect(suggestionBounds, index).apply {
                        set(left, top + dp(23f), left + itemWidth, top + dp(45f))
                    }
                    suggestionRectCount++
                    paint.color = keyColor(special = true)
                    canvas.drawRoundRect(rect, dp(6f), dp(6f), paint)
                    paint.color = keyTextColor()
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = dp(13f)
                    canvas.drawText(word, rect.centerX(), rect.centerY() - (paint.ascent() + paint.descent()) / 2, paint)
                }
            }
        }
        trimRectPool(suggestionBounds, suggestionRectCount)
        val rowHeight = (height - top - previewHeight - pad) / rows.size
        var keyRectCount = 0
        rows.forEachIndexed { rowIndex, row ->
            val gap = dp(4f)
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val usable = width - pad * 2 - gap * (row.size - 1)
            var x = pad
            row.forEach { key ->
                val w = usable * key.weight / total
                val rect = obtainRect(bounds, keyRectCount++).apply {
                    set(x, top + previewHeight + rowIndex * rowHeight + dp(2f),
                        x + w, top + previewHeight + (rowIndex + 1) * rowHeight - dp(3f))
                }
                val radius = dp(if (themeId == 3) 14f else 11f)
                // Plush 3D keycap: soft pink drop shadow, pale raised rim and glossy face.
                paint.color = if (themeId == 1 || themeId == 2) 0x66000000 else 0x668E4564
                shadowRect.set(rect.left, rect.top + dp(3f), rect.right, rect.bottom + dp(3f))
                canvas.drawRoundRect(shadowRect, radius, radius, paint)
                if (themeId == 0 || themeId == 3) {
                    paint.color = 0xFFFFFDFE.toInt()
                    val outer = RectF(rect.left - dp(0.5f), rect.top - dp(0.5f), rect.right + dp(0.5f), rect.bottom + dp(0.5f))
                    canvas.drawRoundRect(outer, radius + dp(1f), radius + dp(1f), paint)
                }
                val base = keyColor(special = key.special, modeKey = key.value == "MODE")
                val topTint = if (themeId == 1 || themeId == 2) 0xFF64718A.toInt() else 0xFFFFFFFF.toInt()
                paint.shader = keyShader(keyRectCount - 1, rect, topTint, base)
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.shader = null
                // Subtle press feedback; only highlight while the pointer remains on this key.
                if (downKeyIndex == keyRectCount - 1 &&
                    bounds.getOrNull(downKeyIndex)?.contains(lastTouchX, lastTouchY) == true) {
                    paint.color = 0x22FFFFFF
                    canvas.drawRoundRect(rect, radius, radius, paint)
                }
                if (!reduceAnimations && (themeId == 5 || themeId == 6)) {
                    paint.shader = furShader()
                    paint.color = 0xFFFFFFFF.toInt()
                    paint.alpha = if (themeId == 5) 42 else 30
                    canvas.drawRoundRect(rect, radius, radius, paint)
                    paint.shader = null
                    paint.alpha = 255
                }
                // A small glossy highlight makes pink and glass keys feel like crafted keycaps.
                paint.color = if (themeId == 1 || themeId == 2) 0x33FFFFFF else 0x99FFFFFF.toInt()
                highlightRect.set(rect.left + dp(3f), rect.top + dp(2f), rect.right - dp(3f), rect.top + dp(5f))
                canvas.drawRoundRect(highlightRect, dp(3f), dp(3f), paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(if (themeId == 0 || themeId == 3) 1.35f else 0.8f)
                paint.color = if (themeId == 0 || themeId == 3) 0xFFFFA9C7.toInt() else accentColor()
                paint.alpha = if (key.special) 210 else 155
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.style = Paint.Style.FILL
                if (themeId == 0 || themeId == 3) {
                    paint.color = 0x66FFFFFF
                    highlightRect.set(rect.left + dp(5f), rect.bottom - dp(5f), rect.right - dp(5f), rect.bottom - dp(3f))
                    canvas.drawRoundRect(highlightRect, dp(2f), dp(2f), paint)
                }
                paint.alpha = 255
                paint.color = keyTextColor()
                paint.textAlign = Paint.Align.CENTER
                val preferredTextSize = if (key.label.length > 5) dp(11f) else dp(18f)
                // Fit labels inside short rows (especially 7-row Bangla Direct at 160dp).
                paint.textSize = minOf(preferredTextSize, rect.height() * 0.68f)
                paint.typeface = keyTypeface
                val baseline = rect.centerY() - (paint.ascent() + paint.descent()) / 2
                canvas.drawText(key.label, rect.centerX(), baseline, paint)
                x += w + gap
            }
        }
        trimRectPool(bounds, keyRectCount)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiTouchCancelled = false
                downSuggestionIndex = suggestionBounds.indexOfFirst { it.contains(event.x, event.y) }
                downKeyIndex = bounds.indexOfFirst { it.contains(event.x, event.y) }
                swipeGestureEligible = SwipeGesturePolicy.canStart(flatKeys.getOrNull(downKeyIndex)?.value)
                spaceCursorGesture = mode == Mode.ENGLISH && !symbols && !emojiMode && !clipboardMode && !passwordField && flatKeys.getOrNull(downKeyIndex)?.value == "SPACE"
                spaceCursorMoved = false
                cursorMoveRemainder = 0f
                downTime = SystemClock.elapsedRealtime()
                deleteRepeatFired = false
                startDeleteRepeat(downKeyIndex)
                swipePath.setLength(0)
                lastSwipeKeyIndex = -1
                swipeTravel = 0f
                swipeDetected = false
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                if (swipeGestureEligible && !spaceCursorGesture && swipeEnabled && mode == Mode.ENGLISH && !symbols && !emojiMode && !clipboardMode) appendSwipeKey(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                val wasPressedKeyHighlighted =
                    bounds.getOrNull(downKeyIndex)?.contains(lastTouchX, lastTouchY) == true
                if (spaceCursorGesture) {
                    cursorMoveRemainder += dx
                    val stepPx = dp(SpacebarCursorPolicy.STEP_DP)
                    val steps = SpacebarCursorPolicy.stepsForDistance(cursorMoveRemainder, stepPx)
                    if (steps != 0) {
                        val action = if (steps < 0) "CURSOR_LEFT" else "CURSOR_RIGHT"
                        repeat(if (steps < 0) -steps else steps) { listener.onKey(action) }
                        cursorMoveRemainder -= steps * stepPx
                        spaceCursorMoved = true
                    }
                } else {
                    swipeTravel += Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (swipeTravel >= dp(38f)) swipeDetected = true
                    // Track the path before the swipe threshold is crossed too; otherwise
                    // early letters can be lost when the gesture becomes a swipe later.
                    if (swipeGestureEligible && swipeEnabled && mode == Mode.ENGLISH && !symbols && !emojiMode && !clipboardMode) {
                        appendSwipeSegment(lastTouchX, lastTouchY, event.x, event.y)
                    }
                }
                // Preserve the previous point until the full segment has been sampled.
                lastTouchX = event.x
                lastTouchY = event.y
                // Pointer events can arrive faster than display frames. Redraw only
                // when the pressed-key highlight actually changes, not for every move.
                val isPressedKeyHighlighted =
                    bounds.getOrNull(downKeyIndex)?.contains(lastTouchX, lastTouchY) == true
                if (wasPressedKeyHighlighted != isPressedKeyHighlighted) invalidate()
                if (deleteRepeatRunnable != null && bounds.getOrNull(downKeyIndex)?.contains(event.x, event.y) != true) cancelDeleteRepeat()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelDeleteRepeat()
                lastShiftTapAt = 0L
                // A second finger can otherwise turn one gesture into an accidental key press.
                // Cancel the entire gesture until every pointer is lifted.
                multiTouchCancelled = true
                downKeyIndex = -1
                downSuggestionIndex = -1
                invalidate()
                spaceCursorGesture = false
                spaceCursorMoved = false
                cursorMoveRemainder = 0f
                resetSwipeTracking()
            }
            MotionEvent.ACTION_UP -> {
                cancelDeleteRepeat()
                if (multiTouchCancelled) {
                    multiTouchCancelled = false
                    downKeyIndex = -1
                    downSuggestionIndex = -1
                    invalidate()
                    spaceCursorGesture = false
                    spaceCursorMoved = false
                    cursorMoveRemainder = 0f
                    resetSwipeTracking()
                    return true
                }
                if (spaceCursorGesture) {
                    cursorMoveRemainder += event.x - lastTouchX
                    val stepPx = dp(SpacebarCursorPolicy.STEP_DP)
                    val steps = SpacebarCursorPolicy.stepsForDistance(cursorMoveRemainder, stepPx)
                    if (steps != 0) {
                        val action = if (steps < 0) "CURSOR_LEFT" else "CURSOR_RIGHT"
                        repeat(if (steps < 0) -steps else steps) { listener.onKey(action) }
                        cursorMoveRemainder -= steps * stepPx
                        spaceCursorMoved = true
                    }
                }
                if (spaceCursorGesture && spaceCursorMoved) {
                    downKeyIndex = -1
                    downSuggestionIndex = -1
                    invalidate()
                    spaceCursorGesture = false
                    spaceCursorMoved = false
                    cursorMoveRemainder = 0f
                    resetSwipeTracking()
                    performClick()
                    return true
                }
                // Include the final segment in distance detection too. Some devices may
                // deliver DOWN and UP without an intermediate MOVE for a very fast swipe.
                val finalDx = event.x - lastTouchX
                val finalDy = event.y - lastTouchY
                swipeTravel += Math.hypot(finalDx.toDouble(), finalDy.toDouble()).toFloat()
                if (swipeTravel >= dp(38f)) swipeDetected = true
                // ACTION_UP may be the only event delivered inside the final key.
                // Capture it before matching so the last letter is not silently lost.
                if (swipeGestureEligible && swipeEnabled && swipeDetected && mode == Mode.ENGLISH && !symbols && !emojiMode && !clipboardMode) {
                    appendSwipeSegment(lastTouchX, lastTouchY, event.x, event.y)
                }
                if (swipeEnabled && swipeDetected && swipePath.length >= 3) {
                    listener.onKey("SWIPE:$swipePath")
                    downKeyIndex = -1
                    downSuggestionIndex = -1
                    invalidate()
                    resetSwipeTracking()
                    performClick()
                    return true
                }
                val suggestionIndex = suggestionBounds.indexOfFirst { it.contains(event.x, event.y) }
                if (suggestionIndex >= 0 && suggestionIndex == downSuggestionIndex) {
                    suggestions.getOrNull(suggestionIndex)?.let { listener.onKey("SUGGEST:$it") }
                } else {
                    val index = bounds.indexOfFirst { it.contains(event.x, event.y) }
                    if (index >= 0 && index == downKeyIndex) {
                        if (keySound) playSoundEffect(SoundEffectConstants.CLICK)
                        if (keyHaptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        flatKeys.getOrNull(index)?.let { key ->
                            if (key.value == "SHIFT") {
                                val now = SystemClock.elapsedRealtime()
                                if (ShiftTapPolicy.isDoubleTap(lastShiftTapAt, now)) {
                                    listener.onKey("CAPS_LOCK_TOGGLE")
                                    lastShiftTapAt = 0L
                                } else {
                                    listener.onKey("SHIFT")
                                    lastShiftTapAt = now
                                }
                            } else {
                                lastShiftTapAt = 0L
                                // Backspace repeat already deleted while held; do not delete again on release.
                                if (!(key.value == "DELETE" && deleteRepeatFired)) {
                                    val held = SystemClock.elapsedRealtime() - downTime >= 450L
                                    listener.onKey(if (held && key.longPressValue != null) "LONG:${key.longPressValue}" else key.value)
                                }
                            }
                        }
                    }
                }
                downKeyIndex = -1
                downSuggestionIndex = -1
                invalidate()
                spaceCursorGesture = false
                spaceCursorMoved = false
                cursorMoveRemainder = 0f
                resetSwipeTracking()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelDeleteRepeat()
                lastShiftTapAt = 0L
                multiTouchCancelled = false
                downKeyIndex = -1
                downSuggestionIndex = -1
                invalidate()
                spaceCursorGesture = false
                spaceCursorMoved = false
                cursorMoveRemainder = 0f
                resetSwipeTracking()
                return true
            }
        }
        return true
    }

    /**
     * Sample the full pointer segment so fast moves or sparse MotionEvent delivery
     * do not skip letters between two reported touch positions.
     */
    private fun appendSwipeSegment(startX: Float, startY: Float, endX: Float, endY: Float) {
        val dx = endX - startX
        val dy = endY - startY
        val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val stepSize = dp(8f).coerceAtLeast(1f)
        // Bound work for unusually large MotionEvent jumps on slow devices.
        val steps = kotlin.math.ceil(distance / stepSize).toInt().coerceIn(1, 64)
        for (step in 1..steps) {
            if (swipePath.length >= 32) return
            val fraction = step.toFloat() / steps
            appendSwipeKey(startX + dx * fraction, startY + dy * fraction)
        }
    }

    private fun appendSwipeKey(x: Float, y: Float) {
        val index = bounds.indexOfFirst { it.contains(x, y) }
        if (index < 0 || index == lastSwipeKeyIndex) return
        val value = flatKeys.getOrNull(index)?.value ?: return
        if (value.length == 1 && value[0] in 'a'..'z') {
            if (swipePath.length < 32) swipePath.append(value)
            lastSwipeKeyIndex = index
        }
    }

    private fun longPressFor(ch: Char): String? = when (ch.lowercaseChar()) {
        'q' -> "1"; 'w' -> "2"; 'e' -> "3"; 'r' -> "4"; 't' -> "5"
        'y' -> "6"; 'u' -> "7"; 'i' -> "8"; 'o' -> "9"; 'p' -> "0"
        'a' -> "@"; 's' -> "#"; 'd' -> "\$"; 'f' -> "%"; 'g' -> "&"
        'h' -> "-"; 'j' -> "+"; 'k' -> "("; 'l' -> ")"
        'z' -> "!"; 'x' -> "?"; 'c' -> ":"; 'v' -> ";"; 'b' -> "\\"; 'n' -> "'"; 'm' -> "\""
        else -> null
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
