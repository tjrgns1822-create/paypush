package kr.paylog.collector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 실제 알림 문구를 흉내 낸 예시. 새 형식을 만나면 여기에 추가해서 확인한다.
class ParserTest {

    @Test fun 신한카드_문자_한줄() {
        val p = Parser.parse("", "[Web발신]\n신한카드(1234)승인 홍*동 12,500원(일시불)10/28 14:23 스타벅스 누적1,234,567원", strict = true)!!
        assertTrue(p.matched)
        assertEquals("승인", p.kind)
        assertEquals(12500.0, p.amount)
        assertEquals("스타벅스", p.merchant)
        assertEquals("신한카드(1234)", p.card)
        assertEquals("일시불", p.installment)
        assertEquals("10/28 14:23", p.txTime)
        assertEquals("누적 1,234,567", p.balance)
    }

    @Test fun 국민카드_여러줄() {
        val p = Parser.parse("KB국민카드", "KB국민카드1*2*승인\n홍*동님\n32,000원 3개월\n10/28 19:05\n교보문고\n누적512,000원")!!
        assertTrue(p.matched)
        assertEquals(32000.0, p.amount)
        assertEquals("교보문고", p.merchant)
        assertEquals("KB국민카드(1*2*)", p.card)
        assertEquals("3개월", p.installment)
    }

    @Test fun 삼성카드_짧은카드명() {
        val p = Parser.parse("", "삼성1234승인 홍*동\n8,900원 일시불\n10/27 08:12 GS25 역삼점\n누적100,000원")!!
        assertEquals("삼성(1234)", p.card)
        assertEquals("GS25 역삼점", p.merchant)
        assertEquals(8900.0, p.amount)
    }

    @Test fun 승인취소() {
        val p = Parser.parse("", "신한카드(1234)승인취소 홍*동 12,500원 10/28 14:30 스타벅스")!!
        assertEquals("취소", p.kind)
        assertEquals(12500.0, p.amount)
        assertEquals("스타벅스", p.merchant)
    }

    @Test fun 앱_푸시_형식() {
        val p = Parser.parse("12,500원 결제", "스타벅스 | 현대카드")!!
        assertTrue(p.matched)
        assertEquals("승인", p.kind)
        assertEquals("스타벅스", p.merchant)
        assertEquals("현대카드", p.card)
    }

    @Test fun 은행_출금_원없음() {
        val p = Parser.parse("", "[KB]10/28 14:23 123456**789 홍길동 출금 50,000 잔액1,000,000")!!
        assertTrue(p.matched)
        assertEquals("출금", p.kind)
        assertEquals(50000.0, p.amount)
        assertEquals("홍길동", p.merchant)
        assertEquals("KB 123456**789", p.card)
        assertEquals("잔액 1,000,000", p.balance)
    }

    @Test fun 토스_입금() {
        val p = Parser.parse("50,000원 입금", "홍길동 → 토스뱅크 통장")!!
        assertEquals("입금", p.kind)
        assertEquals(50000.0, p.amount)
        assertEquals("홍길동 토스뱅크 통장", p.merchant)
    }

    @Test fun 해외결제_달러() {
        val p = Parser.parse("", "현대카드 승인 홍*동 USD 12.99 10/28 03:10 NETFLIX.COM")!!
        assertEquals(12.99, p.amount)
        assertEquals("USD", p.currency)
        assertEquals("NETFLIX.COM", p.merchant)
    }

    @Test fun chak_지역상품권_결제() {
        val p = Parser.parse("결제 완료", "OO상품권 9,000원이 결제되었습니다. (OO식당)")!!
        assertTrue(p.matched)
        assertEquals("승인", p.kind)
        assertEquals(9000.0, p.amount)
    }

    @Test fun 광고는_버림() {
        assertNull(Parser.parse("(광고) 신한카드", "최대 50,000원 캐시백! 지금 결제하세요"))
    }

    @Test fun 누적금액은_결제금액이_아님() {
        val p = Parser.parse("", "롯데카드 승인 누적 300,000원 7,700원 10/28 12:00 맘스터치")!!
        assertEquals(7700.0, p.amount)
    }

    @Test fun 문자앱_친구메시지는_무시() {
        val p = Parser.parse("엄마", "10,000원 입금했어", strict = true)!!
        assertFalse(p.matched)
    }

    @Test fun 금액없으면_미매칭() {
        val p = Parser.parse("신한 SOL페이", "새로운 혜택이 도착했어요")!!
        assertFalse(p.matched)
    }
}
