@file:Suppress("DEPRECATION")

package dev.frozenmilk.sinister

import android.content.Context
import com.qualcomm.robotcore.util.RobotLog
import com.qualcomm.robotcore.util.ThreadPool
import dalvik.system.DexFile
import dev.frozenmilk.sinister.Sinister.Companion.TAG
import dev.frozenmilk.sinister.configurable.ConfigurableScanner
import dev.frozenmilk.sinister.loaders.RootClassLoader
import dev.frozenmilk.sinister.loading.LoadEvent
import dev.frozenmilk.sinister.loading.LoadEventHandler
import dev.frozenmilk.sinister.loading.Pinned
import dev.frozenmilk.sinister.loading.Preload
import dev.frozenmilk.sinister.sdk.apphooks.OnCreateScanner
import dev.frozenmilk.sinister.targeting.FullSearch
import dev.frozenmilk.sinister.targeting.SearchTarget
import dev.frozenmilk.util.graph.emitGraph
import org.firstinspires.ftc.robotcore.internal.system.AppAliveNotifier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import dev.frozenmilk.sinister.SlothBuildMetaData

// NOTE: Sinister uses RobotLog instead of Logger, as it won't have been configured yet,
// and we know we'll be using it in the end unless the user has uploaded an alternate logging system
// and we need a reliable pre-launch logger
// and the default impl would just be printing to std out anyway and end up in the logcat regardless
@Suppress("unused")
object SinisterImpl : Sinister {
    private val rootSearch = FullSearch()
    val teamCodeSearch =
        SearchTarget(SearchTarget.Inclusion.EXCLUDE).apply { include("org.firstinspires.ftc.teamcode") }
    val rootLoader = RootClassLoader(this.javaClass.classLoader!!)
    lateinit var ignoredClasses: List<String>
        private set
    private lateinit var scanners: Set<Scanner>

    @Volatile
    private var run = false
    private val postBoot = CompletableFuture<Unit>()
    private lateinit var packageCodePath: String

    @org.firstinspires.ftc.ftccommon.external.OnCreate
    @JvmStatic
    @Suppress("unused")
    fun onCreate(context: Context) {
        synchronized(this) {
            RobotLog.dd(
                "Meta",
                """
name: ${SlothBuildMetaData.name}
version: ${SlothBuildMetaData.version}
ref: ${SlothBuildMetaData.gitRef}
""",
            )
            RobotLog.vv(TAG, "attempting boot on create")
            packageCodePath = context.packageCodePath
            if (run) {
                RobotLog.vv(TAG, "already booted")
                // no need to call onCreate manually, as that's the only way that we're being called again!
                RobotLog.vv(TAG, "finished boot process")
                return
            }
            RobotLog.vv(TAG, "not yet booted, booting")
            RobotLog.vv(TAG, "setting Sinister delegate")
            val aliveNotifier = ThreadPool.getDefaultScheduler().scheduleWithFixedDelay({
                RobotLog.vv(TAG, "notifying that app is alive")
                AppAliveNotifier.getInstance().notifyAppAlive()
            }, 0, 5, TimeUnit.SECONDS)
            val rootFile = DexFile(context.packageCodePath)
            selfBoot(rootFile)
            rootFile.close()
            run = true
            // we only need to call this once
            OnCreateScanner.CALLSITE.onCreate(context)
            // seems to not be a ton of point?
            aliveNotifier.cancel(false)
            RobotLog.vv(TAG, "finished boot process")
            postBoot.complete(Unit)
        }
    }

    /**
     * one-off procedure, expects a DexFile, rather than a jarfile
     */
    private fun selfBoot(file: DexFile) {
        RobotLog.vv(TAG, "self booting...")
        val allClasses = file.entries().asSequence().mapNotNull {
            if (!rootSearch.determineInclusion(it)) return@mapNotNull null
            try {
                Class.forName(it, false, javaClass.classLoader).also { cls ->
                    cls.name
                    if (cls.isPinned()) rootLoader.pin(it, cls)
                }
            } catch (e: Throwable) {
                RobotLog.ee(TAG, "Error occurred while locating class: $e.")
                rootSearch.exclude(it)
                null
            }
        }.toList()

        // we are going to ignore all non-pinned teamcode classes from here on out
        val rootClasses = mutableListOf<Class<*>>()
        val teamCodeClasses = mutableListOf<String>()
        allClasses.forEach { cls ->
            if (cls.inheritsAnnotation(Pinned::class.java) //
                || !teamCodeSearch.determineInclusion(cls.name)
            ) rootClasses.add(cls)
            else teamCodeClasses.add(cls.name)
        }
        this.ignoredClasses = teamCodeClasses

        // we're going to pre-run the configuration system

        try {
            spawnScannerLoad(
                ConfigurableScanner,
                rootLoader,
                rootClasses.iterator(),
                ThreadPool.getDefault(),
            ).join()
        } catch (e: Throwable) {
            RobotLog.ee(TAG, e, "Failed to run configuration phase")
            RobotLog.setGlobalErrorMsg(
                "Fatal configuration error occurred while running Sloth.\n" +
                        "Try a regular teamcode install, if this error persists, ask for help.\n" +
                        e.stackTraceToString(),
            )
            throw e
        }

        val preloaded = preload(rootLoader, rootClasses)

        scanners = preloaded
            .flatMap { it.staticInstancesOf(Scanner::class.java) }
            .filter { it !== ConfigurableScanner }
            .onEach { RobotLog.vv(TAG, "found scanner ${it.javaClass.simpleName}") }
            .toSet()

        // run scanners on root
        try {
            scanLoad(rootLoader, rootClasses)
        } catch (e: Throwable) {
            RobotLog.ee(TAG, e, "Failed to run scanners")
            RobotLog.setGlobalErrorMsg(
                "Fatal scanning error occurred while running Sloth.\n" +
                        "Try a regular teamcode install, if this error persists, ask for help.\n" +
                        e.stackTraceToString(),
            )
            throw e
        }

        RobotLog.vv(TAG, "...booted")
    }

