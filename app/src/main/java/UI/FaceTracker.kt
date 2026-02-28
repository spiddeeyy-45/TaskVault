package UI


import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs

class FaceTracker {

    companion object {
        private const val TAG = "FaceTracker"
        private const val STABLE_FACE_COUNT = 3
        private const val HEAD_ROLL_THRESHOLD = 15f
        private const val HEAD_PITCH_THRESHOLD = 20f
        private const val HEAD_YAW_THRESHOLD = 25f
    }

    private var stableFaceCount = 0
    private var lastFaceBounds: Rect? = null
    private var isTrackingActive = false
    private var lastEyeClosedState = false

    data class TrackingResult(
        val isEyeClosed: Boolean,
        val isYawning: Boolean,
        val isLookingAway: Boolean,
        val confidence: Float
    )

    fun analyzeFace(face: Face, imageWidth: Int, imageHeight: Int): TrackingResult {
        // Check face orientation first
        val isLookingAway = checkHeadPose(face)

        if (isLookingAway) {
            resetTracking()
            return TrackingResult(
                isEyeClosed = false,
                isYawning = false,
                isLookingAway = true,
                confidence = 0f
            )
        }

        // Check for stable face
        val bounds = face.boundingBox
        if (!isFaceStable(bounds)) {
            return TrackingResult(
                isEyeClosed = false,
                isYawning = false,
                isLookingAway = false,
                confidence = 0.3f
            )
        }

        // Analyze facial features
        val eyeClosed = checkEyesClosed(face)
        val yawning = checkYawning(face)

        // Calculate overall confidence
        val confidence = calculateConfidence(face)

        return TrackingResult(
            isEyeClosed = eyeClosed,
            isYawning = yawning,
            isLookingAway = false,
            confidence = confidence
        )
    }

    private fun checkHeadPose(face: Face): Boolean {
        return abs(face.headEulerAngleY) > HEAD_YAW_THRESHOLD ||
                abs(face.headEulerAngleX) > HEAD_PITCH_THRESHOLD ||
                abs(face.headEulerAngleZ) > HEAD_ROLL_THRESHOLD
    }

    private fun checkEyesClosed(face: Face): Boolean {
        val left = face.leftEyeOpenProbability ?: 1f
        val right = face.rightEyeOpenProbability ?: 1f

        val avg = (left + right) / 2f

        // Hysteresis thresholds
        val CLOSE_THRESHOLD = 0.45f
        val OPEN_THRESHOLD = 0.55f

        lastEyeClosedState = when {
            avg < CLOSE_THRESHOLD -> true
            avg > OPEN_THRESHOLD -> false
            else -> lastEyeClosedState
        }

        return lastEyeClosedState
    }

    private fun checkYawning(face: Face): Boolean {

        val leftEyeProb = face.leftEyeOpenProbability ?: 1f
        val rightEyeProb = face.rightEyeOpenProbability ?: 1f

        val upperLipBottom = face.getContour(FaceContour.UPPER_LIP_BOTTOM)
        val lowerLipTop = face.getContour(FaceContour.LOWER_LIP_TOP)

        if (upperLipBottom != null && lowerLipTop != null &&
            upperLipBottom.points.isNotEmpty() &&
            lowerLipTop.points.isNotEmpty()
        ) {

            val upperLipY = upperLipBottom.points.map { it.y }.average().toFloat()
            val lowerLipY = lowerLipTop.points.map { it.y }.average().toFloat()
            val mouthOpenDistance = abs(lowerLipY - upperLipY)

            val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)
            val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)

            if (leftMouth != null && rightMouth != null) {

                val mouthWidth = abs(rightMouth.position.x - leftMouth.position.x)

                if (mouthWidth > 0) {

                    val mouthAspectRatio = mouthOpenDistance / mouthWidth

                    // 🔥 tuned thresholds
                    val strongYawn = mouthAspectRatio > 0.38f
                    val softYawn = mouthAspectRatio > 0.28f &&
                            (leftEyeProb < 0.6f || rightEyeProb < 0.6f)

                    return strongYawn || softYawn
                }
            }
        }

        return false
    }

    private fun isFaceStable(newBounds: Rect): Boolean {
        lastFaceBounds?.let { last ->
            val widthDiff = abs(last.width() - newBounds.width()).toFloat() / last.width()
            val heightDiff = abs(last.height() - newBounds.height()).toFloat() / last.height()
            val centerXDiff = abs(last.centerX() - newBounds.centerX()).toFloat() / last.width()
            val centerYDiff = abs(last.centerY() - newBounds.centerY()).toFloat() / last.height()

            // If face moved significantly, reset stability
            if (widthDiff > 0.15f || heightDiff > 0.15f ||
                centerXDiff > 0.1f || centerYDiff > 0.1f) {
                stableFaceCount = 0
                lastFaceBounds = newBounds
                return false
            }
        }

        lastFaceBounds = newBounds
        stableFaceCount++

        return stableFaceCount >= STABLE_FACE_COUNT
    }

    private fun calculateConfidence(face: Face): Float {
        var confidence = 0f
        var totalFeatures = 0

        face.leftEyeOpenProbability?.let {
            confidence += if (it < 0.3f) 0.8f else 0.2f
            totalFeatures++
        }

        face.rightEyeOpenProbability?.let {
            confidence += if (it < 0.3f) 0.8f else 0.2f
            totalFeatures++
        }

        face.smilingProbability?.let {
            confidence += if (it < 0.1f) 0.7f else 0.3f
            totalFeatures++
        }

        return if (totalFeatures > 0) confidence / totalFeatures else 0f
    }

    private fun resetTracking() {
        stableFaceCount = 0
        lastFaceBounds = null
    }

    fun reset() {
        resetTracking()
        isTrackingActive = false
    }
}