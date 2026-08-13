package com.swordfish.chimeroid.common.kotlin

data class NTuple2<T1, T2>(val t1: T1, val t2: T2)

data class NTuple4<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)

fun Long.toStringCRC32(): String {
    return "%08x".format(this).uppercase()
}

tailrec suspend fun <T> runCatchingWithRetry(
    retries: Int,
    block: suspend () -> T,
): Result<T> {
    require(retries >= 1)
    val result = runCatching { block() }
    return when {
        retries == 1 -> result
        result.isSuccess -> result
        else -> runCatchingWithRetry(retries - 1, block)
    }
}
