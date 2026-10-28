package kr.paylog.collector

data class KnownApp(val pkg: String, val label: String, val onByDefault: Boolean = true)

/**
 * 처음 설치했을 때 켜져 있는 앱 목록. 패키지명은 2026-10-28 Google Play에서 확인했다.
 * 폰에 없는 앱은 켜져 있어도 아무 일도 하지 않는다. 앱 화면에서 언제든 끄고 켤 수 있다.
 */
object DefaultApps {
    /** 아무 메시지나 오는 앱. 카드사·은행 이름이 있는 알림만 받는다. */
    val MESSENGERS = setOf(
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.kakao.talk",
    )

    val ALL = listOf(
        // 지역상품권
        KnownApp("com.komscochak.m2.client", "지역상품권 chak"),
        // 카드사
        KnownApp("com.shcard.smartpay", "신한 SOL페이"),
        KnownApp("com.kbcard.cxh.appcard", "KB Pay"),
        KnownApp("kr.co.samsungcard.mpocket", "삼성카드"), // 스토어에서 직접 확인 못 함
        KnownApp("com.hyundaicard.appcard", "현대카드"),
        KnownApp("com.lcacApp", "디지로카(롯데카드)"),
        KnownApp("com.wooricard.smartapp", "우리WON카드"),
        KnownApp("com.hanaskcard.paycla", "하나Pay(하나카드)"),
        KnownApp("nh.smart.nhallonepay", "NH pay(농협카드)"),
        KnownApp("kvp.jjy.MispAndroid320", "페이북/ISP(BC카드)"),
        // 은행
        KnownApp("com.kbstar.kbbank", "KB스타뱅킹"),
        KnownApp("com.shinhan.sbanking", "신한 SOL뱅크"),
        KnownApp("com.wooribank.smart.npib", "우리WON뱅킹"),
        KnownApp("com.hanabank.oqf", "하나원큐"),
        KnownApp("com.kebhana.hanapush", "(구)하나원큐"),
        KnownApp("nh.smart.banking", "NH스마트뱅킹"),
        KnownApp("com.ibk.android.ionebank", "IBK i-ONE Bank"),
        KnownApp("com.kakaobank.channel", "카카오뱅크"),
        KnownApp("com.kbankwith.smartbank", "케이뱅크"),
        KnownApp("viva.republica.toss", "토스"),
        KnownApp("com.epost.psf.sdsi", "우체국뱅킹"),
        // 간편결제
        KnownApp("com.samsung.android.spay", "삼성 월렛"),
        KnownApp("com.kakaopay.app", "카카오페이"),
        KnownApp("com.naverfin.payapp", "네이버페이"),
        KnownApp("com.nhnent.payapp", "PAYCO"),
        // 문자 (카드 승인 문자)
        KnownApp("com.samsung.android.messaging", "삼성 메시지"),
        KnownApp("com.google.android.apps.messaging", "Google 메시지"),
        // 친구 대화가 섞이므로 기본은 꺼 둔다
        KnownApp("com.kakao.talk", "카카오톡(알림톡)", onByDefault = false),
    )

    val enabledByDefault: Set<String> = ALL.filter { it.onByDefault }.map { it.pkg }.toSet()

    fun label(pkg: String): String? = ALL.firstOrNull { it.pkg == pkg }?.label
}
