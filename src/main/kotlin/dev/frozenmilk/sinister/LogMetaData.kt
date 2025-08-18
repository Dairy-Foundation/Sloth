package dev.frozenmilk.sinister

import dev.frozenmilk.sinister.loading.Preload
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.sinister.SlothBuildMetaData

@Preload
object LogMetaData {
    init {
        Logger.d("Meta", """
name: ${SlothBuildMetaData.name}
version: ${SlothBuildMetaData.version}
ref: ${SlothBuildMetaData.gitRef}
""")
    }
}