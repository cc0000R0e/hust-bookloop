// Detail content shares the market's demo data. No server requests are made.
const $ = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);
const requestedId = params.get("id");
const book = books.find(
  (item) => item.id === Number(requestedId === null ? 1 : requestedId),
);

function showNotice(title, message) {
  $("modal-title").textContent = title;
  $("modal-body").replaceChildren();
  const paragraph = document.createElement("p");
  paragraph.textContent = message;
  $("modal-body").append(paragraph);
  $("modal").showModal();
}

const notices = {
  about: [
    "关于平台",
    "HUST BookLoop 提供校园教材与学习资料的出售及免费赠送信息。本页面是本地原型，不包含真实交易。",
  ],
  account: [
    "登录 / 注册",
    "账户服务尚未接入。正式版本登录后可查看联系方式；本原型不收集账号或密码。",
  ],
  report: [
    "登录后举报资料",
    "举报功能尚未接入。正式版本登录后可填写举报原因并交由管理员处理，本次点击不会提交举报。",
  ],
};

document.querySelectorAll("[data-notice]").forEach((button) => {
  button.addEventListener("click", () => {
    if (button.dataset.notice === "report") return showReport();
    showNotice(...notices[button.dataset.notice]);
  });
});

$("close").addEventListener("click", () => $("modal").close());
$("modal").addEventListener("click", (event) => {
  if (event.target !== $("modal")) return;
  const rect = $("modal").getBoundingClientRect();
  if (
    event.clientX < rect.left ||
    event.clientX > rect.right ||
    event.clientY < rect.top ||
    event.clientY > rect.bottom
  ) {
    $("modal").close();
  }
});

if (!book) {
  $("missing").hidden = false;
  document.title = "资料未找到 · HUST BookLoop";
} else if (
  ["已售出", "已送出", "已下架"].includes(Demo.listing(book.id).status)
) {
  $("missing").hidden = false;
  $("missing").querySelector("h1").textContent = "这份资料已不可获取";
  $("missing-message").textContent =
    "资料已完成流转或下架，公开页面不再提供正文和联系方式。";
  document.title = "资料不可获取 · HUST BookLoop";
} else {
  const isFree = book.price === 0;
  document.title = `${book.title} · HUST BookLoop`;
  $("detail-content").hidden = false;
  $("crumb").textContent = book.title;
  $("book-title").textContent = book.title;
  $("category").textContent = book.category;
  $("condition").textContent = book.condition;
  $("status").textContent = Demo.listing(book.id).status;
  $("author").textContent = `${book.author} 著 / 编 · ${book.version}`;
  $("price-label").textContent = isFree ? "流转方式" : "转让价格";
  $("price").textContent = isFree ? "免费赠送" : `¥ ${book.price.toFixed(2)}`;
  $("price").classList.toggle("free", isFree);
  $("price-caption").textContent = isFree ? "让知识继续流动" : "由发布者定价";
  $("version").textContent = book.version;
  $("wear").textContent = `${book.condition} · ${book.note}`;
  $("course").textContent = book.course || "未填写";
  $("listing-number").textContent =
    `BL-${String(book.id).padStart(4, "0")}（演示）`;

  book.scope.forEach(([major, semester]) => {
    const chip = document.createElement("span");
    chip.className = "scope-chip";
    chip.textContent = `${major} · ${semester}`;
    $("scopes").append(chip);
  });

  if (!book.tags.length) $("tags").textContent = "未添加";
  book.tags.forEach((tag) => {
    const chip = document.createElement("span");
    chip.className = "tag";
    chip.textContent = tag;
    $("tags").append(chip);
  });

  // Cover markup only contains trusted, bundled demo values.
  const cover = `<span class="book ${book.style}" style="--bg:${book.bg};--fg:${book.fg}" aria-hidden="true">
    <span class="book-top">BOOKLOOP · CAMPUS LIBRARY</span>
    <span class="book-title">${book.cover}</span>
    <span class="book-en">${book.en}</span>
    <span class="book-art"></span>
    <span class="book-foot">学习 · 阅读 · 分享</span>
  </span>`;
  $("cover-art").innerHTML = cover;
  $("description").textContent =
    `这是一份《${book.title}》的演示条目，版本为${book.version}。使用情况为${book.condition}，${book.note}。${isFree ? "以免费赠送的方式流转，有需要的同学可以联系发布者约定领取。" : "有需要的同学可以联系发布者，进一步确认资料情况与交接方式。"}具体缺损、套装组成及实物情况应由发布者补充说明。`;

  if (isFree) {
    document.querySelector(".contact-note").textContent =
      "登录后可查看联系方式，双方自行协商免费领取与交接。";
    $("handoff-copy").textContent = "双方自行约定免费领取与交接方式。";
  }

  $("contact").textContent = Demo.state.loggedIn
    ? "查看演示联系方式"
    : "登录后联系发布者";
  $("contact").addEventListener("click", () => {
    if (!Demo.state.loggedIn) {
      location.href =
        "login.html?next=" + encodeURIComponent("detail.html?id=" + book.id);
      return;
    }
    showNotice(
      "演示联系方式",
      "微信：bookloop-demo（虚构账号，请勿添加）。" +
        (Demo.listing(book.id).status === "已预订"
          ? "此资料已有意向领取者或买家，请先确认是否仍可获取。"
          : "此处仅演示登录后查看联系信息的流程。"),
    );
  });
  $("zoom-cover").addEventListener("click", () => {
    $("modal-title").textContent = `${book.title} · 封面示意`;
    $("modal-body").innerHTML =
      `<div class="zoom-preview">${cover}</div><p class="prototype-note">此图为本地封面示意设计，不是实物照片。</p>`;
    $("modal").showModal();
  });
}

function showReport() {
  if (!book) return;
  if (!Demo.state.loggedIn) {
    location.href =
      "login.html?next=" + encodeURIComponent("detail.html?id=" + book.id);
    return;
  }
  $("modal-title").textContent = "举报资料 · 演示";
  $("modal-body").innerHTML =
    '<form id="report-form"><p>仅记录本地演示举报，可到管理后台查看。请勿填写个人信息。</p><label>举报原因<select name="reason" required><option>资料信息不准确</option><option>虚假交易信息</option><option>不当内容</option><option>其他</option></select></label><label style="display:block;margin:18px 0">补充说明<textarea name="description" maxlength="500" rows="3" style="display:block;width:100%;margin-top:8px"></textarea></label><p id="report-message" role="status"></p><button class="primary" type="submit">提交演示举报</button></form>';
  $("report-form").onsubmit = (event) => {
    event.preventDefault();
    try {
      Demo.addReport(
        book.id,
        event.currentTarget.elements.reason.value,
        event.currentTarget.elements.description.value.trim(),
      );
      showNotice(
        "已记录演示举报",
        "举报已进入本地待处理列表，可前往管理后台的举报处理页面查看。没有向服务器发送数据。",
      );
    } catch (error) {
      $("report-message").textContent = error.message;
    }
  };
}
window.addEventListener("pageshow", (event) => {
  if (event.persisted) location.reload();
});
