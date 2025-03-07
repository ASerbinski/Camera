package app.grapheneos.camera.ui.activities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidxc.exifinterface.media.ExifInterface
import androidx.viewpager2.widget.ViewPager2
import app.grapheneos.camera.GSlideTransformer
import app.grapheneos.camera.GallerySliderAdapter
import app.grapheneos.camera.R
import app.grapheneos.camera.capturer.VideoCapturer
import app.grapheneos.camera.databinding.GalleryBinding
import com.google.android.material.snackbar.Snackbar
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.properties.Delegates

class InAppGallery : AppCompatActivity() {

    lateinit var binding: GalleryBinding
    lateinit var gallerySlider: ViewPager2
    private val mediaUris: ArrayList<Uri> = arrayListOf()
    private var snackBar: Snackbar? = null
    private var ogColor by Delegates.notNull<Int>()

    private val isSecureMode: Boolean
        get() {
            return intent.extras?.containsKey("fileSP") == true
        }

    private val editIntentLauncher =
        registerForActivityResult(StartActivityForResult())
        { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                saveEditedImage(result.data?.data)
            }
        }

    private fun unexpectedError() = showMessage(getString(R.string.editing_unexpected_error))

    private fun saveEditedImage(editingUri : Uri?) {
        val contentUri = editingUri ?: return unexpectedError()
        try {
            contentResolver.openInputStream(contentUri).use { inStream ->
                inStream?.readBytes()?.let { editedStream ->
                    contentResolver.openOutputStream(getCurrentUri())?.use { outputStream ->
                        outputStream.write(editedStream)
                    }
                    showMessage(getString(R.string.edit_successfully))
                    recreate()
                }
            }
        } catch (ignored: SecurityException) {
            showMessage(getString(R.string.editing_permission_error))
        } catch (e: FileNotFoundException) {
            unexpectedError()
        }
    }

    private lateinit var rootView: View

    companion object {
        @SuppressLint("SimpleDateFormat")
        fun convertTime(time: Long, showTimeZone: Boolean = true): String {
            val date = Date(time)
            val format = SimpleDateFormat(
                if (showTimeZone) {
                    "yyyy-MM-dd HH:mm:ss z"
                } else {
                    "yyyy-MM-dd HH:mm:ss"
                }
            )
            format.timeZone = TimeZone.getDefault()
            return format.format(date)
        }

        fun convertTimeForVideo(time: String): String {
            val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val parsedDate = dateFormat.parse(time)
            return convertTime(parsedDate?.time ?: 0)
        }

        fun convertTimeForPhoto(time: String, offset: String? = null): String {

            val timestamp = if (offset != null) {
                "$time $offset"
            } else {
                time
            }

            val dateFormat = SimpleDateFormat(
                if (offset == null) {
                    "yyyy:MM:dd HH:mm:ss"
                } else {
                    "yyyy:MM:dd HH:mm:ss Z"
                }, Locale.US
            )

            if (offset == null) {
                dateFormat.timeZone = TimeZone.getDefault()
            }
            val parsedDate = dateFormat.parse(timestamp)
            return convertTime(parsedDate?.time ?: 0, offset != null)
        }

        fun getRelativePath(uri: Uri, path: String?, fileName: String): String {

            if (path == null) {
                val dPath = URLDecoder.decode(
                    uri.lastPathSegment,
                    "UTF-8"
                )

                val sType = dPath.substring(0, 7).replaceFirstChar {
                    it.uppercase()
                }

                val rPath = dPath.substring(8)

                return "($sType Storage) $rPath"
            }

            return "(Primary Storage) $path$fileName"
        }

    }

    private fun getCurrentUri(): Uri {
        return (gallerySlider.adapter as GallerySliderAdapter).getCurrentUri()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.gallery, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.edit_icon -> {
                editCurrentMedia()
                true
            }

            R.id.delete_icon -> {
                deleteCurrentMedia()
                true
            }

            R.id.info -> {
                showCurrentMediaDetails()
                true
            }

            R.id.share_icon -> {
                shareCurrentMedia()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun editCurrentMedia() {
        if (isSecureMode) {
            showMessage(getString(R.string.edit_not_allowed))
            return
        }

        val mediaUri = getCurrentUri()

        val editIntent = Intent(Intent.ACTION_EDIT)

        editIntent.putExtra(Intent.EXTRA_STREAM, mediaUri)
        editIntent.data = mediaUri

        editIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        editIntentLauncher.launch(
            Intent.createChooser(editIntent, getString(R.string.edit_image))
        )
    }

    private fun deleteCurrentMedia() {

        val mediaUri = getCurrentUri()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_description)
            .setPositiveButton(R.string.delete) { _, _ ->
                var res = false

                if (mediaUri.authority == MediaStore.AUTHORITY) {
                    try {
                        res = contentResolver.delete(mediaUri, null, null) == 1
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    val doc = DocumentFile.fromSingleUri(this, mediaUri)!!
                    res = doc.delete()
                }

                if (res) {
                    MainActivity.camConfig.removeFromGallery(mediaUri)
                    showMessage(getString(R.string.deleted_successfully))
                    (gallerySlider.adapter as GallerySliderAdapter).removeUri(mediaUri)
                } else {
                    showMessage(getString(R.string.deleting_unexpected_error))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()

    }

    private fun showCurrentMediaDetails() {
        val mediaUri = getCurrentUri()

        val mediaCursor = contentResolver.query(
            mediaUri,
            arrayOf(
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE
            ),
            null,
            null,
        )

        if (mediaCursor?.moveToFirst() != true) {
            showMessage(getString(R.string.unexpected_error))

            mediaCursor?.close()
            return
        }

        val relativePath = mediaCursor.getString(0)
        val fileName = mediaCursor.getString(1)
        val size = mediaCursor.getInt(2)

        mediaCursor.close()

        var dateAdded: String? = null
        var dateModified: String? = null

        if (VideoCapturer.isVideo(mediaUri)) {

            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(
                this,
                mediaUri
            )

            val date =
                convertTimeForVideo(
                    mediaMetadataRetriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DATE
                    )!!
                )

            dateAdded = date
            dateModified = date

        } else {
            val iStream = contentResolver.openInputStream(
                mediaUri
            )
            val eInterface = ExifInterface(iStream!!)

            val offset = eInterface.getAttribute(ExifInterface.TAG_OFFSET_TIME)

            if (eInterface.hasAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)) {
                dateAdded = convertTimeForPhoto(
                    eInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)!!,
                    offset
                )
            }

            if (eInterface.hasAttribute(ExifInterface.TAG_DATETIME)) {
                dateModified = convertTimeForPhoto(
                    eInterface.getAttribute(ExifInterface.TAG_DATETIME)!!,
                    offset
                )
            }

            iStream.close()
        }


        val alertDialog: AlertDialog.Builder =
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

        alertDialog.setTitle("File Details")

        val detailsBuilder = StringBuilder()

        detailsBuilder.append("\nFile Name: \n")
        detailsBuilder.append(fileName)
        detailsBuilder.append("\n\n")

        detailsBuilder.append("File Path: \n")
        detailsBuilder.append(getRelativePath(mediaUri, relativePath, fileName))
        detailsBuilder.append("\n\n")

        detailsBuilder.append("File Size: \n")
        if (size == 0) {
            detailsBuilder.append("Loading...")
        } else {
            detailsBuilder.append(
                String.format(
                    "%.2f",
                    (size / (1024f * 1024f))
                )
            )
            detailsBuilder.append(" mb")
        }

        detailsBuilder.append("\n\n")

        detailsBuilder.append("File Created On: \n")
        if (dateAdded == null) {
            detailsBuilder.append("Not found")
        } else {
            detailsBuilder.append(dateAdded)
        }

        detailsBuilder.append("\n\n")

        detailsBuilder.append("Last Modified On: \n")
        if (dateModified == null) {
            detailsBuilder.append("Not found")
        } else {
            detailsBuilder.append(dateModified)
        }

        alertDialog.setMessage(detailsBuilder)

        alertDialog.setPositiveButton("Ok", null)


        alertDialog.show()
    }

    private fun animateBackgroundToBlack() {

        val cBgColor = (rootView.background as ColorDrawable).color

        if (cBgColor == Color.BLACK) {
            return
        }

        val bgColorAnim = ValueAnimator.ofObject(
            ArgbEvaluator(),
            ogColor,
            Color.BLACK
        )
        bgColorAnim.duration = 300
        bgColorAnim.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            rootView.setBackgroundColor(color)
        }
        bgColorAnim.start()
    }

    private fun animateBackgroundToOriginal() {

        val cBgColor = (rootView.background as ColorDrawable).color

        if (cBgColor == ogColor) {
            return
        }

        val bgColorAnim = ValueAnimator.ofObject(
            ArgbEvaluator(),
            Color.BLACK,
            ogColor,
        )
        bgColorAnim.duration = 300
        bgColorAnim.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            this.rootView.setBackgroundColor(color)
        }
        bgColorAnim.start()
    }

    private fun shareCurrentMedia() {

        if (isSecureMode) {
            showMessage(
                getString(R.string.sharing_not_allowed)
            )
            return
        }

        val mediaUri = getCurrentUri()

        val share = Intent(Intent.ACTION_SEND)
        share.putExtra(Intent.EXTRA_STREAM, mediaUri)
        share.setDataAndType(mediaUri, if (VideoCapturer.isVideo(mediaUri)) {
            "video/*"
        } else {
            "image/*"
        })
        share.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        startActivity(
            Intent.createChooser(share, getString(R.string.share_image))
        )

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val showVideosOnly = intent.extras?.getBoolean("show_videos_only")!!
        val isSecureMode = intent.extras?.getBoolean("is_secure_mode") == true

        if (isSecureMode) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        ogColor = ContextCompat.getColor(this, R.color.system_neutral1_900)
        binding = GalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.let {
            it.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.appbar)))
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

        rootView = binding.rootView
        rootView.setOnClickListener {
            toggleActionBarState()
        }

        gallerySlider = binding.gallerySlider
        gallerySlider.setPageTransformer(GSlideTransformer())

        if (isSecureMode) {
            val spName = intent.extras?.getString("fileSP")
            val sp = getSharedPreferences(spName, Context.MODE_PRIVATE)
            val filePaths = sp.getString("filePaths", "")!!.split(",")
            val mediaFileArray: Array<Uri> =
                filePaths.stream().map { Uri.parse(it) }.toArray { length ->
                    arrayOfNulls<Uri>(length)
                }

            mediaUris.addAll(mediaFileArray)
        } else {

            if (MainActivity.isCamConfigInitialized()) {
                if (showVideosOnly) {
                    for (mediaUri in MainActivity.camConfig.mediaUris) {
                        if (VideoCapturer.isVideo(mediaUri)) {
                            mediaUris.add(mediaUri)
                        }
                    }
                } else {
                    mediaUris.addAll(MainActivity.camConfig.mediaUris)
                }
            } else {
                finish()
            }
        }

        // Close gallery if no files are present
        if (mediaUris.isEmpty()) {
            showMessage(getString(R.string.no_image))
            finish()
        }

        gallerySlider.adapter = GallerySliderAdapter(this, mediaUris)

        snackBar = Snackbar.make(gallerySlider, "", Snackbar.LENGTH_LONG)

    }

    fun toggleActionBarState() {
        supportActionBar?.let {
            if (it.isShowing) {
                hideActionBar()
            } else {
                showActionBar()
            }
        }
    }

    fun showActionBar() {
        supportActionBar?.let {
            it.show()
            animateBackgroundToOriginal()
        }
    }

    fun hideActionBar() {
        supportActionBar?.let {
            it.hide()
            animateBackgroundToBlack()
        }
    }

    private fun uriExists(uri: Uri): Boolean {
        try {
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return false
            inputStream.close()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun onResume() {
        super.onResume()

        val gsaUris = (gallerySlider.adapter as GallerySliderAdapter).mediaUris

        if (isSecureMode) {

            val newUris: ArrayList<Uri> = arrayListOf()

            for (mediaUri in gsaUris) {
                if (uriExists(mediaUri)) {
                    newUris.add(mediaUri)
                }
            }

            // If mediaUris have changed
            if (mediaUris.size != newUris.size) {
                gallerySlider.adapter = GallerySliderAdapter(this, newUris)
            }

        } else {

            if (MainActivity.isCamConfigInitialized()){
                val newUris = MainActivity.camConfig.mediaUris
                var urisHaveChanged = false

                for (mediaUri in gsaUris) {
                    if (!newUris.contains(mediaUri)) {
                        urisHaveChanged = true
                        break
                    }
                }

                if (urisHaveChanged) {
                    gallerySlider.adapter = GallerySliderAdapter(this, newUris)
                }
            } else {
                finish()
            }
        }

        showActionBar()
    }

    fun showMessage(msg: String) {
        snackBar?.setText(msg)
        snackBar?.show()
    }
}
