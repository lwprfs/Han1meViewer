package com.yenaly.han1meviewer.HentaiMama.settings

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences

object HentaiMamaCardSettings {

    const val MIN_CARD_MULTIPLIER = 0.5f
    const val MAX_CARD_MULTIPLIER = 10.0f
    const val DEFAULT_CARD_MULTIPLIER = 1.46f

    const val MIN_HEIGHT_MULTIPLIER = 0.5f
    const val MAX_HEIGHT_MULTIPLIER = 10.0f
    const val DEFAULT_HEIGHT_MULTIPLIER = 2.5f

    const val MIN_WIDTH_MULTIPLIER = 0.5f
    const val MAX_WIDTH_MULTIPLIER = 5.0f
    const val DEFAULT_WIDTH_MULTIPLIER = 1.0f

    const val DEFAULT_RECENT_CARD_MULTIPLIER = DEFAULT_CARD_MULTIPLIER
    const val DEFAULT_RECENT_HEIGHT_MULTIPLIER = DEFAULT_HEIGHT_MULTIPLIER
    const val DEFAULT_RECENT_WIDTH_MULTIPLIER = DEFAULT_WIDTH_MULTIPLIER

    const val DEFAULT_UPCOMING_CARD_MULTIPLIER = DEFAULT_CARD_MULTIPLIER
    const val DEFAULT_UPCOMING_HEIGHT_MULTIPLIER = DEFAULT_HEIGHT_MULTIPLIER
    const val DEFAULT_UPCOMING_WIDTH_MULTIPLIER = DEFAULT_WIDTH_MULTIPLIER

    private const val PREF_CARD_MULT = "hentaimama_card_count_multiplier"
    private const val PREF_HEIGHT_MULT = "hentaimama_card_height_multiplier"
    private const val PREF_WIDTH_MULT = "hentaimama_card_width_multiplier"

    private const val PREF_RECENT_CARD_MULT = "hentaimama_recent_card_count_multiplier"
    private const val PREF_RECENT_HEIGHT_MULT = "hentaimama_recent_card_height_multiplier"
    private const val PREF_RECENT_WIDTH_MULT = "hentaimama_recent_card_width_multiplier"

    private const val PREF_UPCOMING_CARD_MULT = "hentaimama_upcoming_card_count_multiplier"
    private const val PREF_UPCOMING_HEIGHT_MULT = "hentaimama_upcoming_card_height_multiplier"
    private const val PREF_UPCOMING_WIDTH_MULT = "hentaimama_upcoming_card_width_multiplier"

    private val _cardMultiplier = mutableFloatStateOf(DEFAULT_CARD_MULTIPLIER)
    private val _heightMultiplier = mutableFloatStateOf(DEFAULT_HEIGHT_MULTIPLIER)
    private val _widthMultiplier = mutableFloatStateOf(DEFAULT_WIDTH_MULTIPLIER)

    private val _recentCardMultiplier = mutableFloatStateOf(DEFAULT_RECENT_CARD_MULTIPLIER)
    private val _recentHeightMultiplier = mutableFloatStateOf(DEFAULT_RECENT_HEIGHT_MULTIPLIER)
    private val _recentWidthMultiplier = mutableFloatStateOf(DEFAULT_RECENT_WIDTH_MULTIPLIER)

    private val _upcomingCardMultiplier = mutableFloatStateOf(DEFAULT_UPCOMING_CARD_MULTIPLIER)
    private val _upcomingHeightMultiplier = mutableFloatStateOf(DEFAULT_UPCOMING_HEIGHT_MULTIPLIER)
    private val _upcomingWidthMultiplier = mutableFloatStateOf(DEFAULT_UPCOMING_WIDTH_MULTIPLIER)

    private val _effectiveAspectRatio = mutableFloatStateOf(
        DEFAULT_WIDTH_MULTIPLIER / ((9f / 16f) * DEFAULT_HEIGHT_MULTIPLIER)
    )
    private val _recentEffectiveAspectRatio = mutableFloatStateOf(
        DEFAULT_RECENT_WIDTH_MULTIPLIER / ((9f / 16f) * DEFAULT_RECENT_HEIGHT_MULTIPLIER)
    )
    private val _upcomingEffectiveAspectRatio = mutableFloatStateOf(
        DEFAULT_UPCOMING_WIDTH_MULTIPLIER / ((9f / 16f) * DEFAULT_UPCOMING_HEIGHT_MULTIPLIER)
    )

    @Volatile private var hasLoaded = false

    val cardMultiplierState: MutableState<Float> get() = _cardMultiplier
    val heightMultiplierState: MutableState<Float> get() = _heightMultiplier
    val widthMultiplierState: MutableState<Float> get() = _widthMultiplier
    val effectiveAspectRatioState: MutableState<Float> get() = _effectiveAspectRatio

