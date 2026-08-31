package com.voiceink.app.core

import kotlinx.serialization.json.Json

/** 全局统一 Json：宽容解析，省略 null */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
