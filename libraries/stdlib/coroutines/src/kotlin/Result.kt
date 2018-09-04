/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

// todo: Figure out how to avoid suppressing errors, move suppressions where they are needed.
@file:Suppress(
    "UNCHECKED_CAST",
    "RedundantVisibilityModifier",
    "NON_PUBLIC_PRIMARY_CONSTRUCTOR_OF_INLINE_CLASS",
    "UNSUPPORTED_FEATURE",
    "INVISIBLE_REFERENCE",
    "INVISIBLE_MEMBER",
    "CANNOT_OVERRIDE_INVISIBLE_MEMBER"
)

package kotlin

import kotlin.contracts.*
import kotlin.internal.InlineOnly
import kotlin.jvm.JvmField

/**
 * A discriminated union that encapsulates successful outcome with a value of type [T]
 * or a failure with an arbitrary [Throwable] exception.
 */
public inline class Result<out T> @PublishedApi internal constructor(
    @PublishedApi internal val value: Any?
) : Serializable {
    // discovery

    /**
     * Returns `true` if this instance represents successful outcome.
     * In this case [isFailure] returns `false`.
     */
    public val isSuccess: Boolean get() = value !is Failure

    /**
     * Returns `true` if this instance represents failed outcome.
     * In this case [isSuccess] returns `false`.
     */
    public val isFailure: Boolean get() = value is Failure

    // exception retrieval

    /**
     * Returns the encapsulated exception if this instance represents [failure][isFailure] or `null`
     * if it is [success][isSuccess].
     *
     * This function is shorthand for `fold(onSuccess = { null }, onFailure = { it })` (see [fold]).
     */
    public fun exceptionOrNull(): Throwable? =
        when (value) {
            is Failure -> value.exception
            else -> null
        }

    /**
     * Returns a string representation of the encapsulated value or `Failure(xxx)` string where
     * `xxx` is a string representation of the exception.
     */
    public override fun toString(): String = value.toString()

    // companion with constructors

    /**
     * Companion object for [Result] class that contains its constructor functions
     * [success] and [failure].
     */
    public companion object {
        /**
         * Returns an instance that encapsulates the given [value] as successful value.
         */
        @InlineOnly public inline fun <T> success(value: T): Result<T> =
            Result(value)

        /**
         * Returns an instance that encapsulates the given [exception] as failure.
         */
        // Implementation note: This function is NOT inline to hide Failure class from ABI
        @InlineOnly public inline fun <T> failure(exception: Throwable): Result<T> =
            Result(createFailure(exception))
    }

    internal class Failure(
        @JvmField
        val exception: Throwable
    ) : Serializable {
        override fun equals(other: Any?): Boolean = other is Failure && exception == other.exception
        override fun hashCode(): Int = exception.hashCode()
        override fun toString(): String = "Failure($exception)"
    }
}

@PublishedApi
internal fun createFailure(exception: Throwable): Any =
    Result.Failure(exception)

/**
 * Calls the specified function [block] and returns its encapsulated result if invocation was successful,
 * catching and encapsulating any thrown exception as a failure.
 */