    private fun preload(loader: ClassLoader, classes: List<Class<*>>) =
        classes
            .filter {
                try {
                    if (it.inheritsAnnotation(Preload::class.java)) {
                        RobotLog.vv(TAG, "preloading: ${it.name}")
                        val _ = it.preload(loader)
                        true
                    } else false
                } catch (e: Throwable) {
                    RobotLog.ee(TAG, e, "failed to preload ${it.name}")
                    RobotLog.setGlobalErrorMsg(
                        "Fatal preloading error occurred while running Sloth.\n" +
                                "Try a regular teamcode install, if this error persists, ask for help.\n" +
                                "Preloading class $it.\n" +
                                e.stackTraceToString(),
                    )
                    throw e
                }
            }

    private fun scanLoad(loader: ClassLoader, classes: List<Class<*>>) {
        val executor = ThreadPool.getDefault()

        RobotLog.vv(TAG, "running scanners for load")
        val graph = scanners
            .emitGraph { it.loadAdjacencyRule }

        val tempRunRound = ArrayList<Scanner>(graph.size)
        val visited = LinkedHashSet<Scanner>(graph.size)

        val nodes = graph.nodes.toMutableSet()
        while (nodes.isNotEmpty()) {
            val iter = nodes.iterator()
            val size = nodes.size
            while (iter.hasNext()) {
                val node = iter.next()
                if (visited.containsAll(graph.map[node]!!.set)) {
                    tempRunRound.add(node)
                    iter.remove()
                }
            }
            check(nodes.size != size) { "Cycle detected in DAG, all remaining elements are in cycle(s). These were:\n$nodes" }
            // we'll consume each of the emitted scanners
            visited.addAll(tempRunRound)
            tempRunRound
                .also {
                    val tasks = it.map { scanner ->
                        spawnScannerLoad(scanner, loader, classes.iterator(), executor)
                    }.toTypedArray()
                    CompletableFuture.allOf(*tasks).join()
                }
                .clear()
        }
    }

    private fun scanUnload(loader: ClassLoader, classes: List<Class<*>>) {
        val executor = ThreadPool.getDefault()

        RobotLog.vv(TAG, "running scanners for unload")
        val graph = scanners
            .emitGraph { it.unloadAdjacencyRule }

        val tempRunRound = ArrayList<Scanner>(graph.size)
        val visited = LinkedHashSet<Scanner>(graph.size)

        val nodes = graph.nodes.toMutableSet()
        while (nodes.isNotEmpty()) {
            val iter = nodes.iterator()
            val size = nodes.size
            while (iter.hasNext()) {
                val node = iter.next()
                if (visited.containsAll(graph.map[node]!!.set)) {
                    visited.add(node)
                    tempRunRound.add(node)
                    iter.remove()
                }
            }
            check(nodes.size != size) { "Cycle detected in DAG, all remaining elements are in cycle(s). These were:\n$nodes" }
            // we'll consume each of the emitted scanners
            tempRunRound
                .also {
                    val tasks = it.map { scanner ->
                        spawnScannerUnload(scanner, loader, classes.iterator(), executor)
                    }.toTypedArray()
                    CompletableFuture.allOf(*tasks).get()
                }
                // clear
                .clear()
        }
    }

    private fun Class<*>.isPinned(): Boolean =
        isAnnotationPresent(Pinned::class.java) || rootLoader.pinned(name) != null || interfaces.any { it?.isPinned() == true } || superclass?.isPinned() == true