    val recentCardMultiplierState: MutableState<Float> get() = _recentCardMultiplier
    val recentHeightMultiplierState: MutableState<Float> get() = _recentHeightMultiplier
    val recentWidthMultiplierState: MutableState<Float> get() = _recentWidthMultiplier
    val recentEffectiveAspectRatioState: MutableState<Float> get() = _recentEffectiveAspectRatio

    val upcomingCardMultiplierState: MutableState<Float> get() = _upcomingCardMultiplier
    val upcomingHeightMultiplierState: MutableState<Float> get() = _upcomingHeightMultiplier
    val upcomingWidthMultiplierState: MutableState<Float> get() = _upcomingWidthMultiplier
    val upcomingEffectiveAspectRatioState: MutableState<Float> get() = _upcomingEffectiveAspectRatio

    var cardMultiplier: Float
        get() = _cardMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_CARD_MULTIPLIER, MAX_CARD_MULTIPLIER)
            _cardMultiplier.floatValue = clamped
            recomputeAspectRatio()
            Preferences.preferenceSp.edit { putFloat(PREF_CARD_MULT, clamped) }
        }

    var heightMultiplier: Float
        get() = _heightMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_HEIGHT_MULTIPLIER, MAX_HEIGHT_MULTIPLIER)
            _heightMultiplier.floatValue = clamped
            recomputeAspectRatio()
            Preferences.preferenceSp.edit { putFloat(PREF_HEIGHT_MULT, clamped) }
        }

    var widthMultiplier: Float
        get() = _widthMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_WIDTH_MULTIPLIER, MAX_WIDTH_MULTIPLIER)
            _widthMultiplier.floatValue = clamped
            recomputeAspectRatio()
            Preferences.preferenceSp.edit { putFloat(PREF_WIDTH_MULT, clamped) }
        }

    var recentCardMultiplier: Float
        get() = _recentCardMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_CARD_MULTIPLIER, MAX_CARD_MULTIPLIER)
            _recentCardMultiplier.floatValue = clamped
            recomputeRecentAspectRatio()
            Preferences.preferenceSp.edit { putFloat(PREF_RECENT_CARD_MULT, clamped) }
        }

    var recentHeightMultiplier: Float
        get() = _recentHeightMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_HEIGHT_MULTIPLIER, MAX_HEIGHT_MULTIPLIER)
            _recentHeightMultiplier.floatValue = clamped
            recomputeRecentAspectRatio()
            Preferences.preferenceSp.edit { putFloat(PREF_RECENT_HEIGHT_MULT, clamped) }
        }

    var recentWidthMultiplier: Float
        get() = _recentWidthMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_WIDTH_MULTIPLIER, MAX_WIDTH_MULTIPLIER)
            _recentWidthMultiplier.floatValue = clamped
            recomputeRecentAspectRatio()
            Preferences.preferenceSp.edit { putFloat(PREF_RECENT_WIDTH_MULT, clamped) }
        }

    var upcomingCardMultiplier: Float
        get() = _upcomingCardMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_CARD_MULTIPLIER, MAX_CARD_MULTIPLIER)
            _upcomingCardMultiplier.floatValue = clamped
            recomputeUpcomingAspectRatio()
            Preferences.preferenceSp.edit { putFloat(PREF_UPCOMING_CARD_MULT, clamped) }
        }

    var upcomingHeightMultiplier: Float
        get() = _upcomingHeightMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_HEIGHT_MULTIPLIER, MAX_HEIGHT_MULTIPLIER)
            _upcomingHeightMultiplier.floatValue = clamped
            recomputeUpcomingAspectRatio()
            Preferences.preferenceSp.edit { putFloat(PREF_UPCOMING_HEIGHT_MULT, clamped) }
        }

    var upcomingWidthMultiplier: Float
        get() = _upcomingWidthMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_WIDTH_MULTIPLIER, MAX_WIDTH_MULTIPLIER)
            _upcomingWidthMultiplier.floatValue = clamped
            recomputeUpcomingAspectRatio()
            Preferences.preferenceSp.edit { putFloat(PREF_UPCOMING_WIDTH_MULT, clamped) }
        }

    val effectiveAspectRatio: Float
        get() = _effectiveAspectRatio.floatValue

    val recentEffectiveAspectRatio: Float
        get() = _recentEffectiveAspectRatio.floatValue

    val upcomingEffectiveAspectRatio: Float
        get() = _upcomingEffectiveAspectRatio.floatValue

    private fun recomputeAspectRatio() {
        val baseHeightPerWidth = 9f / 16f
        _effectiveAspectRatio.floatValue =
            _widthMultiplier.floatValue / (baseHeightPerWidth * _heightMultiplier.floatValue)
    }

    private fun recomputeRecentAspectRatio() {
        val baseHeightPerWidth = 9f / 16f
        _recentEffectiveAspectRatio.floatValue =
            _recentWidthMultiplier.floatValue / (baseHeightPerWidth * _recentHeightMultiplier.floatValue)
    }

    private fun recomputeUpcomingAspectRatio() {
        val baseHeightPerWidth = 9f / 16f
        _upcomingEffectiveAspectRatio.floatValue =
            _upcomingWidthMultiplier.floatValue / (baseHeightPerWidth * _upcomingHeightMultiplier.floatValue)
    }

    val widthScaleFromCards: Float
        get() = 1f / cardMultiplier

    fun effectiveCardCount(baseCount: Float): Float =
        (baseCount * cardMultiplier).coerceAtLeast(1f)

    fun effectiveRecentCardCount(baseCount: Float): Float =
        (baseCount * recentCardMultiplier).coerceAtLeast(1f)

    fun effectiveUpcomingCardCount(baseCount: Float): Float =
        (baseCount * upcomingCardMultiplier).coerceAtLeast(1f)

    fun load() {
        if (hasLoaded) return
        hasLoaded = true

        _cardMultiplier.floatValue = Preferences.preferenceSp
            .getFloat(PREF_CARD_MULT, DEFAULT_CARD_MULTIPLIER)
            .coerceIn(MIN_CARD_MULTIPLIER, MAX_CARD_MULTIPLIER)

        _heightMultiplier.floatValue = Preferences.preferenceSp
            .getFloat(PREF_HEIGHT_MULT, DEFAULT_HEIGHT_MULTIPLIER)
            .coerceIn(MIN_HEIGHT_MULTIPLIER, MAX_HEIGHT_MULTIPLIER)

        _widthMultiplier.floatValue = Preferences.preferenceSp
            .getFloat(PREF_WIDTH_MULT, DEFAULT_WIDTH_MULTIPLIER)
            .coerceIn(MIN_WIDTH_MULTIPLIER, MAX_WIDTH_MULTIPLIER)

        _recentCardMultiplier.floatValue = Preferences.preferenceSp
            .getFloat(PREF_RECENT_CARD_MULT, DEFAULT_RECENT_CARD_MULTIPLIER)
            .coerceIn(MIN_CARD_MULTIPLIER, MAX_CARD_MULTIPLIER)

        _recentHeightMultiplier.floatValue = Preferences.preferenceSp
            .getFloat(PREF_RECENT_HEIGHT_MULT, DEFAULT_RECENT_HEIGHT_MULTIPLIER)
            .coerceIn(MIN_HEIGHT_MULTIPLIER, MAX_HEIGHT_MULTIPLIER)

        _recentWidthMultiplier.floatValue = Preferences.preferenceSp
            .getFloat(PREF_RECENT_WIDTH_MULT, DEFAULT_RECENT_WIDTH_MULTIPLIER)
            .coerceIn(MIN_WIDTH_MULTIPLIER, MAX_WIDTH_MULTIPLIER)

        _upcomingCardMultiplier.floatValue = Preferences.preferenceSp
            .getFloat(PREF_UPCOMING_CARD_MULT, DEFAULT_UPCOMING_CARD_MULTIPLIER)
            .coerceIn(MIN_CARD_MULTIPLIER, MAX_CARD_MULTIPLIER)

        _upcomingHeightMultiplier.floatValue = Preferences.preferenceSp
            .getFloat(PREF_UPCOMING_HEIGHT_MULT, DEFAULT_UPCOMING_HEIGHT_MULTIPLIER)
            .coerceIn(MIN_HEIGHT_MULTIPLIER, MAX_HEIGHT_MULTIPLIER)

        _upcomingWidthMultiplier.floatValue = Preferences.preferenceSp
            .getFloat(PREF_UPCOMING_WIDTH_MULT, DEFAULT_UPCOMING_WIDTH_MULTIPLIER)
            .coerceIn(MIN_WIDTH_MULTIPLIER, MAX_WIDTH_MULTIPLIER)

        recomputeAspectRatio()
        recomputeRecentAspectRatio()
        recomputeUpcomingAspectRatio()
    }

    fun applyCardsMultiplier(value: Float) {
        cardMultiplier = value
        heightMultiplier = DEFAULT_HEIGHT_MULTIPLIER
        widthMultiplier = DEFAULT_WIDTH_MULTIPLIER
    }

    fun resetToDefaults() {
        cardMultiplier = DEFAULT_CARD_MULTIPLIER
        heightMultiplier = DEFAULT_HEIGHT_MULTIPLIER
        widthMultiplier = DEFAULT_WIDTH_MULTIPLIER
    }
}
