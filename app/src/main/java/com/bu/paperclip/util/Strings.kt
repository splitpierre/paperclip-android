package com.bu.paperclip.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** String resources for code that is not @Composable (workers, coroutines, models). */
@SuppressLint("StaticFieldLeak") // only ever the application context
object AppText {
    lateinit var context: Context

    /** Unit tests swap these for a reader of values/strings.xml; the app reads from [context]. */
    var string: (id: Int, args: Array<out Any?>) -> String = { id, args ->
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    }
    var plural: (id: Int, count: Int, args: Array<out Any?>) -> String = { id, count, args ->
        context.resources.getQuantityString(id, count, *args)
    }
}

/** Like stringResource, for code that is not @Composable. */
fun appString(@StringRes id: Int, vararg args: Any?): String = AppText.string(id, args)

/** Like pluralStringResource, for code that is not @Composable. */
fun appPlural(@PluralsRes id: Int, count: Int, vararg args: Any?): String = AppText.plural(id, count, args)
