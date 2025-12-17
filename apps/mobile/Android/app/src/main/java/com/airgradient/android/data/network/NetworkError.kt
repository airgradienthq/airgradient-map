package com.airgradient.android.data.network

import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class NetworkError : Exception() {
    object NoInternetConnection : NetworkError()
    object Timeout : NetworkError()
    data class ServerError(val code: Int) : NetworkError()
    data class UnknownError(val throwable: Throwable) : NetworkError()
    
    companion object {
        fun from(throwable: Throwable): NetworkError {
            return when (throwable) {
                is UnknownHostException -> NoInternetConnection
                is SocketTimeoutException -> Timeout
                is retrofit2.HttpException -> {
                    ServerError(throwable.code())
                }
                else -> UnknownError(throwable)
            }
        }
    }
}

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val error: NetworkError) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(data)
    return this
}

inline fun <T> ApiResult<T>.onError(action: (NetworkError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) action(error)
    return this
}

inline fun <T> ApiResult<T>.onLoading(action: () -> Unit): ApiResult<T> {
    if (this is ApiResult.Loading) action()
    return this
}