// 패턴 지도 3D(시연): 합성 운전자 한 명 = 점 하나. 점검(과제 3개 반응 시간)과 주행(주행일·거리·야간 비율)은 따로 그린다.
// 경계면·초평면·평가하는 색은 그리지 않는다. 점검 공간의 점은 단색이고, 고른 연령대만 진하게 보여 줄 수 있다.
// 주행 공간만 시연용 주행 군집 4개를 서로 구분되는 색으로 칠한다(PO 결정 2026-11-12). 색은 좋고 나쁨을 뜻하지 않는다.
// 내 기록: 지금 값(최근 가운데 값)은 진한 노랑, 지난 점검·주는 같은 노랑으로 오래될수록 연하게. 선으로 잇지 않는다(추세처럼 보이지 않게).
(function () {
  const BAND_LABEL = { all: '전체', '60-64': '60~64세', '65-69': '65~69세', '70-74': '70~74세', '75-79': '75~79세', '80+': '80세 이상' };
  const SPACES = {
    checks: { axes: [['va', '화살표'], ['gng', '누를 때와 멈출 때'], ['ts', '규칙 바꾸기']], fmt: function (v) { return (v / 1000).toFixed(2) + '초'; } },
    driving: { axes: [['drive_days', '주행한 날(주)'], ['km_week', '거리(주, km)'], ['night_share', '야간 비율(%)']], fmt: function (v) { return String(Math.round(v)); } },
  };

  function quantile(sorted, p) { return sorted[Math.min(sorted.length - 1, Math.max(0, Math.round(p * (sorted.length - 1))))]; }
  function css(name) { return getComputedStyle(document.documentElement).getPropertyValue(name).trim(); }

  /** 겹치는 정수 값(주행일 가운데 값)만 화면에서 살짝 흩뜨린다. 같은 입력이면 늘 같은 위치. */
  function jitter(i) { const x = Math.sin(i * 12.9898) * 43758.5453; return (x - Math.floor(x) - 0.5) * 0.3; }

  /** @param history 지난 내 기록(오래된 것부터), 각 항목은 이 공간의 세 축 값을 모두 가진다. */
  /** 내 값과 가장 가까운 시연 군집(축마다 전체 표준편차로 나눈 거리). */
  function nearestGroup(groups, me) {
    if (!groups || !me) return null;
    let best = null, bestD = Infinity;
    groups.items.forEach(function (g) {
      let d = 0;
      Object.keys(groups.scale).forEach(function (k) { const z = (me[k] - g.center[k]) / groups.scale[k]; d += z * z; });
      if (d < bestD) { bestD = d; best = g; }
    });
    return best;
  }

  function view(container, space, drivers, me, history, groups) {
    history = history || [];
    if (space !== 'driving') groups = null;
    const spec = SPACES[space];
    const keys = spec.axes.map(function (a) { return a[0]; });
    const pts = drivers.filter(function (d) { return keys.every(function (k) { return d[k] !== null; }); });
    // 축마다 2~98% 범위를 −1~1로(극단값 몇 개가 그림을 줄이지 않게). 내 값이 밖이면 범위를 넓힌다.
    const ranges = keys.map(function (k) {
      const s = pts.map(function (d) { return d[k]; }).sort(function (a, b) { return a - b; });
      let lo = quantile(s, 0.02), hi = quantile(s, 0.98);
      if (me && me[k] !== undefined) { lo = Math.min(lo, me[k]); hi = Math.max(hi, me[k]); }
      history.forEach(function (h) { lo = Math.min(lo, h[k]); hi = Math.max(hi, h[k]); });
      return [lo, hi];
    });
    function norm(k, v) { const r = ranges[keys.indexOf(k)]; return 2 * (v - r[0]) / Math.max(1e-9, r[1] - r[0]) - 1; }
    const cloud = pts.map(function (d, i) {
      return { band: d.band, group: d.group, p: keys.map(function (k) { return norm(k, d[k] + (k === 'drive_days' ? jitter(i) : 0)); }) };
    });
    const mine = me && keys.every(function (k) { return me[k] !== undefined; }) ? keys.map(function (k) { return norm(k, me[k]); }) : null;
    const past = history.map(function (h, i) {
      // 오래된 것 0.25 → 최근 0.8(하나뿐이면 0.6)
      const a = history.length > 1 ? 0.25 + 0.55 * i / (history.length - 1) : 0.6;
      return { a: a, p: keys.map(function (k) { return norm(k, h[k]); }) };
    });

    const AXIS_SHORT = spec.axes.map(function (a, i) { return ['①', '②', '③'][i] + ' ' + a[1]; });
    const wrap = document.createElement('div');
    wrap.className = 'p3-wrap';
    const canvas = document.createElement('canvas');
    canvas.className = 'p3-canvas';
    canvas.setAttribute('role', 'img');
    canvas.setAttribute('aria-label', (space === 'checks' ? '점검' : '주행') + ' 3차원 보기: 합성 운전자 ' + cloud.length + '명' + (mine ? ', 내 위치 표시' : ''));
    canvas.tabIndex = 0;
    wrap.append(canvas);
    const legend = document.createElement('p');
    legend.className = 'chart-sub p3-legend';
    legend.textContent = spec.axes.map(function (a, i) { return AXIS_SHORT[i] + ': ' + spec.fmt(ranges[i][0]) + ' → ' + spec.fmt(ranges[i][1]); }).join('   ');
    wrap.append(legend);
    const bar = document.createElement('div');
    bar.className = 'choices p3-bands';
    wrap.append(bar);
    // 군집 범례: 누르면 그 군집만 진하게(다시 누르면 전체)
    const colorOf = {};
    let focus = null;
    const myGroup = groups ? nearestGroup(groups, me) : null;
    if (groups) {
      groups.items.forEach(function (gr) { colorOf[gr.id] = gr.color; });
      const lg = document.createElement('div');
      lg.className = 'p3-groups';
      lg.setAttribute('aria-label', '주행 군집 범례');
      groups.items.forEach(function (gr) {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = 'p3-group';
        b.setAttribute('aria-pressed', 'false');
        const sw = document.createElement('span');
        sw.className = 'p3-swatch';
        sw.style.background = gr.color;
        b.append(sw, document.createTextNode(gr.title + (myGroup && myGroup.id === gr.id ? ' (나와 가까움)' : '')));
        b.addEventListener('click', function () {
          focus = focus === gr.id ? null : gr.id;
          lg.querySelectorAll('.p3-group').forEach(function (x) { x.setAttribute('aria-pressed', String(x === b && focus !== null)); });
          draw();
        });
        lg.append(b);
      });
      const me0 = document.createElement('span');
      me0.className = 'p3-group p3-group-me';
      const msw = document.createElement('span');
      msw.className = 'p3-swatch p3-swatch-me';
      me0.append(msw, document.createTextNode('나의 최근 기록'));
      lg.append(me0);
      wrap.append(lg);
    }
    container.append(wrap);

    let yaw = -0.7, pitch = 0.45, band = 'all';
    Object.keys(BAND_LABEL).forEach(function (b) {
      const chip = document.createElement('button');
      chip.type = 'button';
      chip.className = 'chip' + (b === band ? ' on' : '');
      chip.textContent = BAND_LABEL[b];
      chip.setAttribute('aria-pressed', String(b === band));
      chip.addEventListener('click', function () {
        band = b;
        bar.querySelectorAll('.chip').forEach(function (c) { const on = c === chip; c.classList.toggle('on', on); c.setAttribute('aria-pressed', String(on)); });
        draw();
      });
      bar.append(chip);
    });

    function project(p, w, h) {
      // 축 순서: x=첫째, y=둘째(위쪽), z=셋째. 회전 후 직교 투영.
      const cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
      const x1 = p[0] * cy + p[2] * sy, z1 = -p[0] * sy + p[2] * cy;
      const y2 = p[1] * cp - z1 * sp, z2 = p[1] * sp + z1 * cp;
      const s = Math.min(w, h) * 0.33;
      return [w / 2 + x1 * s, h / 2 - y2 * s, z2];
    }

    function draw() {
      const dpr = window.devicePixelRatio || 1;
      const w = canvas.clientWidth, h = canvas.clientHeight;
      canvas.width = w * dpr; canvas.height = h * dpr;
      const g = canvas.getContext('2d');
      g.setTransform(dpr, 0, 0, dpr, 0, 0);
      g.clearRect(0, 0, w, h);
      const ink = css('--primary'), soft = css('--primary-light'), muted = css('--text-2'), me = css('--me');
      g.font = '12px ' + css('--font');
      // 상자 테두리와 축
      const corners = [];
      [-1, 1].forEach(function (a) { [-1, 1].forEach(function (b) { [-1, 1].forEach(function (c) { corners.push([a, b, c]); }); }); });
      g.strokeStyle = muted; g.globalAlpha = 0.25; g.lineWidth = 1;
      corners.forEach(function (c1, i) {
        corners.forEach(function (c2, j) {
          if (j <= i) return;
          const diff = c1.filter(function (v, k) { return v !== c2[k]; }).length;
          if (diff !== 1) return;
          const a = project(c1, w, h), b = project(c2, w, h);
          g.beginPath(); g.moveTo(a[0], a[1]); g.lineTo(b[0], b[1]); g.stroke();
        });
      });
      g.globalAlpha = 1;
      // 점: 뒤에서 앞으로
      const order = cloud.map(function (c) { return { c: c, q: project(c.p, w, h) }; }).sort(function (a, b) { return a.q[2] - b.q[2]; });
      order.forEach(function (o) {
        const on = (band === 'all' || o.c.band === band) && (!focus || o.c.group === focus);
        if (groups && o.c.group) {
          g.globalAlpha = on ? 0.7 : 0.07;
          g.fillStyle = colorOf[o.c.group];
          g.beginPath(); g.arc(o.q[0], o.q[1], on ? 2.6 : 2.2, 0, Math.PI * 2); g.fill();
          return;
        }
        g.globalAlpha = on ? 0.55 : 0.08;
        g.fillStyle = on && band !== 'all' ? ink : soft;
        g.beginPath(); g.arc(o.q[0], o.q[1], on && band !== 'all' ? 2.6 : 2.2, 0, Math.PI * 2); g.fill();
      });
      g.globalAlpha = 1;
      // 축 이름(각 축의 + 끝)과 범위
      spec.axes.forEach(function (a, i) {
        const tip = [-1, -1, -1]; tip[i] = 1.18;
        const base = project([-1, -1, -1], w, h), end = project(tip, w, h);
        g.strokeStyle = muted; g.lineWidth = 1.5;
        g.beginPath(); g.moveTo(base[0], base[1]); g.lineTo(end[0], end[1]); g.stroke();
        g.fillStyle = ink;
        const label = AXIS_SHORT[i];
        const tw = g.measureText(label).width;
        g.fillText(label, Math.min(Math.max(end[0] + 4, 4), w - tw - 4), Math.min(Math.max(end[1] - 4, 14), h - 4));
      });
      // 지난 내 기록: 뒤에서 앞으로, 오래될수록 연하게
      past.map(function (o) { return { a: o.a, q: project(o.p, w, h) }; }).sort(function (x, y) { return x.q[2] - y.q[2]; }).forEach(function (o) {
        g.globalAlpha = o.a; g.fillStyle = me;
        g.beginPath(); g.arc(o.q[0], o.q[1], 3.2, 0, Math.PI * 2); g.fill();
      });
      g.globalAlpha = 1;
      if (mine) {
        const q = project(mine, w, h);
        // 바닥까지 가는 세로 보조선(위치 읽기용)
        const floor = project([mine[0], -1, mine[2]], w, h);
        g.strokeStyle = me; g.setLineDash([4, 3]); g.lineWidth = 2;
        g.beginPath(); g.moveTo(q[0], q[1]); g.lineTo(floor[0], floor[1]); g.stroke(); g.setLineDash([]);
        // 군집 색 위에서도 보이게: 흰 바탕 고리 + 진한 노랑 + 남색 테두리
        g.fillStyle = css('--surface'); g.beginPath(); g.arc(q[0], q[1], 9, 0, Math.PI * 2); g.fill();
        g.fillStyle = me; g.strokeStyle = ink; g.lineWidth = 2;
        g.beginPath(); g.arc(q[0], q[1], 6.5, 0, Math.PI * 2); g.fill(); g.stroke();
        g.fillStyle = ink; g.font = 'bold 14px ' + css('--font');
        g.fillText('나', q[0] + 8, q[1] - 6);
      }
    }

    // 드래그·화살표 키로 돌리기
    let drag = null;
    canvas.addEventListener('pointerdown', function (e) { drag = [e.clientX, e.clientY, yaw, pitch]; canvas.setPointerCapture(e.pointerId); });
    canvas.addEventListener('pointermove', function (e) {
      if (!drag) return;
      yaw = drag[2] + (e.clientX - drag[0]) * 0.01;
      pitch = Math.max(-1.4, Math.min(1.4, drag[3] + (e.clientY - drag[1]) * 0.01));
      draw();
    });
    canvas.addEventListener('pointerup', function () { drag = null; });
    canvas.addEventListener('keydown', function (e) {
      const step = 0.12;
      if (e.key === 'ArrowLeft') yaw -= step; else if (e.key === 'ArrowRight') yaw += step;
      else if (e.key === 'ArrowUp') pitch = Math.max(-1.4, pitch - step); else if (e.key === 'ArrowDown') pitch = Math.min(1.4, pitch + step);
      else return;
      e.preventDefault(); draw();
    });
    window.addEventListener('resize', draw);
    draw();
    return { count: cloud.length, mine: !!mine, past: past.length, myGroup: myGroup };
  }

  window.NBPattern3D = { view: view, nearestGroup: nearestGroup };
})();
