package com.bu.paperclip

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.DraftStore
import com.bu.paperclip.data.Drafts
import com.bu.paperclip.data.SettingsStore
import com.bu.paperclip.net.Api
import com.bu.paperclip.notify.NotifyWorker
import com.bu.paperclip.ui.components.FileOpener
import com.bu.paperclip.util.L10n
import kotlinx.coroutines.launch

class PaperclipApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        L10n.init(this)
        AppState.settings = SettingsStore(this)
        Drafts.store = DraftStore(java.io.File(filesDir, "drafts"))
        FileOpener.init(this)
        NotifyWorker.ensureChannel(this)
        AppState.scope.launch {
            val s = AppState.settings.current()
            if (s.baseUrl.isNotEmpty()) Api.configure(s.baseUrl, s.apiToken)
            NotifyWorker.schedule(this@PaperclipApp, s.notifications)
        }
    }

    /** Images (avatars, attachments) go through the cookie-carrying client. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Api.mediaClient)
        .crossfade(true)
        .build()
}
