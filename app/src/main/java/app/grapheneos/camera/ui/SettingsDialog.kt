package app.grapheneos.camera.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ToggleButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.QualitySelector
import app.grapheneos.camera.R
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.ui.activities.MainActivity
import android.provider.Settings
import app.grapheneos.camera.ui.activities.MoreSettings


class SettingsDialog(mActivity: MainActivity) :
    Dialog(mActivity, R.style.Theme_App) {

    private var dialog: View
    var locToggle: ToggleButton
    private var flashToggle: ImageView
    private var aRToggle: ToggleButton
    var torchToggle: ToggleButton
    private var gridToggle: ImageView
    private var mActivity: MainActivity
    var videoQualitySpinner: Spinner
    private lateinit var vQAdapter: ArrayAdapter<String>
    private var focusTimeoutSpinner: Spinner
    private var timerSpinner: Spinner

    var mScrollView: ScrollView
    var mScrollViewContent: View

    var cmRadioGroup: RadioGroup
    var qRadio: RadioButton
    var lRadio: RadioButton

    var includeAudioToggle: SwitchCompat

    var selfIlluminationToggle: SwitchCompat
    var csSwitch: SwitchCompat

    var sIAPToggle: SwitchCompat

    private val timeOptions = mActivity.resources.getStringArray(R.array.time_options)

    private var includeAudioSetting: View
    private var selfIlluminationSetting: View
    private var sIAPSetting: View
    private var videoQualitySetting: View
    private var timerSetting: View

    private var settingsFrame: View

    private var moreSettingsButton: View

    private val bgBlue = mActivity.getColor(R.color.selected_option_bg)

    init {
        setContentView(R.layout.settings)

        dialog = findViewById(R.id.settings_dialog)
        dialog.setOnClickListener {}

        moreSettingsButton = findViewById(R.id.more_settings)
        moreSettingsButton.setOnClickListener {
            if (!mActivity.videoCapturer.isRecording) {
                val mSIntent = Intent(mActivity, MoreSettings::class.java)
                mActivity.startActivity(mSIntent)
            } else {
                mActivity.showMessage("Cannot switch activities when recording")
            }
        }

        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setDimAmount(0f)

        setOnDismissListener {
            mActivity.settingsIcon.visibility = View.VISIBLE
        }

        this.mActivity = mActivity

        val background: View = findViewById(R.id.background)
        background.setOnClickListener {
            slideDialogUp()
        }

        val rootView = findViewById<SettingsFrameLayout>(R.id.root)
        rootView.setOnInterceptTouchEventListener(
            object: SettingsFrameLayout.OnInterceptTouchEventListener {

                override fun onInterceptTouchEvent(
                    view: SettingsFrameLayout?,
                    ev: MotionEvent?,
                    disallowIntercept: Boolean
                ): Boolean {
                    return mActivity.gestureDetectorCompat.onTouchEvent(ev)
                }

                override fun onTouchEvent(view: SettingsFrameLayout?, event: MotionEvent?): Boolean {
                    return false
                }
            }
        )

        settingsFrame = findViewById(R.id.settings_frame)

        rootView.viewTreeObserver.addOnPreDrawListener(
            object: OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    rootView.viewTreeObserver.removeOnPreDrawListener(this)

                    settingsFrame.layoutParams =
                                (settingsFrame.layoutParams as ViewGroup.MarginLayoutParams).let {
                                    val marginTop = (mActivity.rootView.layoutParams as ViewGroup.MarginLayoutParams).topMargin
                                    it.height = (marginTop + (rootView.measuredWidth * 4/3))
                                    it
                                }

                    return true
                }
            }
        )

        locToggle = findViewById(R.id.location_toggle)
        locToggle.setOnClickListener {

            if (MainActivity.camConfig.isVideoMode) {
                MainActivity.camConfig.requireLocation = false
                mActivity.showMessage(
                    "Geo-tagging currently is not supported for video mode"
                )
                return@setOnClickListener
            }

            if (mActivity.videoCapturer.isRecording) {
                locToggle.isChecked = !locToggle.isChecked
                mActivity.showMessage(
                    "Can't toggle geo-tagging for ongoing recording"
                )
            } else {
                MainActivity.camConfig.requireLocation = locToggle.isChecked
            }
        }

        flashToggle = findViewById(R.id.flash_toggle_option)
        flashToggle.setOnClickListener {
            if (mActivity.requiresVideoModeOnly) {
                mActivity.showMessage(
                    "Cannot switch flash mode in this mode"
                )
            } else {
                MainActivity.camConfig.toggleFlashMode()
            }
        }

        aRToggle = findViewById(R.id.aspect_ratio_toggle)
        aRToggle.setOnClickListener {
            if (MainActivity.camConfig.isVideoMode) {
                aRToggle.isChecked = true
                mActivity.showMessage(
                    "4:3 is not supported in video mode"
                )
            } else {
                MainActivity.camConfig.toggleAspectRatio()
            }
        }

        torchToggle = findViewById(R.id.torch_toggle_option)
        torchToggle.setOnClickListener {
            if (MainActivity.camConfig.isFlashAvailable) {
                MainActivity.camConfig.toggleTorchState()
            } else {
                torchToggle.isChecked = false
                mActivity.showMessage(
                    "Flash/Torch is unavailable for this mode"
                )
            }
        }

        gridToggle = findViewById(R.id.grid_toggle_option)
        gridToggle.setOnClickListener {
            MainActivity.camConfig.gridType = when (MainActivity.camConfig.gridType) {
                CamConfig.GridType.NONE -> CamConfig.GridType.THREE_BY_THREE
                CamConfig.GridType.THREE_BY_THREE -> CamConfig.GridType.FOUR_BY_FOUR
                CamConfig.GridType.FOUR_BY_FOUR -> CamConfig.GridType.GOLDEN_RATIO
                CamConfig.GridType.GOLDEN_RATIO -> CamConfig.GridType.NONE
            }
            updateGridToggleUI()
        }

        videoQualitySpinner = findViewById(R.id.video_quality_spinner)

        videoQualitySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long
                ) {

                    val choice = vQAdapter.getItem(position) as String
                    updateVideoQuality(choice)
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        qRadio = findViewById(R.id.quality_radio)
        lRadio = findViewById(R.id.latency_radio)

        if (mActivity.requiresVideoModeOnly) {
            qRadio.isEnabled = false
            lRadio.isEnabled = false
        }

        cmRadioGroup = findViewById(R.id.cm_radio_group)
        cmRadioGroup.setOnCheckedChangeListener { _, _ ->
            MainActivity.camConfig.emphasisQuality = qRadio.isChecked
            if (MainActivity.camConfig.cameraProvider != null) {
                MainActivity.camConfig.startCamera(true)
            }
        }

        selfIlluminationToggle = findViewById(R.id.self_illumination_switch)
        selfIlluminationToggle.setOnClickListener {
            MainActivity.camConfig.selfIlluminate = selfIlluminationToggle.isChecked
        }

        sIAPToggle = findViewById(R.id.save_image_as_preview_switch)
        sIAPToggle.setOnClickListener {
            MainActivity.camConfig.saveImageAsPreviewed = sIAPToggle.isChecked
        }

        focusTimeoutSpinner = findViewById(R.id.focus_timeout_spinner)
        focusTimeoutSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long
                ) {

                    val selectedOption = focusTimeoutSpinner.selectedItem.toString()
                    updateFocusTimeout(selectedOption)

                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        focusTimeoutSpinner.setSelection(2)

        timerSpinner = findViewById(R.id.timer_spinner)
        timerSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long
                ) {

                    val selectedOption = timerSpinner.selectedItem.toString()

                    if (selectedOption == "Off") {
                        mActivity.timerDuration = 0
                        mActivity.cbText.visibility = View.INVISIBLE
                    } else {

                        try {
                            val durS = selectedOption.substring(0, selectedOption.length - 1)
                            val dur = durS.toInt()

                            mActivity.timerDuration = dur

                            mActivity.cbText.text = selectedOption
                            mActivity.cbText.visibility = View.VISIBLE

                        } catch (exception: Exception) {

                            mActivity.showMessage(
                                "An unexpected error occurred while setting focus timeout"
                            )

                        }

                    }

                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        mScrollView = findViewById(R.id.settings_scrollview)
        mScrollViewContent = findViewById(R.id.settings_scrollview_content)

        csSwitch = findViewById(R.id.camera_sounds_switch)
        csSwitch.setOnCheckedChangeListener { _, value ->
            MainActivity.camConfig.enableCameraSounds = value
        }

        includeAudioSetting = findViewById(R.id.include_audio_setting)
        selfIlluminationSetting = findViewById(R.id.self_illumination_setting)
        sIAPSetting = findViewById(R.id.save_image_as_preview_setting)
        videoQualitySetting = findViewById(R.id.video_quality_setting)
        timerSetting = findViewById(R.id.timer_setting)

        includeAudioToggle = findViewById(R.id.include_audio_switch)
        includeAudioToggle.setOnClickListener {
            MainActivity.camConfig.includeAudio = includeAudioToggle.isChecked
        }
        includeAudioToggle.setOnCheckedChangeListener { _, _ ->
            MainActivity.camConfig.startCamera(true)
        }

        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }

    private fun resize() {
        mScrollViewContent.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {

                mScrollViewContent.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val sdHM =
                    mActivity.resources.getDimension(R.dimen.settings_dialog_horizontal_margin)

                val sH = (mScrollViewContent.width - (sdHM * 8)).toInt()

                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    sH.coerceAtMost(mScrollViewContent.height)
                )

                mScrollView.layoutParams = lp
            }
        })
    }

    fun showOnlyRelevantSettings() {
        if (MainActivity.camConfig.isVideoMode) {
            includeAudioSetting.visibility = View.VISIBLE
            videoQualitySetting.visibility = View.VISIBLE
        } else {
            includeAudioSetting.visibility = View.GONE
            videoQualitySetting.visibility = View.GONE
        }

        selfIlluminationSetting.visibility =
            if (MainActivity.camConfig.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                View.VISIBLE
            } else {
                View.GONE
            }

        sIAPSetting.visibility =
            if (!mActivity.requiresVideoModeOnly && MainActivity.camConfig.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                View.VISIBLE
            } else {
                View.GONE
            }

        timerSetting.visibility = if (MainActivity.camConfig.isVideoMode) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }


    fun updateFocusTimeout(selectedOption: String) {

        if (selectedOption == "Off") {
            MainActivity.camConfig.focusTimeout = 0
        } else {

            try {
                val durS = selectedOption.substring(0, selectedOption.length - 1)
                val dur = durS.toLong()

                MainActivity.camConfig.focusTimeout = dur

            } catch (exception: Exception) {

                mActivity.showMessage(
                    "An unexpected error occurred while setting focus timeout"
                )

            }
        }

        focusTimeoutSpinner.setSelection(timeOptions.indexOf(selectedOption), false)
    }

    fun updateVideoQuality(choice: String, resCam: Boolean = true) {

        val quality = titleToQuality(choice)

        if (quality == MainActivity.camConfig.videoQuality) return

        MainActivity.camConfig.videoQuality = quality

        if (resCam) {
            MainActivity.camConfig.startCamera(true)
        } else {
            videoQualitySpinner.setSelection(getAvailableQTitles().indexOf(choice))

        }
    }

    fun titleToQuality(title: String): Int {
        return when (title) {
            "2160p (UHD)" -> QualitySelector.QUALITY_UHD
            "1080p (FHD)" -> QualitySelector.QUALITY_FHD
            "720p (HD)" -> QualitySelector.QUALITY_HD
            "480p (SD)" -> QualitySelector.QUALITY_SD
            else -> {
                Log.e("TAG", "Unknown quality: $title")
                QualitySelector.QUALITY_SD
            }
        }
    }

    private var wasSelfIlluminationOn = false

    fun selfIllumination() {

//        if (mActivity.config.lensFacing == CameraSelector.LENS_FACING_BACK) {
//
//            mActivity.previewView.setBackgroundColor(Color.BLACK)
//            mActivity.rootView.setBackgroundColor(Color.BLACK)
//
//            mActivity.tabLayout.setTabTextColors(Color.WHITE, Color.WHITE)
//
//            mActivity.tabLayout.setSelectedTabIndicatorColor(bgBlue)
//
//            return
//        }

        if (MainActivity.camConfig.selfIlluminate) {

            val colorFrom: Int = Color.BLACK
            val colorTo: Int = mActivity.getColor(R.color.self_illumination_light)

            val colorAnimation1 = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
            colorAnimation1.duration = 300
            colorAnimation1.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                mActivity.previewView.setBackgroundColor(color)
                mActivity.rootView.setBackgroundColor(color)
                window?.statusBarColor = color
            }

            val colorAnimation2 = ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, Color.BLACK)
            colorAnimation2.duration = 300
            colorAnimation2.addUpdateListener { animator ->
                mActivity.tabLayout.setTabTextColors(
                    animator.animatedValue as Int,
                    Color.WHITE
                )
            }

            val colorAnimation3 = ValueAnimator.ofObject(ArgbEvaluator(), bgBlue, Color.BLACK)
            colorAnimation3.duration = 300
            colorAnimation3.addUpdateListener { animator ->
                mActivity.tabLayout.setSelectedTabIndicatorColor(animator.animatedValue as Int)
            }

            colorAnimation1.start()
            colorAnimation2.start()
            colorAnimation3.start()

            setBrightness(1f)

        } else if (wasSelfIlluminationOn) {

//            mActivity.previewView.setBackgroundColor(Color.BLACK)
//            mActivity.rootView.setBackgroundColor(Color.BLACK)
//
//            mActivity.tabLayout.setTabTextColors(Color.WHITE, Color.WHITE)
//
//            mActivity.tabLayout.setSelectedTabIndicatorColor(bgBlue)

            val colorFrom: Int = mActivity.getColor(R.color.self_illumination_light)
            val colorTo: Int = Color.BLACK

            val colorAnimation1 = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
            colorAnimation1.duration = 300
            colorAnimation1.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                mActivity.previewView.setBackgroundColor(color)
                mActivity.rootView.setBackgroundColor(color)
                window?.statusBarColor = color
            }

            val colorAnimation2 = ValueAnimator.ofObject(ArgbEvaluator(), Color.BLACK, Color.WHITE)
            colorAnimation2.duration = 300
            colorAnimation2.addUpdateListener { animator ->
                mActivity.tabLayout.setTabTextColors(
                    animator.animatedValue as Int,
                    Color.WHITE
                )
            }

            val colorAnimation3 = ValueAnimator.ofObject(ArgbEvaluator(), Color.BLACK, bgBlue)
            colorAnimation3.duration = 300
            colorAnimation3.addUpdateListener { animator ->
                mActivity.tabLayout.setSelectedTabIndicatorColor(animator.animatedValue as Int)
            }

            colorAnimation1.start()
            colorAnimation2.start()
            colorAnimation3.start()

            setBrightness(getSystemBrightness())
        }

        wasSelfIlluminationOn = MainActivity.camConfig.selfIlluminate
    }

    private val slideDownAnimation: Animation by lazy {
        val anim = AnimationUtils.loadAnimation(
            mActivity,
            R.anim.slide_down
        )

        anim.setAnimationListener(object: Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {}

            override fun onAnimationEnd(p0: Animation?) {
                moreSettingsButton.visibility = View.VISIBLE
            }

            override fun onAnimationRepeat(p0: Animation?) {}

        })

        anim
    }

    private val slideUpAnimation: Animation by lazy {
        val anim = AnimationUtils.loadAnimation(
            mActivity,
            R.anim.slide_up
        )

        anim.setAnimationListener(
            object : Animation.AnimationListener {

                override fun onAnimationStart(p0: Animation?) {
                    moreSettingsButton.visibility = View.GONE
                }

                override fun onAnimationEnd(p0: Animation?) {
                    dismiss()
                }

                override fun onAnimationRepeat(p0: Animation?) {}

            }
        )

        anim
    }

    private fun getSystemBrightness(): Float {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            -1
        ) / 255f
    }

    private fun setBrightness(brightness: Float) {

        val layout = mActivity.window.attributes
        layout.screenBrightness = brightness
        mActivity.window.attributes = layout

        window?.let {
            val dialogLayout = it.attributes
            dialogLayout.screenBrightness = brightness
            it.attributes = dialogLayout
        }

    }

    private fun slideDialogDown() {
        dialog.startAnimation(slideDownAnimation)
    }

    fun slideDialogUp() {
        dialog.startAnimation(slideUpAnimation)
    }

    private fun getAvailableQualities(): List<Int> {
        return QualitySelector.getSupportedQualities(
            MainActivity.camConfig.camera!!.cameraInfo
        )
    }

    private fun getAvailableQTitles(): List<String> {

        val titles = arrayListOf<String>()

        getAvailableQualities().forEach {
            titles.add(getTitleFor(it))
        }

        return titles

    }

    private fun getTitleFor(quality: Int): String {
        return when (quality) {
            QualitySelector.QUALITY_UHD -> "2160p (UHD)"
            QualitySelector.QUALITY_FHD -> "1080p (FHD)"
            QualitySelector.QUALITY_HD -> "720p (HD)"
            QualitySelector.QUALITY_SD -> "480p (SD)"
            else -> {
                Log.i("TAG", "Unknown constant: $quality")
                "Unknown"
            }
        }
    }

    fun updateGridToggleUI() {
        mActivity.previewGrid.postInvalidate()
        gridToggle.setImageResource(
            when (MainActivity.camConfig.gridType) {
                CamConfig.GridType.NONE -> R.drawable.grid_off_circle
                CamConfig.GridType.THREE_BY_THREE -> R.drawable.grid_3x3_circle
                CamConfig.GridType.FOUR_BY_FOUR -> R.drawable.grid_4x4_circle
                CamConfig.GridType.GOLDEN_RATIO -> R.drawable.grid_goldenratio_circle
            }
        )
    }

    fun updateFlashMode() {
        flashToggle.setImageResource(
            if (MainActivity.camConfig.isFlashAvailable) {
                when (MainActivity.camConfig.flashMode) {
                    ImageCapture.FLASH_MODE_ON -> R.drawable.flash_on_circle
                    ImageCapture.FLASH_MODE_AUTO -> R.drawable.flash_auto_circle
                    else -> R.drawable.flash_off_circle
                }
            } else {
                R.drawable.flash_off_circle
            }
        )
    }

    override fun show() {

        this.resize()

        updateFlashMode()

        if(MainActivity.camConfig.isVideoMode) {
            aRToggle.isChecked = true
        } else {
            aRToggle.isChecked = MainActivity.camConfig.aspectRatio == AspectRatio.RATIO_16_9
        }

        torchToggle.isChecked = MainActivity.camConfig.isTorchOn

        updateGridToggleUI()

        mActivity.settingsIcon.visibility = View.INVISIBLE
        super.show()

        slideDialogDown()
    }

    fun reloadQualities(qualityText: String = "") {

        val titles = getAvailableQTitles()

        vQAdapter = ArrayAdapter<String>(
            mActivity,
            android.R.layout.simple_spinner_item,
            titles
        )

        vQAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        videoQualitySpinner.adapter = vQAdapter

        val qt = if (qualityText.isEmpty()) {
            getTitleFor(MainActivity.camConfig.videoQuality)
        } else {
            qualityText
        }

        videoQualitySpinner.setSelection(titles.indexOf(qt))
    }
}