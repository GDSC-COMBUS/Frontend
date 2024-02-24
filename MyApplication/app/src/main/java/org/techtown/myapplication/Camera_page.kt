package org.techtown.myapplication

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.view.View
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.PermissionChecker
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.techtown.myapplication.connection.RetrofitClient
import org.techtown.myapplication.databinding.ActivityCameraPageBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Base64
import java.io.InputStream


typealias LumaListener = (luma: Double) -> Unit
class Camera_page : AppCompatActivity(), TextToSpeech.OnInitListener {
    private val REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE = 1001
    private lateinit var binding: ActivityCameraPageBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService
    private val PICK_VIDEO_REQUEST = 1

    var bus_num:String = ""
    var videoUri: Uri? = null

    // TextToSpeech 객체 선언
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityCameraPageBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // TextToSpeech 초기화
        tts = TextToSpeech(this, this)

        val extras = intent.extras
        bus_num = extras!!.getString("bus_num").toString()

        binding.busNumTxt.visibility = View.GONE

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons
        binding.videoCaptureButton.setOnClickListener {
            binding.busNumTxt.visibility = View.GONE
            captureVideo()
            //openAlbum()
            //takePhoto()
            }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    // TextToSpeech 초기화 완료 시 호출되는 콜백
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // TextToSpeech 초기화 성공 시 설정
            val locale = Locale("en", "US") // 영어 설정
            val result = tts.setLanguage(locale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language specified is not supported!")
            } else {
                // TTS 설정 완료 후 "승차정류장을 말씀해주세요" 음성 출력
                //speakOut("Please tell me a boarding stop")
            }
        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }

    // 음성으로 메시지 출력하는 함수
    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun openAlbum() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_VIDEO_REQUEST)

    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_VIDEO_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val videoUri = data.data // 선택된 동영상의 URI를 가져옵니다.
                Log.e("URI",videoUri.toString())
                val videoPath = videoUri?.path // 선택된 동영상의 파일 경로를 가져옵니다.

                if (videoPath != null) {
                    // 동영상 경로를 사용하여 추가 작업을 수행할 수 있습니다.
                    // 여기서는 예를 들어 동영상 경로를 토스트 메시지로 표시합니다.
                    Toast.makeText(this, "Selected Video: $videoPath", Toast.LENGTH_SHORT).show()
                    val file = File(videoPath)
                    val mediaType = "video/*".toMediaTypeOrNull()
                    val body1 = file.asRequestBody(mediaType)
                    //val requestFile = RequestBody.create(MediaType.parse("video/mp4"), file)
                    val body = MultipartBody.Part.createFormData("videoFile", file.name, body1)

                    val call = RetrofitObject.getRetrofitService.BusnumCamera(body,bus_num)

                    call.enqueue(object : Callback<RetrofitClient.ResponseCamera> {
                        override fun onResponse(
                            call: Call<RetrofitClient.ResponseCamera>,
                            response: Response<RetrofitClient.ResponseCamera>
                        ) {
                            if (response.isSuccessful){
                                val response = response.body()
                                if (response != null){
                                    if (response.status == "OK"){
                                        Log.e("Retrofit", response.status)
                                        Log.e("Retrofit",response.data.correct.toString())
                                        Toast.makeText(this@Camera_page,response.data.correct.toString(),Toast.LENGTH_SHORT).show()
                                        if (response.data.correct == true){
                                            binding.busNumTxt.visibility = View.VISIBLE
                                            binding.busNumTxt.text = "${bus_num}번 버스입니다."
                                        }
                                        else if(response.data.correct == false){
                                            binding.busNumTxt.visibility = View.VISIBLE
                                            binding.busNumTxt.text = "${bus_num}번 버스가 아닙니다."
                                        }
                                    }else{
                                    }
                                }
                            }
                            else{
                                Log.e("Retrofit", "fail")
                                Toast.makeText(this@Camera_page,"fail",Toast.LENGTH_SHORT).show()}
                        }
                        override fun onFailure(call: Call<RetrofitClient.ResponseCamera>, t: Throwable) {
                            val errorMessage = "Call Failed: ${t.message}"
                            Log.e("Retrofit", errorMessage)
                            Toast.makeText(this@Camera_page,errorMessage,Toast.LENGTH_SHORT).show()
                        }
                    })
                } else {
                    Toast.makeText(this, "Failed to retrieve video path", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Video selection cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
            //
            //imageCapture = ImageCapture.Builder().build()
            //

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                //
                //cameraProvider.bindToLifecycle(
                //    this, cameraSelector, preview, imageCapture)
                //

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()

        // TextToSpeech 사용 후에는 반드시 종료해야 함
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else if (requestCode == REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 파일 액세스 권한이 부여된 경우
                captureVideo()
            } else {
                // 권한이 거부된 경우
                Toast.makeText(this, "파일 액세스 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }



    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 액세스 권한 요청
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE)
            return
        }
        val videoCapture = this.videoCapture ?: return

        binding.videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@Camera_page,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            background = getDrawable(R.drawable.button_status_background_2)
                            setTextColor(Color.WHITE)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)


                            } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        binding.videoCaptureButton.apply {
                            text = getString(R.string.start_capture)
                            background = getDrawable(R.drawable.button_status_background)
                            setTextColor(Color.BLACK)
                            isEnabled = true
                        }
                        videoUri = recordEvent.outputResults.outputUri
                        connect_camera(videoUri!!,this)
                        val contentResolver = applicationContext.contentResolver
                        contentResolver.delete(videoUri!!, null, null) // 동영상 파일 삭제
                    }
                }
            }

    }

    fun connect_camera(videoUri:Uri,context:Context){
        val inputStream = context.contentResolver.openInputStream(videoUri)
        val tempFile = File.createTempFile("prefix", "extension")
        tempFile.outputStream().use {
            inputStream?.copyTo(it)
        }
        val mediaType = "video/mp4".toMediaTypeOrNull()
        val requestBody = tempFile.asRequestBody(mediaType)
        val body = MultipartBody.Part.createFormData("videoFile", tempFile.name, requestBody)

        val call = RetrofitObject.getRetrofitService.BusnumCamera(body, bus_num)
            call.enqueue(object : Callback<RetrofitClient.ResponseCamera> {
                override fun onResponse(
                    call: Call<RetrofitClient.ResponseCamera>,
                    response: Response<RetrofitClient.ResponseCamera>
                ) {
                    if (response.isSuccessful){
                        val response = response.body()
                        if (response != null){
                            if (response.status == "OK"){
                                Log.e("Retrofit", response.status)
                                Log.e("Retrofit",response.data.correct.toString())
                                //Toast.makeText(this@Camera_page,response.data.correct.toString(),Toast.LENGTH_SHORT).show()
                                if (response.data.correct == true){
                                    binding.busNumTxt.visibility = View.VISIBLE
                                    speakOut("This is bus number $bus_num")
                                    binding.busNumTxt.text = "This is bus number $bus_num"
                                }
                                else if(response.data.correct == false){
                                    binding.busNumTxt.visibility = View.VISIBLE
                                    speakOut("This is not bus number $bus_num")
                                    binding.busNumTxt.text = "This is not bus number $bus_num"
                                }
                            }else{
                            }
                        }
                    }
                    else{
                        Log.e("Retrofit", "fail")
                        Toast.makeText(this@Camera_page,"fail",Toast.LENGTH_SHORT).show()}
                }
                override fun onFailure(call: Call<RetrofitClient.ResponseCamera>, t: Throwable) {
                    val errorMessage = "Call Failed: ${t.message}"
                    Log.e("Retrofit", errorMessage)
                    Toast.makeText(this@Camera_page,errorMessage,Toast.LENGTH_SHORT).show()

                }
            })
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, msg)
                    val imageFile = File(output.savedUri?.path ?: "")
                    if (!imageFile.exists()) {
                        Log.e(TAG, "Image file does not exist")
                    }

                    val uri = Uri.parse(output.savedUri.toString()) // 예시 URI
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)

                    if (inputStream != null) {
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()

                        if (bitmap != null) {
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                            val imageBytes = outputStream.toByteArray()
                            val encodedImage = Base64.encodeToString(imageBytes, Base64.DEFAULT)

                            // 이제 encodedImage에는 Base64로 인코딩된 이미지 데이터가 포함됩니다.
                            Log.d("Base64 Image", encodedImage)

                            // 이제 여기서 서버로 전송하거나 다른 작업을 수행할 수 있습니다.
                            val requestBody = encodedImage.toRequestBody("text/plain".toMediaTypeOrNull())
                            val body = MultipartBody.Part.createFormData("file", "photo.jpg", requestBody)
                            Log.e("body",body.toString())
                            val call = RetrofitObject.getRetrofitService.BusnumPhoto(body,bus_num)

                            call.enqueue(object : Callback<RetrofitClient.ResponseCamera> {
                                override fun onResponse(
                                    call: Call<RetrofitClient.ResponseCamera>,
                                    response: Response<RetrofitClient.ResponseCamera>
                                ) {
                                    if (response.isSuccessful){
                                        val response = response.body()
                                        if (response != null){
                                            if (response.status == "OK"){
                                                Log.e("Retrofit", response.status)
                                                Log.e("Retrofit",response.data.correct.toString())
                                                Toast.makeText(this@Camera_page,response.data.correct.toString(),Toast.LENGTH_SHORT).show()
                                                if (response.data.correct == true){
                                                    binding.busNumTxt.visibility = View.VISIBLE
                                                    speakOut("This is bus number {$bus_num}")
                                                    binding.busNumTxt.text = "This is bus number {$bus_num}"
                                                }
                                                else if(response.data.correct == false){
                                                    binding.busNumTxt.visibility = View.VISIBLE
                                                    speakOut("This is not bus number {$bus_num}")
                                                    binding.busNumTxt.text = "This is not bus number {$bus_num}"
                                                }
                                            }else{
                                            }
                                        }
                                    }
                                    else{
                                        Log.e("Retrofit", "fail")
                                        Toast.makeText(this@Camera_page,"fail",Toast.LENGTH_SHORT).show()}
                                }
                                override fun onFailure(call: Call<RetrofitClient.ResponseCamera>, t: Throwable) {
                                    val errorMessage = "Call Failed: ${t.message}"
                                    Log.e("Retrofit", errorMessage)
                                    Toast.makeText(this@Camera_page,errorMessage,Toast.LENGTH_SHORT).show()

                                }
                            })
                        } else {
                            Log.e("Image Error", "Bitmap decoding failed")
                        }
                    } else {
                        Log.e("Input Stream Error", "Failed to open input stream")
                    }

                    //val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    //val outputStream = ByteArrayOutputStream()
                    //bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    //val imageBytes = outputStream.toByteArray()
                    //val encodedImage = Base64.encodeToString(imageBytes, Base64.DEFAULT)
                    //Log.e("body",encodedImage)


                    }
                    //connect_photo(output.savedUri!!)
                })
            }


    fun connect_photo(saveUri:Uri){
        // 선택된 동영상의 URI를 가져옵니다.
        val videoPath = saveUri?.path // 선택된 동영상의 파일 경로를 가져옵니다.

        if (videoPath != null) {
            // 동영상 경로를 사용하여 추가 작업을 수행할 수 있습니다.
            // 여기서는 예를 들어 동영상 경로를 토스트 메시지로 표시합니다.
            //Toast.makeText(this, "Selected Video: $videoPath", Toast.LENGTH_SHORT).show()

            //val file = File(videoPath)
            //val mediaType = "image/jpeg".toMediaType()
            //val body1 = file.toString().toRequestBody(mediaType)
            //val body1 = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            //val requestFile = RequestBody.create(MediaType.parse("video/mp4"), file)
            //val body = MultipartBody.Part.createFormData("file", file.name, body1)

            val file = File(videoPath)
            val mediaType = "image/jpeg".toMediaTypeOrNull() // 이미지 파일의 MediaType을 설정합니다.
            val requestBody = file.asRequestBody(mediaType) // 이미지 파일을 읽어서 RequestBody로 변환합니다.
            val body = MultipartBody.Part.createFormData("file", file.name, requestBody)

            /*val file = File(videoPath)
            val mediaType = "image/jpeg".toMediaType() // MediaType을 "image/jpeg" 형식으로 설정합니다.
            val requestBody = file.asRequestBody(mediaType)
            val body = MultipartBody.Part.createFormData("file", file.name, requestBody)
*/
            val call = RetrofitObject.getRetrofitService.BusnumPhoto(body,bus_num)

            call.enqueue(object : Callback<RetrofitClient.ResponseCamera> {
                override fun onResponse(
                    call: Call<RetrofitClient.ResponseCamera>,
                    response: Response<RetrofitClient.ResponseCamera>
                ) {
                    if (response.isSuccessful){
                        val response = response.body()
                        if (response != null){
                            if (response.status == "OK"){
                                Log.e("Retrofit", response.status)
                                Log.e("Retrofit",response.data.correct.toString())
                                Toast.makeText(this@Camera_page,response.data.correct.toString(),Toast.LENGTH_SHORT).show()
                                if (response.data.correct == true){
                                    binding.busNumTxt.visibility = View.VISIBLE
                                    speakOut("This is bus number {$bus_num}")
                                    binding.busNumTxt.text = "This is bus number {$bus_num}"
                                }
                                else if(response.data.correct == false){
                                    binding.busNumTxt.visibility = View.VISIBLE
                                    speakOut("This is not bus number {$bus_num}")
                                    binding.busNumTxt.text = "This is not bus number {$bus_num}"
                                }
                            }else{
                            }
                        }
                    }
                    else{
                        Log.e("Retrofit", "fail")
                        Toast.makeText(this@Camera_page,"fail",Toast.LENGTH_SHORT).show()}
                }
                override fun onFailure(call: Call<RetrofitClient.ResponseCamera>, t: Throwable) {
                    val errorMessage = "Call Failed: ${t.message}"
                    Log.e("Retrofit", errorMessage)
                    Toast.makeText(this@Camera_page,errorMessage,Toast.LENGTH_SHORT).show()

                }
            })
        }}
}
