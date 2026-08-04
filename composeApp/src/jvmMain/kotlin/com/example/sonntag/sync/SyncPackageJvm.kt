package com.example.sonntag.sync

import java.util.Base64

actual fun base64Encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

actual fun base64Decode(text: String): ByteArray = Base64.getDecoder().decode(text)
