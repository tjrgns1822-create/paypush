package kr.paylog.collector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 대기열의 행을 본인 Google Apps Script 웹앱으로 보낸다.
 * 이 앱이 인터넷으로 무언가를 보내는 곳은 여기 한 곳뿐이고,
 * 주소가 script.google.com 웹앱이 아니면 보내지 않는다.
 */
object Uploader {
    private const val ALLOWED_PREFIX = "https://script.google.com/macros/s/"
    private const val BATCH = 50

    fun isAllowed(url: String) = url.startsWith(ALLOWED_PREFIX) && url.endsWith("/exec")

    /** 대기열을 비울 때까지 보낸다. 다시 시도해야 하면 false. */
    fun flush(context: Context): Boolean {
        val prefs = Prefs(context)
        val url = prefs.webAppUrl
        val store = PendingStore.get(context)
        if (!isAllowed(url)) {
            prefs.lastStatus = "웹앱 주소가 비었거나 올바르지 않아 보내지 않음 (대기 ${store.count()}건)"
            return true // 주소를 넣으면 화면에서 다시 보낸다
        }
        var sent = 0
        while (true) {
            val batch = store.peek(BATCH)
            if (batch.isEmpty()) break
            val rows = JSONArray().apply { batch.forEach { put(JSONObject(it.second)) } }
            val body = JSONObject().put("token", prefs.token).put("rows", rows)
            val res = try {
                post(url, body.toString())
            } catch (e: Exception) {
                prefs.lastStatus = "${now()} 전송 실패, 나중에 다시 보냄: ${e.message} (대기 ${store.count()}건)"
                return false
            }
            if (!res.optBoolean("ok")) {
                prefs.lastStatus = "${now()} 시트가 거부함: ${res.optString("error")} (대기 ${store.count()}건)"
                return false
            }
            val ids = res.optJSONArray("ids") ?: JSONArray()
            if (ids.length() == 0) break
            store.remove((0 until ids.length()).map { ids.getString(it) })
            sent += res.optInt("added")
        }
        prefs.lastStatus = "${now()} 전송 완료 (새로 추가 ${sent}건)"
        return true
    }

    /** 연결 확인용. 시트에 아무것도 쓰지 않는다. */
    fun test(context: Context): String {
        val prefs = Prefs(context)
        if (!isAllowed(prefs.webAppUrl)) return "웹앱 주소는 https://script.google.com/macros/s/…/exec 형태여야 합니다"
        return try {
            val res = post(prefs.webAppUrl, JSONObject().put("token", prefs.token).put("test", true).toString())
            if (res.optBoolean("ok")) "연결 성공: 시트 '${res.optString("sheet")}'" else "연결은 됐지만 거부됨: ${res.optString("error")}"
        } catch (e: Exception) {
            "연결 실패: ${e.message}"
        }
    }

    private fun post(url: String, body: String): JSONObject {
        var conn = open(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        var code = conn.responseCode
        // Apps Script는 실행 결과를 script.googleusercontent.com 주소로 넘겨준다 (302 → GET)
        var hops = 0
        while (code in 300..399 && hops++ < 3) {
            val next = URL(URL(url), conn.getHeaderField("Location") ?: break)
            conn.disconnect()
            if (next.protocol != "https" || !(next.host.endsWith(".googleusercontent.com") || next.host.endsWith(".google.com"))) {
                throw IOException("Google이 아닌 주소로 넘어가려 해서 멈춤: ${next.host}")
            }
            conn = open(next.toString())
            code = conn.responseCode
        }
        if (code !in 200..299) throw IOException("HTTP $code")
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            // 로그인 화면 HTML이 오면 웹앱 접근 권한이 "모든 사용자"가 아닌 것
            throw IOException("응답이 JSON이 아님. 웹앱 배포의 액세스 권한을 '모든 사용자'로 했는지 확인")
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 15_000
        readTimeout = 30_000
    }

    private fun now() = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date())
}
