package com.suda.agent.service

import android.util.Log
import fi.iki.elonen.NanoHTTPD

// 웹에서 오는 명령을 -> ConversationService 쪽 콜백으로 넘겨주는 작은 HTTP 서버
class VoiceHttpServer(
    private val onStartStt: () -> Unit,
    private val onStopStt: () -> Unit
) : NanoHTTPD(8080) {   // 포트는 8080 사용 (원하면 바꿔도 됨)

    private val TAG = "VoiceHttpServer"

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "Incoming request: $method $uri")

        // CORS 프리플라이트(OPTIONS) 처리
        if (method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK").apply {
                addCorsHeaders(this)
            }
        }

        return when {
            uri == "/start-stt" && method == Method.POST -> {
                onStartStt()
                newFixedLengthResponse("STT started").apply {
                    addCorsHeaders(this)
                }
            }

            uri == "/stop-stt" && method == Method.POST -> {
                onStopStt()
                newFixedLengthResponse("STT stopped").apply {
                    addCorsHeaders(this)
                }
            }

            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found").apply {
                    addCorsHeaders(this)
                }
            }
        }
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Accept")
    }
}
