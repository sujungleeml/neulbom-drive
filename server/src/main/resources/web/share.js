// 공유된 기록(읽기 전용). 링크를 만든 사람이 고른 범위만 서버가 보내 준다.
(function () {
  const NB = window.NB;
  const main = document.getElementById('shared');
  const token = location.pathname.split('/').filter(Boolean).pop() || '';

  function ended(text) {
    main.replaceChildren(NB.el('div', 'box', text));
  }

  async function load() {
    let res;
    try { res = await fetch('/v1/shared/' + encodeURIComponent(token)); } catch (e) { ended('기록을 불러오지 못했어요. 연결을 확인한 뒤 다시 열어 주세요.'); return; }
    if (res.status === 410 || res.status === 404) { ended('이 공유 링크는 기간이 끝났거나 취소되었어요. 필요하면 새 링크를 받아 주세요.'); return; }
    if (!res.ok) { ended('기록을 불러오지 못했어요. 잠시 뒤 다시 열어 주세요.'); return; }
    const data = await res.json();
    main.replaceChildren();
    main.append(NB.el('p', 'state', NB.dateLabel(data.expiresAt) + '까지 볼 수 있는 링크예요.'));
    if (data.driving) {
      const sec = NB.el('section', 'panel');
      const head = NB.el('div', 'period');
      head.append(NB.el('h1', null, '이번 달 나의 드라이빙'));
      if (data.driving.origin === 'MOCK') head.append(NB.el('span', 'badge', '모의 주행 기록'));
      const cards = NB.el('div', 'cards');
      NB.drivingCards(data.driving, cards);
      const notes = NB.el('ul', 'notices');
      NB.listItems(notes, data.driving.notices);
      const box = NB.el('article', 'box');
      box.append(NB.el('h2', null, '알아 두실 점'), notes);
      sec.append(head, NB.el('p', 'state', NB.DRIVING_STATE[data.driving.state] || ''), cards, box);
      main.append(sec);
    }
    if (data.checks) {
      const sec = NB.el('section', 'panel');
      const head = NB.el('div', 'period');
      head.append(NB.el('h1', null, '주의·반응 기록'));
      if (data.checks.mockVehicle) head.append(NB.el('span', 'badge', '모의 차량에서 한 점검'));
      const cards = NB.el('div', 'cards');
      NB.checkCards(data.checks, cards);
      const notes = NB.el('ul', 'notices');
      NB.listItems(notes, data.checks.notices);
      const box = NB.el('article', 'box');
      box.append(NB.el('h2', null, '알아 두실 점'), notes);
      sec.append(head, NB.el('p', 'state', NB.CHECK_STATE[data.checks.state] || ''), cards, box);
      main.append(sec);
    }
  }

  load();
})();
