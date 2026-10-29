const $ = (id) => document.getElementById(id);
const page = document.body.dataset.page;
const esc = (value) =>
  String(value).replace(
    /[&<>"']/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        char
      ],
  );
let pendingAction = null;

function feedback(message) {
  $("feedback").textContent = message;
}
function confirmAction(title, description, needsReason, action, initial = "") {
  $("action-title").textContent = title;
  $("action-description").textContent = description;
  $("reason-label").hidden = !needsReason;
  $("action-reason").required = needsReason;
  $("action-reason").value = initial;
  $("action-reason").setCustomValidity("");
  pendingAction = action;
  $("action-dialog").showModal();
}
["cancel-action", "back-action"].forEach(
  (id) => ($(id).onclick = () => $("action-dialog").close()),
);
$("action-reason").oninput = () => $("action-reason").setCustomValidity("");
$("action-form").onsubmit = (event) => {
  event.preventDefault();
  const reason = $("action-reason").value.trim();
  if ($("action-reason").required && !reason) {
    $("action-reason").setCustomValidity("请输入有效说明。");
    $("action-reason").reportValidity();
    return;
  }
  try {
    pendingAction(reason);
    $("action-dialog").close();
    render();
  } catch (error) {
    $("action-description").textContent = error.message;
  }
};
$("reset-demo").onclick = () =>
  confirmAction(
    "重置演示数据",
    "恢复初始资料状态、基础选项和举报，退出演示账号。不会修改项目文件。",
    false,
    () => {
      Demo.reset();
      feedback("演示数据已重置。");
    },
  );

document.querySelectorAll(".subnav a").forEach((link) => {
  if (
    link.getAttribute("href") ===
    `${page === "mine" ? "my-listings" : page}.html`
  )
    link.setAttribute("aria-current", "page");
});

function sessionStrip() {
  $("session-strip").replaceChildren();
  const label = document.createElement("span");
  label.textContent = Demo.state.loggedIn
    ? "演示账号已登录 · campus_reader"
    : "正在以原型体验模式浏览";
  $("session-strip").append(label);
  if (Demo.state.loggedIn) {
    const logout = document.createElement("button");
    logout.className = "plain";
    logout.textContent = "退出演示账号";
    logout.onclick = () => {
      Demo.login(false);
      sessionStrip();
      feedback("已退出演示账号。");
    };
    $("session-strip").append(logout);
  }
  $("storage-warning").textContent = Demo.storageAvailable
    ? ""
    : " 当前浏览器不支持会话存储，跨页状态可能无法保留。";
}

function empty(text) {
  return `<div class="empty-panel">${esc(text)}</div>`;
}
function row(book, admin = false) {
  const item = Demo.listing(book.id);
  let controls = `<a href="detail.html?id=${book.id}">查看公开页</a>`;
  if (admin) {
    controls += item.blocked
      ? `<button data-release="${book.id}">解除限制</button>`
      : `<button class="danger" data-block="${book.id}">管理下架</button>`;
  } else {
    if (!item.blocked && !item.completed)
      controls += `<a href="publish.html?edit=${book.id}">编辑</a>`;
    controls += Demo.actions(book.id)
      .map((next) => {
        let label = `标记${next}`;
        if (next === "已下架") label = "下架";
        if (next === "在售" || next === "待赠送")
          label = item.status === "已预订" ? "取消预订" : "重新上架";
        return `<button data-transition="${book.id}" data-next="${next}">${label}</button>`;
      })
      .join("");
  }
  controls += `<button data-history="${book.id}">状态记录</button>`;
  return `<article class="listing-row"><div class="mini-cover" style="background:${book.bg};color:${book.fg}" aria-hidden="true">${esc(book.title)}</div><div class="listing-info"><h2>${esc(book.title)}</h2><p>${esc(book.category)} · ${book.price ? "¥ " + book.price.toFixed(2) : "免费赠送"}</p><span class="status-pill">${item.status}${item.blocked ? " · 管理员限制" : ""}</span>${item.reason ? `<p>管理说明：${esc(item.reason)}</p>` : ""}${item.completed ? "<p>流转已完成，无法编辑或重新上架。</p>" : ""}</div><div class="row-actions">${controls}</div></article>`;
}

