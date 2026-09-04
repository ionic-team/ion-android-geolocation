package io.ionic.libs.iongeolocationlib.view

import android.app.Activity
import android.content.Context
import io.ionic.libs.iongeolocationlib.controller.IONGLOCController
import io.ionic.libs.ionnativeislandslib.NativeIslandAccessibility
import io.ionic.libs.ionnativeislandslib.NativeIslandsRegistry
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Adapter callback that reports whether precise location was granted. */
fun interface IONGLOCLocationButtonPermissionRequester {
    fun requestPreciseLocation(callback: (Boolean) -> Unit)
}

/** Registers the location button and its host permission callback. */
object IONGLOCLocationButtonRegistry {
    private val permissionRequesters =
        WeakHashMap<Activity, WeakReference<IONGLOCLocationButtonPermissionRequester>>()

    @JvmStatic
    fun register(controller: IONGLOCController) {
        NativeIslandsRegistry.register(
            componentName = "os.locationButton",
            accessibility = NativeIslandAccessibility.NATIVE,
            requiresUnobscuredSurface = requiresUnobscuredSurface(),
            factory = { context, activity -> IONGLOCLocationButtonIsland(context, activity, controller) },
        )
    }

    @JvmStatic
    fun requiresUnobscuredSurface(): Boolean =
        IONGLOCLocationButtonIsland.requiresUnobscuredSurface()

    @JvmStatic
    fun register(
        activity: Activity,
        controller: IONGLOCController,
        requester: IONGLOCLocationButtonPermissionRequester,
    ) {
        synchronized(permissionRequesters) {
            permissionRequesters[activity] = WeakReference(requester)
        }
        register(controller)
    }

    @JvmStatic
    fun unregister(activity: Activity) {
        synchronized(permissionRequesters) {
            permissionRequesters.remove(activity)
        }
    }

    internal fun permissionRequester(
        activity: Activity,
    ): IONGLOCLocationButtonPermissionRequester? =
        synchronized(permissionRequesters) {
            val reference = permissionRequesters[activity]
            val requester = reference?.get()
            if (reference != null && requester == null) {
                permissionRequesters.remove(activity)
            }
            requester
        }
}
