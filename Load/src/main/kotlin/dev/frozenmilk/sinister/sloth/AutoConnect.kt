package dev.frozenmilk.sinister.sloth

import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream

enum class AutoConnect {
    NEVER {
        override fun <T> invoke(
            execOperations: ExecOperations,
            adbExecutable: String,
            address: String,
            f: () -> T,
        ) = f()
    },
    MAINTAIN {
        override fun <T> invoke(
            execOperations: ExecOperations,
            adbExecutable: String,
            address: String,
            f: () -> T,
        ): T = run {
            val sout = ByteArrayOutputStream()
            execOperations.exec {
                it.commandLine(
                    adbExecutable,
                    "devices",
                )
                it.standardOutput = sout
            }
            val connected = sout.toString().lines().size > 3
            try {
                if (!connected)
                    execOperations.exec {
                        it.commandLine(
                            adbExecutable,
                            "connect",
                            address,
                        )
                    }
                f()
            } finally {
                if (!connected)
                    execOperations.exec {
                        it.commandLine(
                            adbExecutable,
                            "disconnect",
                        )
                    }
            }
        }
    },
    ALWAYS {
        override fun <T> invoke(
            execOperations: ExecOperations,
            adbExecutable: String,
            address: String,
            f: () -> T,
        ): T = run {
            try {
                execOperations.exec {
                    it.commandLine(
                        adbExecutable,
                        "connect",
                        address,
                    )
                }
                f()
            } finally {
                execOperations.exec {
                    it.commandLine(
                        adbExecutable,
                        "disconnect",
                    )
                }
            }
        }
    };

    abstract operator fun <T> invoke(
        execOperations: ExecOperations,
        adbExecutable: String,
        address: String,
        f: () -> T,
    ): T
}