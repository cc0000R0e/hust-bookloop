// Detail content shares the market's demo data. No server requests are made.
const $ = id => document.getElementById(id);
const params = new URLSearchParams(location.search);
const requestedId = params.get('id');
const book = books.find(item => item.id === Number(requestedId === null ? 1 : requestedId));

function showNotice(title, message) {
  $('modal-title').textContent = title;
  $('modal-body').replaceChildren();
  const paragraph = document.createElement('p');
  paragraph.textContent = message;
  $('modal-body').append(paragraph);
  $('modal').showModal();
}

const notices = {
  about: ['关于平台', 'HUST BookLoop 提供校园教材与学习资料的出售及免费赠送信息。本页面是本地原型，不包含真实交易。'],
  account: ['登录 / 注册', '账户服务尚未接入。正式版本登录后可查看联系方式；本原型不收集账号或密码。'],
  report: ['登录后举报资料', '举报功能尚未接入。正式版本登录后可填写举报原因并交由管理员处理，本次点击不会提交举报。']
};

document.querySelectorAll('[data-notice]').forEach(button => {
  button.addEventListener('click', () => showNotice(...notices[button.dataset.notice]));
});

$('close').addEventListener('click', () => $('modal').close());
$('modal').addEventListener('click', event => {
  if (event.target !== $('modal')) return;
  const rect = $('modal').getBoundingClientRect();
  if (event.clientX < rect.left || event.clientX > rect.right ||
      event.clientY < rect.top || event.clientY > rect.bottom) {
    $('modal').close();
  }
});

if (!book) {
  $('missing').hidden = false;
  document.title = '资料未找到 · HUST BookLoop';
} else {
  const isFree = book.price === 0;
  document.title = `${book.title} · HUST BookLoop`;
  $('detail-content').hidden = false;
  $('crumb').textContent = book.title;
  $('book-title').textContent = book.title;
  $('category').textContent = book.category;
  $('condition').textContent = book.condition;
  $('status').textContent = isFree ? '待赠送' : '在售';
  $('author').textContent = `${book.author} 著 / 编 · ${book.version}`;
  $('price-label').textContent = isFree ? '流转方式' : '转让价格';
  $('price').textContent = isFree ? '免费赠送' : `¥ ${book.price.toFixed(2)}`;
  $('price').classList.toggle('free', isFree);
  $('price-caption').textContent = isFree ? '让知识继续流动' : '由发布者定价';
  $('version').textContent = book.version;
  $('wear').textContent = `${book.condition} · ${book.note}`;
  $('course').textContent = book.course || '未填写';
  $('listing-number').textContent = `BL-${String(book.id).padStart(4, '0')}（演示）`;

  book.scope.forEach(([major, semester]) => {
    const chip = document.createElement('span');
    chip.className = 'scope-chip';
    chip.textContent = `${major} · ${semester}`;
    $('scopes').append(chip);
  });

  if (!book.tags.length) $('tags').textContent = '未添加';
  book.tags.forEach(tag => {
    const chip = document.createElement('span');
    chip.className = 'tag';
    chip.textContent = tag;
    $('tags').append(chip);
  });

  // Cover markup only contains trusted, bundled demo values.
  const cover = `<span class="book ${book.style}" style="--bg:${book.bg};--fg:${book.fg}" aria-hidden="true">
    <span class="book-top">BOOKLOOP · CAMPUS LIBRARY</span>
    <span class="book-title">${book.cover}</span>
    <span class="book-en">${book.en}</span>
    <span class="book-art"></span>
    <span class="book-foot">学习 · 阅读 · 分享</span>
  </span>`;
  $('cover-art').innerHTML = cover;
  $('description').textContent = `这是一份《${book.title}》的演示条目，版本为${book.version}。使用情况为${book.condition}，${book.note}。${isFree ? '以免费赠送的方式流转，有需要的同学可以联系发布者约定领取。' : '有需要的同学可以联系发布者，进一步确认资料情况与交接方式。'}具体缺损、套装组成及实物情况应由发布者补充说明。`;

  if (isFree) {
    document.querySelector('.contact-note').textContent = '登录后可查看联系方式，双方自行协商免费领取与交接。';
    $('handoff-copy').textContent = '双方自行约定免费领取与交接方式。';
  }

  $('contact').addEventListener('click', () => showNotice(...notices.account));
  $('zoom-cover').addEventListener('click', () => {
    $('modal-title').textContent = `${book.title} · 封面示意`;
    $('modal-body').innerHTML = `<div class="zoom-preview">${cover}</div><p class="prototype-note">此图为本地封面示意设计，不是实物照片。</p>`;
    $('modal').showModal();
  });
}
