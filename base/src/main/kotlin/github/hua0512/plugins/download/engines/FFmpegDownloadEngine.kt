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

package github.hua0512.plugins.download.engines

import github.hua0512.app.App
import github.hua0512.data.stream.StreamData
import github.hua0512.utils.executeProcess
import github.hua0512.utils.process.Redirect
import github.hua0512.utils.withIOContext
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.io.OutputStream

/**
 * FFmpegDownloadEngine is a download engine that uses ffmpeg to download the stream.
 * @author hua0512
 * @date : 2024/2/12 18:22
 */
class FFmpegDownloadEngine() : BaseDownloadEngine() {

  companion object {
    @JvmStatic
    private val logger = LoggerFactory.getLogger(FFmpegDownloadEngine::class.java)
  }

  // output stream for writing 'q' to stop ffmpeg process
  private var ous: OutputStream? = null

  // ffmpeg process
  private var ffmpegProcess: Process? = null

  override suspend fun startDownload(): StreamData? {
    // ffmpeg running commands
    val cmds = buildFFMpegCmd(
      headers,
      cookies,
      downloadUrl!!,
      downloadFormat!!,
      fileLimitSize,
      fileLimitDuration,
      downloadFilePath
    )
    val streamer = streamData!!.streamer

    logger.info("(${streamer.name}) Starting download using ffmpeg...")
    onDownloadStarted()
    // last size of the file
    var lastSize = 0L
    val exitCode =
      executeProcess(
        App.ffmpegPath,
        *cmds,
        stdout = Redirect.CAPTURE,
        stderr = Redirect.CAPTURE,
        destroyForcibly = true,
        getOutputStream = {
          ous = it
        },
        getProcess = {
          ffmpegProcess = it
        }) { line ->
        processFFmpegOutputLine(line, streamer.name, lastSize) { size, diff, bitrate ->
          lastSize = size
          onDownloadProgress(diff, bitrate)
        }
      }
    ffmpegProcess = null
    ous = null
    return if (exitCode != 0) {
      logger.error("(${streamer.name}) download failed, exit code: $exitCode")
      null
    } else {
      // case when download is successful (exit code is 0)
      streamData!!.copy(
        dateStart = startTime.epochSeconds,
        dateEnd = Clock.System.now().epochSeconds,
        outputFilePath = downloadFilePath,
      )
    }
  }

  override suspend fun stopDownload(): Boolean {
    // stop ffmpeg process by writing 'q' to the output stream
    withIOContext {
      ous?.apply {
        // check if the process is still running
        if (ffmpegProcess?.isAlive == false) {
          logger.info("FFmpeg process is not running")
          return@withIOContext
        }
        logger.info("(${streamData!!.streamer.name}) Stopping ffmpeg process...")
        try {
          write("q\n".toByteArray())
          flush()
        } catch (e: Exception) {
          logger.error("Error sending stop signal to ffmpeg process", e)
        }
      }
    }
    // wait for the process to exit
    val code = withIOContext { ffmpegProcess?.waitFor() }
    if (code != 0) {
      logger.error("FFmpeg process exited with code $code")
      return false
    }
    return true
  }
}