package com.yenaly.han1meviewer.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yenaly.han1meviewer.logic.update.UpdateManager

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == HUpdateWorker.ACTION_DISCARD_UPDATE) {
            UpdateManager.discard(context)
        }
    }
}
