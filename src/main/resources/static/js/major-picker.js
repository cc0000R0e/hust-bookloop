(() => {
  const sources = new WeakMap();
  let pickerId = 0;
  function enhance(select) {
    if (sources.has(select)) return;
    const options = Array.from(select.options, option => ({
      value: option.value,
      text: option.textContent,
      colleges: (option.dataset.colleges || '').split('|').filter(Boolean)
    }));
    sources.set(select, options);
    const collegeValue = select.parentElement.querySelector('.college-value');
    const empty = options.find(option => option.value === '' || option.value === '0');
    select.hidden = true;
    select.required = false;
    const controls = document.createElement('span');
    controls.className = 'major-picker-tools';
    const search = document.createElement('input');
    search.type = 'search';
    search.placeholder = '搜索学院或专业；专业可不选';
    search.setAttribute('aria-label', '搜索学院或专业');
    search.autocomplete = 'off';
    const college = document.createElement('select');
    college.setAttribute('aria-label', '选择学院（专业可不选）');
    college.add(new Option('未选学院 / 不限学院', ''));
    const names = new Set(options.flatMap(option => option.colleges));
    if (collegeValue.value) names.add(collegeValue.value);
    Array.from(names).sort((a, b) => a.localeCompare(b, 'zh-CN'))
      .forEach(name => college.add(new Option(name, name)));
    college.value = collegeValue.value;
    const hint = document.createElement('span');
    hint.className = 'major-picker-hint';
    hint.setAttribute('role', 'status');
    const selectedText = document.createElement('span');
    selectedText.className = 'major-picker-selected';
    const suggestions = document.createElement('span');
    suggestions.className = 'major-picker-suggestions';
    suggestions.id = `major-suggestions-${++pickerId}`;
    suggestions.setAttribute('role', 'listbox');
    suggestions.setAttribute('aria-label', '匹配的专业');
    suggestions.hidden = true;
    search.setAttribute('role', 'combobox');
    search.setAttribute('aria-autocomplete', 'list');
    search.setAttribute('aria-controls', suggestions.id);
    search.setAttribute('aria-expanded', 'false');
    let candidates = [];
    let activeIndex = -1;
    function closeSuggestions() {
      suggestions.hidden = true;
      search.setAttribute('aria-expanded', 'false');
      search.removeAttribute('aria-activedescendant');
      activeIndex = -1;
    }
    function render() {
      collegeValue.value = college.value;
      const current = options.find(option => option.value === select.value && option.value !== '' && option.value !== '0');
      selectedText.textContent = current ? `已选专业：${current.text}`
        : college.value ? `已选学院：${college.value}（未指定专业）` : empty.text;
      hint.textContent = current ? '可清除专业，仅保留学院。' : '可以只选学院；专业通过搜索建议选择。';
    }
    function changed() {
      render();
      select.dispatchEvent(new Event('change', { bubbles: true }));
      collegeValue.dispatchEvent(new Event('change', { bubbles: true }));
    }
    function choose(option) {
      select.value = option.value;
      if (!option.colleges.includes(college.value)) {
        college.value = option.colleges.length === 1 ? option.colleges[0] : '';
      }
      search.value = '';
      closeSuggestions();
      changed();
    }
    function showSuggestions() {
      const query = search.value.trim().toLocaleLowerCase();
      if (!query) { closeSuggestions(); return; }
      candidates = options.filter(option => option.value !== '' && option.value !== '0'
        && (!college.value || option.colleges.includes(college.value))
        && `${option.text} ${option.colleges.join(' ')}`.toLocaleLowerCase().includes(query));
      suggestions.replaceChildren();
      activeIndex = -1;
      search.removeAttribute('aria-activedescendant');
      candidates.forEach((option, index) => {
        const item = document.createElement('span');
        item.className = 'major-picker-suggestion';
        item.id = `${suggestions.id}-${index}`;
        item.setAttribute('role', 'option');
        item.setAttribute('aria-selected', 'false');
        const name = document.createElement('span');
        name.textContent = option.text;
        const detail = document.createElement('small');
        detail.textContent = option.colleges.join(' / ') || '未分组';
        item.append(name, detail);
        item.addEventListener('pointerdown', event => event.preventDefault());
        item.addEventListener('click', event => { event.preventDefault(); choose(option); });
        suggestions.append(item);
      });
      suggestions.hidden = candidates.length === 0;
      search.setAttribute('aria-expanded', String(candidates.length > 0));
      hint.textContent = candidates.length ? '点击专业可精确选择；不选专业则保留上方学院。' : '没有匹配结果，请换个关键词或直接选择学院。';
    }
    const actions = document.createElement('span');
    actions.className = 'major-picker-actions';
    const action = (text, handler) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = text;
      button.addEventListener('click', event => { event.preventDefault(); handler(); search.value = ''; closeSuggestions(); changed(); });
      actions.append(button);
    };
    action('清除专业', () => { select.value = empty.value; });
    action('清空选择', () => { select.value = empty.value; college.value = ''; });
    if (empty.value === '' && options.some(option => option.value === '0')) {
      action('仅看不限专业', () => { select.value = '0'; college.value = ''; });
    }
    controls.append(search, suggestions, college, selectedText, actions, hint);
    select.before(controls);
    function searchChanged() {
      const query = search.value.trim().toLocaleLowerCase();
      const matches = query ? options.filter(option => option.value !== '' && option.value !== '0'
        && `${option.text} ${option.colleges.join(' ')}`.toLocaleLowerCase().includes(query)) : [];
      const colleges = new Set(matches.flatMap(option => option.colleges));
      college.value = colleges.size === 1 && matches.every(option => option.colleges.length > 0)
        ? colleges.values().next().value : '';
      select.value = empty.value;
      changed();
      showSuggestions();
    }
    search.addEventListener('input', event => { if (!event.isComposing) searchChanged(); });
    search.addEventListener('compositionstart', closeSuggestions);
    search.addEventListener('compositionend', searchChanged);
    search.addEventListener('focus', showSuggestions);
    search.addEventListener('blur', closeSuggestions);
    search.addEventListener('keydown', event => {
      if (event.isComposing || event.keyCode === 229) return;
      if (event.key === 'Escape') { event.preventDefault(); closeSuggestions(); return; }
      if (event.key === 'Enter') {
        event.preventDefault();
        if (!suggestions.hidden && candidates.length) choose(candidates[Math.max(0, activeIndex)]);
        return;
      }
      if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
      event.preventDefault();
      if (suggestions.hidden) showSuggestions();
      if (!candidates.length || suggestions.hidden) return;
      activeIndex = activeIndex < 0 ? (event.key === 'ArrowDown' ? 0 : candidates.length - 1)
        : (activeIndex + (event.key === 'ArrowDown' ? 1 : -1) + candidates.length) % candidates.length;
      Array.from(suggestions.children).forEach((item, index) => item.setAttribute('aria-selected', String(index === activeIndex)));
      const active = suggestions.children[activeIndex];
      search.setAttribute('aria-activedescendant', active.id);
      active.scrollIntoView({ block: 'nearest' });
    });
    college.addEventListener('change', () => {
      search.value = '';
      select.value = empty.value;
      closeSuggestions();
      changed();
    });
    render();
  }
  window.MajorPicker = {
    enhance,
    prepareClone(source, clone) {
      clone.querySelectorAll('.major-picker-tools').forEach(node => node.remove());
      const original = source.querySelector('[data-major-picker]');
      const target = clone.querySelector('[data-major-picker]');
      if (sources.has(original)) {
        target.replaceChildren();
        sources.get(original).forEach(option => {
          const node = new Option(option.text, option.value);
          node.dataset.colleges = option.colleges.join('|');
          target.add(node);
        });
      }
    }
  };
  document.querySelectorAll('[data-major-picker]').forEach(enhance);
})();
