package com.scalc.calculator.calc.history

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
 * Test-only file [Storage] with the semantics of DataStore's `FileStorage`: a missing file reads
 * as the serializer default, writes go to a scratch file that atomically replaces the target.
 *
 * DataStore's `FileStorage` replaces the file with `File.renameTo` when `Build.VERSION.SDK_INT`
 * is below 26, which is always the case in JVM unit tests (SDK_INT = 0). On Windows hosts
 * `renameTo` cannot overwrite an existing file, so the second write would fail there; devices
 * are unaffected. java.nio's replacing move behaves the same on every host, so the tests still
 * run the real DataStore implementation and [HistorySerializer] against real files.
 */
internal class JvmFileStorage<T>(
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
