/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2024 hua0512 (https://github.com/hua0512)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package github.hua0512.utils

import kotlinx.coroutines.delay
import java.io.IOException

/**
 * @author hua0512
 * @date : 2024/2/16 17:32
 */

suspend fun <T> withRetry(
  maxRetries: Int = 3,
  initialDelayMillis: Long = 1000,
  maxDelayMillis: Long = 5000,
  factor: Double = 2.0,
  onError: (suspend (e: IOException, retryCount: Int) -> Unit)? = null,
  block: suspend () -> T,
): T {
  var currentDelay = initialDelayMillis
  repeat(maxRetries) { retryCount ->
    try {
      return block()
    } catch (e: IOException) {
      onError?.invoke(e, retryCount)
      if (retryCount == maxRetries - 1) {
        // If we've reached the maximum retries, propagate the exception
        throw e
      }
    }
    delay(currentDelay)
    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
  }
  // This should not be reached
  throw IllegalStateException("Unreachable statement")
}