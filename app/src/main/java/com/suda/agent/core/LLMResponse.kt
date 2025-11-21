package com.suda.agent.core

import android.util.Log

data class LLMResponse(
    val token: String,
    val parameters: Map<String, String>
)

object LLMResponseParser {

    private val TAG = LLMResponseParser::class.simpleName

    fun llmResponseParse(llmResponse: String): List<LLMResponse> {
        Log.d(TAG, "llmResponse : $llmResponse")

        val llmResponseList = ArrayList<LLMResponse>()

        llmResponse.split(";").forEach { item ->
            val tokenRegex = Regex("<.*?>")
            val paramRegex = Regex("\\((.*?)\\)")

            val tokenMatch = tokenRegex.find(item)
            val token = tokenMatch?.value ?: ""

            var tempItem = item

            try {
                val paramMatch = paramRegex.find(tempItem)
                val paramString = paramMatch?.groups?.get(1)?.value ?: ""
                val parameterMap = HashMap<String, String>()

                if (paramString.isNotEmpty()) {
                    paramString.split(",").forEach { param ->
                        val keyValue = param.split("=").map { it.trim() }
                        val key = keyValue[0]
                        val value = keyValue[1]
                        parameterMap[key] = value
                    }
                }

                llmResponseList.add(LLMResponse(token, parameterMap))
            } catch (e: Exception) {
                return listOf()
            }
        }

        return llmResponseList
    }
}