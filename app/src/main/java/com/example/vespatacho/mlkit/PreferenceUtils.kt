package com.example.vespatacho.mlkit

import android.content.Context
import android.preference.PreferenceManager
import androidx.camera.core.CameraSelector
import com.example.vespatacho.R
import com.example.vespatacho.mlkit.CameraSource.SizePair
import com.google.android.gms.common.images.Size
import com.google.common.base.Preconditions

/** Utility class to retrieve shared preferences.  */
object PreferenceUtils {

    fun getCameraPreviewSizePair(context: Context, cameraId: Int): SizePair? {
        Preconditions.checkArgument(
            cameraId == CameraSource.CAMERA_FACING_BACK
                    || cameraId == CameraSource.CAMERA_FACING_FRONT
        )
        val previewSizePrefKey: String?
        val pictureSizePrefKey: String?
        if (cameraId == CameraSource.CAMERA_FACING_BACK) {
            previewSizePrefKey = context.getString(R.string.pref_key_rear_camera_preview_size)
            pictureSizePrefKey = context.getString(R.string.pref_key_rear_camera_picture_size)
        } else {
            previewSizePrefKey = context.getString(R.string.pref_key_front_camera_preview_size)
            pictureSizePrefKey = context.getString(R.string.pref_key_front_camera_picture_size)
        }

        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            return SizePair(
                Size.parseSize(sharedPreferences.getString(previewSizePrefKey, null)!!),
                Size.parseSize(sharedPreferences.getString(pictureSizePrefKey, null)!!)
            )
        } catch (e: Exception) {
            return null
        }
    }

    fun getCameraXTargetResolution(context: Context, lensfacing: Int): android.util.Size? {
        Preconditions.checkArgument(
            lensfacing == CameraSelector.LENS_FACING_BACK
                    || lensfacing == CameraSelector.LENS_FACING_FRONT
        )
        val prefKey =
            if (lensfacing == CameraSelector.LENS_FACING_BACK)
                context.getString(R.string.pref_key_camerax_rear_camera_target_resolution)
            else
                context.getString(R.string.pref_key_camerax_front_camera_target_resolution)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            return android.util.Size.parseSize(sharedPreferences.getString(prefKey, null))
        } catch (_: Exception) {
            return null
        }
    }

    @JvmStatic
    fun shouldHideDetectionInfo(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_info_hide)
        return sharedPreferences.getBoolean(prefKey, false)
    }

    @JvmStatic
    fun shouldGroupRecognizedTextInBlocks(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_group_recognized_text_in_blocks)
        return sharedPreferences.getBoolean(prefKey, false)
    }

    @JvmStatic
    fun showLanguageTag(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_show_language_tag)
        return sharedPreferences.getBoolean(prefKey, false)
    }

    @JvmStatic
    fun shouldShowTextConfidence(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_show_text_confidence)
        return sharedPreferences.getBoolean(prefKey, false)
    }

    @JvmStatic
    fun isCameraLiveViewportEnabled(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_camera_live_viewport)
        return sharedPreferences.getBoolean(prefKey, false)
    }
}
