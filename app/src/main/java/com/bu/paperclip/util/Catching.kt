package com.bu.paperclip.util

import kotlinx.coroutines.CancellationException

/** Unlike kotlin.runCatching, cancellation is control flow, never a recoverable error. */
inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
