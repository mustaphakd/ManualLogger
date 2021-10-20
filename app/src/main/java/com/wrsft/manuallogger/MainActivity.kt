package com.wrsft.manuallogger

import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import com.wrsft.manuallogger.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*
import java.io.*
import android.content.Intent
import android.text.style.StyleSpan
import androidx.core.net.toUri


lateinit var binding: ActivityMainBinding
lateinit var spannableStringBuilder: SpannableStringBuilder
const val MAX_FILE_SIZE = 2 * 1024 * 1024
const val MAX_VIEW_SIZE = 500 * 1024
var rollLogFile: Boolean = false
lateinit var currentLog: File

class MainActivity : AppCompatActivity() {

    private var exitThreads: Boolean = false
    private lateinit var currentLocalTime: Date
    private val dateTimeFormat = SimpleDateFormat("yyyyMMdd_HHmmss_z", Locale.ENGLISH)
    private val cal: Calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+0:00"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // extend SpannableFactor to  allow textview text's buffer to be reused (Mutable)
        val spannableFactor = object : Spannable.Factory() {
            override fun newSpannable(source: CharSequence?): Spannable {
                //return super.newSpannable(source)
                return source as Spannable
            }
        }
        binding.textView.setSpannableFactory(spannableFactor)

        //SpannableStringBuilder allows for mutable text and spanning
        spannableStringBuilder = SpannableStringBuilder("")

        binding.textView.apply {
            setText(spannableStringBuilder, TextView.BufferType.SPANNABLE)
            setMovementMethod( ScrollingMovementMethod())
            setTextIsSelectable(true)
        }


        currentLocalTime = cal.time
        val logs = getTempFiles()

        val tempBuffer = StringBuilder()
        currentLog = logs?.let { findWritableFile(it) } ?: createLogFile(tempBuffer)
        tempBuffer.clear()



        Timer("logFile-Timer", false).schedule(
            object : TimerTask() {
                override fun run() {

                   // val length = currentLog.length()
                   // println("logFile-Timer check file { ${currentLog.name} } current length: $length --- totalSpace : ${currentLog.totalSpace}")
                    if(currentLog.length() >= MAX_FILE_SIZE)
                        rollLogFile = true
                }
            },
            500,
            1000 * 6
        )

        binding.btnclear.setOnClickListener {
            spannableStringBuilder.clear()
        }

        binding.btndirector.setOnClickListener {
            currentLog.parentFile?.let {
                openDirectory(it)
            }
        }

        binding.button.setOnClickListener {

           exitThreads = !exitThreads

            binding.button.text = if (!exitThreads){
               startThreads()
                "Stop"
            }else {
                //stopThreads()
                "Start"
            }

            Thread.sleep(50)
        }

