package com.yenaly.yenaly_libs.base.settings

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.annotation.XmlRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialSharedAxis
import com.yenaly.yenaly_libs.utils.unsafeLazy

abstract class YenalySettingsFragment(@param:XmlRes private val xmlRes: Int,
                                      private val sharedPrefsName: String? = null) :
    PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (sharedPrefsName != null) {
            preferenceManager.sharedPreferencesName = sharedPrefsName
        }
        setPreferencesFromResource(xmlRes, rootKey)
        initPreferencesVariable()
        onPreferencesCreated(savedInstanceState)
        bindDataObservers()
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun setDivider(divider: Drawable?) {
        super.setDivider(null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backgroundColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface)
        view.setBackgroundColor(backgroundColor)
    }

    open fun bindDataObservers() = Unit

    open fun initPreferencesVariable() = Unit

    abstract fun onPreferencesCreated(savedInstanceState: Bundle?)

    fun <T : Preference> preference(key: String) = unsafeLazy { findPreference<T>(key) }

    fun <T : Preference> safePreference(key: String) = unsafeLazy {
        checkNotNull(findPreference<T>(key)) {
            "The preference belonged to the key \"$key\" is null."
        }
    }
}
