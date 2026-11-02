(() => {
  const form = document.getElementById("publish-form");
  const photos = document.getElementById("photos");
  const previews = document.getElementById("image-previews");
  const imageError = document.getElementById("image-error");
  let files = [];
  let urls = [];

  function renderPhotos() {
    urls.forEach((url) => URL.revokeObjectURL(url));
    urls = [];
    previews.replaceChildren();
    const transfer = new DataTransfer();
    files.forEach((file, index) => {
      transfer.items.add(file);
      const item = document.createElement("div");
      item.className = "image-item";
      const image = document.createElement("img");
      const url = URL.createObjectURL(file);
      urls.push(url);
      image.src = url;
      image.alt = `${index === 0 ? "封面" : "照片"}：${file.name}`;
      const name = document.createElement("p");
      name.textContent = `${index === 0 ? "封面 · " : ""}${file.name}`;
      const remove = document.createElement("button");
      remove.type = "button";
      remove.textContent = "删除";
      remove.addEventListener("click", () => {
        files.splice(index, 1);
        imageError.textContent = "";
        renderPhotos();
      });
      item.append(image, name, remove);
      if (index > 0) {
        const cover = document.createElement("button");
        cover.type = "button";
        cover.textContent = "设为封面";
        cover.addEventListener("click", () => {
          files.unshift(files.splice(index, 1)[0]);
          renderPhotos();
        });
        item.append(cover);
      }
      previews.append(item);
    });
    photos.files = transfer.files;
  }

  photos.addEventListener("change", () => {
    const added = Array.from(photos.files);
    const valid = added.every((file) =>
      ["image/jpeg", "image/png", "image/webp"].includes(file.type) &&
      file.size > 0 && file.size <= 5 * 1024 * 1024);
    if (!valid || added.length + files.length > 6) {
      imageError.textContent = "最多6张照片，格式须为JPG、PNG或WebP，每张不超过5MB且不能为空。";
    } else {
      files.push(...added);
      imageError.textContent = "";
    }
    renderPhotos();
  });

  const rows = document.getElementById("scope-rows");
  const addScope = document.getElementById("add-scope");
  function syncScopes() {
    Array.from(rows.children).forEach((row, index) => {
      row.querySelectorAll("[data-scope]").forEach((select) => {
        select.name = `scopes[${index}].${select.dataset.scope}`;
        select.id = `scope-${index}-${select.dataset.scope}`;
      });
      row.querySelector("[data-remove-scope]").disabled = rows.children.length === 1;
    });
    addScope.disabled = rows.children.length >= 50;
  }
  addScope.addEventListener("click", () => {
    if (rows.children.length >= 50) return;
    const row = rows.firstElementChild.cloneNode(true);
    window.MajorPicker?.prepareClone(rows.firstElementChild, row);
    row.querySelector('[data-scope="majorId"]').value = "0";
    row.querySelector('[data-scope="college"]').value = "";
    row.querySelector('[data-scope="semester"]').value = "UNDEFINED";
    rows.append(row);
    window.MajorPicker?.enhance(row.querySelector("[data-major-picker]"));
    syncScopes();
  });
  rows.addEventListener("click", (event) => {
    if (event.target.matches("[data-remove-scope]") && rows.children.length > 1) {
      event.target.closest(".scope-row").remove();
      syncScopes();
    }
  });
  syncScopes();

  const price = document.getElementById("price");
  let salePrice = form.elements.transferType.value === "SALE" ? price.value : "";
  function syncMode() {
    const gift = form.elements.transferType.value === "GIFT";
    if (gift && !price.readOnly) salePrice = price.value;
    if (!gift && price.readOnly) price.value = salePrice;
    price.readOnly = gift;
    price.required = !gift;
    price.min = gift ? "0" : "0.01";
    if (gift) price.value = "0";
    document.getElementById("summary-mode").textContent = gift ? "免费赠送" : "出售";
    document.getElementById("summary-price").textContent = gift ? "¥0.00" : price.value ? `¥${price.value}` : "填写价格后显示";
  }
  form.querySelectorAll('[name="transferType"]').forEach((radio) => radio.addEventListener("change", syncMode));
  price.addEventListener("input", syncMode);
  syncMode();
  const description = document.getElementById("description");
  function count() { document.getElementById("description-count").textContent = `${description.value.length} / 2000`; }
  description.addEventListener("input", count);
  count();
  form.addEventListener("submit", (event) => {
    const scopes = Array.from(rows.children).map((row) =>
      `${row.querySelector('[data-scope="majorId"]').value}:${row.querySelector('[data-scope="majorId"]').value === "0" ? row.querySelector('[data-scope="college"]').value : ""}:${row.querySelector('[data-scope="semester"]').value}`);
    const duplicates = new Set(scopes).size !== scopes.length;
    document.getElementById("scope-error").textContent = duplicates ? "适用范围不能重复。" : "";
    if (duplicates) event.preventDefault();
  });
  window.addEventListener("pagehide", (event) => {
    if (!event.persisted) urls.forEach((url) => URL.revokeObjectURL(url));
  });
})();
