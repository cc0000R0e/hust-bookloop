(() => {
  const sources = new WeakMap();
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
    controls.append(search, college, hint);
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
    search.addEventListener('input', () => {
      // A new search starts across all colleges, not inside the previous filter.
      const query = search.value.trim().toLocaleLowerCase();
      const matches = query ? options.filter(option => option.value !== '' && option.value !== '0'
        && `${option.text} ${option.colleges.join(' ')}`.toLocaleLowerCase().includes(query)) : [];
      const colleges = new Set(matches.flatMap(option => option.colleges));
      college.value = colleges.size === 1 && matches.every(option => option.colleges.length > 0)
        ? colleges.values().next().value : '';
      render();
    });
    search.addEventListener('keydown', event => { if (event.key === 'Enter') event.preventDefault(); });
    college.addEventListener('change', () => {
      // Explicit college selection replaces, rather than intersects, the search.
      search.value = '';
      render();
    });
    select.addEventListener('change', () => {
      const current = options.find(option => option.value === select.value);
      search.value = '';
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
