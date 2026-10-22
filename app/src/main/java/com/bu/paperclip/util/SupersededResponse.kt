package com.bu.paperclip.util

/** The response lost its session/company owner; the coroutine itself is still active. */
class SupersededResponse : Exception("Response belongs to an earlier session or company")
