package kr.paylog.collector

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** 설정 화면 하나. 레이아웃 파일 없이 코드로 그린다. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private lateinit var accessStatus: TextView
    private lateinit var queueStatus: TextView
    private lateinit var urlInput: EditText
    private lateinit var tokenView: TextView
    private lateinit var appList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        })
        build()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun build() {
        header("1. 알림 접근 권한")
        accessStatus = text("")
        button("알림 접근 설정 열기") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        note("권한 켜기가 회색으로 막혀 있으면: 아래 '앱 정보' → 오른쪽 위 ⋮ → '제한된 설정 허용' 후 다시 시도")
        button("앱 정보 열기") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        button("배터리 사용 '제한 없음'으로 바꾸기") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        header("2. 구글 시트 연결")
        note("Apps Script 웹앱 주소 (https://script.google.com/macros/s/…/exec)")
        urlInput = EditText(this).apply {
            setText(prefs.webAppUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        root.addView(urlInput)
        button("주소 저장") {
            val url = urlInput.text.toString().trim()
            if (!Uploader.isAllowed(url)) {
                toast("script.google.com 웹앱 주소(/exec로 끝남)만 쓸 수 있습니다")
            } else {
                prefs.webAppUrl = url
                toast("저장했습니다")
                UploadJob.schedule(this)
            }
        }
        note("토큰: Apps Script의 스크립트 속성 TOKEN에 똑같이 넣어 주세요")
        tokenView = text("").apply { setTextIsSelectable(true); typeface = Typeface.MONOSPACE }
        button("토큰 복사") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("token", prefs.token))
            toast("복사했습니다")
        }
        button("토큰 새로 만들기") {
            prefs.newToken()
            refresh()
            toast("새 토큰을 Apps Script에도 바꿔 넣어야 합니다")
        }
        val tokenInput = EditText(this).apply {
            hint = "다시 설치했다면 예전 토큰을 여기 붙여 넣기"
            isSingleLine = true
            typeface = Typeface.MONOSPACE
        }
        root.addView(tokenInput)
        button("붙여 넣은 토큰으로 바꾸기") {
            val t = tokenInput.text.toString().trim()
            if (t.matches(Regex("""[0-9A-Za-z]{16,128}"""))) {
                prefs.setToken(t)
                tokenInput.setText("")
                refresh()
                toast("토큰을 바꿨습니다. 연결 테스트를 눌러 확인하세요")
            } else {
                toast("토큰은 영문·숫자 16자 이상입니다")
            }
        }
        button("연결 테스트") { background({ Uploader.test(this) }) { toast(it) } }

        header("3. 전송 상태")
        queueStatus = text("")
        button("대기 중인 것 지금 보내기") {
            background({ Uploader.flush(this) }) { refresh() }
        }

        header("4. 수집할 앱")
        note("체크한 앱의 알림만 읽습니다. 처음 보는 앱은 그 앱에서 알림이 한 번 오면 아래 목록에 나타납니다.")
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(appList)
        val manual = EditText(this).apply { hint = "패키지명 직접 추가 (예: com.example.app)"; isSingleLine = true }
        root.addView(manual)
        button("추가") {
            val pkg = manual.text.toString().trim()
            if (pkg.matches(Regex("""[A-Za-z][\w]*(\.[A-Za-z_][\w]*)+"""))) {
                prefs.packages = prefs.packages + pkg
                manual.setText("")
                refresh()
            } else {
                toast("패키지명 형식이 아닙니다")
            }
        }

        header("5. 옵션")
        checkbox("알림 원문도 시트에 저장 (추출이 틀렸을 때 확인용)", prefs.sendRaw) { prefs.sendRaw = it }
        checkbox("금액을 못 찾은 알림도 '미분류'로 저장", prefs.keepUnmatched) { prefs.keepUnmatched = it }
    }

    private fun refresh() {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        accessStatus.text = if (enabled) "✅ 허용됨" else "❌ 아직 허용 안 됨"
        tokenView.text = prefs.token
        queueStatus.text = "보내지 못하고 기다리는 알림: ${PendingStore.get(this).count()}건\n마지막 결과: ${prefs.lastStatus}"
        renderApps()
    }

    private fun renderApps() {
        appList.removeAllViews()
        val chosen = prefs.packages
        val seen = prefs.seenApps()
        val labels = LinkedHashMap<String, String>()
        DefaultApps.ALL.forEach { labels[it.pkg] = it.label }
        chosen.filter { it !in labels }.forEach { labels[it] = seen[it] ?: it }
        seen.keys.sorted().filter { it !in labels }.forEach { labels[it] = seen.getValue(it) }

        labels.forEach { (pkg, label) ->
            val mark = if (pkg in seen) "  · 이 폰에서 알림 옴" else ""
            appList.addView(CheckBox(this).apply {
                text = "$label$mark\n$pkg"
                isChecked = pkg in chosen
                setOnCheckedChangeListener { _, on ->
                    prefs.packages = if (on) prefs.packages + pkg else prefs.packages - pkg
                }
            })
        }
    }

    private fun <T> background(work: () -> T, done: (T) -> Unit) {
        Thread { val r = work(); runOnUiThread { done(r) } }.start()
    }

    private fun header(s: String) = root.addView(TextView(this).apply {
        text = s
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    })

    private fun text(s: String) = TextView(this).apply { text = s; textSize = 15f }.also { root.addView(it) }

    private fun note(s: String) = root.addView(TextView(this).apply { text = s; textSize = 13f; alpha = 0.7f })

    private fun button(label: String, onClick: () -> Unit) = root.addView(Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    })

    private fun checkbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
        root.addView(CheckBox(this).apply {
            text = label
            isChecked = checked
            setOnCheckedChangeListener { _, on -> onChange(on) }
        })

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