function renderListings(admin) {
  const query = $(admin ? "admin-search" : "listing-search")
    .value.trim()
    .toLowerCase();
  const status = $(admin ? "admin-state" : "status-filter").value;
  const list = books.filter(
    (book) =>
      book.title.toLowerCase().includes(query) &&
      (!status || Demo.listing(book.id).status === status),
  );
  $(admin ? "admin-list" : "listing-list").innerHTML =
    list.map((book) => row(book, admin)).join("") ||
    empty("没有匹配的发布记录，试试其他状态或关键词。");
  if (!admin) {
    const items = Object.values(Demo.state.listings);
    const counts = [
      ["全部发布", items.length],
      [
        "可获取",
        items.filter((item) => ["在售", "待赠送"].includes(item.status)).length,
      ],
      ["已预订", items.filter((item) => item.status === "已预订").length],
      ["已完成", items.filter((item) => item.completed).length],
    ];
    $("stats").innerHTML = counts
      .map(
        ([name, count]) =>
          `<div class="stat"><strong>${count}</strong>${name}</div>`,
      )
      .join("");
  }
}

function renderReports() {
  const filter = $("report-filter").value;
  $("report-list").innerHTML =
    Demo.state.reports
      .filter((report) => !filter || report.status === filter)
      .map((report) => {
        const book = books.find((book) => book.id === report.bookId);
        return `<article class="report-card"><h2>${esc(book.title)} <span class="status-pill">${report.status}</span></h2><p>举报原因：${esc(report.reason)}<br>${esc(report.description || "无补充说明")}</p>${report.result ? `<p>处理说明：${esc(report.result)}</p>` : ""}<div class="row-actions"><a href="detail.html?id=${book.id}">查看资料</a>${report.status === "待处理" ? `<button data-report="${report.id}" data-result="已处理">处理并下架</button><button data-report="${report.id}" data-result="驳回">驳回举报</button>` : ""}</div></article>`;
      })
      .join("") || empty("当前没有此状态的举报。");
}

function renderOptions() {
  [
    ["majors", "major-list"],
    ["tags", "tag-list"],
  ].forEach(([type, id]) => {
    $(id).innerHTML =
      (type === "majors"
        ? '<div class="option-row"><span>不限专业</span><small>固定选项</small></div>'
        : "") +
      Demo.state[type]
        .map(
          (option) =>
            `<div class="option-row"><span>${esc(option.name)} <small>${option.enabled ? "启用" : "停用"}</small></span><button data-option="${option.id}" data-type="${type}" data-op="rename">修改</button><button data-option="${option.id}" data-type="${type}" data-op="toggle">${option.enabled ? "停用" : "启用"}</button></div>`,
        )
        .join("");
  });
}

function render() {
  sessionStrip();
  if (page === "mine") renderListings(false);
  if (page === "admin-listings") renderListings(true);
  if (page === "admin-reports") renderReports();
  if (page === "admin-options") renderOptions();
}

document.addEventListener("click", (event) => {
  const button = event.target.closest("button");
  if (!button) return;
  const data = button.dataset;
  if (data.transition)
    confirmAction(
      `确认${button.textContent}`,
      `资料将变为“${data.next}”。完成流转后不能重新上架。`,
      false,
      () => {
        Demo.transition(Number(data.transition), data.next);
        feedback("状态已更新，市场和详情页会同步读取演示状态。");
      },
    );
  if (data.block)
    confirmAction(
      "管理员下架",
      "填写下架原因，发布者将无法自行恢复。",
      true,
      (reason) => {
        Demo.block(Number(data.block), reason);
        feedback("已下架并记录原因。");
      },
    );
  if (data.release)
    confirmAction(
      "解除管理员限制",
      "解除后仍保持下架。未完成资料可由发布者重新上架，已完成资料仍不可恢复。",
      false,
      () => Demo.release(Number(data.release)),
    );
  if (data.history) {
    const history = Demo.listing(Number(data.history)).history;
    confirmAction(
      "状态记录",
      history.length
        ? history
            .map(
              (item) =>
                `${item.time} ${item.actor}：${item.from} → ${item.to}${item.reason ? "；" + item.reason : ""}`,
            )
            .join("\n")
        : "暂无状态变更记录。",
      false,
      () => {},
    );
  }
  if (data.report)
    confirmAction(
      data.result === "已处理" ? "处理举报并下架" : "驳回举报",
      "请填写处理说明，结果将保留在举报记录中。",
      true,
      (reason) => {
        const report = Demo.state.reports.find(
          (report) => report.id === Number(data.report),
        );
        if (report.status !== "待处理") throw new Error("此举报已经处理。");
        if (data.result === "已处理") Demo.block(report.bookId, reason);
        report.status = data.result;
        report.result = reason;
        Demo.save();
        feedback("举报处理完成。");
      },
    );
  if (data.option) {
    const option = Demo.state[data.type].find(
      (option) => option.id === Number(data.option),
    );
    confirmAction(
      data.op === "rename"
        ? "修改名称"
        : `${option.enabled ? "停用" : "启用"}选项`,
      "停用后保留历史资料关联，不允许新增关联。此操作会影响发布表单的可选项。",
      data.op === "rename",
      (value) => {
        if (data.op === "rename") {
          if (value.length > (data.type === "majors" ? 40 : 30))
            throw new Error("名称过长。");
          if (
            value === "不限专业" ||
            Demo.state[data.type].some(
              (other) => other.id !== option.id && other.name === value,
            )
          )
            throw new Error("该名称已存在或是固定选项。");
          option.name = value;
        } else option.enabled = !option.enabled;
        Demo.save();
        feedback("选项已更新。");
      },
      option.name,
    );
  }
});

