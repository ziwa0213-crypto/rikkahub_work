package me.rerere.rikkahub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.service.LocalModelIdleWatcher
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class LocalModelUnloadReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        get<AppScope>().launch {
            try { get<LocalModelIdleWatcher>().requestUnload(intent.getStringExtra("modelId")) }
            finally { result.finish() }
        }
    }
}
