// Market filters, rendering, and prototype notices.
    const $=id=>document.getElementById(id);let mode='all';
    $('major').replaceChildren(new Option('全部专业', ''), new Option('不限专业', '不限专业'));
    Demo.state.majors.forEach(option => $('major').add(new Option(option.name, option.name)));
    $('tag').replaceChildren(new Option('全部标签', ''));
    Demo.state.tags.forEach(option => $('tag').add(new Option(option.name, option.name)));
    const semesters=['大一上','大一下','大二上','大二下','大三上','大三下','大四上','大四下','大五上','大五下','研究生','未定义'];
    semesters.forEach(s=>$('semester').add(new Option(s,s)));
    function matches(b){const q=$('keyword').value.trim().toLowerCase();return Demo.available(b.id)&&(!q||[b.title,b.author,b.course,...b.tags].join(' ').toLowerCase().includes(q))&&b.scope.some(([m,s])=>(!$('major').value||m===$('major').value||m===anyMajor)&&(!$('semester').value||s===$('semester').value))&&(!$('category').value||b.category===$('category').value)&&(!$('tag').value||b.tags.includes($('tag').value))&&(mode==='all'||(mode==='free'?b.price===0:b.price>0))}
    function render(){const list=books.filter(matches);if($('sort').value==='low')list.sort((a,b)=>a.price-b.price);if($('sort').value==='high')list.sort((a,b)=>b.price-a.price);$('count').innerHTML=`共 <strong>${list.length}</strong> 条可获取资料`;$('grid').innerHTML=list.map(b=>`<a href="detail.html?id=${b.id}" class="card" data-id="${b.id}" aria-label="查看${b.title}，${b.price?'售价'+b.price+'元':'免费赠送'}"><span class="cover-stage"><span class="condition">${b.condition}</span><span class="book ${b.style}" style="--bg:${b.bg};--fg:${b.fg}" aria-hidden="true"><span class="book-top">BOOKLOOP · CAMPUS LIBRARY</span><span class="book-title">${b.cover}</span><span class="book-en">${b.en}</span><span class="book-art"></span><span class="book-foot">学习 · 阅读 · 分享</span></span></span><span class="card-body"><span class="card-title">${b.title}</span><span class="author">${b.author}</span><span class="tags"><span class="tag">${b.category}</span><span class="tag">${b.tags[0]||b.scope[0][1]}</span>${b.scope.some(([m])=>m===anyMajor)?'<span class="tag">不限专业</span>':''}</span><span class="price ${b.price===0?'free':''}">${b.price?'<small>¥</small>'+b.price.toFixed(2):'免费赠送'}</span></span></a>`).join('')||'<div class="empty"><h2>暂时没有匹配的资料</h2><p>试试其他关键词，或减少筛选条件。</p><button type="button" id="empty-reset">清除筛选</button></div>';if($('empty-reset'))$('empty-reset').onclick=reset;}
    function reset(){$('keyword').value='';['major','semester','category','tag'].forEach(id=>$(id).value='');$('sort').value='new';setMode('all');}
    function setMode(value){mode=value;document.querySelectorAll('[data-mode]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.mode===mode)));render()}
    function show(title,html){$('modal-title').textContent=title;$('modal-body').innerHTML=html;$('modal').showModal()}
    const info={about:['关于 HUST BookLoop','让闲置教材与学习资料找到下一位读者。平台支持出售与免费赠送，你可以按专业、学期和考试标签寻找需要的资料。'],account:['登录 / 注册','当前为本地交互原型，尚未接入账户服务。正式版本需要登录才能发布资料或查看联系方式。']};
    document.querySelectorAll('[data-info]').forEach(b=>b.onclick=()=>{const i=info[b.dataset.info];show(i[0],`<p>${i[1]}</p><p class="prototype-note">本页不会提交或保存个人信息。</p>`)});
    document.querySelectorAll('[data-mode]').forEach(b=>b.onclick=()=>setMode(b.dataset.mode));
    ['major','semester','category','tag','sort'].forEach(id=>$(id).addEventListener('change',render));$('keyword').addEventListener('input',render);$('search-form').onsubmit=e=>{e.preventDefault();render()};$('reset').onclick=reset;
    $('close').onclick=()=>$('modal').close();$('modal').addEventListener('click',e=>{if(e.target===$('modal')){const r=$('modal').getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)$('modal').close()}});
    $('keyword').value = new URLSearchParams(location.search).get('q') || '';
    render();
    window.addEventListener('pageshow', () => { if (window.__bookloopShown) location.reload(); window.__bookloopShown = true; });

