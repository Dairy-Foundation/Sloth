package dev.frozenmilk.sinister.sdk

import java.util.Collections

internal class FalseSingletonSet<T>(member: T) : MutableSet<T> by Collections.singleton(member) {
    override fun add(element: T) = false
    override fun remove(element: T) = false
}