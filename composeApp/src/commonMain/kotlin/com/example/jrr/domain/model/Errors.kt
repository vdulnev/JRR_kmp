package com.example.jrr.domain.model

sealed interface DomainError {
    val message: String
}

sealed interface McwsError : DomainError {
    data class Network(override val message: String, val cause: Throwable? = null) : McwsError
    data class HttpStatus(val status: Int, override val message: String) : McwsError
    data class Parse(override val message: String, val cause: Throwable? = null) : McwsError
    data class Auth(override val message: String) : McwsError
    data class Unknown(override val message: String, val cause: Throwable? = null) : McwsError
}

sealed interface SettingsError : DomainError {
    data class Read(override val message: String, val cause: Throwable? = null) : SettingsError
    data class Write(override val message: String, val cause: Throwable? = null) : SettingsError
    data object Missing : SettingsError {
        override val message: String = "Setting not present"
    }
}

sealed interface LookupError : DomainError {
    data class Network(override val message: String, val cause: Throwable? = null) : LookupError
    data class Parse(override val message: String, val cause: Throwable? = null) : LookupError
    data class NotFound(override val message: String) : LookupError
}
