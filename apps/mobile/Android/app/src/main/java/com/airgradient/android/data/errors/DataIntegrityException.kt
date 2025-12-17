package com.airgradient.android.data.errors

class DataIntegrityException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

