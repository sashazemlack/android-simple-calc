package com.scalc.calculator.exchange.data

import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Serializer
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import androidx.datastore.core.createSingleProcessCoordinator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Test-only file [Storage] with the same semantics as DataStore's `FileStorage` (read via the
 * serializer, missing file = default value, write to a scratch file and atomically replace).
 *
 * Why it exists: `FileStorage` replaces the file with `File.renameTo` whenever
 * `Build.VERSION.SDK_INT < 26`, and `SDK_INT` is always 0 in JVM unit tests. On Windows hosts
 * `renameTo` cannot replace an existing file, so every second write would fail there. On devices
 * (POSIX rename) the production `FileStorage` is unaffected. This storage uses java.nio instead,
 * so tests still exercise the real `DataStoreImpl`, the real serializer and the real
 * `ReplaceFileCorruptionHandler` on real files.
 */
class NioFileStorage<T>(
    private val serializer: Serializer<T>,
    private val produceFile: () -> File,
) : Storage<T> {

    override fun createConnection(): StorageConnection<T> = Connection(produceFile().absoluteFile)

    private inner class Connection(private val file: File) : StorageConnection<T> {
        private val writeMutex = Mutex()

        override val coordinator: InterProcessCoordinator = createSingleProcessCoordinator(file.path)

        override suspend fun <R> readScope(block: suspend ReadScope<T>.(locked: Boolean) -> R): R =
            FileScope(file).block(false)

        override suspend fun writeScope(block: suspend WriteScope<T>.() -> Unit) {
            writeMutex.withLock { FileScope(file).block() }
        }

        override fun close() = Unit
    }

    private inner class FileScope(private val file: File) : WriteScope<T> {
        override suspend fun readData(): T = try {
            FileInputStream(file).use { serializer.readFrom(it) }
        } catch (e: FileNotFoundException) {
            if (file.exists()) throw e
            serializer.defaultValue
        }

        override suspend fun writeData(value: T) {
            file.parentFile?.mkdirs()
            val scratch = File(file.path + ".tmp")
            FileOutputStream(scratch).use { serializer.writeTo(value, it) }
            Files.move(scratch.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }

        override fun close() = Unit
    }
}
