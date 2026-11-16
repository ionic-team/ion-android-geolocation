package io.ionic.libs.iongeolocationlib.view

import io.ionic.libs.iongeolocationlib.controller.IONGLOCController
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult
import io.ionic.libs.ionnativeislandslib.NativeIslandAccessibility
import io.ionic.libs.ionnativeislandslib.NativeIslandsRegistry

/** Registers the location button island. */
object IONGLOCLocationButtonRegistry {
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
}
