package com.suda.agent.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SqliteHelper(context: Context?) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object{
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "SUDA_API_MAPPING.db"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("CREATE TABLE API_CALL (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", token_header TEXT" +
                ", api_method TEXT" +
                ", answer_kr TEXT" +
                ", answer_en TEXT" +
                ")")

        db?.execSQL("CREATE TABLE API_PARAM (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT " +
                ", api_call_id INTEGER " +
                ", param TEXT" +
                ", value TEXT" +
                ", FOREIGN KEY (api_call_id) REFERENCES API_CALL (id) ON DELETE CASCADE)")

        db?.execSQL("CREATE TABLE LLM_PARAM (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", api_call_id INTEGER" +
                ", param TEXT" +
                ", value TEXT" +
                ", FOREIGN KEY (api_call_id) REFERENCES API_CALL (id) ON DELETE CASCADE)")

        db?.execSQL("CREATE TABLE PARALLEL_ANSWER (" +
                "llm_response TEXT PRIMARY KEY" +
                ", answer_kr TEXT" +
                ", answer_en TEXT)")

        db?.execSQL("CREATE TABLE MULTITURN_ANSWER (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", token_header TEXT" +
                ", multiturn_end INTEGER" +
                ", answer_order INTEGER" +
                ", answer_kr TEXT" +
                ", answer_en TEXT)")

        db?.execSQL("CREATE TABLE MULTITURN_LLM_PARAM (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT " +
                ", multiturn_answer_id INTEGER " +
                ", param TEXT" +
                ", value TEXT" +
                ", FOREIGN KEY (multiturn_answer_id) REFERENCES API_CALL (id) ON DELETE CASCADE)")

    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS API_CALL")
        db?.execSQL("DROP TABLE IF EXISTS API_PARAM")
        db?.execSQL("DROP TABLE IF EXISTS LLM_PARAM")
        db?.execSQL("DROP TABLE IF EXISTS PARALLEL_ANSWER")
        db?.execSQL("DROP TABLE IF EXISTS MULTITURN_ANSWER")
        db?.execSQL("DROP TABLE IF EXISTS MULTITURN_LLM_PARAM")
        onCreate(db)
    }

    fun insert(tokenHeader: String, apiMethod: String, answerKr: String, answerEn: String, llmParams: Map<String, String>, apiParams: Map<String, String>) {
        val check = selectByMultiParam(tokenHeader, llmParams)

        if (check != null) {
            return
        }

        val db = writableDatabase
        db.beginTransaction()

        try {
            val apiCall = ContentValues().apply {
                put("token_header", tokenHeader)
                put("api_method", apiMethod)
                put("answer_kr", answerKr)
                put("answer_en", answerEn)
            }
            val apiCallId = db.insert("API_CALL", null, apiCall)

            llmParams.forEach { (param, value) ->
                val llmParam = ContentValues().apply {
                    put("api_call_id", apiCallId)
                    put("param", param)
                    put("value", value)
                }
                db.insert("LLM_PARAM", null, llmParam)
            }

            apiParams.forEach{ (param, value) ->
                val apiParam = ContentValues().apply {
                    put("api_call_id", apiCallId)
                    put("param", param)
                    put("value", value)
                }
                db.insert("API_PARAM", null, apiParam)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertMultiturnAnswer(tokenHeader: String, answerOrder: Int, isMultiturnEnd: Boolean, answerKr: String, answerEn: String, params: Map<String, String>) {
        val check = selectMultiturnAnswerByParam(tokenHeader, answerOrder, params)

        if (check != null) {
            return
        }

        val db = writableDatabase
        db.beginTransaction()

        try {
            val multiturnAnswer = ContentValues().apply {
                put("token_header", tokenHeader)
                put("answer_order", answerOrder)
                put("answer_kr", answerKr)
                put("answer_en", answerEn)
                put("multiturn_end", if (isMultiturnEnd) 1 else 0)
            }
            val id = db.insert("MULTITURN_ANSWER", null, multiturnAnswer)

            params.forEach { (param, value) ->
                val param = ContentValues().apply {
                    put("multiturn_answer_id", id)
                    put("param", param)
                    put("value", value)
                }
                db.insert("MULTITURN_LLM_PARAM", null, param)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun selectMultiturnAnswerByParam(tokenHeader: String, multiturnCount: Int, params: Map<String, String>): MultiturnAnswer? {
        var result: MultiturnAnswer? = null
        val db = this.readableDatabase

        var query = "SELECT ma.answer_order, ma.multiturn_end, ma.answer_kr, ma.answer_en from MULTITURN_ANSWER ma "

        params.keys.forEachIndexed { index, _ ->
            query += "left join MULTITURN_LLM_PARAM lp$index on ma.id = lp$index.multiturn_answer_id "
        }

        query += "where ma.token_header = ? AND ma.answer_order = ? "

        params.keys.forEachIndexed { index, _ ->
            query += "AND lp$index.param = ? AND lp$index.value = ?"
        }

        var queryArgs: Array<String> = arrayOf(tokenHeader, multiturnCount.toString())

        params.forEach { (key, value) ->
            queryArgs = queryArgs.plus(key).plus(value)
        }

        val cursor = db.rawQuery(query, queryArgs)

        if (cursor.moveToFirst()) {
            val answerOrder = cursor.getInt(cursor.getColumnIndexOrThrow("answer_order"))
            val answerKR = cursor.getString(cursor.getColumnIndexOrThrow("answer_kr"))
            val answerEN = cursor.getString(cursor.getColumnIndexOrThrow("answer_en"))
            val multiturnEnd = cursor.getInt(cursor.getColumnIndexOrThrow("multiturn_end"))

            result = MultiturnAnswer(if (multiturnEnd == 0) false else true, answerOrder, answerKR, answerEN)
        }
        cursor.close()

        return result
    }

    fun selectByMultiParam(tokenHeader: String, params: Map<String, String>): ApiCallParam? {
        var result: ApiCallParam? = null
        val db = this.readableDatabase

        var query = "SELECT ac.api_method, ac.answer_kr, ac.answer_en, ap.param AS api_param, ap.value AS api_value " +
                "FROM API_CALL ac " +
                "LEFT JOIN API_PARAM ap ON ac.id = ap.api_call_id "

        params.keys.forEachIndexed { index, _ ->
            query += "LEFT JOIN LLM_PARAM lp$index ON ac.id = lp$index.api_call_id "
        }

        query += "WHERE ac.token_header = ? "

        params.keys.forEachIndexed { index, _ ->
            query += "AND lp$index.param = ? AND lp$index.value = ? "
        }

        var queryArgs = arrayOf(tokenHeader)

        params.forEach { (key, value) ->
            queryArgs = queryArgs.plus(key).plus(value)
        }

        val cursor = db.rawQuery(query, queryArgs)

        if (cursor.moveToFirst()) {
            val apiMethod = cursor.getString(cursor.getColumnIndexOrThrow("api_method"))
            val answerKR = cursor.getString(cursor.getColumnIndexOrThrow("answer_kr"))
            val answerEN = cursor.getString(cursor.getColumnIndexOrThrow("answer_en"))

            val apiParams = HashMap<String, String>()

            do {
                val apiParam = cursor.getString(cursor.getColumnIndexOrThrow("api_param"))
                val apiValue = cursor.getString(cursor.getColumnIndexOrThrow("api_value"))

                if (apiParam != null && apiValue != null) {
                    apiParams[apiParam] = apiValue
                }
            } while (cursor.moveToNext())

            result = ApiCallParam(apiMethod, answerKR, answerEN, apiParams)
        }
        cursor.close()

        return result
    }


    fun selectParallelAnswer(llmResponse: String): ParallelAnswer? {
        var result: ParallelAnswer? = null
        val db = this.readableDatabase

        val cursor = db.rawQuery("""
            SELECT answer_kr, answer_en
            FROM PARALLEL_ANSWER
            WHERE llm_response = ?
        """.trimIndent(), arrayOf(llmResponse)
        )

        if (cursor.moveToFirst()) {
            val answerKR = cursor.getString(cursor.getColumnIndexOrThrow("answer_kr"))
            val answerEN = cursor.getString(cursor.getColumnIndexOrThrow("answer_en"))

            result = ParallelAnswer(answerKR, answerEN)
        }
        cursor.close()

        return result
    }

    fun insertParallelAnswer(llmResponse: String, answerKr: String, answerEn: String) {
        val check = selectParallelAnswer(llmResponse)

        if (check != null) {
            return
        }

        val db = writableDatabase
        db.beginTransaction()

        try {
            val parallelAnswer = ContentValues().apply {
                put("llm_response", llmResponse)
                put("answer_en", answerEn)
                put("answer_kr", answerKr)
            }
            db.insert("PARALLEL_ANSWER", null, parallelAnswer)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun selectByApiParam(tokenHeader: String, params: Map<String, String>): ApiCallParam? {
        var result: ApiCallParam? = null
        val db = this.readableDatabase

        var query = "SELECT ac.api_method, ac.answer_kr, ac.answer_en, lp.param, lp.value " +
                "from API_CALL ac " +
                "left join LLM_PARAM lp on ac.id = lp.api_call_id "

        params.keys.forEachIndexed { index, _ ->
            query += "left join API_PARAM ap$index on ac.id = ap$index.api_call_id "
        }

        query += "where ac.token_header = ? "

        params.keys.forEachIndexed { index, _ ->
            query += "AND ap$index.param = ? AND ap$index.value = ?"
        }

        var queryArgs = arrayOf(tokenHeader)

        params.forEach { (key, value) ->
            queryArgs = queryArgs.plus(key).plus(value)
        }

        val cursor = db.rawQuery(query, queryArgs)

        if (cursor.moveToFirst()) {
            val apiMethod = cursor.getString(cursor.getColumnIndexOrThrow("api_method"))
            val answerKR = cursor.getString(cursor.getColumnIndexOrThrow("answer_kr"))
            val answerEN = cursor.getString(cursor.getColumnIndexOrThrow("answer_en"))

            val apiParams = HashMap<String, String>()

            do {
                val apiParam = cursor.getString(cursor.getColumnIndexOrThrow("param"))
                val apiValue = cursor.getString(cursor.getColumnIndexOrThrow("value"))

                apiParams[apiParam] = apiValue
            } while (cursor.moveToNext())

            result = ApiCallParam(apiMethod, answerKR, answerEN, apiParams)
        }
        cursor.close()

        return result
    }

    data class ApiCallParam (
        val apiMethod: String,
        val answerKr: String,
        val answerEn: String,
        val params: Map<String, String>
    )

    data class ParallelAnswer (
        val answerKr: String,
        val answerEn: String
    )

    data class MultiturnAnswer (
        val isMultiturnEnd: Boolean,
        val answerOrder: Int,
        val answerKr: String,
        val answerEn: String
    )

    fun initApiParam() {
        insert("<maum_0>","","오븐을 켰습니다.","The oven has turned on.", mapOf("object" to "1", "type" to "1"), mapOf())
        insert("<maum_0>","","오븐을 껐습니다.","The oven has turned off.", mapOf("object" to "1", "type" to "2"), mapOf())
        insert("<maum_0>","","욕조에 물을 채우고 있습니다.","The bathtub is being filled.", mapOf("object" to "2", "type" to "1"), mapOf())
        insert("<maum_0>","","욕조에 있는 물을 비우고 있습니다.","The bathtub is being drained.", mapOf("object" to "2", "type" to "2"), mapOf())
        insert("<maum_0>","","에어컨이 켜졌습니다.","The air conditioner has turned on.", mapOf("object" to "3", "type" to "1"), mapOf())
        insert("<maum_0>","","에어컨이 꺼졌습니다.","The air conditioner has turned off.", mapOf("object" to "3", "type" to "2"), mapOf())
        insert("<maum_0>","","청소기가 켜졌습니다.","The vacuum cleaner has turned on.", mapOf("object" to "4", "type" to "1"), mapOf())
        insert("<maum_0>","","청소기가 꺼졌습니다.","The vacuum cleaner has turned off.", mapOf("object" to "4", "type" to "2"), mapOf())
        insert("<maum_0>","","커피 머신이 커피를 만들고 있습니다.","The coffee machine is making coffee.", mapOf("object" to "5", "type" to "1"), mapOf())
        insert("<maum_0>","","커피 머신이 라떼를 만들고 있습니다.","The coffee machine is making latte.", mapOf("object" to "5", "type" to "2"), mapOf())
        insert("<maum_0>","","블라인더가 올라갔습니다.","The blinder was raised.", mapOf("object" to "6", "type" to "1"), mapOf())
        insert("<maum_0>","","블라인더가 내려갔습니다.","The blinder was lowered.", mapOf("object" to "6", "type" to "2"), mapOf())
        insert("<maum_0>","","","The blinder was raised half way.", mapOf("object" to "6", "type" to "11"), mapOf())
        insert("<maum_0>","","","The blinder was lowered half way.", mapOf("object" to "6", "type" to "22"), mapOf())
        insert("<maum_0>","","조명을 켰습니다.","", mapOf("object" to "7", "type" to "1"), mapOf())
        insert("<maum_0>","","조명을 껐습니다.","", mapOf("object" to "7", "type" to "2"), mapOf())
        insert("<maum_0>","","영화모드로 변경 했습니다.","", mapOf("object" to "7", "type" to "3"), mapOf())
        insert("<maum_0>","","보일러를 켰습니다.","", mapOf("object" to "8", "type" to "1"), mapOf())
        insert("<maum_0>","","보일러를 껐습니다.","", mapOf("object" to "8", "type" to "2"), mapOf())
        insert("<maum_0>","","전열교환기를 켰습니다.","", mapOf("object" to "9", "type" to "1"), mapOf())
        insert("<maum_0>","","전열교환기를 껐습니다.","", mapOf("object" to "9", "type" to "2"), mapOf())

        insert("<maum_1>","","오븐 온도가 올랐습니다.","The oven temperature was increased.", mapOf("object" to "1", "type" to "1"), mapOf())
        insert("<maum_1>","","오븐 온도가 내려갔습니다.","The oven temperature was decreased.", mapOf("object" to "1", "type" to "2"), mapOf())
        insert("<maum_1>","","오븐이 요청한 온도로 설정되었습니다.","The oven is now set to the requested temperature.", mapOf("object" to "1", "type" to "3"), mapOf())
        insert("<maum_1>","","욕조 온도가 올랐습니다.","The bathtub temperature was increased.", mapOf("object" to "2", "type" to "1"), mapOf())
        insert("<maum_1>","","욕조 온도가 내려갔습니다.","The bathtub temperature was decreased.", mapOf("object" to "2", "type" to "2"), mapOf())
        insert("<maum_1>","","욕조가 요청한 온도로 설정되었습니다.","The bathtub is now set to the requested temperature.", mapOf("object" to "2", "type" to "3"), mapOf())
        insert("<maum_1>","","에어컨 온도가 올랐습니다.","The air conditioner temperature was increased.", mapOf("object" to "3", "type" to "1"), mapOf())
        insert("<maum_1>","","에어컨 온도가 내려갔습니다.","The air conditioner temperature was decreased.", mapOf("object" to "3", "type" to "2"), mapOf())
        insert("<maum_1>","","에어컨이 요청한 온도로 설정되었습니다.","The air conditioner is now set to the requested temperature.", mapOf("object" to "3", "type" to "3"), mapOf())

        insert("<maum_5>","","주차는 지하 2층 나47 구역에 있습니다.","", mapOf(), mapOf())
        insert("<maum_6>","","조명을 껐습니다. 엘리베이터를 호출 하였습니다. 주차는 지하 2층 나47 구역에 있습니다.","", mapOf(), mapOf())

        insertMultiturnAnswer("<maum_0>", 1, false, "어떤 기기에 대한 요청이실까요?", "Which device would you like to try?", mapOf("object" to "-1"))
        insertMultiturnAnswer("<maum_0>", 2, true, "이해하지 못했습니다. 다시 시도해주세요.", "Please try again", mapOf("object" to "-1"))

        insertMultiturnAnswer("<maum_1>", 1, false, "말씀하신 온도를 어떤 기기에 설정하면 될까요?", "Which device would you like to set the temperature for?", mapOf("object" to "-1"))
        insertMultiturnAnswer("<maum_1>", 2, true, "이해하지 못했습니다. 다시 시도해주세요.", "Please try again", mapOf("object" to "-1"))

    }
}