package com.yenaly.han1meviewer.HentaiMama.settings

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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

    private const val BASE_HEIGHT_PER_WIDTH = 9f / 16f

    private val _cardMultiplier = mutableFloatStateOf(DEFAULT_CARD_MULTIPLIER)
    private val _heightMultiplier = mutableFloatStateOf(DEFAULT_HEIGHT_MULTIPLIER)
    private val _widthMultiplier = mutableFloatStateOf(DEFAULT_WIDTH_MULTIPLIER)

    private val _recentCardMultiplier = mutableFloatStateOf(DEFAULT_RECENT_CARD_MULTIPLIER)
    private val _recentHeightMultiplier = mutableFloatStateOf(DEFAULT_RECENT_HEIGHT_MULTIPLIER)
    private val _recentWidthMultiplier = mutableFloatStateOf(DEFAULT_RECENT_WIDTH_MULTIPLIER)

    private val _upcomingCardMultiplier = mutableFloatStateOf(DEFAULT_UPCOMING_CARD_MULTIPLIER)
    private val _upcomingHeightMultiplier = mutableFloatStateOf(DEFAULT_UPCOMING_HEIGHT_MULTIPLIER)
    private val _upcomingWidthMultiplier = mutableFloatStateOf(DEFAULT_UPCOMING_WIDTH_MULTIPLIER)

    val effectiveAspectRatioState: State<Float> = derivedStateOf {
        _widthMultiplier.floatValue / (BASE_HEIGHT_PER_WIDTH * _heightMultiplier.floatValue)
    }

    val recentEffectiveAspectRatioState: State<Float> = derivedStateOf {
        _recentWidthMultiplier.floatValue /
                (BASE_HEIGHT_PER_WIDTH * _recentHeightMultiplier.floatValue)
    }

    val upcomingEffectiveAspectRatioState: State<Float> = derivedStateOf {
        _upcomingWidthMultiplier.floatValue /
                (BASE_HEIGHT_PER_WIDTH * _upcomingHeightMultiplier.floatValue)
    }

    val cardMultiplierState: MutableState<Float> get() = _cardMultiplier
    val heightMultiplierState: MutableState<Float> get() = _heightMultiplier
    val widthMultiplierState: MutableState<Float> get() = _widthMultiplier

    val recentCardMultiplierState: MutableState<Float> get() = _recentCardMultiplier
    val recentHeightMultiplierState: MutableState<Float> get() = _recentHeightMultiplier
    val recentWidthMultiplierState: MutableState<Float> get() = _recentWidthMultiplier

    val upcomingCardMultiplierState: MutableState<Float> get() = _upcomingCardMultiplier
    val upcomingHeightMultiplierState: MutableState<Float> get() = _upcomingHeightMultiplier
    val upcomingWidthMultiplierState: MutableState<Float> get() = _upcomingWidthMultiplier

    @Volatile private var hasLoaded = false

    var cardMultiplier: Float
        get() = _cardMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_CARD_MULTIPLIER, MAX_CARD_MULTIPLIER)
            _cardMultiplier.floatValue = clamped
            Preferences.preferenceSp.edit { putFloat(PREF_CARD_MULT, clamped) }
        }

    var heightMultiplier: Float
        get() = _heightMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_HEIGHT_MULTIPLIER, MAX_HEIGHT_MULTIPLIER)
            _heightMultiplier.floatValue = clamped
            Preferences.preferenceSp.edit { putFloat(PREF_HEIGHT_MULT, clamped) }
        }

    var widthMultiplier: Float
        get() = _widthMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_WIDTH_MULTIPLIER, MAX_WIDTH_MULTIPLIER)
            _widthMultiplier.floatValue = clamped
            Preferences.preferenceSp.edit { putFloat(PREF_WIDTH_MULT, clamped) }
        }

    var recentCardMultiplier: Float
        get() = _recentCardMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_CARD_MULTIPLIER, MAX_CARD_MULTIPLIER)
            _recentCardMultiplier.floatValue = clamped
            Preferences.preferenceSp.edit { putFloat(PREF_RECENT_CARD_MULT, clamped) }
        }

    var recentHeightMultiplier: Float
        get() = _recentHeightMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_HEIGHT_MULTIPLIER, MAX_HEIGHT_MULTIPLIER)
            _recentHeightMultiplier.floatValue = clamped
            Preferences.preferenceSp.edit { putFloat(PREF_RECENT_HEIGHT_MULT, clamped) }
        }

    var recentWidthMultiplier: Float
        get() = _recentWidthMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_WIDTH_MULTIPLIER, MAX_WIDTH_MULTIPLIER)
            _recentWidthMultiplier.floatValue = clamped
            Preferences.preferenceSp.edit { putFloat(PREF_RECENT_WIDTH_MULT, clamped) }
        }

    var upcomingCardMultiplier: Float
        get() = _upcomingCardMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_CARD_MULTIPLIER, MAX_CARD_MULTIPLIER)
            _upcomingCardMultiplier.floatValue = clamped
            Preferences.preferenceSp.edit { putFloat(PREF_UPCOMING_CARD_MULT, clamped) }
        }

    var upcomingHeightMultiplier: Float
        get() = _upcomingHeightMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_HEIGHT_MULTIPLIER, MAX_HEIGHT_MULTIPLIER)
            _upcomingHeightMultiplier.floatValue = clamped
            Preferences.preferenceSp.edit { putFloat(PREF_UPCOMING_HEIGHT_MULT, clamped) }
        }

    var upcomingWidthMultiplier: Float
        get() = _upcomingWidthMultiplier.floatValue
        set(value) {
            val clamped = value.coerceIn(MIN_WIDTH_MULTIPLIER, MAX_WIDTH_MULTIPLIER)
            _upcomingWidthMultiplier.floatValue = clamped
            Preferences.preferenceSp.edit { putFloat(PREF_UPCOMING_WIDTH_MULT, clamped) }
        }

    val effectiveAspectRatio: Float
        get() = effectiveAspectRatioState.value

    val recentEffectiveAspectRatio: Float
        get() = recentEffectiveAspectRatioState.value

    val upcomingEffectiveAspectRatio: Float
        get() = upcomingEffectiveAspectRatioState.value

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