        startThreads()
        binding.button.text = "Stop"
    }

    private fun openDirectory(file: File) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.setDataAndType(file.toUri(), "*/*") // or use application/*

        startActivity(intent)
    }

    // 2 threads : one threads write to file in cachedDir every seconds current time
    // second threads read from file and update spannable texview

    private lateinit var writingThread: Thread
    private lateinit var readingThread: Thread

    private  fun startThreads() {

            writingThread = Thread {
                writeToFile()
            }

            readingThread = Thread {
                Thread.sleep(1500)
                readFromFile()
            }

        writingThread.start()
        readingThread.start()
    }

    private  fun writeToFile() {
        val buffer = StringBuilder()
        while (true)
        {
            Thread.sleep(1000)

            if(rollLogFile) {
                currentLog = createLogFile(buffer)
                rollLogFile = false
                buffer.clear()
            }

            getCurrentDateTime(buffer)
            buffer.append("-- WriteFile running .... \r\n")

            currentLog.appendText(buffer.toString())
            buffer.clear()

            if(exitThreads) break
        }
    }


    private fun createLogFile( buffer : StringBuilder) : File = getCurrentDateTime(buffer).let {
            val temp = getFileNameFromTime(buffer.toString())
            getTempFile(temp)
    }

    private  fun getFileNameFromTime(formattedTime: String) : String
    {
        return formattedTime.substring(0, 15)
    }


    val newLine : IntArray = intArrayOf(10, 13)

    private fun readFromFile() {

        var bytesRead = 0L
        var buffer : ByteArray? = ByteArray(128)
        var recv: Int
        //println("Readfile running ..... : $buffer")
        var counter : Int
        val fullpath: StringBuilder = StringBuilder().apply {
            append(currentLog.absolutePath)
        }

        var bufferedInputStream : BufferedInputStream? = BufferedInputStream(FileInputStream(fullpath.toString())).also { it.skip(bytesRead) }

        while (true) {

            Thread.sleep(2000)
         //println("Readfile running strt.....")

            if(!fullpath.contains(currentLog.absolutePath)) {
                fullpath.clear()
                fullpath.append(currentLog.absolutePath)

                //bufferedInputStream.reset()
                bufferedInputStream?.close()
                bytesRead = 0
                bufferedInputStream = BufferedInputStream(FileInputStream(fullpath.toString()))
                Thread.sleep(40)
            }

           // bufferedInputStream.skip(bytesRead)

            recv = -1
            //println("Readfile running ..... : $buffer")
            counter = 0


            println("Readfile running ....*******************************************.")
            while(bufferedInputStream!!.read().let<Int, Int> { recv = it; recv } > -1) {

                bytesRead += 1
                counter %= 128

                buffer!![counter++] = recv.toByte()

                if(recv !in newLine && !exitThreads || counter < 2) continue
                if(buffer[(counter - 2)].toInt() !in newLine) continue


                runOnUiThread {
                    if(buffer != null)
                    {
                        spannableStringBuilder.insert(0, buffer!!.take(counter).toByteArray().toString(Charsets.UTF_8))
                        if ( spannableStringBuilder.count() >= 15 )
                            spannableStringBuilder.setSpan( StyleSpan(Typeface.BOLD), 0, 15, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        binding.textView.scrollTo(0, 0)

                        if(binding.textView.length() >= MAX_VIEW_SIZE ){
                            binding.textView.setLines(binding.textView.lineCount / 2)
                        }
                    }
                }

                counter = 0

                if(exitThreads) break
                Thread.sleep(50)
            }

            //println("Readfile running .....")

            if(exitThreads) break
        }


        buffer = null
        bufferedInputStream?.close()
        bufferedInputStream = null
    }

    private fun getTempFile(fileName: String): File {
        val ext: File? = getExternalFilesDir(null)
        val tempDir = File(ext, "logs/$fileName")

        tempDir.parentFile?.mkdirs()

        if (!tempDir.exists() || !tempDir.canWrite()) {
            tempDir.createNewFile()
        }

       // println("Temp file to be used for logging: ${tempDir.absolutePath}")

        return tempDir
    }


    private fun getTempFiles(): Array<File>? {
        val ext: File? = getExternalFilesDir(null)
        val tempDir = File(ext, "logs/")
        tempDir.mkdirs()

        if (!tempDir.exists() || !tempDir.canWrite()) {
            return null
        }

        val files = tempDir.listFiles()

        files?.forEach {
            println("existing file found in logs: ${it.name}    =>  ${it.length()} bytes")
        }
        return files
    }

    private  fun findWritableFile(files: Array<File>): File?
    {
        files.forEach {
            if(it.length() < MAX_FILE_SIZE)
                return it
        }

        return null
    }

    private fun getCurrentDateTime(buffer: StringBuilder) {
       // try {            //dateTimeFormat.timeZone = TimeZone.getTimeZone("GMT+1:00")
        currentLocalTime = cal.time
        buffer.append(dateTimeFormat.format(currentLocalTime))
            //cal.clear()
        //return localTime
       // }finally {

        //}
    }
}