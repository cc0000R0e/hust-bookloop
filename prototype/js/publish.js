// Local-only form prototype. Inputs and images stay in memory until navigation.
const $ = id => document.getElementById(id);
const form = $('listing-form');
const semesterOptions = ['大一上', '大一下', '大二上', '大二下', '大三上', '大三下', '大四上', '大四下', '大五上', '大五下', '研究生', '未定义'];
const majorOptions = ['不限专业', ...Demo.state.majors.filter(option => option.enabled).map(option => option.name)];
const tagOptions = Demo.state.tags.filter(option => option.enabled).map(option => option.name);
const images = [];
const editId = new URLSearchParams(location.search).get('edit');
const editing = editId !== null;
const original = editing ? books.find(book => book.id === Number(editId)) : null;
let lastSalePrice = '';
let loadingImages = false;

function createElement(tag, text, className) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  if (className) element.className = className;
  return element;
}

function addScope(major = '', semester = '') {
  const row = createElement('div', undefined, 'scope-row');
  [['专业', majorOptions, major], ['学期', semesterOptions, semester]].forEach(([name, options, value]) => {
    const label = createElement('label');
    label.append(createElement('span', name, 'sr-only'));
    const select = document.createElement('select');
    select.required = true;
    select.dataset.field = name === '专业' ? 'major' : 'semester';
    select.add(new Option(`请选择${name}`, ''));
    [...new Set([...options, ...(value ? [value] : [])])].forEach(option => select.add(new Option(option, option)));
    select.value = value;
    label.append(select);
    row.append(label);
  });
  const remove = createElement('button', '移除');
  remove.type = 'button';
  remove.addEventListener('click', () => {
    row.remove();
    refreshScopeButtons();
    $('scope-error').textContent = '';
  });
  row.append(remove);
  $('scope-rows').append(row);
  refreshScopeButtons();
}

function refreshScopeButtons() {
  const rows = document.querySelectorAll('.scope-row');
  rows.forEach(row => row.querySelector('button').disabled = rows.length === 1);
}

function scopes() {
  return [...document.querySelectorAll('.scope-row')].map(row => [
    row.querySelector('[data-field="major"]').value,
    row.querySelector('[data-field="semester"]').value
  ]);
}

function hasDuplicateScopes(values) {
  return new Set(values.map(value => JSON.stringify(value))).size !== values.length;
}

function freeMode() {
  return form.querySelector('[name="mode"]:checked').value === 'free';
}

function previewStatus() {
  if (original && ['已预订', '已下架'].includes(Demo.listing(original.id).status)) return Demo.listing(original.id).status;
  return freeMode() ? '待赠送' : '在售';
}

function refreshSummary() {
  $('summary-mode').textContent = freeMode() ? '免费赠送' : '出售';
  $('summary-price').textContent = freeMode() ? '无需支付书籍价款' :
    ($('price').value && Number($('price').value) > 0 ? `¥ ${Number($('price').value).toFixed(2)}` : '填写价格后显示');
  $('summary-status').textContent = `预览状态：${previewStatus()}`;
}

function changeMode() {
  if (freeMode()) {
    lastSalePrice = $('price').value;
    $('price').value = '0';
  } else {
    $('price').value = lastSalePrice;
  }
  $('price').disabled = freeMode();
  $('price').required = !freeMode();
  $('price-required').hidden = freeMode();
  $('price-help').textContent = freeMode() ? '免费赠送的价格固定为0，页面展示“免费赠送”。' : '大于0，最多两位小数。';
  refreshSummary();
}

function renderImages() {
  $('image-previews').replaceChildren();
  images.forEach((image, index) => {
    const item = createElement('div', undefined, 'image-item');
    const thumbnail = document.createElement('img');
    thumbnail.src = image.url;
    thumbnail.alt = `所选图片${index + 1}：${image.name}`;
    const name = createElement('p', `${index === 0 ? '封面 · ' : ''}${image.name}`);
    item.append(thumbnail, name);
    if (index !== 0) {
      const cover = createElement('button', '设为封面');
      cover.type = 'button';
      cover.addEventListener('click', () => {
        images.unshift(images.splice(index, 1)[0]);
        renderImages();
      });
      item.append(cover);
    }
    const remove = createElement('button', '移除');
    remove.type = 'button';
    remove.addEventListener('click', () => {
      URL.revokeObjectURL(images[index].url);
      images.splice(index, 1);
      renderImages();
    });
    item.append(remove);
    $('image-previews').append(item);
  });
}

function imageFileError(file) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return '仅支持JPG、PNG和WebP图片。';
  if (file.size > 5 * 1024 * 1024) return '单张图片不能超过5MB。';
  if (file.size === 0) return '不能使用空文件。';
  return '';
}

$('photos').addEventListener('change', async event => {
  const files = [...event.target.files];
  event.target.value = '';
  if (files.length + images.length > 6) {
    $('image-error').textContent = '最多选择6张图片，请先移除部分图片。';
    return;
  }
  loadingImages = true;
  $('photos').disabled = true;
  $('submit-button').disabled = true;
  const errors = [];
  try {
    for (const file of files) {
      const error = imageFileError(file);
      if (error) { errors.push(`${file.name}：${error}`); continue; }
      const url = URL.createObjectURL(file);
      try {
        const image = new Image();
        image.src = url;
        await image.decode();
        images.push({name: file.name, url});
      } catch {
        URL.revokeObjectURL(url);
        errors.push(`${file.name}：无法读取为有效图片。`);
      }
    }
  } finally {
    loadingImages = false;
    $('photos').disabled = false;
    $('submit-button').disabled = false;
    $('image-error').textContent = errors.join(' ');
    renderImages();
  }
});

