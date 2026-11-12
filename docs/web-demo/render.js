// 늘봄 웹 공용 그리기(메인·공유 화면). 서버가 만든 문장은 그대로 쓰고, 숫자만 읽기 쉽게 바꾼다.
(function () {
  const DRIVING_LABELS = {
    driving_days: '주행한 날',
    duration: '주행 시간',
    distance: '주행 거리',
    harsh_decel_per_100km: '급감속 기록 (100 km당)',
    night_share: '야간 주행 비율',
  };
  const TASK_LABELS = {
    VISUAL_ATTENTION: '화살표 보고 누르기',
    GO_NO_GO: '누를 때와 멈출 때',
    TASK_SWITCHING: '규칙 바꾸기',
  };
  const SECONDARY = {
    omission_rate: ['놓친 비율', 'pct'],
    commission_rate: ['멈출 때 누른 비율', 'pct'],
    switch_cost_ms: ['규칙이 바뀔 때 더 걸린 시간', 'sec'],
  };
  const CHECK_STATE = {
    COLLECTING: '아직 비교할 만큼 점검 기록이 모이지 않았어요. 차에서 몇 번 더 해 보시면 함께 볼 수 있어요.',
    DESCRIPTIVE: '최근 점검 기록이에요.',
    TREND_AVAILABLE: '여러 번의 점검 기록을 함께 보여 드려요.',
    QUALITY_LIMITED: '일부 활동은 점검 방식이나 기기가 바뀌어 기록을 새로 모으고 있어요. 활동마다 아래에 적어 두었어요.',
  };
  const DRIVING_STATE = {
    COLLECTING: '아직 이번 달 주행 기록이 모이지 않았어요.',
    DESCRIPTIVE: '이번 달 기록이에요.',
    TREND_AVAILABLE: '이번 달 기록과 지난달과의 비교예요.',
    QUALITY_LIMITED: '이번 달 기록이에요. 지난달과는 조건이 달라 비교하지 않았어요.',
  };
  const STATUS = { COMPLETED: '마침', INTERRUPTED: '안전을 위해 멈춤', CANCELLED: '중간에 그만둠', IN_PROGRESS: '진행 중' };

  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }

  /** 숫자 + 작은 단위. 단위·숫자 모두 코드가 만든 값만 넣는다. */
  function valueEl(parts) {
    const v = el('div', 'value');
    parts.forEach(function (p) {
      if (p[1]) v.append(document.createTextNode(p[0]), el('small', null, p[1]));
      else v.append(document.createTextNode(p[0]));
    });
    return v;
  }

  function drivingValue(line) {
    const v = line.value;
    if (v === null || v === undefined) return valueEl([['—']]);
    switch (line.key) {
      case 'driving_days': return valueEl([[String(Math.round(v)), '일 · ' + Math.round(line.extra || 0) + '번']]);
      case 'duration': {
        const m = Math.round(v / 60);
        return valueEl([[String(Math.floor(m / 60)), '시간'], [' ' + (m % 60), '분']]);
      }
      case 'distance': return valueEl([[v.toFixed(1), 'km']]);
      case 'harsh_decel_per_100km': return valueEl([[v.toFixed(1), '번']]);
      case 'night_share': return valueEl([[String(Math.round(v * 100)), '%']]);
      default: return valueEl([[String(v)]]);
    }
  }

  function drivingCards(report, container) {
    container.replaceChildren();
    report.lines.forEach(function (line, i) {
      const c = el('article', 'card' + (i === 0 ? ' lead' : '') + (line.value === null ? ' empty' : ''));
      c.append(el('div', 'label', DRIVING_LABELS[line.key] || line.key), drivingValue(line), el('div', 'text', line.text));
      container.append(c);
    });
  }

  function seconds(ms) { return (ms / 1000).toFixed(2); }
  function pct(x) { return Math.round(x * 100); }

  function checkCards(report, container) {
    container.replaceChildren();
    report.tasks.forEach(function (t) {
      const c = el('article', 'card' + (t.medianRtMs === null ? ' empty' : ''));
      c.append(el('div', 'label', TASK_LABELS[t.taskId] || t.taskId));
      if (t.medianRtMs === null) {
        c.append(valueEl([['—']]));
      } else {
        c.append(valueEl([[seconds(t.medianRtMs), '초']]));
        c.append(el('div', 'sub', '정확히 누르는 데 걸린 시간(가운데 값)'));
        const facts = el('ul', 'facts');
        if (t.accuracy !== null) facts.append(el('li', null, '맞게 누른 비율 ' + pct(t.accuracy) + '%'));
        Object.keys(SECONDARY).forEach(function (k) {
          if (!(k in t.secondary)) return;
          const v = t.secondary[k];
          const label = SECONDARY[k][0];
          if (v === null) { facts.append(el('li', 'muted', label + ': 기록이 적어 보여 드리지 않아요')); return; }
          facts.append(el('li', null, label + ' ' + (SECONDARY[k][1] === 'pct' ? pct(v) + '%' : seconds(v) + '초')));
        });
        c.append(facts);
      }
      c.append(el('div', 'text', t.text));
      // 같은 방식·기기의 점검끼리만 비교한다. 뺀 기록이 있으면 숫자로 알린다.
      if (t.excludedCount > 0) {
        c.append(el('div', 'sub', '비교한 점검 ' + t.comparableCount + '번 · 방식이나 기기가 달라 뺀 점검 ' + t.excludedCount + '번'));
      }
      container.append(c);
    });
  }

  function listItems(container, items) {
    container.replaceChildren();
    items.forEach(function (n) { container.append(el('li', null, n)); });
  }

  function historyList(report, container) {
    container.replaceChildren();
    if (!report.history.length) { container.append(el('li', null, '아직 점검 기록이 없어요.')); return; }
    report.history.slice().reverse().slice(0, 8).forEach(function (h) {
      const li = el('li');
      li.append(el('span', null, dateLabel(h.startedAt)), el('span', h.status === 'COMPLETED' ? 'yes' : 'no', STATUS[h.status] || h.status));
      container.append(li);
    });
  }

  function dateLabel(iso) {
    const d = new Date(iso);
    return (d.getMonth() + 1) + '월 ' + d.getDate() + '일 ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  // ---- 한눈에 보기 차트(SVG). 레인마다 따로 그리고, 서로 다른 레인을 한 그림에 넣지 않는다. ----
  const SVGNS = 'http://www.w3.org/2000/svg';
  function svgEl(tag, attrs, text) {
    const e = document.createElementNS(SVGNS, tag);
    Object.keys(attrs || {}).forEach(function (k) { e.setAttribute(k, attrs[k]); });
    if (text !== undefined) e.textContent = text;
    return e;
  }
  function weekLabel(iso) { const p = iso.split('-'); return Number(p[1]) + '/' + Number(p[2]); }

  /** 주별 막대. values의 null은 막대 없이 '—'. */
  function barChart(weekStarts, values, opts) {
    const W = 320, H = 120, top = 14, bottom = 22, n = weekStarts.length;
    const max = Math.max(opts.max || 0, ...values.map(function (v) { return v || 0; }), 1);
    const svg = svgEl('svg', { viewBox: '0 0 ' + W + ' ' + H, role: 'img', 'aria-label': opts.label });
    const bw = W / n;
    weekStarts.forEach(function (w, i) {
      const v = values[i];
      const x = i * bw + bw * 0.18, width = bw * 0.64;
      const partial = opts.partialLast && i === n - 1;
      if (v === null || v === undefined) {
        // 계산하지 않은 주(값 없음)는 막대 대신 '—'. 0회와 구분한다.
        svg.append(svgEl('text', { x: x + width / 2, y: H - bottom - 4, 'text-anchor': 'middle', class: 'axis' }, '—'));
      } else {
        const h = Math.max(2, (H - top - bottom) * v / max);
        svg.append(svgEl('rect', { x: x, y: H - bottom - h, width: width, height: h, rx: 3, class: 'bar' + (v === 0 ? ' zero' : '') + (partial ? ' partial' : '') }));
        svg.append(svgEl('text', { x: x + width / 2, y: H - bottom - h - 3, 'text-anchor': 'middle', class: 'axis' }, opts.format ? opts.format(v) : String(v)));
      }
      if (n <= 8 || i % 2 === 0 || partial) svg.append(svgEl('text', { x: x + width / 2, y: H - 6, 'text-anchor': 'middle', class: 'axis' + (partial ? ' partial-label' : '') }, weekLabel(w) + (partial ? '~' : '')));
    });
    return svg;
  }

  /** 주별 점검: 마침은 채운 점, 멈춤은 빈 점. */
  function dotChart(weekStarts, weeks, label, partialLast) {
    const W = 320, H = 90, n = weekStarts.length, bw = W / n;
    const svg = svgEl('svg', { viewBox: '0 0 ' + W + ' ' + H, role: 'img', 'aria-label': label });
    weeks.forEach(function (w, i) {
      const cx = i * bw + bw / 2;
      let y = H - 30;
      for (let k = 0; k < w.completed; k++) { svg.append(svgEl('circle', { cx: cx, cy: y, r: 6, class: 'dot' })); y -= 15; }
      for (let k = 0; k < w.stopped; k++) { svg.append(svgEl('circle', { cx: cx, cy: y, r: 6, class: 'dot stopped' })); y -= 15; }
      const partial = partialLast && i === n - 1;
      if (n <= 8 || i % 2 === 0 || partial) svg.append(svgEl('text', { x: cx, y: H - 6, 'text-anchor': 'middle', class: 'axis' + (partial ? ' partial-label' : '') }, weekLabel(weekStarts[i]) + (partial ? '~' : '')));
    });
    return svg;
  }

  /**
   * 선으로 이을 최소 기록 수. 점검 리포트가 변화 방향을 보여 주는 기준(유효 점검 4회)과 맞춘 **잠정 시각화 규칙**이며,
   * 통계적 추세 검증 기준이 아니다. 그보다 적으면 점만 찍는다.
   */
  const MIN_POINTS_FOR_LINE = 4;

  /**
   * 과제 하나의 반응시간(가운데 값) 점. 같은 비교 키끼리만, 그리고 그 구간 기록이 충분할 때만 선으로 잇는다.
   * 키가 바뀌는 곳에는 점선 구분선.
   * x는 기간 안의 날짜 위치, y는 이 과제 값만으로 정한다(다른 과제·다른 레인과 축을 공유하지 않음).
   */
  function taskChart(points, from, to, label) {
    const W = 320, H = 120, top = 16, bottom = 18, left = 6, right = 6;
    const svg = svgEl('svg', { viewBox: '0 0 ' + W + ' ' + H, role: 'img', 'aria-label': label });
    const shown = points.filter(function (p) { return p.medianRtMs !== null; });
    if (!shown.length) return null;
    const t0 = new Date(from + 'T00:00:00').getTime(), t1 = new Date(to + 'T23:59:59').getTime();
    const vs = shown.map(function (p) { return p.medianRtMs; });
    const lo = Math.min.apply(null, vs) * 0.9, hi = Math.max.apply(null, vs) * 1.1;
    function X(p) { return left + (W - left - right) * (new Date(p.startedAt).getTime() - t0) / Math.max(1, t1 - t0); }
    function Y(v) { return top + (H - top - bottom) * (1 - (v - lo) / Math.max(1, hi - lo)); }
    // 같은 비교 키가 이어지는 구간으로 나눈다.
    const segments = [];
    shown.forEach(function (p, i) {
      if (i === 0 || shown[i - 1].compareKey !== p.compareKey) {
        if (i > 0) {
          const cx = (X(shown[i - 1]) + X(p)) / 2;
          svg.append(svgEl('line', { x1: cx, y1: top - 6, x2: cx, y2: H - bottom, class: 'cut' }));
        }
        segments.push([]);
      }
      segments[segments.length - 1].push(p);
    });
    segments.filter(function (seg) { return seg.length >= MIN_POINTS_FOR_LINE; }).forEach(function (seg) {
      const d = seg.map(function (p, i) { return (i ? 'L ' : 'M ') + X(p).toFixed(1) + ' ' + Y(p.medianRtMs).toFixed(1); }).join(' ');
      svg.append(svgEl('path', { d: d, class: 'line' }));
    });
    shown.forEach(function (p) { svg.append(svgEl('circle', { cx: X(p), cy: Y(p.medianRtMs), r: 4, class: 'dot' })); });
    const vmin = Math.min.apply(null, vs), vmax = Math.max.apply(null, vs);
    svg.append(svgEl('text', { x: left, y: Y(vmax) - 4, class: 'axis' }, (vmax / 1000).toFixed(2) + '초'));
    if (vmax !== vmin) svg.append(svgEl('text', { x: left, y: Y(vmin) + 12, class: 'axis' }, (vmin / 1000).toFixed(2) + '초'));
    return svg;
  }

  window.NB = {
    el: el, drivingCards: drivingCards, checkCards: checkCards, listItems: listItems, historyList: historyList,
    dateLabel: dateLabel, CHECK_STATE: CHECK_STATE, DRIVING_STATE: DRIVING_STATE,
    barChart: barChart, dotChart: dotChart, taskChart: taskChart, TASK_LABELS: TASK_LABELS,
  };
})();
