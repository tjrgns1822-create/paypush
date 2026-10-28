package kr.paylog.collector

import android.content.Context
import java.security.SecureRandom

/** 설정값. 모두 이 폰 안에만 저장된다. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("paylog", Context.MODE_PRIVATE)

    var webAppUrl: String
        get() = sp.getString("url", "").orEmpty()
        set(v) = sp.edit().putString("url", v.trim()).apply()

    /** Apps Script에 같이 넣어 두는 비밀값. 주소를 알아도 이 값 없이는 시트에 쓸 수 없다. */
    val token: String
        get() = sp.getString("token", null) ?: newToken()

    fun newToken(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val t = bytes.joinToString("") { "%02x".format(it) }
        sp.edit().putString("token", t).apply()
        return t
    }

    var packages: Set<String>
        get() = sp.getStringSet("packages", null)?.toSet() ?: DefaultApps.enabledByDefault
        set(v) = sp.edit().putStringSet("packages", v).apply()

    /** 알림 원문도 시트에 남길지. 추출이 틀렸을 때 원문으로 바로잡을 수 있다. */
    var sendRaw: Boolean
        get() = sp.getBoolean("send_raw", true)
        set(v) = sp.edit().putBoolean("send_raw", v).apply()

    /** 금액·종류를 못 찾은 알림도 "미분류"로 보낼지 */
    var keepUnmatched: Boolean
        get() = sp.getBoolean("keep_unmatched", false)
        set(v) = sp.edit().putBoolean("keep_unmatched", v).apply()

    var lastStatus: String
        get() = sp.getString("last_status", "아직 보낸 적 없음").orEmpty()
        set(v) = sp.edit().putString("last_status", v).apply()

    /** 최근 알림을 보낸 앱 (패키지명 → 앱 이름). 알림 내용은 저장하지 않는다. */
    fun seenApps(): Map<String, String> =
        sp.getStringSet("seen", emptySet()).orEmpty().mapNotNull {
            val i = it.indexOf('\t')
            if (i < 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()

    fun addSeen(pkg: String, label: String) {
        val cur = sp.getStringSet("seen", emptySet()).orEmpty()
        if (cur.any { it.startsWith("$pkg\t") }) return
        val next = (cur + "$pkg\t$label").let { if (it.size > 80) it.drop(it.size - 80).toSet() else it }
        sp.edit().putStringSet("seen", next).apply()
    }
}
