package com.swordfish.chimeroid.common.kotlin

fun <K, V> Map<K, V>.reverseLookup(): Map<V, K> {
    return entries.associateBy({ it.value }, { it.key })
}