window.addEventListener("pageshow", (event) => {
  if (event.persisted) location.reload();
});

[
  "listing-search",
  "status-filter",
  "admin-search",
  "admin-state",
  "report-filter",
].forEach((id) => {
  if ($(id))
    $(id).addEventListener(id.includes("search") ? "input" : "change", render);
});

function formMessage(form, message, error = false) {
  const target = form.querySelector(".form-feedback");
  target.textContent = message;
  target.classList.toggle("error", error);
}

if ($("login-form"))
  $("login-form").onsubmit = (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.elements.account.value.trim()) {
      formMessage(form, "账号不能只有空格。", true);
      return;
    }
    Demo.login(true);
    form.elements.password.value = "";
    // Only allow known local destinations; never navigate to arbitrary query URLs.
    const next = new URLSearchParams(location.search).get("next") || "";
    const target =
      /^(my-listings|profile|publish)\.html$/.test(next) ||
      /^detail\.html\?id=\d+$/.test(next)
        ? next
        : "my-listings.html";
    location.href = target;
  };
if ($("register-form"))
  $("register-form").onsubmit = (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (form.elements.password.value !== form.elements.confirm.value) {
      formMessage(form, "两次密码不一致。", true);
      return;
    }
    form.reset();
    formMessage(
      form,
      "注册表单校验通过；未创建真实账号。可前往登录页体验演示登录。",
    );
  };
if ($("profile-form"))
  $("profile-form").onsubmit = (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.elements.contact.value.trim()) {
      formMessage(form, "联系账号不能为空。", true);
      return;
    }
    formMessage(
      form,
      "资料修改预览通过；未保存到服务器或浏览器存储，历史发布联系方式未改变。",
    );
  };
if ($("password-form"))
  $("password-form").onsubmit = (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (form.elements.old.value !== "demo123") {
      formMessage(form, "原密码不正确，请使用演示值 demo123。", true);
      return;
    }
    if (form.elements.password.value !== form.elements.confirm.value) {
      formMessage(form, "两次新密码不一致。", true);
      return;
    }
    if (form.elements.password.value === form.elements.old.value) {
      formMessage(form, "新密码不能与原密码相同。", true);
      return;
    }
    form.reset();
    formMessage(form, "密码修改流程验证通过，未保存任何密码。");
  };
[
  ["major-form", "majors"],
  ["tag-form", "tags"],
].forEach(([id, type]) => {
  if (!$(id)) return;
  $(id).onsubmit = (event) => {
    event.preventDefault();
    const input = event.currentTarget.elements.name;
    const name = input.value.trim();
    if (
      !name ||
      name === "不限专业" ||
      Demo.state[type].some((option) => option.name === name)
    ) {
      feedback("名称为空、重复或属于固定选项，请修改。");
      return;
    }
    Demo.state[type].push({ id: Date.now(), name, enabled: true });
    Demo.save();
    input.value = "";
    render();
    feedback("已新增选项。");
  };
});
render();
