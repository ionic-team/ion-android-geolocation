package io.ionic.libs.iongeolocationlib.view

import android.app.Activity
import io.ionic.libs.iongeolocationlib.controller.IONGLOCController
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult
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
    fun register(
        controller: IONGLOCController,
        errorCodeMapper: ((Throwable) -> String?)? = null,
        positionMapper: ((IONGLOCLocationResult) -> Map<String, Any?>)? = null,
    ) {
        NativeIslandsRegistry.register(
            componentName = "os.locationButton",
            accessibility = NativeIslandAccessibility.NATIVE,
            requiresUnobscuredSurface = requiresUnobscuredSurface(),
            factory = { context, activity ->
                IONGLOCLocationButtonIsland(context, activity, controller, errorCodeMapper, positionMapper)
            },
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
        errorCodeMapper: ((Throwable) -> String?)? = null,
        positionMapper: ((IONGLOCLocationResult) -> Map<String, Any?>)? = null,
    ) {
        synchronized(permissionRequesters) {
            permissionRequesters[activity] = WeakReference(requester)
        }
        register(controller, errorCodeMapper, positionMapper)
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