[...new Set([...tagOptions, ...(original ? original.tags : [])])].forEach(tag => {
  const label = document.createElement('label');
  const checkbox = document.createElement('input');
  checkbox.type = 'checkbox';
  checkbox.name = 'tags';
  checkbox.value = tag;
  label.append(checkbox, document.createTextNode(tag));
  $('tag-options').append(label);
});

$('add-scope').addEventListener('click', () => addScope());
form.querySelectorAll('[name="mode"]').forEach(radio => radio.addEventListener('change', changeMode));
$('price').addEventListener('input', refreshSummary);
$('description').addEventListener('input', () => {
  $('description-count').textContent = `${$('description').value.length} / 2000`;
});

form.addEventListener('submit', event => {
  event.preventDefault();
  if (loadingImages) return;
  // Normalize before native validation so whitespace does not count as content.
  ['title', 'author', 'publisher', 'version', 'description', 'course', 'contact-value'].forEach(id => {
    $(id).value = $(id).value.trim();
  });
  if (!form.reportValidity()) return;
  $('image-error').textContent = images.length ? '' : '请至少选择一张实物照片。';
  $('scope-error').textContent = hasDuplicateScopes(scopes()) ? '适用范围不能重复，请修改或移除重复的组合。' : '';
  if (!images.length) { $('photos').focus(); return; }
  if ($('scope-error').textContent) {
    $('scope-rows').querySelector('select').focus();
    return;
  }
  // Only safe text nodes are used for user-provided content.
  const preview = $('preview-content');
  preview.replaceChildren();
  const cover = document.createElement('img');
  cover.src = images[0].url;
  cover.alt = '本次预览的封面';
  cover.className = 'preview-cover';
  preview.append(cover, createElement('h3', $('title').value));
  const lines = [
    `${$('category').value} · ${freeMode() ? '免费赠送' : `¥ ${Number($('price').value).toFixed(2)}`} · ${previewStatus()}`,
    `成色：${$('condition').value} · ${$('note').value}`,
    `作者：${$('author').value || '未填写'} · 出版社：${$('publisher').value || '未填写'}`,
    `版本：${$('version').value || '未填写'}`,
    `适用范围：${scopes().map(scope => scope.join(' · ')).join('；')}`,
    `课程：${$('course').value || '未填写'}`,
    `标签：${[...form.querySelectorAll('[name="tags"]:checked')].map(tag => tag.value).join('、') || '未添加'}`,
    `联系方式：${$('contact-type').value}（预览不公开账号）`,
    `图片数量：${images.length}张`
  ];
  lines.forEach(line => preview.append(createElement('p', line)));
  preview.append(createElement('p', $('description').value, 'preview-description'));
  $('preview-title').textContent = editing ? '编辑效果预览 · 尚未保存' : '发布效果预览 · 尚未发布';
  $('preview-dialog').showModal();
});

['close-preview', 'continue-edit'].forEach(id => {
  $(id).addEventListener('click', () => $('preview-dialog').close());
});

if (editing && original && (Demo.listing(original.id).blocked || Demo.listing(original.id).completed)) {
  $('not-found').hidden = false;
  $('not-found').querySelector('h2').textContent = '当前资料不可编辑';
  $('not-found').querySelector('p').textContent = '已完成或被管理员限制的资料不可修改，请返回我的发布查看状态。';
  $('form-layout').hidden = true;
} else if (editing && !original) {
  $('not-found').hidden = false;
  $('form-layout').hidden = true;
  $('page-title').textContent = '资料未找到';
} else if (editing) {
  document.title = '编辑资料 · HUST BookLoop';
  $('page-title').textContent = '编辑资料';
  $('breadcrumb').textContent = '编辑资料';
  $('intro').textContent = `正在编辑演示条目：${original.title}`;
  $('prototype-banner').textContent = '编辑模式使用预置演示资料，所有修改仅供预览，不影响首页数据。正式系统仅允许发布者编辑自己的合法状态条目。';
  $('mode-link').textContent = '切换到新建发布 →';
  $('mode-link').href = 'publish.html';
  $('submit-button').textContent = '预览修改效果 →';
  ['title', 'author', 'category', 'version', 'course', 'condition', 'note'].forEach(id => {
    $(id).value = original[id];
  });
  $('description').value = `《${original.title}》演示资料，${original.version}，${original.condition}，${original.note}。请补充实际内容、缺损或套装组成。`;
  $('description-count').textContent = `${$('description').value.length} / 2000`;
  $('price').value = original.price || '';
  $('contact-value').value = 'demo-contact';
  original.scope.forEach(([major, semester]) => addScope(major, semester));
  form.querySelectorAll('[name="tags"]').forEach(tag => tag.checked = original.tags.includes(tag.value));
  if (!original.price) {
    form.querySelector('[name="mode"][value="free"]').checked = true;
    changeMode();
  }
  const demoSvg = '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="520"><rect width="400" height="520" fill="#e6e1d0"/><rect x="45" y="60" width="310" height="400" fill="#42636a"/><text x="200" y="235" text-anchor="middle" fill="white" font-size="28">BOOKLOOP</text><text x="200" y="280" text-anchor="middle" fill="white" font-size="18">DEMO COVER</text></svg>';
  images.push({name: '原有封面（演示示意图）', url: URL.createObjectURL(new Blob([demoSvg], {type: 'image/svg+xml'}))});
  renderImages();
} else {
  addScope();
}
refreshSummary();

// Reserved records can edit content but cannot change transfer mode.
if (original && Demo.listing(original.id).status === '已预订') {
  form.querySelectorAll('[name="mode"]').forEach(input => input.disabled = true);
  $('price-help').textContent += ' 当前已预订，流转方式不可修改。';
}
