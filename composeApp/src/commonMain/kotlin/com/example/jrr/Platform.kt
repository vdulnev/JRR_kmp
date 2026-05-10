package com.example.jrr

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform