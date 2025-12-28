package com.sevendeuce.monerodroid.util

import android.os.Build

enum class CpuArchitecture {
    ARM_V7,
    ARM_V8,
    UNSUPPORTED
}

object ArchitectureDetector {

    fun detectArchitecture(): CpuArchitecture {
        val supportedAbis = Build.SUPPORTED_ABIS

        for (abi in supportedAbis) {
            when {
                abi.contains("arm64") || abi.contains("aarch64") -> {
                    return CpuArchitecture.ARM_V8
                }
                abi.contains("armeabi-v7a") || abi.contains("armeabi") -> {
                    return CpuArchitecture.ARM_V7
                }
            }
        }

        return CpuArchitecture.UNSUPPORTED
    }

    fun getMoneroDownloadUrl(): String? {
        return when (detectArchitecture()) {
            CpuArchitecture.ARM_V7 -> "https://downloads.getmonero.org/cli/androidarm7"
            CpuArchitecture.ARM_V8 -> "https://downloads.getmonero.org/cli/androidarm8"
            CpuArchitecture.UNSUPPORTED -> null
        }
    }

    fun getArchitectureName(): String {
        return when (detectArchitecture()) {
            CpuArchitecture.ARM_V7 -> "ARMv7 (32-bit)"
            CpuArchitecture.ARM_V8 -> "ARMv8 (64-bit)"
            CpuArchitecture.UNSUPPORTED -> "Unsupported"
        }
    }

    fun isSupported(): Boolean {
        return detectArchitecture() != CpuArchitecture.UNSUPPORTED
    }
}
