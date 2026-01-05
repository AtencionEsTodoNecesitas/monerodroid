package com.sevendeuce.monerodroid.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.sevendeuce.monerodroid.data.StorageInfo
import com.sevendeuce.monerodroid.data.StorageLocation
import java.io.File

class StorageManager(private val context: Context) {

    companion object {
        const val MONERO_DIR = "monerodroid"
        const val BLOCKCHAIN_DIR = "blockchain"
        const val CONFIG_DIR = "config"
        const val BINARY_DIR = "bin"
        const val MIN_STORAGE_PRUNED_GB = 50L
        const val MIN_STORAGE_FULL_GB = 300L
    }

    fun getInternalStorageInfo(): StorageInfo {
        val path = context.filesDir.absolutePath
        return getStorageInfo(path, StorageLocation.INTERNAL)
    }

    fun getExternalStorageInfo(): StorageInfo? {
        val externalDirs = context.getExternalFilesDirs(null)
        // First entry is primary external (usually same as internal)
        // Second entry (if exists) is removable SD card
        val sdCard = externalDirs.getOrNull(1)

        return if (sdCard != null && sdCard.exists() && sdCard.canWrite()) {
            getStorageInfo(sdCard.absolutePath, StorageLocation.EXTERNAL)
        } else {
            null
        }
    }

    private fun getStorageInfo(path: String, location: StorageLocation): StorageInfo {
        return try {
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            StorageInfo(
                location = location,
                path = path,
                totalBytes = totalBlocks * blockSize,
                freeBytes = availableBlocks * blockSize,
                isAvailable = true
            )
        } catch (e: Exception) {
            StorageInfo(
                location = location,
                path = path,
                totalBytes = 0,
                freeBytes = 0,
                isAvailable = false
            )
        }
    }

    fun getMoneroDataDir(useExternal: Boolean): File {
        val baseDir = if (useExternal) {
            context.getExternalFilesDirs(null).getOrNull(1) ?: context.filesDir
        } else {
            context.filesDir
        }
        return File(baseDir, "$MONERO_DIR/$BLOCKCHAIN_DIR").also { it.mkdirs() }
    }

    fun getConfigDir(): File {
        return File(context.filesDir, "$MONERO_DIR/$CONFIG_DIR").also { it.mkdirs() }
    }

    fun getBinaryDir(): File {
        return File(context.filesDir, "$MONERO_DIR/$BINARY_DIR").also { it.mkdirs() }
    }

    fun getMonerodBinaryPath(): File {
        // First check if bundled binary exists in native lib dir (preferred for execution)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val bundledBinary = File(nativeLibDir, "libmonerod.so")
        if (bundledBinary.exists() && bundledBinary.canExecute()) {
            return bundledBinary
        }
        // Fall back to copied binary in app data
        return File(getBinaryDir(), "monerod")
    }

    fun getNativeLibMonerodPath(): File? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val bundledBinary = File(nativeLibDir, "libmonerod.so")
        return if (bundledBinary.exists()) bundledBinary else null
    }

    fun getConfigFilePath(): File {
        return File(getConfigDir(), "monerod.conf")
    }

    fun hasAdequateStorage(useExternal: Boolean, isPruned: Boolean): Boolean {
        val storageInfo = if (useExternal) {
            getExternalStorageInfo() ?: return false
        } else {
            getInternalStorageInfo()
        }

        // Check if blockchain already exists - if so, only need minimal space for operations
        val dataDir = getMoneroDataDir(useExternal)
        val lmdbFile = File(dataDir, "lmdb/data.mdb")
        if (lmdbFile.exists() && lmdbFile.length() > 1_000_000_000) { // >1GB means blockchain exists
            // Only need 5GB free for ongoing operations when blockchain already synced
            return storageInfo.freeGb >= 5f
        }

        return storageInfo.hasAdequateStorage(isPruned)
    }

    fun isExternalStorageAvailable(): Boolean {
        return getExternalStorageInfo()?.isAvailable == true
    }

    fun getSelectedStorageInfo(useExternal: Boolean): StorageInfo {
        return if (useExternal) {
            getExternalStorageInfo() ?: getInternalStorageInfo()
        } else {
            getInternalStorageInfo()
        }
    }
}