    override fun <LOADER : ClassLoader> stageLoad(
        prior: LoadEvent<LOADER>?,
        loader: LOADER,
        classNames: List<String>,
        apply: Consumer<LoadEvent<LOADER>>
    ) {
        val event = LoadEvent(prior, loader)
            .afterRelease {
                synchronized(this) {
                    val aliveNotifier = ThreadPool.getDefaultScheduler().scheduleWithFixedDelay(
                        {
                            RobotLog.vv(TAG, "notifying that app is alive")
                            AppAliveNotifier.getInstance().notifyAppAlive()
                        },
                        0,
                        5,
                        TimeUnit.SECONDS,
                    )
                    load(loader, classNames)
                    aliveNotifier.cancel(false)
                }
            }
            .afterUnload {
                synchronized(this) {
                    val aliveNotifier = ThreadPool.getDefaultScheduler().scheduleWithFixedDelay(
                        {
                            RobotLog.vv(TAG, "notifying that app is alive")
                            AppAliveNotifier.getInstance().notifyAppAlive()
                        },
                        0,
                        5,
                        TimeUnit.SECONDS,
                    )
                    unload(loader, classNames)
                    aliveNotifier.cancel(false)
                }
            }
        apply.accept(event)
        if (!run) postBoot.thenRun { LoadEventHandler.handleEventStaging(event) }
        else LoadEventHandler.handleEventStaging(event)
    }

    private fun load(loader: ClassLoader, classNames: List<String>) {
        // TODO: this is all wrong
        val classes = classNames.mapNotNull {
            if (!rootSearch.determineInclusion(it)) return@mapNotNull null
            try {
                Class.forName(it, false, loader).also { cls ->
                    if (cls.isPinned()) return@mapNotNull null
                }
            } catch (e: Throwable) {
                RobotLog.ee(TAG, e, "Error occurred while locating class $it.")
                RobotLog.setGlobalErrorMsg(
                    "Fatal class locating error occurred while running Sloth.\n" +
                            "Try a regular teamcode install, if this error persists, ask for help.\n" +
                            e.stackTraceToString(),
                )
                throw e
            }
        }

        // we're going to pre-run the configuration system
        try {
            spawnScannerLoad(
                ConfigurableScanner,
                loader,
                classes.iterator(),
                ThreadPool.getDefault(),
            ).join()
        } catch (e: Throwable) {
            RobotLog.ee(TAG, e, "Failed to run configuration phase")
            RobotLog.setGlobalErrorMsg(
                "Fatal configuration error occurred while running Sloth.\n" +
                        "Try a regular teamcode install, if this error persists, ask for help.\n" +
                        e.stackTraceToString(),
            )
            throw e
        }

        // ensure that we enforce the preloading
        repeat(preload(loader, classes).count()) { }

        // run scanners
        try {
            scanLoad(loader, classes)
        } catch (e: Throwable) {
            RobotLog.ee(TAG, e, "Failed to scan load.")
            RobotLog.setGlobalErrorMsg(
                "Fatal loading error occurred while running Sloth.\n" +
                        "Try a regular teamcode install, if this error persists, ask for help.\n" +
                        e.stackTraceToString(),
            )
            throw e
        }
    }

    private fun unload(loader: ClassLoader, classNames: List<String>) {
        // TODO: this is also wrong
        val classes = classNames.mapNotNull {
            if (!rootSearch.determineInclusion(it)) return@mapNotNull null
            try {
                Class.forName(it, false, loader).also { cls ->
                    if (cls.isPinned()) return@mapNotNull null
                }
            } catch (e: Throwable) {
                RobotLog.ee(TAG, e, "Error occurred while locating class $it.")
                RobotLog.setGlobalErrorMsg(
                    "Fatal class locating error occurred while running Sloth.\n" +
                            "Try a regular teamcode install, if this error persists, ask for help.\n" +
                            e.stackTraceToString(),
                )
                throw e
            }
        }

        // run scanners
        try {
            scanUnload(loader, classes)
        } catch (e: Throwable) {
            RobotLog.ee(TAG, e, "Failed to scan unload.")
            RobotLog.setGlobalErrorMsg(
                "Fatal unloading error occurred while running Sloth.\n" +
                        "Try a regular teamcode install, if this error persists, ask for help.\n" +
                        e.stackTraceToString(),
            )
            throw e
        }

        // we're going to pre-run the configuration system
        try {
            spawnScannerUnload(
                ConfigurableScanner,
                loader,
                classes.iterator(),
                ThreadPool.getDefault(),
            ).join()
        } catch (e: Throwable) {
            RobotLog.ee(TAG, e, "Failed to run configuration phase")
            RobotLog.setGlobalErrorMsg(
                "Fatal configuration error occurred while running Sloth.\n" +
                        "Try a regular teamcode install, if this error persists, ask for help.\n" +
                        e.stackTraceToString(),
            )
            throw e
        }
    }

