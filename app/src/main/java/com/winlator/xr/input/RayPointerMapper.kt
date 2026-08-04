package com.winlator.xr.input

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A host-local coordinate system: +X right, +Y up, and -Z forward from the viewer. */
data class PointerVector3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    fun dot(other: PointerVector3): Float = x * other.x + y * other.y + z * other.z

    fun plus(other: PointerVector3): PointerVector3 = PointerVector3(x + other.x, y + other.y, z + other.z)

    fun minus(other: PointerVector3): PointerVector3 = PointerVector3(x - other.x, y - other.y, z - other.z)

    fun times(scale: Float): PointerVector3 = PointerVector3(x * scale, y * scale, z * scale)

    fun normalizedOrNull(): PointerVector3? {
        val length = sqrt(dot(this))
        return if (length > EPSILON) times(1f / length) else null
    }

    private companion object {
        private const val EPSILON = 0.00001f
    }
}

data class ControllerPointerRay(
    val origin: PointerVector3,
    val direction: PointerVector3,
) {
    companion object {
        /**
         * Converts the existing XR yaw/pitch axes to the documented host-local forward ray.
         * Positive yaw turns right; positive pitch turns up. Roll does not affect a laser pointer.
         */
        fun fromYawPitch(origin: PointerVector3, yawDegrees: Float, pitchDegrees: Float): ControllerPointerRay {
            val yaw = yawDegrees / 180f * PI.toFloat()
            val pitch = pitchDegrees / 180f * PI.toFloat()
            val horizontal = cos(pitch)
            return ControllerPointerRay(
                origin = origin,
                direction = PointerVector3(
                    x = sin(yaw) * horizontal,
                    y = sin(pitch),
                    z = -cos(yaw) * horizontal,
                ),
            )
        }
    }
}

/**
 * A rectangular virtual screen. `right` and `up` must be non-parallel screen-space basis vectors.
 * The rendering bridge will derive this from the same virtual-screen transform used by XrRenderer.
 */
data class VirtualScreenSurface(
    val center: PointerVector3,
    val normal: PointerVector3,
    val right: PointerVector3,
    val up: PointerVector3,
    val widthMeters: Float,
    val heightMeters: Float,
    val pixelWidth: Int,
    val pixelHeight: Int,
) {
    init {
        require(widthMeters > 0f) { "widthMeters must be positive" }
        require(heightMeters > 0f) { "heightMeters must be positive" }
        require(pixelWidth > 0) { "pixelWidth must be positive" }
        require(pixelHeight > 0) { "pixelHeight must be positive" }
    }

    companion object {
        fun facingViewer(
            distanceMeters: Float,
            widthMeters: Float,
            heightMeters: Float,
            pixelWidth: Int,
            pixelHeight: Int,
        ): VirtualScreenSurface {
            require(distanceMeters > 0f) { "distanceMeters must be positive" }
            return VirtualScreenSurface(
                center = PointerVector3(0f, 0f, -distanceMeters),
                normal = PointerVector3(0f, 0f, 1f),
                right = PointerVector3(1f, 0f, 0f),
                up = PointerVector3(0f, 1f, 0f),
                widthMeters = widthMeters,
                heightMeters = heightMeters,
                pixelWidth = pixelWidth,
                pixelHeight = pixelHeight,
            )
        }
    }
}

data class RayPointerHit(
    val rawNormalizedX: Float,
    val rawNormalizedY: Float,
    val normalizedX: Float,
    val normalizedY: Float,
    val pixelX: Int,
    val pixelY: Int,
    val distanceMeters: Float,
    val isInsideSurface: Boolean,
) {
    val wasClamped: Boolean get() = !isInsideSurface
}

interface RayPointerMapper {
    /** Returns null for an invalid/parallel/behind-screen ray; out-of-bounds hits are clamped. */
    fun map(ray: ControllerPointerRay, surface: VirtualScreenSurface): RayPointerHit?
}

class RayPointerMapperImpl : RayPointerMapper {
    override fun map(ray: ControllerPointerRay, surface: VirtualScreenSurface): RayPointerHit? {
        val direction = ray.direction.normalizedOrNull() ?: return null
        val normal = surface.normal.normalizedOrNull() ?: return null
        val right = surface.right.normalizedOrNull() ?: return null
        val up = surface.up.normalizedOrNull() ?: return null
        if (abs(right.dot(up)) > BASIS_PARALLEL_LIMIT ||
            abs(right.dot(normal)) > BASIS_PARALLEL_LIMIT ||
            abs(up.dot(normal)) > BASIS_PARALLEL_LIMIT
        ) {
            return null
        }

        val denominator = normal.dot(direction)
        if (abs(denominator) < PARALLEL_LIMIT) return null
        val distance = normal.dot(surface.center.minus(ray.origin)) / denominator
        if (distance <= MIN_FORWARD_DISTANCE) return null

        val point = ray.origin.plus(direction.times(distance))
        val offset = point.minus(surface.center)
        val rawNormalizedX = offset.dot(right) / surface.widthMeters + 0.5f
        val rawNormalizedY = 0.5f - offset.dot(up) / surface.heightMeters
        val inside = rawNormalizedX in 0f..1f && rawNormalizedY in 0f..1f
        val normalizedX = rawNormalizedX.coerceIn(0f, 1f)
        val normalizedY = rawNormalizedY.coerceIn(0f, 1f)

        return RayPointerHit(
            rawNormalizedX = rawNormalizedX,
            rawNormalizedY = rawNormalizedY,
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            pixelX = (normalizedX * (surface.pixelWidth - 1)).roundToInt(),
            pixelY = (normalizedY * (surface.pixelHeight - 1)).roundToInt(),
            distanceMeters = distance,
            isInsideSurface = inside,
        )
    }

    private companion object {
        private const val PARALLEL_LIMIT = 0.0001f
        private const val BASIS_PARALLEL_LIMIT = 0.001f
        private const val MIN_FORWARD_DISTANCE = 0.0001f
    }
}
