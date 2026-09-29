package com.yenaly.yenaly_libs.base

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.yenaly.yenaly_libs.R

abstract class YenalyBottomSheetDialogFragment<DB : ViewDataBinding> :
    BottomSheetDialogFragment(), IViewBinding<DB> {

    private var _binding: DB? = null
    override val binding get() = _binding!!

    final override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): DB {
        return getViewBinding(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val layoutInflater = LayoutInflater.from(context)
        _binding = getViewBinding(layoutInflater, null)
        dialog.setContentView(binding.root)
        initData(savedInstanceState, dialog)
        return dialog
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding?.unbind()
    }

    open fun setStyle() {
        setStyle(STYLE_NORMAL, R.style.YenalyBottomSheetDialog)
    }

    abstract fun getViewBinding(layoutInflater: LayoutInflater): DB

    abstract fun initData(savedInstanceState: Bundle?, dialog: Dialog)

    fun showIn(fragment: Fragment) {

        val tag = this.javaClass.name
        val fragmentManager = fragment.childFragmentManager
        if (fragmentManager.findFragmentByTag(tag) != null) {
            return
        }
        show(fragmentManager, tag)
    }

    fun showIn(activity: FragmentActivity) {
        val fragmentManager = activity.supportFragmentManager
        if (fragmentManager.findFragmentByTag(this.javaClass.name) != null) {
            return
        }
        show(fragmentManager, this.javaClass.name)
    }
}
