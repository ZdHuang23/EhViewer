/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.tags.GalleryTagDatabase
import com.hippo.ehviewer.tags.TagSyncWorker
import com.hippo.ehviewer.ui.keepNoMediaFileStatus
import com.hippo.ehviewer.ui.scene.BaseScene
import com.hippo.unifile.UniFile
import com.hippo.util.ExceptionUtils
import com.hippo.util.launchNonCancellable
import kotlinx.coroutines.launch

class DownloadFragment : BasePreferenceFragment() {
    private var adJobs: List<androidx.work.WorkInfo> = emptyList()
    private var storageJob: kotlinx.coroutines.Job? = null
    private var tagSyncFailed = false
    private var mDownloadLocation: Preference? = null
    private var pickImageDirLauncher = registerForActivityResult<Uri?, Uri>(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            requireActivity().contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val uniFile = UniFile.fromTreeUri(requireActivity(), treeUri)
            if (uniFile != null) {
                Settings.putDownloadLocation(uniFile)
                lifecycleScope.launchNonCancellable {
                    keepNoMediaFileStatus()
                }
                onUpdateDownloadLocation()
            } else {
                showTip(
                    R.string.settings_download_cant_get_download_location,
                    BaseScene.LENGTH_SHORT,
                )
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.download_settings)
        mDownloadLocation = findPreference(Settings.KEY_DOWNLOAD_LOCATION)
        val mediaScan = findPreference<Preference>(Settings.KEY_MEDIA_SCAN)
        val multiThreadDownload = findPreference<Preference>(Settings.KEY_MULTI_THREAD_DOWNLOAD)
        val downloadDelay = findPreference<Preference>(Settings.KEY_DOWNLOAD_DELAY)
        val preloadImage = findPreference<Preference>(Settings.KEY_PRELOAD_IMAGE)
        val downloadOriginImage = findPreference<Preference>(Settings.KEY_DOWNLOAD_ORIGIN_IMAGE)
        mDownloadLocation?.onPreferenceClickListener = this
        mediaScan!!.onPreferenceChangeListener = this
        multiThreadDownload!!.setSummaryProvider {
            getString(R.string.settings_download_concurrency_summary, (it as ListPreference).entry)
        }
        downloadDelay!!.setSummaryProvider {
            getString(R.string.settings_download_download_delay_summary, (it as ListPreference).entry)
        }
        preloadImage!!.setSummaryProvider {
            getString(R.string.settings_download_preload_image_summary, (it as ListPreference).entry)
        }
        downloadOriginImage!!.setSummaryProvider {
            getString(R.string.settings_download_download_origin_image_summary, (it as ListPreference).entry)
        }
        onUpdateDownloadLocation()
        listOf("tag_fill", "tag_retry", "tag_refresh", "tag_status").forEach { key ->
            findPreference<Preference>(key)?.setOnPreferenceClickListener {
                if (key == "tag_status") {
                    refreshTagStatus()
                } else if (key == "tag_refresh") {
                    AlertDialog.Builder(requireContext()).setMessage(R.string.tag_refresh_confirm)
                        .setPositiveButton(android.R.string.ok) { _, _ -> TagSyncWorker.schedule(requireContext(), seed = true, refresh = true) }
                        .setNegativeButton(android.R.string.cancel, null).show()
                } else {
                    TagSyncWorker.schedule(requireContext(), seed = true, retry = key == "tag_retry")
                    showTip(R.string.tag_started, BaseScene.LENGTH_SHORT)
                }
                true
            }
        }
        listOf("tag_auto_sync", "tag_sync_paused").forEach { key ->
            findPreference<SwitchPreferenceCompat>(key)?.setOnPreferenceChangeListener { _, value ->
                if (key == "tag_auto_sync" && value == true || key == "tag_sync_paused" && value == false) {
                    // Post until the preference value has been committed.
                    android.os.Handler(android.os.Looper.getMainLooper()).post { TagSyncWorker.schedule(requireContext()) }
                }
                true
            }
        }
        androidx.work.WorkManager.getInstance(requireContext()).getWorkInfosForUniqueWorkLiveData("gallery-tag-sync")
            .observe(this) { jobs ->
                tagSyncFailed = jobs.lastOrNull()?.state == androidx.work.WorkInfo.State.FAILED
                refreshTagStatus()
            }
        refreshTagStatus()
        findPreference<Preference>("ad_clean_all")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext()).setMessage(R.string.ad_bulk_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    com.hippo.ehviewer.ads.AdCleanupWorker.schedule(requireContext(), bulk = true)
                }.setNegativeButton(android.R.string.cancel, null).show()
            true
        }
        listOf("ad_auto", "ad_paused").forEach { key ->
            findPreference<SwitchPreferenceCompat>(key)?.setOnPreferenceChangeListener { _, value ->
                if (key == "ad_auto" && value == true || key == "ad_paused" && value == false) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (key == "ad_auto") TagSyncWorker.schedule(requireContext(), seed = true)
                        com.hippo.ehviewer.ads.AdCleanupWorker.schedule(requireContext())
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post { if (isAdded) refreshAdStatus() }
                true
            }
        }
        androidx.work.WorkManager.getInstance(requireContext()).getWorkInfosForUniqueWorkLiveData(com.hippo.ehviewer.ads.AdCleanupWorker.WORK)
            .observe(this) { jobs ->
                adJobs = jobs
                refreshAdStatus()
                if (jobs.none { it.state == androidx.work.WorkInfo.State.RUNNING }) refreshStorage()
            }
    }

    private fun refreshAdStatus() {
        val active = adJobs.lastOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val data = active?.progress
        val state = when {
            prefs.getBoolean("ad_paused", false) -> getString(R.string.ad_state_paused)
            active != null -> getString(R.string.ad_state_running)
            adJobs.any { !it.state.isFinished } -> getString(R.string.ad_state_queued)
            else -> getString(R.string.ad_state_idle)
        }
        findPreference<Preference>("ad_status")?.summary = state + "\n" + getString(
            R.string.ad_progress,
            data?.getInt("done", 0) ?: prefs.getInt("ad_done", 0),
            data?.getInt("total", 0) ?: prefs.getInt("ad_total", 0),
            data?.getInt("removed", 0) ?: prefs.getInt("ad_removed", 0),
            data?.getInt("errors", 0) ?: prefs.getInt("ad_errors", 0),
            data?.getInt("incomplete", 0) ?: prefs.getInt("ad_incomplete", 0),
            data?.getInt("waiting", 0) ?: prefs.getInt("ad_waiting", 0),
        )
    }

    override fun onResume() {
        super.onResume()
        refreshAdStatus()
        refreshStorage()
    }

    private fun refreshStorage() {
        if (storageJob?.isActive == true) return
        val context = requireContext().applicationContext
        val location = Settings.downloadLocation
        findPreference<Preference>("download_storage")?.setSummary(R.string.ad_storage_calculating)
        storageJob = lifecycleScope.launch {
            try {
                val (usage, cacheBytes) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    location?.let { com.hippo.ehviewer.ads.DownloadStorage.measure(it) } to com.hippo.ehviewer.EhApplication.imageCache.size
                }
                fun format(bytes: Long) = android.text.format.Formatter.formatFileSize(context, bytes)
                findPreference<Preference>("download_storage")?.summary = getString(
                    R.string.ad_storage_summary,
                    usage?.let { format(it.bytes) } ?: getString(R.string.settings_download_invalid_download_location),
                    format(cacheBytes),
                ) + if (usage?.unknown != null && usage.unknown > 0) "\n" + getString(R.string.ad_storage_partial, usage.unknown) else ""
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                findPreference<Preference>("download_storage")?.setSummary(R.string.ad_storage_failed)
            }
        }
    }

    private fun refreshTagStatus() {
        lifecycleScope.launch {
            val rows = GalleryTagDatabase.get(requireContext()).tags().all()
            val summary = getString(
                R.string.tag_stats,
                rows.count { it.complete },
                rows.count { it.status == "pending" },
                rows.count { it.status == "unavailable" },
                rows.count { it.status == "failed" },
                rows.count { it.status == "missing" },
            )
            findPreference<Preference>("tag_status")?.summary = if (tagSyncFailed) "$summary\n${getString(R.string.tag_sync_failed)}" else summary
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mDownloadLocation = null
    }

    private fun onUpdateDownloadLocation() {
        val file = Settings.downloadLocation
        if (mDownloadLocation != null) {
            if (file != null) {
                mDownloadLocation!!.summary = file.uri.toString()
            } else {
                mDownloadLocation!!.setSummary(R.string.settings_download_invalid_download_location)
            }
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val key = preference.key
        if (Settings.KEY_DOWNLOAD_LOCATION == key) {
            val file = Settings.downloadLocation
            if (file != null &&
                !UniFile.isFileUri(Settings.downloadLocation!!.uri)
            ) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_download_download_location)
                    .setMessage(file.uri.toString())
                    .setPositiveButton(R.string.settings_download_pick_new_location) { _, _ -> openDirPickerL() }
                    .setNeutralButton(R.string.settings_download_reset_location) { _, _ ->
                        val uniFile = UniFile.fromFile(AppConfig.getDefaultDownloadDir())
                        if (uniFile != null) {
                            Settings.putDownloadLocation(uniFile)
                            lifecycleScope.launchNonCancellable {
                                keepNoMediaFileStatus()
                            }
                            onUpdateDownloadLocation()
                        } else {
                            showTip(
                                R.string.settings_download_cant_get_download_location,
                                BaseScene.LENGTH_SHORT,
                            )
                        }
                    }
                    .show()
            } else {
                openDirPickerL()
            }
            return true
        }
        return false
    }

    private fun openDirPickerL() {
        try {
            pickImageDirLauncher.launch(null)
        } catch (e: Throwable) {
            ExceptionUtils.throwIfFatal(e)
            showTip(R.string.error_cant_find_activity, BaseScene.LENGTH_SHORT)
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val key = preference.key
        if (Settings.KEY_MEDIA_SCAN == key) {
            if (newValue is Boolean) {
                lifecycleScope.launchNonCancellable {
                    keepNoMediaFileStatus()
                }
            }
            return true
        }
        return false
    }

    override val fragmentTitle: Int
        get() = R.string.settings_download
}
