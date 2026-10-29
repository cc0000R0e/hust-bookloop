// Fictional workflow state only. Never store passwords or contact information.
const Demo = (() => {
  const key = "bookloop-workflow-v1";
  let storageAvailable = true;
  function defaults() {
    return {
      loggedIn: false,
      listings: Object.fromEntries(
        books.map((book) => [
          book.id,
          {
            status: book.price ? "在售" : "待赠送",
            blocked: false,
            completed: false,
            reason: "",
            history: [],
          },
        ]),
      ),
      reports: [
        {
          id: 1,
          bookId: 4,
          reason: "资料信息不准确",
          description: "演示举报：请核对描述中的版本信息。",
          status: "待处理",
          result: "",
        },
      ],
      majors: ["计算机科学与技术", "物联网工程"].map((name, i) => ({
        id: i + 1,
        name,
        enabled: true,
      })),
      tags: ["英语四级", "英语六级", "雅思", "托福", "考研"].map((name, i) => ({
        id: i + 1,
        name,
        enabled: true,
      })),
    };
  }
  let state = defaults();
  try {
    const saved = JSON.parse(sessionStorage.getItem(key));
    if (saved && saved.listings && saved.reports && saved.majors && saved.tags)
      state = saved;
  } catch {
    storageAvailable = false;
  }
  // Map renamed options onto bundled fixtures on each page load. IDs remain stable.
  const initialMajors = ["计算机科学与技术", "物联网工程"];
  const initialTags = ["英语四级", "英语六级", "雅思", "托福", "考研"];
  books.forEach((book) => {
    book.scope = book.scope.map(([major, semester]) => {
      const option = state.majors.find(
        (option) => option.id === initialMajors.indexOf(major) + 1,
      );
      return [option ? option.name : major, semester];
    });
    book.tags = book.tags.map((tag) => {
      const option = state.tags.find(
        (option) => option.id === initialTags.indexOf(tag) + 1,
      );
      return option ? option.name : tag;
    });
  });
  function save() {
    try {
      sessionStorage.setItem(key, JSON.stringify(state));
    } catch {
      storageAvailable = false;
    }
  }
  function listing(id) {
    return state.listings[id];
  }
  function available(id) {
    return ["在售", "待赠送"].includes(listing(id).status);
  }
  function actions(id) {
    const item = listing(id);
    const book = books.find((book) => book.id === Number(id));
    if (item.blocked || item.completed) return [];
    const open = book.price ? "在售" : "待赠送";
    const done = book.price ? "已售出" : "已送出";
    if (item.status === open) return ["已预订", done, "已下架"];
    if (item.status === "已预订") return [open, done, "已下架"];
    if (item.status === "已下架") return [open];
    return [];
  }
  function record(id, from, to, actor, reason = "") {
    listing(id).history.unshift({
      from,
      to,
      actor,
      reason,
      time: new Date().toLocaleString("zh-CN"),
    });
  }
  function transition(id, next) {
    if (!actions(id).includes(next))
      throw new Error("当前状态不允许此操作，请刷新后重试。");
    const item = listing(id);
    record(id, item.status, next, "发布者");
    item.status = next;
    item.completed = ["已售出", "已送出"].includes(next);
    save();
  }
  function block(id, reason) {
    if (!reason.trim()) throw new Error("请填写下架原因。");
    const item = listing(id);
    record(id, item.status, "已下架", "管理员", reason.trim());
    item.status = "已下架";
    item.blocked = true;
    item.reason = reason.trim();
    save();
  }
  function release(id) {
    const item = listing(id);
    if (!item.blocked) throw new Error("此条目未被管理员限制。");
    item.blocked = false;
    record(
      id,
      "已下架",
      "已下架",
      "管理员",
      "解除限制；已完成条目仍不可重新上架。",
    );
    save();
  }
  function addReport(bookId, reason, description) {
    if (
      state.reports.some(
        (report) => report.bookId === bookId && report.status === "待处理",
      )
    )
      throw new Error("该资料已有待处理演示举报，请勿重复提交。");
    state.reports.push({
      id: Date.now(),
      bookId,
      reason,
      description,
      status: "待处理",
      result: "",
    });
    save();
  }
  function login(value) {
    state.loggedIn = value;
    save();
  }
  function reset() {
    state = defaults();
    save();
  }
  return {
    get state() {
      return state;
    },
    get storageAvailable() {
      return storageAvailable;
    },
    save,
    listing,
    available,
    actions,
    transition,
    block,
    release,
    addReport,
    login,
    reset,
  };
})();
