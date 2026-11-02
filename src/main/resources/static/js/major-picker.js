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
    const controls = document.createElement('span');
    controls.className = 'major-picker-tools';
    const search = document.createElement('input');
    search.type = 'search';
    search.placeholder = '搜索专业或学院，如：计算机';
    search.setAttribute('aria-label', '搜索专业或学院');
    const college = document.createElement('select');
    college.setAttribute('aria-label', '按学院筛选专业');
    college.add(new Option('所有学院', ''));
    Array.from(new Set(options.flatMap(option => option.colleges))).sort((a, b) => a.localeCompare(b, 'zh-CN'))
      .forEach(name => college.add(new Option(name, name)));
    college.hidden = college.options.length === 1;
    const hint = document.createElement('span');
    hint.className = 'major-picker-hint';
    hint.setAttribute('role', 'status');
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
    search.autocomplete = 'off';
    let candidates = [];
    let activeIndex = -1;
    function closeSuggestions() {
      suggestions.hidden = true;
      search.setAttribute('aria-expanded', 'false');
      search.removeAttribute('aria-activedescendant');
      activeIndex = -1;
    }
    function choose(option) {
      select.value = option.value;
      select.dispatchEvent(new Event('change', { bubbles: true }));
      hint.textContent = `已选择：${option.text}`;
      closeSuggestions();
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
        item.addEventListener('click', event => {
          event.preventDefault();
          choose(option);
        });
        suggestions.append(item);
      });
      suggestions.hidden = candidates.length === 0;
      search.setAttribute('aria-expanded', String(candidates.length > 0));
    }
    controls.append(search, suggestions, college, hint);
    select.before(controls);
    select.setAttribute('aria-label', '选择专业');
    function render() {
      const selected = select.value;
      const query = search.value.trim().toLocaleLowerCase();
      const fixed = options.filter(option => option.value === '' || option.value === '0');
      const matches = options.filter(option => option.value !== '' && option.value !== '0'
        && (!college.value || option.colleges.includes(college.value))
        && `${option.text} ${option.colleges.join(' ')}`.toLocaleLowerCase().includes(query));
      select.replaceChildren();
      const add = (target, option) => {
        const element = new Option(option.text, option.value, false, option.value === selected);
        element.dataset.colleges = option.colleges.join('|');
        target.append(element);
      };
      fixed.forEach(option => add(select, option));
      const groups = new Map();
      matches.forEach(option => {
        const label = college.value || option.colleges.join(' / ') || '其他专业';
        if (!groups.has(label)) {
          const group = document.createElement('optgroup');
          group.label = label;
          groups.set(label, group);
          select.append(group);
        }
        add(groups.get(label), option);
      });
      const current = options.find(option => option.value === selected);
      if (current && !fixed.includes(current) && !matches.includes(current)) {
        const group = document.createElement('optgroup');
        group.label = '当前已选（保留至你选择其他专业）';
        add(group, current);
        select.append(group);
      }
      select.value = selected;
      hint.textContent = matches.length ? `找到 ${matches.length} 个专业，请在下方选择。` : '没有匹配专业；可清空搜索，或暂不填写。';
    }
    function searchChanged() {
      // A new search starts across all colleges, not inside the previous filter.
      const query = search.value.trim().toLocaleLowerCase();
      const matches = query ? options.filter(option => option.value !== '' && option.value !== '0'
        && `${option.text} ${option.colleges.join(' ')}`.toLocaleLowerCase().includes(query)) : [];
      const colleges = new Set(matches.flatMap(option => option.colleges));
      college.value = colleges.size === 1 && matches.every(option => option.colleges.length > 0)
        ? colleges.values().next().value : '';
      render();
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
      // Explicit college selection replaces, rather than intersects, the search.
      search.value = '';
      closeSuggestions();
      render();
    });
    select.addEventListener('change', () => {
      const current = options.find(option => option.value === select.value);
      search.value = '';
      closeSuggestions();
      if (current?.colleges.length === 1) {
        college.value = current.colleges[0];
      } else if (!current?.colleges.includes(college.value)) {
        college.value = '';
      }
      render();
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
