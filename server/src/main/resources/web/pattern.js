// 패턴 지도(시연): 합성 운전자 분포(연령 구간별 분위수) 위에 내 값을 점 하나로 보여 준다.
// 군집·유형·평가하는 색·판정 문구는 쓰지 않는다. 분포는 pattern-map.json(시연용 합성 데이터)에서만 온다.
(function () {
  const NB = window.NB;
  const $ = function (id) { return document.getElementById(id); };
  const SVGNS = 'http://www.w3.org/2000/svg';
  const MIN_CHECKS = 4;   // 합성 분포와 같은 규칙(같은 방식 점검 4번 이상)
  const MIN_WEEKS = 4;    // 주행 값을 낼 최소 완료 주
  const BAND_LABEL = { '전체': '전체', '60-64': '60~64세', '65-69': '65~69세', '70-74': '70~74세', '75-79': '75~79세', '80+': '80세 이상' };

  function svgEl(tag, attrs, text) {
    const e = document.createElementNS(SVGNS, tag);
    Object.keys(attrs || {}).forEach(function (k) { e.setAttribute(k, attrs[k]); });
    if (text !== undefined) e.textContent = text;
    return e;
  }
  function median(xs) {
    const s = xs.slice().sort(function (a, b) { return a - b; });
    const m = Math.floor(s.length / 2);
    return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
  }

  /** 값 → 보여 줄 문자열. 반응 시간은 웹의 다른 화면처럼 초로 보여 준다. */
  function fmt(metric, v) {
    if (metric.unit === 'ms') return (v / 1000).toFixed(2) + '초';
    if (metric.unit === '%') return Math.round(v) + '%';
    if (metric.unit === 'km') return Math.round(v) + 'km';
    return v.toFixed(1) + metric.unit;
  }

  // ---- 내 값(기존 '한눈에 보기' API, 최근 12주) ----
  function myCheckValue(metric, lane) {
    const t = lane.tasks.find(function (x) { return x.taskId === metric.taskId; });
    if (!t) return { reason: '아직 점검 기록이 없어요.' };
    const shown = t.points.filter(function (p) { return p.medianRtMs !== null; });
    if (!shown.length) return { reason: '아직 점검 기록이 없어요.' };
    const key = shown[shown.length - 1].compareKey;
    const want = 'v' + metric.taskVersion + '·s' + metric.setVersion + '·';
    if (key.indexOf(want) !== 0) return { reason: '점검 방식이 이 지도와 달라 함께 보여 드리지 않아요.' };
    const same = shown.filter(function (p) { return p.compareKey === key; });
    if (same.length < MIN_CHECKS) return { reason: '같은 방식의 점검이 ' + same.length + '번이에요. ' + MIN_CHECKS + '번 모이면 함께 보여 드려요.' };
    return { value: median(same.map(function (p) { return p.medianRtMs; })), note: '최근 12주 같은 방식 점검 ' + same.length + '번의 가운데 값' };
  }

  function myDrivingValue(metric, o) {
    const done = o.driving.weeks.slice(0, o.lastWeekInProgress ? -1 : undefined);
    // 첫 주행 주부터 센다(기록을 시작하기 전의 빈 주가 값을 끌어내리지 않게). 그 뒤의 주행 없는 주는 0으로 센다.
    const first = done.findIndex(function (w) { return w.drives > 0; });
    const weeks = first < 0 ? [] : done.slice(first);
    if (weeks.length < MIN_WEEKS) return { reason: '주행 기록이 아직 적어요.' };
    if (metric.id === 'drive_days') return { value: median(weeks.map(function (w) { return w.drivingDays; })), note: '최근 ' + weeks.length + '주 가운데 값' };
    if (metric.id === 'km_week') return { value: median(weeks.map(function (w) { return w.distanceKm || 0; })), note: '최근 ' + weeks.length + '주 가운데 값' };
    const dur = weeks.filter(function (w) { return w.nightShare !== null && w.durationS > 0; });
    const total = dur.reduce(function (a, w) { return a + w.durationS; }, 0);
    if (!total) return { reason: '주행 기록이 아직 적어요.' };
    return { value: 100 * dur.reduce(function (a, w) { return a + w.nightShare * w.durationS; }, 0) / total, note: '최근 ' + weeks.length + '주 주행 시간 기준' };
  }

  // ---- 그림: 지표 하나 = 연령 구간 줄 여러 개, 같은 가로축 ----
  function bandChart(metric, mine) {
    const rows = metric.bands;
    const W = 640, left = 92, right = 24, top = 26, rowH = 34, bottom = 26;
    const H = top + rows.length * rowH + bottom;
    const vals = [];
    rows.forEach(function (r) { if (r.q) vals.push(r.q['5'], r.q['95']); });
    if (mine && mine.value !== undefined) vals.push(mine.value);
    let lo = Math.min.apply(null, vals), hi = Math.max.apply(null, vals);
    const pad = (hi - lo) * 0.06 || 1;
    lo -= pad; hi += pad;
    if (metric.unit === '%' || metric.unit === '일' || metric.unit === 'km') lo = Math.max(0, lo);
    function X(v) { return left + (W - left - right) * (v - lo) / (hi - lo); }
    const svg = svgEl('svg', { viewBox: '0 0 ' + W + ' ' + H, role: 'img',
      'aria-label': metric.label + ' — 합성 운전자 분포' + (mine && mine.value !== undefined ? ', 내 값 ' + fmt(metric, mine.value) : '') });
    rows.forEach(function (r, i) {
      const y = top + i * rowH + rowH / 2;
      svg.append(svgEl('text', { x: left - 10, y: y + 4, 'text-anchor': 'end', class: 'axis band-label' + (r.band === '전체' ? ' all' : '') }, BAND_LABEL[r.band] || r.band));
      if (!r.q) { svg.append(svgEl('text', { x: left, y: y + 4, class: 'axis' }, '합성 기록이 적어 그리지 않았어요')); return; }
      const q = r.q;
      svg.append(svgEl('line', { x1: X(q['5']), x2: X(q['95']), y1: y, y2: y, class: 'pm-whisker' }));
      svg.append(svgEl('rect', { x: X(q['10']), y: y - 7, width: Math.max(1, X(q['90']) - X(q['10'])), height: 14, rx: 7, class: 'pm-outer' }));
      svg.append(svgEl('rect', { x: X(q['25']), y: y - 9, width: Math.max(1, X(q['75']) - X(q['25'])), height: 18, rx: 9, class: 'pm-inner' }));
      svg.append(svgEl('line', { x1: X(q['50']), x2: X(q['50']), y1: y - 11, y2: y + 11, class: 'pm-mid' }));
    });
    // 가로축 눈금(양 끝과 가운데)
    const yAxis = top + rows.length * rowH + 16;
    [lo + pad, (lo + hi) / 2, hi - pad].forEach(function (v) {
      svg.append(svgEl('text', { x: X(v), y: yAxis, 'text-anchor': 'middle', class: 'axis' }, fmt(metric, v)));
    });
    if (mine && mine.value !== undefined) {
      const x = X(mine.value);
      svg.append(svgEl('line', { x1: x, x2: x, y1: top - 6, y2: top + rows.length * rowH, class: 'pm-me' }));
      svg.append(svgEl('text', { x: x, y: top - 10, 'text-anchor': 'middle', class: 'pm-me-label' }, '나 ' + fmt(metric, mine.value)));
    }
    return svg;
  }

  function figure(metric, mine) {
    const fig = NB.el('figure', 'chart pm-figure');
    fig.append(NB.el('figcaption', null, metric.label));
    fig.append(bandChart(metric, mine));
    if (mine.value !== undefined) fig.append(NB.el('p', 'chart-sub', '나: ' + fmt(metric, mine.value) + ' (' + mine.note + ')'));
    else fig.append(NB.el('p', 'chart-note', mine.reason));
    return fig;
  }

  const POINT_KEY = { va_rt: 'va', gng_rt: 'gng', ts_rt: 'ts', drive_days: 'drive_days', km_week: 'km_week', night_share: 'night_share' };
  let points = null;
  /**
   * 지난 내 기록(3D용). 점검: 세 과제 모두 지도와 같은 방식이고 각 과제의 최신 비교 키와 같은 점검만(키를 넘어 잇지 않음).
   * 주행: 다 끝난 주. 합성 점(운전자별 가운데 값)과 같은 눈금이 되도록 각 시점까지 최근 4번(4주)의 가운데 값으로 낸다. 오래된 것부터.
   */
  function myHistory(map, o) {
    const byTask = {};
    map.metrics.filter(function (m) { return m.axis === 'checks'; }).forEach(function (m) {
      const shown = (o.checks.tasks.find(function (t) { return t.taskId === m.taskId; }) || { points: [] }).points.filter(function (p) { return p.medianRtMs !== null; });
      const key = shown.length ? shown[shown.length - 1].compareKey : null;
      const ok = key && key.indexOf('v' + m.taskVersion + '·s' + m.setVersion + '·') === 0;
      byTask[POINT_KEY[m.id]] = {};
      if (ok) shown.filter(function (p) { return p.compareKey === key; }).forEach(function (p) { byTask[POINT_KEY[m.id]][p.startedAt] = p.medianRtMs; });
    });
    const checkRows = Object.keys(byTask.va || {}).sort().filter(function (t) { return t in byTask.gng && t in byTask.ts; })
      .map(function (t) { return { va: byTask.va[t], gng: byTask.gng[t], ts: byTask.ts[t] }; });
    const weeks = o.driving.weeks.slice(0, o.lastWeekInProgress ? -1 : undefined);
    // 첫 주행 주부터(그 앞의 기록 없는 주는 넣지 않는다)
    const first = weeks.findIndex(function (w) { return w.drives > 0; });
    const weekRows = first < 0 ? [] : weeks.slice(first).map(function (w) {
      return { drive_days: w.drivingDays, km_week: w.distanceKm || 0, night: w.nightShare, dur: w.durationS };
    });
    const checks = rolling(checkRows, function (win) {
      return { va: median(win.map(function (r) { return r.va; })), gng: median(win.map(function (r) { return r.gng; })), ts: median(win.map(function (r) { return r.ts; })) };
    });
    const driving = rolling(weekRows, function (win) {
      const d = win.filter(function (r) { return r.night !== null && r.dur > 0; });
      const total = d.reduce(function (a, r) { return a + r.dur; }, 0);
      return { drive_days: median(win.map(function (r) { return r.drive_days; })), km_week: median(win.map(function (r) { return r.km_week; })),
        night_share: total ? 100 * d.reduce(function (a, r) { return a + r.night * r.dur; }, 0) / total : null };
    }).filter(function (r) { return r.night_share !== null; });
    // 마지막 창은 지금의 나와 같은 값이라 뺀다
    return { checks: checks.slice(0, -1), driving: driving.slice(0, -1) };
  }

  const WINDOW = 4;
  /** 각 시점까지 최근 WINDOW개로 만든 값(창이 다 찬 시점부터). */
  function rolling(rows, f) {
    const out = [];
    for (let i = WINDOW - 1; i < rows.length; i++) out.push(f(rows.slice(i - WINDOW + 1, i + 1)));
    return out;
  }

  async function render3d(me, history) {
    try {
      if (!points) points = await (await fetch('pattern-points.json')).json();
      [['checks', '세 활동 모두 같은 방식의 점검이 4번 이상 모이면 "나"를 함께 보여 드려요.'],
       ['driving', '주행 기록이 4주 이상 모이면 "나"를 함께 보여 드려요.']].forEach(function (a) {
        const box = $('pattern-' + a[0] + '-3d');
        box.replaceChildren();
        const r = window.NBPattern3D.view(box, a[0], points.drivers, me, history[a[0]], points.groups);
        if (a[0] === 'driving') groupCards(points.groups, r.myGroup);
        $('pattern-' + a[0] + '-3d-note').textContent = '점 하나 = 합성 운전자 한 명(' + r.count.toLocaleString() + '명). ' +
          (a[0] === 'driving' ? '색은 시연용 주행 군집이에요. 범례를 누르면 그 군집만 진하게 보여요.' : '점이 모인 모양은 생성기 가정에서 나온 것이고, 나눠진 무리나 경계가 있다는 뜻이 아니에요.') +
          (a[0] === 'driving' ? ' 주행한 날은 겹치지 않게 살짝 흩뜨려 그렸어요.' : '') + (r.mine ? '' : ' ' + a[1]) +
          (r.past ? ' 진한 노랑은 지금의 나(최근 가운데 값), 연한 노랑 ' + r.past + '개는 지난 시점의 나예요(오래될수록 연하게). 합성 점과 같은 눈금이 되도록 그 시점까지 최근 4' + (a[0] === 'checks' ? '번' : '주') + '의 가운데 값으로 찍었어요.' : '');
      });
    } catch (e) { /* 3D 보기는 보조 화면이라 실패해도 나머지는 그대로 둔다 */ }
  }

  /** 시연용 주행 군집 설명 카드. 숫자는 합성 데이터에서 실제로 뽑힌 가운데 값이다. */
  function groupCards(groups, mine) {
    const box = $('pattern-groups');
    if (!box || !groups) return;
    box.replaceChildren();
    groups.items.forEach(function (g) {
      const card = NB.el('article', 'pm-group' + (mine && mine.id === g.id ? ' mine' : ''));
      card.style.setProperty('--group', g.color);
      const head = NB.el('div', 'pm-group-head');
      head.append(NB.el('span', 'p3-swatch'), NB.el('h3', null, g.title));
      head.firstChild.style.background = g.color;
      card.append(head, NB.el('p', null, g.desc));
      const c = g.center;
      card.append(NB.el('p', 'pm-group-nums', '가운데 값: 주 ' + c.drive_days.toFixed(1) + '일, 주 ' + Math.round(c.km_week) + 'km, 야간 ' + Math.round(c.night_share) + '%'));
      card.append(NB.el('p', 'chart-sub', '합성 운전자 ' + g.n + '명'));
      if (mine && mine.id === g.id) card.append(NB.el('p', 'pm-group-me', '내 최근 4주 기록과 가장 가까운 군집이에요.'));
      box.append(card);
    });
    $('pattern-groups-note').textContent = groups.notice + ' 어느 군집에 가깝다는 것만으로 나를 판단하지 않아요.';
  }

  let cache = null;
  async function load() {
    const state = $('pattern-state');
    state.textContent = '불러오는 중이에요.';
    try {
      if (!cache) cache = await (await fetch('pattern-map.json')).json();
      const map = cache;
      let o = null;
      try { o = await (await fetch('/v1/reports/me/overview?weeks=12')).json(); } catch (e) { o = null; }
      $('pattern-notice').textContent = map.notice;
      const checks = $('pattern-checks'), driving = $('pattern-driving');
      checks.replaceChildren(); driving.replaceChildren();
      const me = {};
      map.metrics.forEach(function (m) {
        let mine;
        if (!o) mine = { reason: '내 기록을 불러오지 못했어요.' };
        else mine = m.axis === 'checks' ? myCheckValue(m, o.checks) : myDrivingValue(m, o);
        if (mine.value !== undefined) me[POINT_KEY[m.id]] = mine.value;
        (m.axis === 'checks' ? checks : driving).append(figure(m, mine));
      });
      await render3d(me, o ? myHistory(map, o) : { checks: [], driving: [] });
      const s = map.source;
      NB.listItems($('pattern-about'), [
        '분포는 시연을 위해 만든 합성 운전자 ' + s.drivers.toLocaleString() + '명 × ' + s.weeks + '주 기록이에요(생성기 ' + s.generator_version + '). 실제 사람의 기록이 아니에요.',
        '연령대 사이의 차이는 생성기에 넣은 가정이에요. 실제 연령별 차이를 확인한 결과가 아니에요.',
        '진한 띠는 합성 운전자 가운데 50%, 옅은 띠는 80%, 가는 선은 90%가 있는 범위예요. 세로 막대는 가운데 값이에요.',
        '띠의 어느 쪽이 좋다거나 나쁘다는 뜻은 없어요. 건강이나 운전 능력에 대한 판단이 아니에요.',
        '점검 값은 같은 방식(과제 버전·자극 세트)의 점검이 ' + MIN_CHECKS + '번 이상일 때만 함께 보여 드려요. 합성 분포도 첫 점검 1번을 빼고 같은 규칙으로 계산했어요.',
        '주행과 점검은 따로 보여 드려요. 두 값을 합친 값을 만들지 않아요.',
        '주행 군집 4개는 시연을 위해 정한 가정으로 만든 묶음이에요. 실제 운전자에게서 찾은 묶음이 아니에요. 점검 공간에는 군집을 만들지 않았어요.',
      ]);
      state.textContent = '내 기록이 합성 운전자 분포의 어디쯤인지 위치만 보여 드려요. 연령대 줄은 모두 같은 "나" 선을 지나요.';
    } catch (e) {
      state.textContent = '패턴 지도를 불러오지 못했어요.';
    }
  }

  window.NBPattern = { load: load };
})();
