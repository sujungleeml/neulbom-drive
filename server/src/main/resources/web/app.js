// 늘봄 웹(조회·설정 전용). 점검은 차에서만 한다 — 이 파일에는 점검을 실행하는 코드가 없다.
(function () {
  const NB = window.NB;
  const $ = function (id) { return document.getElementById(id); };
  const SIGNALS = {
    VEHICLE_STATE: '주행·주차 상태', SPEED: '속도', BRAKE: '브레이크', ACCEL_PEDAL: '가속 페달',
    LONG_ACCEL: '앞뒤 가속도', STEERING: '조향', GEAR: '기어',
  };
  const SIGNAL_STATUS = { SUPPORTED: '받음', UNSUPPORTED: '받지 않음', UNVERIFIED: '확인 전' };
  const CONSENTS = {
    DRIVING_RECORD: ['주행 습관 기록', '차량이 허용하는 범위의 주행 시간·속도·감속 정보. 위치·경로·목적지는 받지 않아요.'],
    COGNITIVE_CHECK: ['주차 중 주의·반응 점검', '주차 중 직접 시작한 점검의 반응 기록. 끄면 차에서 점검 제안과 점검이 나타나지 않아요.'],
  };
  const PAUSE = [[0, '쉬지 않음'], [7, '1주'], [14, '2주'], [30, '30일']];
  let chosenPause = null; // 이번에 고른 기간(서버는 끝나는 날짜만 알려 준다)
  const SHARE_DAYS = [[1, '1일'], [3, '3일'], [7, '7일']];
  const SCOPES = { DRIVING_SUMMARY: '나의 드라이빙', CHECK_RECORDS: '주의·반응 기록' };
  const LINK_STATE = { ACTIVE: '열려 있음', EXPIRED: '기간 끝남', REVOKED: '취소함' };
  const OFFLINE = '기록을 불러오지 못했어요. 연결을 확인한 뒤 다시 열어 주세요.';

  async function getJson(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error(res.status);
    return res.json();
  }
  async function send(method, url, body) {
    const res = await fetch(url, { method: method, headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
    if (!res.ok) throw new Error(res.status);
    return res.status === 204 ? null : res.json();
  }

  // ---- 나의 드라이빙 ----
  const now = new Date();
  let year = now.getFullYear();
  let month = now.getMonth() + 1;
  function periodKey() { return year + '-' + String(month).padStart(2, '0'); }

  async function loadDriving() {
    $('period-title').textContent = year + '년 ' + month + '월';
    try {
      const r = await getJson('/v1/reports/me/driving?period=' + periodKey());
      $('state').textContent = NB.DRIVING_STATE[r.state] || '';
      $('mock').hidden = r.origin !== 'MOCK';
      NB.drivingCards(r, $('cards'));
      renderChanges(r);
      loadSuggestions();
      NB.listItems($('notices'), r.notices);
      renderQuality(r.quality);
    } catch (e) { $('state').textContent = OFFLINE; }
  }

  // 늘어난 변화에만 일반 운전 습관 정보(habits.json)의 한 절을 함께 보여 준다. 점검 기록으로는 연결하지 않는다.
  const CHANGE_TIP = { harsh_decel_per_100km: '길 위에서', night_share: '나에게 맞는 시간과 길', distance: '쉬어 가기' };
  let habitsCache = null;
  // 이번 달의 기록과 작은 제안: 시연 서버에서는 합성 시나리오, 그 밖에는 실제 경로의 상태 문장만 보여 준다.
  let suggestScenario = new URLSearchParams(location.search).get('scenario') || 'R01';
  async function loadSuggestions() {
    const box = $('suggest');
    let s = null, demo = true;
    try {
      const res = await fetch('/v1/demo/suggestions?scenario=' + encodeURIComponent(suggestScenario));
      if (res.ok) s = await res.json();
      else { demo = false; s = await getJson('/v1/reports/me/suggestions'); }
    } catch (e) { box.hidden = true; return; }
    box.hidden = false;
    $('suggest-badge').hidden = !s.synthetic;
    const chips = $('suggest-scenarios');
    chips.replaceChildren();
    if (demo) s.scenarios.forEach(function (sc) {
      const b = NB.el('button', 'chip' + (sc.id === s.scenario ? ' on' : ''), sc.id);
      b.type = 'button';
      b.title = sc.label;
      b.setAttribute('aria-label', sc.id + ' ' + sc.label);
      b.setAttribute('aria-pressed', String(sc.id === s.scenario));
      b.addEventListener('click', function () { suggestScenario = sc.id; loadSuggestions(); });
      chips.append(b);
    });
    chips.hidden = !demo;
    const main = $('suggest-main');
    main.replaceChildren();
    $('suggest-state').textContent = s.primary ? '' : s.notice;
    if (!s.primary) return;
    // 3·4. 두 기록을 함께 살펴보는 한 문장과 실천할 수 있는 짧은 제안
    const lead = NB.el('div', 'suggest-lead');
    lead.append(NB.el('p', 'together', s.primary.together), NB.el('p', 'action', s.primary.action));
    main.append(lead);
    // 1·2. 각 기록에서 관찰된 변화(근거 수치와 비교 기간)
    const cols = NB.el('div', 'suggest-cols');
    [['주의와 반응 활동에서 달라진 점', s.activityEvidence, s.activityBasis], ['주행 기록에서 달라진 점', s.drivingEvidence, s.drivingBasis]].forEach(function (c) {
      const col = NB.el('section', 'suggest-col');
      col.append(NB.el('h3', null, c[0]));
      const ul = NB.el('ul', null);
      c[1].forEach(function (t) { ul.append(NB.el('li', null, t)); });
      col.append(ul, NB.el('p', 'basis', c[2] || ''));
      cols.append(col);
    });
    main.append(cols);
    if (s.others.length) {
      const more = NB.el('details', 'suggest-more');
      more.append(NB.el('summary', null, '다른 제안 ' + s.others.length + '개 더 보기'));
      s.others.forEach(function (o) { more.append(NB.el('p', 'other', o.together + ' ' + o.action)); });
      main.append(more);
    }
    main.append(NB.el('p', 'suggest-note', s.notice + ' 운전 실력이나 건강을 판단한 것이 아니에요. 필요한 것만 골라 참고해 주세요.'));
  }

  async function renderChanges(r) {
    const list = $('change-list');
    list.replaceChildren();
    const changes = r.lines.filter(function (l) { return l.changeText; });
    if (r.state !== 'TREND_AVAILABLE' || !changes.length) {
      $('change-state').textContent = '아직 기록을 모으는 중이에요. 두 달 모두 8일, 100 km 이상 기록이 모이면 지난달과 비교해 보여 드려요.';
      return;
    }
    const today = new Date();
    const isNow = r.period === today.getFullYear() + '-' + String(today.getMonth() + 1).padStart(2, '0');
    $('change-state').textContent = isNow ? '이번 달은 오늘(' + today.getDate() + '일)까지의 기록이에요. 거리처럼 쌓이는 값은 달이 끝날 때까지 더 늘 수 있어요.' : '';
    try { if (!habitsCache) habitsCache = await getJson('habits.json'); } catch (e) { habitsCache = { sections: [] }; }
    changes.forEach(function (l) {
      const li = NB.el('li', null);
      li.append(NB.el('p', null, l.changeText));
      const sec = l.direction === 'MORE' ? habitsCache.sections.find(function (s) { return s.title === CHANGE_TIP[l.key]; }) : null;
      if (sec) {
        const tip = NB.el('div', 'tip');
        const head = NB.el('p', null);
        head.append(document.createTextNode('함께 보면 좋은 운전 습관 정보: '), NB.el('strong', null, sec.title));
        const ul = NB.el('ul', null);
        sec.items.forEach(function (t) { ul.append(NB.el('li', null, t)); });
        tip.append(head, ul);
        li.append(tip);
      }
      list.append(li);
    });
  }

  function renderQuality(q) {
    const box = $('driving-quality');
    box.replaceChildren();
    if (!q || q.drives === 0) { box.append(NB.el('li', null, '이번 달에는 아직 주행 기록이 없어요.')); return; }
    [['받은 주행 기록', q.drives + '번', null], ['리포트에 넣은 주행', q.included + '번', null],
     ['신호가 일부 끊긴 주행(참고용)', q.limited + '번', q.limited ? 'no' : null],
     ['계산에서 뺀 주행', q.excluded + '번', q.excluded ? 'no' : null]].forEach(function (row) {
      const li = NB.el('li');
      li.append(NB.el('span', null, row[0]), NB.el('span', row[2], row[1]));
      box.append(li);
    });
  }

  async function loadSignals() {
    const list = $('signals');
    try {
      const items = await getJson('/v1/signal-support');
      list.replaceChildren();
      if (!items.length) { list.append(NB.el('li', null, '차량 연결 전이에요.')); return; }
      items.forEach(function (s) {
        const li = NB.el('li');
        li.append(NB.el('span', null, SIGNALS[s.signal] || s.signal),
          NB.el('span', s.status === 'SUPPORTED' ? 'yes' : 'no', (SIGNAL_STATUS[s.status] || s.status) + (s.sdkName === 'MOCK' ? ' (모의)' : '')));
        list.append(li);
      });
    } catch (e) { /* 리포트 쪽에서 연결 문제를 안내한다 */ }
  }

  // ---- 나의 주의·반응 기록 ----
  async function loadChecks() {
    try {
      const r = await getJson('/v1/reports/me/checks');
      $('checks-state').textContent = NB.CHECK_STATE[r.state] || '';
      $('checks-mock').hidden = !r.mockVehicle;
      NB.checkCards(r, $('checks-cards'));
      NB.listItems($('checks-notices'), r.notices);
      NB.historyList(r, $('checks-history'));
      renderRecords(r);
    } catch (e) { $('checks-state').textContent = OFFLINE; }
  }

  // 점검별 기록: 과제마다 따로(축을 공유하지 않음). 같은 비교 키 구간이 4번 이상일 때만 선으로 잇는다(NB.taskChart).
  const HIDDEN = { TOO_FEW_TRIALS: '유효한 반응이 적어 값을 내지 않았어요', UNKNOWN_VERSION: '알 수 없는 점검 방식이라 값을 내지 않았어요' };
  function keyLabel(key) {
    const p = key.split('·');
    return '방식 ' + p[0].replace('v', '') + ' · 자극 ' + p[1].replace('s', '') + ' · ' + p.slice(2).join('·');
  }
  function localDay(iso) { const d = new Date(iso); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); }
  function renderRecords(r) {
    const box = $('checks-records');
    box.replaceChildren();
    r.tasks.forEach(function (t) {
      const name = NB.TASK_LABELS[t.taskId] || t.taskId;
      const fig = NB.el('figure', 'chart');
      fig.append(NB.el('figcaption', null, name + ' · 정확히 누르는 데 걸린 시간(가운데 값)'));
      const pts = t.points || [];
      if (!pts.length) { fig.append(NB.el('p', 'empty-chart', '아직 점검 기록이 없어요.')); box.append(fig); return; }
      const holder = NB.el('div');
      const svg = NB.taskChart(pts, localDay(pts[0].startedAt), localDay(pts[pts.length - 1].startedAt), name + ' 점검별 반응시간');
      if (svg) holder.append(svg); else holder.append(NB.el('p', 'empty-chart', '값을 낸 점검이 아직 없어요.'));
      fig.append(holder);
      const shown = pts.filter(function (p) { return p.medianRtMs !== null; });
      const lastKey = shown.length ? shown[shown.length - 1].compareKey : null;
      const sameCount = shown.filter(function (p) { return p.compareKey === lastKey; }).length;
      if (lastKey && sameCount < 4) fig.append(NB.el('p', 'chart-note', '지금 방식의 점검이 ' + sameCount + '번이라 점만 보여 드려요. 4번부터 선으로 이어요.'));
      if (new Set(pts.map(function (p) { return p.compareKey; })).size > 1) fig.append(NB.el('p', 'chart-warn', '점검 방식이나 기기가 바뀐 곳은 점선으로 나눴어요.'));
      const table = NB.el('table', 'rec-table');
      const head = NB.el('tr');
      ['날짜', '걸린 시간', '맞게 누름', '점검 방식'].forEach(function (h) { head.append(NB.el('th', null, h)); });
      table.append(head);
      pts.slice().reverse().slice(0, 8).forEach(function (p) {
        const tr = NB.el('tr', p.medianRtMs === null ? 'muted' : null);
        tr.append(NB.el('td', null, NB.dateLabel(p.startedAt)));
        if (p.medianRtMs === null) {
          const td = NB.el('td', null, HIDDEN[p.hiddenReason] || '값 없음');
          td.colSpan = 2;
          tr.append(td);
        } else {
          tr.append(NB.el('td', null, (p.medianRtMs / 1000).toFixed(2) + '초'));
          tr.append(NB.el('td', null, p.accuracy === null ? '—' : Math.round(p.accuracy * 100) + '%'));
        }
        tr.append(NB.el('td', 'key', keyLabel(p.compareKey)));
        table.append(tr);
      });
      fig.append(table);
      if (pts.length > 8) fig.append(NB.el('p', 'chart-sub', '최근 8번만 표로 보여 드려요(전체 ' + pts.length + '번).'));
      box.append(fig);
    });
  }

  // ---- 나의 여정 ----
  let journeyMonths = 6;
  function renderJourneyRange() {
    const box = $('journey-range');
    box.replaceChildren();
    [[6, '6개월'], [12, '12개월']].forEach(function (r) {
      const b = NB.el('button', 'chip' + (r[0] === journeyMonths ? ' on' : ''), r[1]);
      b.type = 'button';
      b.setAttribute('role', 'radio');
      b.setAttribute('aria-checked', String(r[0] === journeyMonths));
      b.addEventListener('click', function () { journeyMonths = r[0]; renderJourneyRange(); loadJourney(); });
      box.append(b);
    });
  }

  function journeyCard(m) {
    const parts = m.period.split('-');
    const card = NB.el('article', 'month' + (m.text === '기록이 없는 달이에요.' ? ' quiet' : ''));
    const head = NB.el('div', 'month-head');
    head.append(NB.el('h2', null, parts[0] + '년 ' + Number(parts[1]) + '월'));
    if (m.driving.mock || m.checks.mock) head.append(NB.el('span', 'badge small-badge', '모의 기록 포함'));
    card.append(head);
    if (m.driving.drives === 0 && m.checks.completed === 0 && m.checks.stopped === 0) {
      card.append(NB.el('p', 'text', m.text)); // 빈 달은 숫자 없이 한 줄로
      return card;
    }
    const cols = NB.el('div', 'month-cols');
    const d = NB.el('div', 'month-col');
    d.append(NB.el('div', 'label', '운전'));
    d.append(NB.el('div', 'big', m.driving.drivingDays + '일'));
    const dDetail = [m.driving.drives + '번'];
    if (m.driving.distanceKm !== null && m.driving.drives > 0) dDetail.push(m.driving.distanceKm.toFixed(1) + ' km');
    d.append(NB.el('div', 'small', dDetail.join(' · ')));
    const c = NB.el('div', 'month-col');
    c.append(NB.el('div', 'label', '주의·반응 점검'));
    c.append(NB.el('div', 'big', m.checks.completed + '번'));
    c.append(NB.el('div', 'small', m.checks.stopped > 0 ? '중간에 멈춘 점검 ' + m.checks.stopped + '번' : '마친 점검'));
    cols.append(d, c);
    card.append(cols, NB.el('p', 'text', m.text));
    return card;
  }

  async function loadJourney() {
    try {
      const j = await getJson('/v1/reports/me/journey?months=' + journeyMonths);
      const box = $('journey-months');
      box.replaceChildren();
      j.months.forEach(function (m) { box.append(journeyCard(m)); });
      const active = j.months.filter(function (m) { return m.driving.drives > 0 || m.checks.completed > 0; }).length;
      $('journey-state').textContent = '최근 ' + j.months.length + '개월 중 ' + active + '개월에 기록이 있어요.';
      NB.listItems($('journey-notices'), j.notices);
    } catch (e) { $('journey-state').textContent = OFFLINE; }
  }

  // ---- 건강한 운전 습관 정보(일반 정보, 사용자가 직접 열어 볼 때만) ----
  async function loadHabits() {
    try {
      const h = await getJson('/habits.json');
      $('habits-draft').hidden = h.reviewStatus !== 'DRAFT';
      $('habits-note').textContent = h.reviewNote;
      const box = $('habits-sections');
      box.replaceChildren();
      h.sections.forEach(function (sec) {
        const card = NB.el('article', 'card habit');
        card.append(NB.el('h2', null, sec.title));
        const ul = NB.el('ul', 'habit-items');
        sec.items.forEach(function (t) { ul.append(NB.el('li', null, t)); });
        card.append(ul);
        box.append(card);
      });
    } catch (e) { $('habits-note').textContent = OFFLINE; }
  }

  // ---- 설정·권한 ----
  function renderSettings(s) {
    const box = $('consents');
    box.replaceChildren();
    Object.keys(CONSENTS).forEach(function (type) {
      const found = s.consents.find(function (c) { return c.type === type; });
      const on = !!(found && found.granted);
      const row = NB.el('div', 'row');
      const text = NB.el('div', 'grow');
      text.append(NB.el('strong', null, CONSENTS[type][0]), NB.el('p', 'small', CONSENTS[type][1]));
      const sw = NB.el('button', 'switch' + (on ? ' on' : ''));
      sw.type = 'button';
      sw.setAttribute('role', 'switch');
      sw.setAttribute('aria-checked', String(on));
      sw.setAttribute('aria-label', CONSENTS[type][0]);
      sw.append(NB.el('span', null, on ? '켜짐' : '꺼짐'));
      sw.addEventListener('click', async function () {
        sw.disabled = true;
        try { renderSettings(await send('PUT', '/v1/settings/me/consents/' + type, { granted: !on })); }
        catch (e) { $('data-state').textContent = '바꾸지 못했어요. 잠시 뒤 다시 해 주세요.'; sw.disabled = false; }
      });
      row.append(text, sw);
      box.append(row);
    });

    const pause = $('pause');
    pause.replaceChildren();
    const until = s.offersPausedUntil ? new Date(s.offersPausedUntil) : null;
    PAUSE.forEach(function (p) {
      const b = NB.el('button', 'chip', p[1]);
      b.type = 'button';
      b.setAttribute('role', 'radio');
      const selected = p[0] === 0 ? !until : (until !== null && chosenPause === p[0]);
      b.setAttribute('aria-checked', String(selected));
      if (selected) b.classList.add('on');
      b.addEventListener('click', async function () {
        try { chosenPause = p[0]; renderSettings(await send('PUT', '/v1/settings/me/offers', { pauseDays: p[0] })); } catch (e) { /* 그대로 둔다 */ }
      });
      pause.append(b);
    });
    $('pause-state').textContent = until ? (until.getMonth() + 1) + '월 ' + until.getDate() + '일까지 점검 제안을 쉬어요.' : '정해진 간격으로 점검을 제안해요.';
    $('retention').textContent = '기록은 ' + s.retentionDays + '일 동안 보관해요.';
  }

  async function loadSettings() {
    try { renderSettings(await getJson('/v1/settings/me')); } catch (e) { $('data-state').textContent = OFFLINE; }
  }

  $('delete-all').addEventListener('click', async function () {
    if (!window.confirm('서버에 있는 주행·점검 기록과 공유 링크를 모두 지울까요? 지운 기록은 되돌릴 수 없어요.')) return;
    try {
      await send('DELETE', '/v1/me/data');
      $('data-state').textContent = '기록을 모두 지웠어요. 동의를 바꾼 이력은 남겨 두어요.';
      loadDriving(); loadChecks(); loadShares();
    } catch (e) { $('data-state').textContent = '지우지 못했어요. 잠시 뒤 다시 해 주세요.'; }
  });

  // ---- 가족 공유 ----
  let shareDays = 7;
  function renderShareDays() {
    const box = $('share-days');
    box.replaceChildren();
    SHARE_DAYS.forEach(function (d) {
      const b = NB.el('button', 'chip' + (d[0] === shareDays ? ' on' : ''), d[1]);
      b.type = 'button';
      b.setAttribute('role', 'radio');
      b.setAttribute('aria-checked', String(d[0] === shareDays));
      b.addEventListener('click', function () { shareDays = d[0]; renderShareDays(); });
      box.append(b);
    });
  }

  async function loadShares() {
    const list = $('share-list');
    try {
      const links = await getJson('/v1/share-links');
      list.replaceChildren();
      if (!links.length) { list.append(NB.el('li', null, '아직 만든 링크가 없어요.')); return; }
      links.forEach(function (l) {
        const li = NB.el('li', 'share-item');
        const info = NB.el('div', 'grow');
        info.append(NB.el('strong', null, l.scopes.map(function (s) { return SCOPES[s] || s; }).join(' · ')),
          NB.el('p', 'small', NB.dateLabel(l.createdAt) + ' 만듦 · ' + NB.dateLabel(l.expiresAt) + '까지 · ' + l.views + '번 열어 봄'));
        li.append(info, NB.el('span', l.state === 'ACTIVE' ? 'yes' : 'no', LINK_STATE[l.state] || l.state));
        if (l.state === 'ACTIVE') {
          const cancel = NB.el('button', 'btn secondary small-btn', '취소');
          cancel.type = 'button';
          cancel.addEventListener('click', async function () {
            try { await send('DELETE', '/v1/share-links/' + l.id); loadShares(); } catch (e) { /* 다음 새로고침 때 다시 */ }
          });
          li.append(cancel);
        }
        list.append(li);
      });
    } catch (e) { list.replaceChildren(NB.el('li', null, OFFLINE)); }
  }

  $('share-create').addEventListener('click', async function () {
    const scopes = Array.from(document.querySelectorAll('input[name=scope]:checked')).map(function (i) { return i.value; });
    if (!scopes.length) { $('share-state').textContent = '보여 줄 기록을 하나 이상 골라 주세요.'; return; }
    try {
      const link = await send('POST', '/v1/share-links', { scopes: scopes, days: shareDays });
      $('share-url').value = location.origin + link.path;
      $('share-result').hidden = false;
      $('share-state').textContent = NB.dateLabel(link.expiresAt) + '까지 열려 있어요.';
      loadShares();
    } catch (e) { $('share-state').textContent = '링크를 만들지 못했어요. 잠시 뒤 다시 해 주세요.'; }
  });
  $('share-copy').addEventListener('click', async function () {
    const url = $('share-url').value;
    try { await navigator.clipboard.writeText(url); $('share-state').textContent = '링크를 복사했어요.'; }
    catch (e) { $('share-url').select(); $('share-state').textContent = '링크를 길게 눌러 복사해 주세요.'; }
  });

  // ---- 한눈에 보기(D23): 두 레인을 같은 주 축에 따로 ----
  let overviewWeeks = 8;
  function renderOverviewRange() {
    const box = $('overview-range');
    box.replaceChildren();
    [4, 8, 12].forEach(function (w) {
      const b = NB.el('button', 'chip' + (w === overviewWeeks ? ' on' : ''), w + '주');
      b.type = 'button';
      b.setAttribute('role', 'radio');
      b.setAttribute('aria-checked', String(w === overviewWeeks));
      b.addEventListener('click', function () { overviewWeeks = w; renderOverviewRange(); loadOverview(); });
      box.append(b);
    });
  }

  function chartBox(container, svg, emptyText) {
    container.replaceChildren(svg || NB.el('p', 'empty-chart', emptyText));
  }

  async function loadOverview() {
    try {
      const o = await getJson('/v1/reports/me/overview?weeks=' + overviewWeeks);
      $('overview-state').textContent = o.from.replace(/-/g, '.') + ' ~ ' + o.to.replace(/-/g, '.') + ' (' + o.weeks + '주)';
      // 주행 레인
      const d = o.driving;
      $('lane-driving-text').textContent = d.text;
      $('lane-driving-mock').hidden = !d.weeks.some(function (w) { return w.mock; });
      const partial = o.lastWeekInProgress;
      chartBox($('chart-driving-days'), NB.barChart(o.weekStarts, d.weeks.map(function (w) { return w.drivingDays; }), { label: '주마다 주행한 날', max: 7, partialLast: partial }));
      const decel = d.weeks.map(function (w) { return w.harshDecelPer100Km; });
      chartBox($('chart-driving-decel'), decel.some(function (v) { return v !== null; })
        ? NB.barChart(o.weekStarts, decel, { label: '주별 100km당 급감속 기록', format: function (v) { return v.toFixed(1); }, partialLast: partial })
        : null, '주행 거리가 충분한 주가 아직 없어요.');
      // 그래프 바로 아래: '—'(계산 안 함)과 0회의 차이, 진행 중인 주.
      const short = d.weeks.filter(function (w) { return w.harshDecelStatus === 'NOT_ENOUGH_DISTANCE'; }).length;
      const none = d.weeks.filter(function (w) { return w.harshDecelStatus === 'NO_DRIVING'; }).length;
      const why = [];
      if (short) why.push('주행 50km 미만 ' + short + '주');
      if (none) why.push('주행 없음 ' + none + '주');
      $('legend-driving-decel').textContent = '숫자 = 100km당 횟수(0은 급감속 없음) · — = 계산하지 않음' + (why.length ? ' (' + why.join(', ') + ')' : '');
      $('legend-driving-days').textContent = partial ? '마지막 주(~ 표시)는 아직 진행 중이라 지금까지만 셌어요.' : '';
      NB.listItems($('lane-driving-notes'), d.notes);
      // 점검 레인
      const c = o.checks;
      $('lane-checks-text').textContent = '이 기간에 점검을 ' + c.completed + '번 마쳤어요' + (c.stopped ? ', ' + c.stopped + '번은 중간에 멈췄어요.' : '.');
      $('lane-checks-mock').hidden = !c.weeks.some(function (w) { return w.mock; });
      chartBox($('chart-checks-weeks'), NB.dotChart(o.weekStarts, c.weeks, '주마다 한 점검', partial));
      $('legend-checks-weeks').textContent = '● 마친 점검   ○ 중간에 멈춘 점검' + (partial ? ' · 마지막 주(~)는 진행 중' : '');
      const box = $('lane-checks-tasks');
      box.replaceChildren();
      c.tasks.forEach(function (t) {
        const fig = NB.el('figure', 'chart');
        fig.append(NB.el('figcaption', null, (NB.TASK_LABELS[t.taskId] || t.taskId) + ' · 정확히 누르는 데 걸린 시간(가운데 값, 초)'));
        const holder = NB.el('div');
        chartBox(holder, NB.taskChart(t.points, o.from, o.to, (NB.TASK_LABELS[t.taskId] || t.taskId) + ' 반응시간'), '이 기간에 보여 드릴 기록이 없어요.');
        fig.append(holder);
        // 꼭 알아야 할 안내는 접지 않고 그래프 바로 아래에. 측정 조건 변경은 주의 색, 데이터 품질은 중립 색.
        const hidden = t.points.filter(function (p) { return p.medianRtMs === null; }).length;
        if (t.excludedCount > 0) fig.append(NB.el('p', 'chart-warn', '점검 방식이나 기기가 바뀐 곳은 점선으로 나눴어요.'));
        if (hidden > 0) fig.append(NB.el('p', 'chart-note', '유효한 반응이 적은 점검 ' + hidden + '번은 그래프에 없어요.'));
        // 설명은 첫 문장만 보이고, 나머지와 비교 횟수는 펼쳐 보기(넓은 화면에서는 처음부터 펼침).
        const sentences = t.text.split(/(?<=요\.)\s+/);
        fig.append(NB.el('p', 'chart-text', sentences[0]));
        const more = document.createElement('details');
        more.className = 'chart-more';
        if (window.matchMedia('(min-width: 900px)').matches) more.open = true;
        more.append(NB.el('summary', null, '자세히'));
        if (sentences.length > 1) more.append(NB.el('p', 'chart-sub', sentences.slice(1).join(' ')));
        more.append(NB.el('p', 'chart-sub', '비교한 점검 ' + t.comparableCount + '번' + (t.excludedCount > 0 ? ' · 방식이나 기기가 달라 비교에서 뺀 점검 ' + t.excludedCount + '번' : '')));
        more.append(NB.el('p', 'chart-sub', '같은 방식의 점검이 4번 이상 모인 구간만 선으로 이었어요.'));
        fig.append(more);
        box.append(fig);
      });
      NB.listItems($('lane-checks-notes'), c.notes);
      NB.listItems($('overview-notices'), o.notices);
    } catch (e) { $('overview-state').textContent = OFFLINE; }
  }

  // ---- 탭 ----
  const loaders = { overview: loadOverview, driving: loadDriving, checks: loadChecks, journey: loadJourney, settings: loadSettings, share: loadShares, habits: loadHabits, pattern: window.NBPattern.load };
  function openTab(name) {
    document.querySelectorAll('.tab').forEach(function (t) {
      const on = t.dataset.tab === name;
      t.classList.toggle('on', on);
      if (on) t.setAttribute('aria-current', 'page'); else t.removeAttribute('aria-current');
    });
    document.querySelectorAll('.panel').forEach(function (p) { p.hidden = p.id !== name; });
    if (loaders[name]) loaders[name]();
    if (location.hash !== '#' + name) history.pushState(null, '', '#' + name);
  }
  document.querySelectorAll('.tab').forEach(function (tab) { tab.addEventListener('click', function () { openTab(tab.dataset.tab); }); });
  document.querySelectorAll('.to-share').forEach(function (b) { b.addEventListener('click', function () { openTab('share'); }); });
  document.querySelectorAll('.to-tab').forEach(function (b) { b.addEventListener('click', function () { openTab(b.dataset.to); }); });
  $('prev').addEventListener('click', function () { month -= 1; if (month === 0) { month = 12; year -= 1; } loadDriving(); });
  $('next').addEventListener('click', function () { month += 1; if (month === 13) { month = 1; year += 1; } loadDriving(); });

  function tabFromHash() {
    const name = location.hash.slice(1);
    const panel = document.getElementById(name);
    return panel && panel.classList.contains('panel') ? name : 'overview';
  }
  // 주소의 #탭 이름을 따른다 — 브라우저 뒤로 가기·즐겨찾기로도 같은 화면이 열린다.
  window.addEventListener('hashchange', function () { openTab(tabFromHash()); });

  renderShareDays();
  renderJourneyRange();
  renderOverviewRange();
  loadSignals();
  openTab(tabFromHash());
})();
