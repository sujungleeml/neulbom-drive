// 늘봄 Drive 읽기 전용 웹 시연: 서버 없이 미리 담아 둔 합성·모의 데이터(demo-data.js)로 화면을 띄운다.
// 기존 웹 화면(app.js 등)은 그대로 쓰고, fetch만 가로채 응답을 돌려준다. 바꾸는 요청은 막는다.
(function () {
  'use strict';
  var DATA = window.NEULBOM_DEMO_DATA || {};
  var NOTICE = '읽기 전용 시연이라 바꾸거나 내려받을 수 없어요.';

  function respond(body, status) {
    return Promise.resolve(new Response(JSON.stringify(body), {
      status: status || 200,
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
    }));
  }

  function keyOf(input) {
    var raw = typeof input === 'string' ? input : input.url;
    var u = new URL(raw, 'http://demo.local/');
    var path = u.pathname.replace(/^.*\/(habits\.json|pattern-points\.json|pattern-map\.json)$/, '/$1');
    return path + u.search;
  }

  var realFetch = window.fetch ? window.fetch.bind(window) : null;
  window.fetch = function (input, init) {
    var method = ((init && init.method) || 'GET').toUpperCase();
    var key = keyOf(input);
    if (method !== 'GET') {
      toast(NOTICE);
      return respond({ error: 'READ_ONLY_DEMO' }, 405);
    }
    if (Object.prototype.hasOwnProperty.call(DATA, key)) return respond(DATA[key]);
    if (key.indexOf('/v1/reports/me/driving?') === 0 && DATA.__driving_empty) return respond(DATA.__driving_empty);
    if (key.indexOf('/v1/') === 0) return respond({ error: 'NOT_IN_DEMO' }, 404);
    return realFetch ? realFetch(input, init) : respond({ error: 'NOT_FOUND' }, 404);
  };

  // 서버로 가는 링크(내 기록 내려받기)는 막고 안내만 한다.
  document.addEventListener('click', function (e) {
    var a = e.target.closest && e.target.closest('a[href^="/v1/"]');
    if (a) { e.preventDefault(); toast(NOTICE); }
  }, true);

  var toastTimer = null;
  function toast(text) {
    var t = document.getElementById('demo-toast');
    if (!t) return;
    t.textContent = text;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.hidden = true; }, 2600);
  }

  document.addEventListener('DOMContentLoaded', function () {
    var bar = document.createElement('div');
    bar.className = 'demo-bar';
    bar.setAttribute('role', 'note');
    bar.innerHTML = '<strong>읽기 전용 시연</strong> 서버 없이 미리 담아 둔 <b>모의 기록·합성 데이터</b>로 보여 드려요. 실제 사용자 기록이 아니며, 바꾼 내용은 저장되지 않아요.' +
      (DATA.__captured_at ? ' <span class="demo-at">데이터 기준 ' + DATA.__captured_at + '</span>' : '');
    document.body.prepend(bar);
    var t = document.createElement('div');
    t.id = 'demo-toast';
    t.className = 'demo-toast';
    t.setAttribute('role', 'status');
    t.hidden = true;
    document.body.append(t);
  });
})();
