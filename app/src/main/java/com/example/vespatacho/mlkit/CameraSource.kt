package com.example.vespatacho.mlkit

import android.annotation.SuppressLint
import android.app.Activity
import android.hardware.Camera
import com.google.android.gms.common.images.Size
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Manages the camera and allows UI updates on top of it (e.g. overlaying extra Graphics or
 * displaying extra information). This receives preview frames from the camera at a specified rate,
 * sending those frames to child classes' detectors / classifiers as fast as it is able to process.
 */
open class CameraSource(protected var activity: Activity, private val graphicOverlay: GraphicOverlay) {
    private var camera: Camera? = null

    /** Rotation of the device, and thus the associated preview images captured from the device.  */
    private var rotationDegrees = 0

    private var previewSize: Size? = null

    private val processingRunnable: FrameProcessingRunnable
    private val processorLock = Any()

    private var frameProcessor: VisionImageProcessor? = null

    init {
        graphicOverlay.clear()
        processingRunnable = FrameProcessingRunnable()
    }

    // ==============================================================================================
    // Public
    // ==============================================================================================

    /**
     * Stores a preview size and a corresponding same-aspect-ratio picture size. To avoid distorted
     * preview images on some devices, the picture size must be set to a size that is the same aspect
     * ratio as the preview size or the preview may end up being distorted. If the picture size is
     * null, then there is no picture size with the same aspect ratio as the preview size.
     */
    class SizePair {
        val preview: Size
        val picture: Size?

        constructor(previewSize: Size, pictureSize: Size?) {
            preview = previewSize
            picture = pictureSize
        }
    }

    // ==============================================================================================
    // Frame processing
    // ==============================================================================================

    /**
     * This runnable controls access to the underlying receiver, calling it to process frames when
     * available from the camera. This is designed to run detection on frames as fast as possible
     * (i.e., without unnecessary context switching or waiting on the next frame).
     * 
     * 
     * While detection is running on a frame, new frames may be received from the camera. As these
     * frames come in, the most recent frame is held onto as pending. As soon as detection and its
     * associated processing is done for the previous frame, detection on the mostly recently received
     * frame will immediately start on the same thread.
     */
    private inner class FrameProcessingRunnable : Runnable {
        // This lock guards all of the member variables below.
        private val lock = Any()
        private var active = true

        // These pending variables hold the state associated with the new frame awaiting processing.
        private var pendingFrameData: ByteBuffer? = null

        /**
         * As long as the processing thread is active, this executes detection on frames continuously.
         * The next pending frame is either immediately available or hasn't been received yet. Once it
         * is available, we transfer the frame info to local variables and run detection on that frame.
         * It immediately loops back for the next frame without pausing.
         * 
         * 
         * If detection takes longer than the time in between new frames from the camera, this will
         * mean that this loop will run without ever waiting on a frame, avoiding any context switching
         * or frame acquisition time latency.
         * 
         * 
         * If you find that this is using more CPU than you'd like, you should probably decrease the
         * FPS setting above to allow for some idle time in between frames.
         */
        @SuppressLint("InlinedApi")
        override fun run() {
            var data: ByteBuffer?

            while (true) {
                synchronized(lock) {
                    while (active && (pendingFrameData == null)) {
                        try {
                            // Wait for the next frame to be received from the camera, since we
                            // don't have it yet.
                            (lock as Object).wait()
                        } catch (e: InterruptedException) {
                            Timber.d(e, "Frame processing loop terminated.")
                            return
                        }
                    }
                    if (!active) {
                        // Exit the loop once this camera source is stopped or released.  We check
                        // this here, immediately after the wait() above, to handle the case where
                        // setActive(false) had been called, triggering the termination of this
                        // loop.
                        return
                    }

                    // Hold onto the frame data locally, so that we can use this for detection
                    // below.  We need to clear pendingFrameData to ensure that this buffer isn't
                    // recycled back to the camera before we are done using that data.
                    data = pendingFrameData
                    pendingFrameData = null
                }

                // The code below needs to run outside of synchronization, because this will allow
                // the camera to add pending frame(s) while we are running detection on the current
                // frame.
                try {
                    synchronized(processorLock) {
                        frameProcessor!!.processByteBuffer(
                            data,
                            FrameMetadata.Builder()
                                .setWidth(previewSize!!.getWidth())
                                .setHeight(previewSize!!.getHeight())
                                .setRotation(rotationDegrees)
                                .build(),
                            graphicOverlay
                        )
                    }
                } catch (t: Exception) {
                    Timber.e(t, "Exception thrown from receiver.")
                } finally {
                    camera!!.addCallbackBuffer(data!!.array())
                }
            }
        }
    }

    companion object {
        @SuppressLint("InlinedApi")
        const val CAMERA_FACING_BACK: Int = Camera.CameraInfo.CAMERA_FACING_BACK

        @SuppressLint("InlinedApi")
        const val CAMERA_FACING_FRONT: Int = Camera.CameraInfo.CAMERA_FACING_FRONT

    }
}
