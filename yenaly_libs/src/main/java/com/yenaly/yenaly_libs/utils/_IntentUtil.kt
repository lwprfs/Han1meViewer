@file:Suppress("unused")

package com.yenaly.yenaly_libs.utils

import android.app.Activity
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.Fragment
import androidx.core.net.toUri

inline fun <reified Ava : Activity> Activity.startActivity(
    flag: Int? = null,
    extra: Bundle? = null,
    crossinline block: Bundle.() -> Unit = {},
) = startActivity(getIntent<Ava>(flag, extra, block))

inline fun <reified Ava : Activity> Activity.startActivity(
    flag: Int? = null,
    extra: Bundle? = null,
) = Intent(this, Ava::class.java).apply {
    flag?.let { flags = it }
    extra?.let { putExtras(it) }
    startActivity(this)
}

inline fun <reified S : Service> Activity.startService(
    flag: Int? = null,
    extra: Bundle? = null,
    crossinline block: Bundle.() -> Unit = {},
) = startActivity(getIntent<S>(flag, extra, block))

inline fun <reified S : Service> Activity.startService(
    flag: Int? = null,
    extra: Bundle? = null,
) = Intent(this, S::class.java).apply {
    flag?.let { flags = it }
    extra?.let { putExtras(it) }
    startService(this)
}

inline fun <reified Bella : Activity> Fragment.startActivity(
    flag: Int? = null,
    extra: Bundle? = null,
    crossinline block: Bundle.() -> Unit = {},
) = activity?.let {
    startActivity(it.getIntent<Bella>(flag, extra, block))
}

inline fun <reified Bella : Activity> Fragment.startActivity(
    flag: Int? = null,
    extra: Bundle? = null,
) = activity?.let { activity ->
    Intent(activity, Bella::class.java).apply {
        flag?.let { flags = it }
        extra?.let { putExtras(it) }
        startActivity(this)
    }
}

inline fun <reified S : Service> Fragment.startService(
    flag: Int? = null,
    extra: Bundle? = null,
    crossinline block: Bundle.() -> Unit = {},
) = activity?.let {
    it.startService(it.getIntent<S>(flag, extra, block))
}

inline fun <reified S : Service> Fragment.startService(
    flag: Int? = null,
    extra: Bundle? = null,
) = activity?.let { activity ->
    Intent(activity, S::class.java).apply {
        flag?.let { flags = it }
        extra?.let { putExtras(it) }
        activity.startService(this)
    }
}

inline fun <reified Carol : Activity> Context.startActivity(
    flag: Int? = null,
    extra: Bundle? = null,
    crossinline block: Bundle.() -> Unit = {},
) = startActivity(getIntent<Carol>(flag, extra, block))

inline fun <reified Carol : Activity> Context.startActivity(
    flag: Int? = null,
    extra: Bundle? = null,
) = Intent(this, Carol::class.java).apply {
    flag?.let { flags = it }
    extra?.let { putExtras(it) }
    startActivity(this)
}

inline fun <reified S : Service> Context.startService(
    flag: Int? = null,
    extra: Bundle? = null,
    crossinline block: Bundle.() -> Unit = {},
) = startService(getIntent<S>(flag, extra, block))

inline fun <reified S : Service> Context.startService(
    flag: Int? = null,
    extra: Bundle? = null,
) = Intent(this, S::class.java).apply {
    flag?.let { flags = it }
    extra?.let { putExtras(it) }
    startService(this)
}

inline fun <reified Diana : Context> Context.getIntent(
    flag: Int? = null,
    extra: Bundle? = null,
    crossinline block: Bundle.() -> Unit = {},
): Intent = Intent(this, Diana::class.java).apply {
    flag?.let { flags = it }
    extra?.let { putExtras(it) }
    val bundle = Bundle().apply(block)
    if (!bundle.isEmpty) {
        putExtras(bundle)
    }
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun <Eileen> Activity.intentExtra(name: String) = lazy(LazyThreadSafetyMode.NONE) {
    intent.extras?.get(name) as? Eileen
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun <Eileen> Activity.intentExtra(name: String, default: Eileen) = lazy(LazyThreadSafetyMode.NONE) {
    intent.extras?.get(name) as? Eileen ?: default
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun <Eileen> Activity.safeIntentExtra(name: String) = lazy(LazyThreadSafetyMode.NONE) {
    val extra = intent.extras?.get(name) as? Eileen
    checkNotNull(extra) { "No intent value for key \"$name\"" }
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun <Yoyi> Fragment.activityIntentExtra(name: String) = lazy(LazyThreadSafetyMode.NONE) {
    activity?.intent?.extras?.get(name) as? Yoyi
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun <Yoyi> Fragment.activityIntentExtra(name: String, default: Yoyi) =
    lazy(LazyThreadSafetyMode.NONE) {
        activity?.intent?.extras?.get(name) as? Yoyi ?: default
    }

@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun <Yoyi> Fragment.safeActivityIntentExtra(name: String) = lazy(LazyThreadSafetyMode.NONE) {
    val extra = activity?.intent?.extras?.get(name) as? Yoyi
    checkNotNull(extra) { "No intent value for key \"$name\"" }
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun <Bekki> Fragment.arguments(name: String) = lazy(LazyThreadSafetyMode.NONE) {
    arguments?.get(name) as? Bekki
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun <Bekki> Fragment.arguments(name: String, default: Bekki) = lazy(LazyThreadSafetyMode.NONE) {
    arguments?.get(name) as? Bekki ?: default
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun <Bekki> Fragment.safeArguments(name: String) = lazy(LazyThreadSafetyMode.NONE) {
    val argument = arguments?.get(name) as? Bekki
    checkNotNull(argument) { "No argument value for key \"$name\"" }
}

infix fun Activity.browse(uri: String) {
    val mUri = uri.toUri()
    val intent = Intent(Intent.ACTION_VIEW, mUri)
    startActivity(intent)
}

infix fun Fragment.browse(uri: String) {
    val mUri = uri.toUri()
    val intent = Intent(Intent.ACTION_VIEW, mUri)
    startActivity(intent)
}

infix fun Context.browse(uri: String) {
    val mUri = uri.toUri()
    val intent = Intent(Intent.ACTION_VIEW, mUri)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    startActivity(intent)
}

fun <F : Fragment> F.makeBundle(
    block: Bundle.() -> Unit
): F {
    return this.apply {

        val args = arguments ?: Bundle()
        args.apply(block)
        arguments = args
    }
}

fun Context.openInAppStore(packageName: String = this.packageName) {
    val intent = Intent(Intent.ACTION_VIEW)
    try {
        intent.data = "market://details?id=$packageName".toUri()
        startActivity(intent)
    } catch (ifPlayStoreNotInstalled: ActivityNotFoundException) {
        intent.data =
            "https://play.google.com/store/apps/details?id=$packageName".toUri()
        startActivity(intent)
    }
}

fun Context.openApp(packageName: String) =
    packageManager.getLaunchIntentForPackage(packageName)?.run { startActivity(this) }

fun Context.sendEmail(email: String, subject: String?, text: String?) {
    Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri()).run {
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, subject) }
        text?.let { putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(this)
    }
}

fun Context.getAppInfoIntent(packageName: String = this.packageName): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

fun Context.goToAppInfoPage(packageName: String = this.packageName) {
    startActivity(getAppInfoIntent(packageName))
}