@InlineOnly public inline fun <R> runCatching(block: () -> R): Result<R> {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/**
 * Calls the specified function [block] with `this` value as its receiver and returns its encapsulated result
 * if invocation was successful, catching and encapsulating any thrown exception as a failure.
 */
@InlineOnly public inline fun <T, R> T.runCatching(block: T.() -> R): Result<R> {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

// -- extensions ---

/**
 * Returns the encapsulated value if this instance represents [success][Result.isSuccess] or throws the encapsulated exception
 * if it is [failure][Result.isFailure].
 *
 * This function is shorthand for `getOrElse { throw it }` (see [getOrElse]).
 */
@InlineOnly public inline fun <T> Result<T>.getOrThrow(): T {
    exceptionOrNull()?.let { throw it }
    return value as T
}

/**
 * Returns the encapsulated value if this instance represents [success][Result.isSuccess] or `null`
 * if it is [failure][Result.isFailure].
 *
 * This function is shorthand for `getOrElse { null }` (see [getOrElse]).
 */
@InlineOnly public inline fun <T> Result<T>.getOrNull(): T? {
    if (isFailure) return null
    return value as T
}

/**
 * Returns the encapsulated value if this instance represents [success][Result.isSuccess] or the
 * result of [onFailure] function for encapsulated exception if it is [failure][Result.isFailure].
 *
 * Note, that an exception thrown by [onFailure] function is rethrown by this function.
 *
 * This function is shorthand for `fold(onSuccess = { it }, onFailure = onFailure)` (see [fold]).
 */
@InlineOnly public inline fun <R, T : R> Result<T>.getOrElse(onFailure: (exception: Throwable) -> R): R {
    contract {
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when(val exception = exceptionOrNull()) {
        null -> value as T
        else -> onFailure(exception)
    }
}

/**
 * Returns the encapsulated value if this instance represents [success][Result.isSuccess] or the
 * [defaultValue] if it is [failure][Result.isFailure].
 *
 * This function is shorthand for `getOrElse { defaultValue }` (see [getOrElse]).
 */
@InlineOnly public inline fun <R, T : R> Result<T>.getOrDefault(defaultValue: R): R {
    if (isFailure) return defaultValue
    return value as T
}

/**
 * Returns the the result of [onSuccess] for encapsulated value if this instance represents [success][Result.isSuccess]
 * or the result of [onFailure] function for encapsulated exception if it is [failure][Result.isFailure].
 *
 * Note, that an exception thrown by [onSuccess] or by [onFailure] function is rethrown by this function.
 */
@InlineOnly public inline fun <R, T> Result<T>.fold(
    onSuccess: (value: T) -> R,
    onFailure: (exception: Throwable) -> R
): R {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when(val exception = exceptionOrNull()) {
        null -> onSuccess(value as T)
        else -> onFailure(exception)
    }
}

// transformation

/**
 * Returns the encapsulated result of the given [transform] function applied to encapsulated value
 * if this instance represents [success][Result.isSuccess] or the
 * original encapsulated exception if it is [failure][Result.isFailure].
 *
 * Note, that an exception thrown by [transform] function is rethrown by this function.
 * See [mapCatching] for an alternative that encapsulates exceptions.
 */
@InlineOnly public inline fun <R, T> Result<T>.map(transform: (value: T) -> R): Result<R> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }
    return when(val exception = exceptionOrNull()) {
        null -> Result.success(transform(value as T))
        else -> Result(value)
    }
}

/**
 * Returns the encapsulated result of the given [transform] function applied to encapsulated value
 * if this instance represents [success][Result.isSuccess] or the
 * original encapsulated exception if it is [failure][Result.isFailure].
 *
 * Any exception thrown by [transform] function is caught, encapsulated as a failure and returned by this function.
 * See [map] for an alternative that rethrows exceptions.
 */
@InlineOnly public inline fun <R, T> Result<T>.mapCatching(transform: (value: T) -> R): Result<R> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }
    return when(val exception = exceptionOrNull()) {
        null -> runCatching { transform(value as T) }
        else -> Result(value)
    }
}

/**
 * Returns the encapsulated result of the given [transform] function applied to encapsulated exception
 * if this instance represents [failure][Result.isFailure] or the
 * original encapsulated value if it is [success][Result.isSuccess].
 *
 * Note, that an exception thrown by [transform] function is rethrown by this function.
 * See [recoverCatching] for an alternative that encapsulates exceptions.
 */
@InlineOnly public inline fun <R, T: R> Result<T>.recover(transform: (exception: Throwable) -> R): Result<R> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }
    return when(val exception = exceptionOrNull()) {
        null -> this
        else -> Result.success(transform(exception))
    }
}

/**
 * Returns the encapsulated result of the given [transform] function applied to encapsulated exception
 * if this instance represents [failure][Result.isFailure] or the
 * original encapsulated value if it is [success][Result.isSuccess].
 *
 * Any exception thrown by [transform] function is caught, encapsulated as a failure and returned by this function.
 * See [recover] for an alternative that rethrows exceptions.
 */
@InlineOnly public inline fun <R, T: R> Result<T>.recoverCatching(transform: (exception: Throwable) -> R): Result<R> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }
    val value = value // workaround for inline classes BE bug
    return when(val exception = exceptionOrNull()) {
        null -> this
        else -> runCatching { transform(exception) }
    }
}

// "peek" onto value/exception and pipe

/**
 * Performs the given [action] on encapsulated value if this instance represents [success][Result.isSuccess].
 * Returns the original `Result` unchanged.
 */
@InlineOnly public inline fun <T> Result<T>.onFailure(action: (exception: Throwable) -> Unit): Result<T> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    exceptionOrNull()?.let { action(it) }
    return this
}

/**
 * Performs the given [action] on encapsulated exception if this instance represents [failure][Result.isFailure].
 * Returns the original `Result` unchanged.
 */
@InlineOnly public inline fun <T> Result<T>.onSuccess(action: (value: T) -> Unit): Result<T> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    if (isSuccess) action(value as T)
    return this
}

// -------------------