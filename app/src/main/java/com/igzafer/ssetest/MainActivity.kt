package com.igzafer.ssetest

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : AppCompatActivity() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        findViewById<Button>(R.id.startBtn).setOnClickListener {
            it.isEnabled = false
            GlobalScope.launch(Dispatchers.Main) {
                getEventsFlow()
                    .flowOn(Dispatchers.IO)
                    .collect { event ->
                        if (event == "finish") {
                            it.isEnabled = true
                        } else {
                            findViewById<TextView>(R.id.counter).text = event.toString()

                        }
                    }
            }
        }

    }

    private suspend fun getStreamConnection(url: String): HttpURLConnection =
        withContext(Dispatchers.IO) {
            return@withContext (URL(url).openConnection() as HttpURLConnection).also {
                it.setRequestProperty("Accept", "text/event-stream")
                it.setRequestProperty("Connection", "keep-alive")
                it.doInput = true
            }
        }

    private fun getEventsFlow(): Flow<Any> = flow {
        coroutineScope {
            val conn =
                getStreamConnection("https://egecetv.egecetv.com/api/graphql?query=subscription+MySubscription+%7B+countdown%28from%3A+100%29+%7D")
            withContext(Dispatchers.IO) {
                conn.connect()
            }

            val input = conn.inputStream.bufferedReader()
            try {
                while (isActive) {
                    val line = withContext(Dispatchers.IO) {
                        input.readLine()
                    }
                    if (line == null) {
                        conn.disconnect()
                        withContext(Dispatchers.IO) {
                            input.close()
                        }
                        emit("finish")
                    } else {
                        if (!line.equals("")) {
                            val data = line.replace("}", "").split(":")
                            val result = data[data.size - 1]
                            emit(result)
                        }
                    }
                }
            } catch (e: IOException) {
                this.cancel(CancellationException("Network Problem", e))
            } finally {
                conn.disconnect()
                emit("finish")
                withContext(Dispatchers.IO) {
                    input.close()
                }
            }
        }
    }
}