package com.example.sonntag.net

internal actual fun beforeStart() = MulticastLock.acquire()

internal actual fun afterStop() = MulticastLock.release()
