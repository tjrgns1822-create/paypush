/**
 * 결제알림 수집기 → 구글 시트 기록용 Apps Script 웹앱.
 *
 * 설정:
 *  1. 기록할 구글 시트에서 확장 프로그램 → Apps Script를 열고 이 코드를 붙여 넣는다.
 *  2. 프로젝트 설정(톱니바퀴) → 스크립트 속성에 TOKEN 을 추가하고, 폰 앱에 보이는 토큰을 값으로 넣는다.
 *  3. 배포 → 새 배포 → 유형: 웹 앱, 실행 사용자: 나, 액세스 권한: 모든 사용자 → 배포.
 *  4. 나온 웹앱 주소(…/exec)를 폰 앱에 넣는다.
 *
 * 이 스크립트는 받은 행을 이 시트에 쓰기만 하고, 다른 곳으로 아무것도 보내지 않는다.
 */

const SHEET_NAME = '결제내역';
const HEADERS = [
  '수신시각', '거래시각(알림)', '구분', '금액', '통화', '가맹점/내용',
  '카드/계좌', '할부', '누적/잔액', '앱', '패키지', '원문', 'ID',
];
// 같은 행이 두 번 들어오지 않도록 최근 이만큼의 ID와 비교한다
const DEDUP_WINDOW = 2000;

function doPost(e) {
  let body;
  try {
    body = JSON.parse(e.postData.contents);
  } catch (err) {
    return json_({ ok: false, error: 'bad_json' });
  }

  const token = PropertiesService.getScriptProperties().getProperty('TOKEN');
  if (!token) return json_({ ok: false, error: 'TOKEN 스크립트 속성이 없음' });
  if (body.token !== token) return json_({ ok: false, error: '토큰이 다름' });

  if (body.test) return json_({ ok: true, test: true, sheet: SHEET_NAME });

  const rows = Array.isArray(body.rows) ? body.rows : [];
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const sheet = getSheet_();
    const last = sheet.getLastRow();
    const n = Math.min(DEDUP_WINDOW, last - 1);
    const existing = new Set(
      n > 0 ? sheet.getRange(last - n + 1, HEADERS.length, n, 1).getValues().map(r => String(r[0])) : []
    );

    const ids = [];
    const out = [];
    rows.forEach(r => {
      if (!r || !r.id) return;
      ids.push(String(r.id));
      if (existing.has(String(r.id))) return;
      existing.add(String(r.id));
      out.push([
        r.receivedAt ? new Date(r.receivedAt) : new Date(),
        safe_(r.txTime),
        safe_(r.kind),
        typeof r.amount === 'number' ? r.amount : '',
        safe_(r.currency),
        safe_(r.merchant),
        safe_(r.card),
        safe_(r.installment),
        safe_(r.balance),
        safe_(r.app),
        safe_(r.pkg),
        safe_(r.raw),
        String(r.id),
      ]);
    });

    if (out.length) {
      sheet.getRange(last + 1, 1, out.length, HEADERS.length).setValues(out);
    }
    // 앱은 ids에 있는 행만 폰에서 지운다 (이미 있던 중복도 받은 것으로 친다)
    return json_({ ok: true, added: out.length, ids: ids });
  } finally {
    lock.releaseLock();
  }
}

function doGet() {
  return ContentService.createTextOutput('결제알림 수집기 웹앱이 동작 중입니다.');
}

function getSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
    sheet.appendRow(HEADERS);
    sheet.setFrozenRows(1);
    sheet.getRange('A:A').setNumberFormat('yyyy-mm-dd hh:mm:ss');
    sheet.getRange('D:D').setNumberFormat('#,##0.##');
  }
  return sheet;
}

// 알림 글자가 =, +, -, @ 로 시작하면 시트가 수식으로 읽지 않도록 앞에 ' 를 붙인다
function safe_(v) {
  const s = v == null ? '' : String(v);
  return /^[=+\-@]/.test(s) ? "'" + s : s;
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
