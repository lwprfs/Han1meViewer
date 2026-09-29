package com.yenaly.yenaly_libs.base.settings

import android.os.Bundle
import androidx.databinding.DataBindingUtil
import com.yenaly.yenaly_libs.R
import com.yenaly.yenaly_libs.base.frame.FrameActivity
import com.yenaly.yenaly_libs.databinding.YenalySettingsDataBinding

abstract class YenalySettingsActivity : FrameActivity() {

    lateinit var binding: YenalySettingsDataBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.yenaly_activity_settings)
        binding.lifecycleOwner = this
        supportActionBar?.hide()
        setSupportActionBar(binding.settingsToolbar)
        binding.settingsToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container_view, initFragmentContainer())
                .commit()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::binding.isInitialized) {
            binding.unbind()
        }
    }

    abstract fun initFragmentContainer(): YenalySettingsFragment
}
