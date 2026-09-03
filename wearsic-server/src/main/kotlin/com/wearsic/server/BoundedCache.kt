package com.wearsic.server

import java.util.Collections
import java.util.LinkedHashMap

/**
 * Simple thread-safe, size-bounded, access-order LRU cache.
 * No TTL of its own — callers wrap entries (e.g. CachedStreamTarget) with
 * their own expiry timestamp when TTL semantics are needed.
 */
class BoundedCache<K, V>(private val maxSize: Int) {

    private val map: MutableMap<K, V> = Collections.synchronizedMap(
        object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
                size > maxSize
        }
    )

    fun get(key: K): V? = map[key]

    fun put(key: K, value: V) {
        map[key] = value
    }

    fun getOrPut(key: K, compute: () -> V): V =
        map[key] ?: compute().also { map[key] = it }

    fun remove(key: K) {
        map.remove(key)
    }

    fun clear() = map.clear()

    val size: Int get() = map.size
}
