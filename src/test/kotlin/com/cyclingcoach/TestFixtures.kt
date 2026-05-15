package com.cyclingcoach

import java.nio.charset.Charset

fun readFixture(path: String, charset: Charset = Charsets.UTF_8): String =
    checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(path.trimStart('/'))) {
        "Test fixture not found: $path"
    }.reader(charset).readText()
