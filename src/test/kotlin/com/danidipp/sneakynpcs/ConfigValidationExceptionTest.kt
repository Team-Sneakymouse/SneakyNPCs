package com.danidipp.sneakynpcs

import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertSame

class ConfigValidationExceptionTest {
    @Test
    fun `unwrapAsyncThrowable unwraps nested async wrappers`() {
        val root = IllegalStateException("boom")

        val unwrapped = unwrapAsyncThrowable(CompletionException(ExecutionException(root)))

        assertSame(root, unwrapped)
    }

    @Test
    fun `unwrapAsyncThrowable returns original throwable when not wrapped`() {
        val root = IllegalArgumentException("plain")

        val unwrapped = unwrapAsyncThrowable(root)

        assertSame(root, unwrapped)
    }
}
