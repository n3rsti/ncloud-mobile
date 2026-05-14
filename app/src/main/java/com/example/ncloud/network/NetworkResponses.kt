package com.example.ncloud.network

internal data class RawResponse(
    val code: Int,
    val body: String
)

internal data class RawBytesResponse(
    val code: Int,
    val bytes: ByteArray,
    val errorBody: String
)
