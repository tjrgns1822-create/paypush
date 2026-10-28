package kr.paylog.collector

/**
 * 알림 제목·본문에서 결제 정보를 뽑는다.
 * 안드로이드에 의존하지 않으므로 JVM 단위 테스트(ParserTest)로 검증한다.
 */
data class Parsed(
    /** 금액과 거래 종류를 둘 다 찾았으면 true */
    val matched: Boolean,
    /** 승인, 취소, 거절, 입금, 출금 중 하나. 못 찾으면 "미분류" */
    val kind: String,
    val amount: Double?,
    val currency: String,
    val merchant: String,
    val card: String,
    val installment: String,
    /** 알림 본문에 적힌 거래 시각 (예: "10/28 14:23"). 없으면 빈 문자열 */
    val txTime: String,
    /** 누적 사용액이나 잔액 (예: "누적 1,234,567") */
    val balance: String,
)

object Parser {
    // 광고성 알림은 법적으로 "(광고)" 표기가 붙는다
    private val AD = Regex("""[(\[]\s*광고\s*[)\]]""")

    // 앞에 있는 규칙이 우선한다. "승인취소"는 취소, "승인거절"은 거절.
    private val KIND_RULES = listOf(
        "거절" to Regex("""거절|승인\s*불가|한도\s*초과"""),
        "취소" to Regex("""취소|환불"""),
        "입금" to Regex("""입금|받았|들어왔|충전"""),
        "출금" to Regex("""출금|이체|송금|보냈"""),
        "승인" to Regex("""승인|결제|사용|일시불|할부|구매"""),
    )

    private const val NUM = """(?:\d{1,3}(?:,\d{3})+|\d+)"""
    private val KRW = Regex("""(?<![\d.,])($NUM)\s*원""")
    private val BANK_AMOUNT = Regex("""(?:입금|출금|이체|송금)\s*:?\s*($NUM)(?![\d,])""")
    private val FOREIGN = Regex(
        "\\b(USD|EUR|JPY|CNY|GBP|HKD|SGD|AUD|CAD|VND|THB|TWD|PHP)\\s*([\\d,]+(?:\\.\\d+)?)" +
            "|([$€¥£])\\s*([\\d,]+(?:\\.\\d+)?)"
    )
    private val SYMBOLS = mapOf("$" to "USD", "€" to "EUR", "¥" to "JPY", "£" to "GBP")

    // 금액 바로 앞에 이런 말이 오면 결제 금액이 아니다 (누적 1,234원, 잔액 5,000원 등)
    private val NOT_PRICE_BEFORE = Regex("""(누적|잔액|잔고|한도|적립|포인트|캐시백|할인|가능|최대|최소)\s*:?\s*$""")
    private val BALANCE = Regex("""(누적|잔액|잔고)\s*:?\s*(-?$NUM)\s*원?""")

    private val CARD = Regex("""([A-Za-z가-힣]{1,8}(?:카드|체크))\s*[(\[]?([\d*]{3,4})?[)\]]?""")
    private val CARD_SHORT = Regex("""(KB국민|국민|신한|삼성|현대|롯데|우리|하나|NH농협|농협|NH|BC|비씨|씨티|IBK|기업|카카오뱅크|케이뱅크|토스)\s*([\d*]{4})""")
    private val ACCOUNT = Regex("""\d{2,6}[-*]+[\d*-]{2,}""")
    private val INSTALLMENT = Regex("""일시불|\d{1,2}\s*개월""")

    private val DATE_SLASH = Regex("""(?<![\d.,])(\d{1,2})/(\d{1,2})(?:\s*(\d{1,2}:\d{2}))?""")
    private val DATE_KOREAN = Regex("""(\d{1,2})월\s*(\d{1,2})일(?:\s*(\d{1,2}:\d{2}))?""")
    private val TIME_ONLY = Regex("""(?<![\d:])(\d{1,2}:\d{2})(?![\d:])""")

    // 문자·카카오톡처럼 아무 메시지나 오는 앱은 카드사·은행 이름이 있어야 결제 알림으로 본다
    private val BRAND = Regex("""카드|체크|은행|뱅크|Bank|BANK|페이|Pay|농협|신협|새마을|우체국|저축|증권|상품권|\[(?:KB|NH|IBK|SC|BNK|DGB|MG)\]""")

