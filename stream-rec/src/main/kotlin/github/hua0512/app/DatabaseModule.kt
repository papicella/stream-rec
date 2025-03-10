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

package github.hua0512.app

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import dagger.Module
import dagger.Provides
import github.hua0512.StreamRecDatabase
import github.hua0512.dao.AppConfigDao
import github.hua0512.dao.AppConfigDaoImpl
import github.hua0512.dao.UserDao
import github.hua0512.dao.UserDaoImpl
import github.hua0512.dao.stats.StatsDao
import github.hua0512.dao.stats.StatsDaoImpl
import github.hua0512.dao.stream.StreamDataDao
import github.hua0512.dao.stream.StreamDataDaoImpl
import github.hua0512.dao.stream.StreamerDao
import github.hua0512.dao.stream.StreamerDaoImpl
import github.hua0512.dao.upload.*
import github.hua0512.logger
import github.hua0512.repo.LocalDataSource
import java.util.*
import javax.inject.Singleton
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString

/**
 * @author hua0512
 * @date : 2024/2/19 11:40
 */
@Module
class DatabaseModule {

  private  var firstRun : Boolean = true

  @Provides
  @Singleton
  fun provideSqlDriver(): SqlDriver {
    val path = Path(LocalDataSource.getDefaultPath()).also {
      it.createParentDirectories()
      logger.info("Database path: ${it.pathString}")
    }
    firstRun = LocalDataSource.isFirstRun()
    return LogSqliteDriver(
      sqlDriver = JdbcSqliteDriver("jdbc:sqlite:${path.pathString}", Properties().apply {
        put("foreign_keys", "true")
      }),
      logger = { logger.trace(it) }
    )
  }

  @Provides
  @Singleton
  fun provideDatabase(sqlDriver: SqlDriver): StreamRecDatabase {
    StreamRecDatabase.Schema.create(sqlDriver)
    val dbVersion = LocalDataSource.getDbVersion()
    val schemaVersion = StreamRecDatabase.Schema.version
    logger.info("Database version: $dbVersion")
    // if not first run, check db version and migrate if needed
    if (!firstRun) {
      try {
        if (dbVersion < schemaVersion) {
          logger.info("Trying to migrate database from version $dbVersion to $schemaVersion")
          StreamRecDatabase.Schema.migrate(sqlDriver, dbVersion, schemaVersion)
          LocalDataSource.writeDbVersion(schemaVersion)
        }
      } catch (e: Exception) {
        logger.error("Failed to migrate database", e)
      }
    } else {
      // write db version
      LocalDataSource.writeDbVersion(schemaVersion)
      firstRun = false
    }
    return StreamRecDatabase(driver = sqlDriver)
  }

  @Provides
  fun provideUserDao(database: StreamRecDatabase): UserDao = UserDaoImpl(database)

  @Provides
  fun provideAppConfigDao(database: StreamRecDatabase): AppConfigDao = AppConfigDaoImpl(database)

  @Provides
  fun provideStreamerDao(database: StreamRecDatabase): StreamerDao {
    return StreamerDaoImpl(database)
  }

  @Provides
  fun provideStreamDataDao(database: StreamRecDatabase): StreamDataDao = StreamDataDaoImpl(database)

  @Provides
  fun provideUploadActionDao(database: StreamRecDatabase): UploadActionDao = UploadActionDaoImpl(database)

  @Provides
  fun provideUploadDataDao(database: StreamRecDatabase): UploadDataDao = UploadDataDaoImpl(database)

  @Provides
  fun provideUploadResultDao(database: StreamRecDatabase): UploadResultDao = UploadResultDaoImpl(database)

  @Provides
  fun provideUploadActionFilesDao(database: StreamRecDatabase): UploadActionFilesDao = UploadActionFilesDaoImpl(database)

  @Provides
  fun provideStatsDao(database: StreamRecDatabase): StatsDao = StatsDaoImpl(database)
}