    private fun spawnScannerLoad(
        scanner: Scanner,
        loader: ClassLoader,
        classes: Iterator<Class<*>>,
        executor: ExecutorService,
    ): CompletableFuture<*> = CompletableFuture.runAsync(
        {
            RobotLog.vv(TAG, "running scanner ${scanner.javaClass.name}")
            // pre scan hook
            try {
                scanner.beforeScan(loader)
            } catch (err: Throwable) {
                RobotLog.ee(TAG, err, "$scanner failed before scan")
                RobotLog.setGlobalErrorMsg(
                    "Fatal scanning error occurred while running Sloth.\n" +
                            "Try a regular teamcode install, if this error persists, ask for help.\n" +
                            "Running scanner $scanner.\n" +
                            "Before scan\n" +
                            err.stackTraceToString(),
                )
                throw err
            }
            // scanning classes
            classes.forEach { cls ->
                if (scanner.targets.determineInclusion(cls.name)) {
                    try {
                        scanner.scan(loader, cls)
                    } catch (err: Throwable) {
                        val ignorableLoadingError =
                            err is NoClassDefFoundError //
                                    && err.cause is ClassNotFoundException //
                                    && !teamCodeSearch.determineInclusion(cls.name)

                        if (!ignorableLoadingError) {
                            RobotLog.ee(TAG, err, "$scanner failed to scan $cls")
                            RobotLog.setGlobalErrorMsg(
                                "Fatal scanning error occurred while running Sloth.\n" +
                                        "Try a regular teamcode install, if this error persists, ask for help.\n" +
                                        "Running scanner $scanner.\n" +
                                        "Scanning class $cls.\n" +
                                        err.stackTraceToString()
                            )
                            throw err
                        }
                    }
                }
            }
            // post scan hook
            try {
                scanner.afterScan(loader)
            } catch (err: Throwable) {
                RobotLog.ee(TAG, err, "$scanner failed after scan")
                RobotLog.setGlobalErrorMsg(
                    "Fatal scanning error occurred while running Sloth.\n" +
                            "Try a regular teamcode install, if this error persists, ask for help.\n" +
                            "Running scanner $scanner.\n" +
                            "After scan\n" +
                            err.stackTraceToString()
                )
                throw err
            }
            RobotLog.vv(TAG, "finished scanner ${scanner.javaClass.name}")
        },
        executor,
    )

    private fun spawnScannerUnload(
        scanner: Scanner,
        loader: ClassLoader,
        classes: Iterator<Class<*>>,
        executor: ExecutorService
    ): CompletableFuture<*> {
        return CompletableFuture.runAsync(
            {
                RobotLog.vv(TAG, "running scanner ${scanner.javaClass.name}")
                // pre unload hook
                try {
                    scanner.beforeUnload(loader)
                } catch (err: Throwable) {
                    RobotLog.ee(TAG, err, "$scanner failed before unload")
                    RobotLog.setGlobalErrorMsg(
                        "Fatal scanning error occurred while running Sloth.\n" +
                                "Try a regular teamcode install, if this error persists, ask for help.\n" +
                                "Running scanner $scanner.\n" +
                                "Before unload\n" +
                                err.stackTraceToString(),
                    )
                    throw err
                }
                // unloading classes
                classes.forEach { cls ->
                    if (scanner.targets.determineInclusion(cls.name)) {
                        try {
                            scanner.unload(loader, cls)
                        } catch (err: Throwable) {
                            val ignorableLoadingError =
                                err is NoClassDefFoundError
                                        && err.cause is ClassNotFoundException
                                        && !teamCodeSearch.determineInclusion(cls.name)

                            if (!ignorableLoadingError) {
                                RobotLog.ee(TAG, err, "$scanner failed to unload $cls")
                                RobotLog.setGlobalErrorMsg(
                                    "Fatal scanning error occurred while running Sloth.\n" +
                                            "Try a regular teamcode install, if this error persists, ask for help.\n" +
                                            "Running scanner $scanner.\n" +
                                            "Unloading class $cls.\n" +
                                            err.stackTraceToString()
                                )
                                throw err
                            }
                        }
                    }
                }
                // post unload hook
                try {
                    scanner.afterUnload(loader)
                } catch (err: Throwable) {
                    RobotLog.ee(TAG, err, "$scanner failed after unload")
                    RobotLog.setGlobalErrorMsg(
                        "Fatal scanning error occurred while running Sloth.\n" +
                                "Try a regular teamcode install, if this error persists, ask for help.\n" +
                                "Running scanner $scanner.\n" +
                                "After unload\n" +
                                err.stackTraceToString()
                    )
                    throw err
                }
                RobotLog.vv(TAG, "finished scanner ${scanner.javaClass.name}")
            },
            executor,
        )
    }
}
