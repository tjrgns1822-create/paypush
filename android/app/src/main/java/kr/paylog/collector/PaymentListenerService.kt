package kr.paylog.collector

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 폰에 뜨는 알림을 받는 서비스. 고른 앱의 알림만 보고, 나머지는 바로 버린다.
 */
class PaymentListenerService : NotificationListenerService() {

    private val seenThisRun = HashSet<String>()

    override fun onListenerConnected() {
        UploadJob.schedulePeriodic(this)
        if (PendingStore.get(this).count() > 0) UploadJob.schedule(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val prefs = Prefs(this)
        rememberApp(prefs, pkg)
        if (pkg !in prefs.packages) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val parsed = Parser.parse(title, text, strict = pkg in DefaultApps.MESSENGERS) ?: return
        if (!parsed.matched && !prefs.keepUnmatched) return

        val id = sha1("$pkg|$title|$text")
        val row = JSONObject()
            .put("id", id)
            .put("receivedAt", ISO.format(Date(sbn.postTime)))
            .put("kind", parsed.kind)
            .put("amount", parsed.amount ?: JSONObject.NULL)
            .put("currency", parsed.currency)
            .put("merchant", parsed.merchant)
            .put("card", parsed.card)
            .put("installment", parsed.installment)
            .put("txTime", parsed.txTime)
            .put("balance", parsed.balance)
            .put("app", appLabel(pkg))
            .put("pkg", pkg)
            .put("raw", if (prefs.sendRaw) "$title\n$text".trim() else "")

        if (PendingStore.get(this).addIfNew(id, row.toString(), System.currentTimeMillis())) {
            UploadJob.schedule(this)
        }
    }

    /** 앱 목록 화면에 "최근 알림 보낸 앱"으로 보여 주려고 이름만 기억한다. */
    private fun rememberApp(prefs: Prefs, pkg: String) {
        if (!seenThisRun.add(pkg)) return
        prefs.addSeen(pkg, appLabel(pkg))
    }

    private fun appLabel(pkg: String): String =
        DefaultApps.label(pkg) ?: try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    }
}
