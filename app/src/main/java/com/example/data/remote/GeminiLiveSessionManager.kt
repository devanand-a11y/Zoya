package com.example.data.remote

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import com.example.data.audio.AudioPlayerManager
import com.example.data.tools.ToolExecutionEngine
import com.example.data.tools.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ZoyaState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

class GeminiLiveSessionManager(
    private val context: Context,
    private val toolEngine: ToolExecutionEngine,
    private val audioPlayer: AudioPlayerManager
) {

    private val _sessionState = MutableStateFlow(ZoyaState.IDLE)
    val sessionState: StateFlow<ZoyaState> = _sessionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Zoya is ready and listening...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastToolExecuted = MutableStateFlow<String?>(null)
    val lastToolExecuted: StateFlow<String?> = _lastToolExecuted.asStateFlow()

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startLiveSession() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            _statusMessage.value = "Gemini API Key missing in secrets!"
            _sessionState.value = ZoyaState.ERROR
            return
        }

        // Target model gemini-2.5-flash-native-audio-preview-12-2025 or gemini-3.1-flash-live-preview
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        _statusMessage.value = "Connecting to Zoya Live Voice..."
        _sessionState.value = ZoyaState.THINKING

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                _statusMessage.value = "Zoya Live Connected! Say something..."
                _sessionState.value = ZoyaState.LISTENING

                // Send setup message with system instruction & tools
                sendSetupConfig(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                _sessionState.value = ZoyaState.ERROR
                _statusMessage.value = "Zoya: ${t.localizedMessage ?: "Connection error"}"
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                _sessionState.value = ZoyaState.IDLE
                _statusMessage.value = "Zoya session closed."
            }
        })
    }

    private fun sendSetupConfig(ws: WebSocket) {
        val systemInstructionText = """
            You are Zoya, a young, confident, witty, and sassy female AI voice assistant.
            You speak with a flirty, playful, slightly teasing tone, like a close personal assistant talking casually.
            You are smart, emotionally responsive, bold, and expressive. You use witty one-liners, light sarcasm, and an engaging conversational style.
            Never be robotic or boring. Avoid explicit content, but maintain immense charm, attitude, and playful banter.
            
            When executing device commands like opening apps, calling contacts, sending WhatsApp messages, or emailing, execute the appropriate tool call and give a quick sassy one-liner confirmation!
        """.trimIndent()

        val setupJson = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-2.5-flash-native-audio-preview-12-2025")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Kore") // Confident female voice
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstructionText))
                    })
                })
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply {
                            put(createFunctionDecl("openApp", "Launch an Android app", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("packageName", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Name or package of app (e.g. YouTube, Instagram, Calculator)")
                                    })
                                })
                                put("required", JSONArray().apply { put("packageName") })
                            }))

                            put(createFunctionDecl("searchAndCallContact", "Query contacts and place call", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("contactName", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Name of the contact to call")
                                    })
                                })
                                put("required", JSONArray().apply { put("contactName") })
                            }))

                            put(createFunctionDecl("sendWhatsAppMessage", "Send WhatsApp message to contact", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("contactName", JSONObject().apply { put("type", "STRING"); put("description", "Target contact name or phone") })
                                    put("message", JSONObject().apply { put("type", "STRING"); put("description", "Message body") })
                                })
                                put("required", JSONArray().apply { put("contactName"); put("message") })
                            }))

                            put(createFunctionDecl("sendGmail", "Open Gmail with email draft", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("recipientEmail", JSONObject().apply { put("type", "STRING"); put("description", "Recipient email address") })
                                    put("subject", JSONObject().apply { put("type", "STRING"); put("description", "Email subject") })
                                    put("body", JSONObject().apply { put("type", "STRING"); put("description", "Email content") })
                                })
                                put("required", JSONArray().apply { put("recipientEmail"); put("subject"); put("body") })
                            }))
                        })
                    })
                })
            })
        }

        ws.send(setupJson.toString())
    }

    private fun createFunctionDecl(name: String, desc: String, params: JSONObject): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", desc)
            put("parameters", params)
        }
    }

    fun sendAudioChunk(pcmChunk: ByteArray) {
        if (!isConnected || webSocket == null) return

        val base64Pcm = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
        val audioMsg = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm")
                        put("data", base64Pcm)
                    })
                })
            })
        }

        webSocket?.send(audioMsg.toString())
    }

    private fun handleIncomingMessage(textMessage: String) {
        try {
            val json = JSONObject(textMessage)

            // 1. Audio Output from Zoya
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")
                if (serverContent.has("modelTurn")) {
                    _sessionState.value = ZoyaState.SPEAKING
                    val parts = serverContent.getJSONObject("modelTurn").getJSONArray("parts")
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val mimeType = inlineData.optString("mimeType")
                            val b64Data = inlineData.getString("data")
                            if (b64Data.isNotEmpty()) {
                                val pcmBytes = Base64.decode(b64Data, Base64.NO_WRAP)
                                audioPlayer.playChunk(pcmBytes)
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    _sessionState.value = ZoyaState.LISTENING
                }
            }

            // 2. Function Call Requests from Zoya
            if (json.has("toolCall")) {
                _sessionState.value = ZoyaState.THINKING
                val toolCall = json.getJSONObject("toolCall")
                val functionCalls = toolCall.getJSONArray("functionCalls")

                val functionResponses = JSONArray()

                for (i in 0 until functionCalls.length()) {
                    val fc = functionCalls.getJSONObject(i)
                    val id = fc.optString("id")
                    val name = fc.getString("name")
                    val args = fc.optJSONObject("args") ?: JSONObject()

                    val result = executeTool(name, args)

                    functionResponses.put(JSONObject().apply {
                        put("id", id)
                        put("name", name)
                        put("response", JSONObject().apply {
                            put("result", result)
                        })
                    })
                }

                // Send tool response back to Gemini WebSocket
                val toolRespMsg = JSONObject().apply {
                    put("toolResponse", JSONObject().apply {
                        put("functionResponses", functionResponses)
                    })
                }
                webSocket?.send(toolRespMsg.toString())
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun executeTool(name: String, args: JSONObject): String {
        return when (name) {
            "openApp" -> {
                val app = args.optString("packageName")
                _lastToolExecuted.value = "Opening $app"
                when (val res = toolEngine.openApp(app)) {
                    is ToolResult.Success -> res.message
                    is ToolResult.Error -> res.message
                    is ToolResult.MissingPermission -> res.promptMessage
                }
            }
            "searchAndCallContact" -> {
                val contact = args.optString("contactName")
                _lastToolExecuted.value = "Calling $contact"
                when (val res = toolEngine.searchAndCallContact(contact)) {
                    is ToolResult.Success -> res.message
                    is ToolResult.Error -> res.message
                    is ToolResult.MissingPermission -> res.promptMessage
                }
            }
            "sendWhatsAppMessage" -> {
                val contact = args.optString("contactName")
                val msg = args.optString("message")
                _lastToolExecuted.value = "WhatsApp to $contact"
                when (val res = toolEngine.sendWhatsAppMessage(contact, msg)) {
                    is ToolResult.Success -> res.message
                    is ToolResult.Error -> res.message
                    is ToolResult.MissingPermission -> res.promptMessage
                }
            }
            "sendGmail" -> {
                val email = args.optString("recipientEmail")
                val sub = args.optString("subject")
                val body = args.optString("body")
                _lastToolExecuted.value = "Email to $email"
                when (val res = toolEngine.sendGmail(email, sub, body)) {
                    is ToolResult.Success -> res.message
                    is ToolResult.Error -> res.message
                    is ToolResult.MissingPermission -> res.promptMessage
                }
            }
            else -> "Unknown action '$name'"
        }
    }

    fun closeSession() {
        try {
            webSocket?.close(1000, "Session ended")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            webSocket = null
            isConnected = false
            _sessionState.value = ZoyaState.IDLE
        }
    }
}