    private val STOP = Regex("""누적|잔액|잔고|이용\s*가능|\n""")
    private val NOISE = listOf(
        Regex("""\[[^\]]{0,15}]"""),
        CARD, CARD_SHORT, ACCOUNT, FOREIGN, KRW, BANK_AMOUNT, INSTALLMENT,
        Regex("""[가-힣]\*[가-힣*]{0,3}\s*님?"""), // 마스킹된 이름 (홍*동님)
        Regex("""[\d*]*\*[\d*]+"""), // 마스킹된 번호
        DATE_SLASH, DATE_KOREAN, TIME_ONLY,
        Regex("""승인|취소|결제|사용|입금|출금|이체|송금|완료|체크|해외|국내|님"""),
    )
    private val PUNCT = Regex("""[|:·,()\-→>/\s]+""")

    /**
     * 광고면 null. 그 밖에는 항상 결과를 돌려주고, 결제 알림으로 볼 만한지는 matched로 알린다.
     * strict가 true면 카드사·은행 이름도 있어야 matched가 된다 (문자 앱용).
     */
    fun parse(title: String, text: String, strict: Boolean = false): Parsed? {
        val full = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
        if (AD.containsMatchIn(full)) return null

        val kind = KIND_RULES.firstOrNull { it.second.containsMatchIn(full) }?.first
        val (amount, currency) = amountOf(full)
        val dt = DATE_SLASH.find(full) ?: DATE_KOREAN.find(full)
        val time = dt ?: TIME_ONLY.find(full)

        var matched = kind != null && amount != null
        if (strict && !BRAND.containsMatchIn(full)) matched = false

        return Parsed(
            matched = matched,
            kind = kind ?: "미분류",
            amount = amount,
            currency = currency,
            merchant = merchantOf(full, time),
            card = cardOf(full),
            installment = INSTALLMENT.find(full)?.value?.replace(" ", "").orEmpty(),
            txTime = dateText(dt) ?: time?.value.orEmpty(),
            balance = BALANCE.find(full)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }.orEmpty(),
        )
    }

    private fun amountOf(s: String): Pair<Double?, String> {
        for (m in KRW.findAll(s)) {
            val before = s.substring(maxOf(0, m.range.first - 8), m.range.first)
            if (NOT_PRICE_BEFORE.containsMatchIn(before)) continue
            return m.groupValues[1].replace(",", "").toDouble() to "KRW"
        }
        BANK_AMOUNT.find(s)?.let { return it.groupValues[1].replace(",", "").toDouble() to "KRW" }
        FOREIGN.find(s)?.let { m ->
            val code = m.groupValues[1].ifEmpty { SYMBOLS[m.groupValues[3]].orEmpty() }
            val num = m.groupValues[2].ifEmpty { m.groupValues[4] }.replace(",", "")
            return num.toDoubleOrNull() to code
        }
        return null to "KRW"
    }

    private fun cardOf(s: String): String {
        // 제목에도 카드 이름이 있을 수 있어서 끝자리 번호까지 붙은 것을 먼저 고른다
        CARD.findAll(s).let { all -> all.firstOrNull { it.groupValues[2].isNotEmpty() } ?: all.firstOrNull() }?.let { m ->
            val last = m.groupValues[2]
            return if (last.isEmpty()) m.groupValues[1] else "${m.groupValues[1]}($last)"
        }
        CARD_SHORT.find(s)?.let { return "${it.groupValues[1]}(${it.groupValues[2]})" }
        ACCOUNT.find(s)?.let { m ->
            val bank = Regex("""\[([^\]]{1,10})]""").find(s)?.groupValues?.get(1)
            return if (bank == null || bank.contains("발신")) m.value else "$bank ${m.value}"
        }
        return ""
    }

    private fun dateText(m: MatchResult?): String? {
        m ?: return null
        val month = m.groupValues[1].toInt()
        val day = m.groupValues[2].toInt()
        if (month !in 1..12 || day !in 1..31) return null
        val t = m.groupValues[3]
        return "%02d/%02d".format(month, day) + if (t.isEmpty()) "" else " $t"
    }

    /** 카드 문자는 보통 "날짜 시각 가맹점" 순서라서 시각 뒤를 먼저 본다. */
    private fun merchantOf(full: String, time: MatchResult?): String {
        if (time != null) {
            val after = full.substring(time.range.last + 1)
            val sameLine = after.split(STOP, limit = 2)[0]
            clean(sameLine).let { if (it.isNotEmpty()) return it }
            val nextLine = after.substringAfter('\n', "").substringBefore('\n')
            if (!nextLine.trimStart().startsWith("누적") && !nextLine.trimStart().startsWith("잔액")) {
                clean(nextLine).let { if (it.isNotEmpty()) return it }
            }
        }
        return full.split('\n')
            .map { clean(it.split(STOP, limit = 2)[0]) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    private fun clean(s: String): String =
        NOISE.fold(s) { acc, r -> acc.replace(r, " ") }.replace(PUNCT, " ").trim()
}
