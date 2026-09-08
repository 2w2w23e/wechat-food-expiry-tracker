(() => {
  const rootScreens = new Set(["today", "inventory", "settings"]);
  const screens = new Map(
    [...document.querySelectorAll("[data-screen]")].map((screen) => [
      screen.dataset.screen,
      screen,
    ]),
  );
  const nav = document.querySelector(".main-nav");
  const navButtons = [...document.querySelectorAll("[data-nav]")];
  const historyStack = [];
  let currentScreen = "today";
  let snackbarTimer;
  let selectedFilter = "全部库存";
  let editorMode = "add";
  let reminderDirty = false;

  function refreshIcons() {
    if (window.lucide) {
      window.lucide.createIcons({
        attrs: {
          "stroke-width": 2,
          "aria-hidden": "true",
        },
      });
    }
  }

  function showScreen(name, options = {}) {
    if (!screens.has(name) || name === currentScreen) {
      return;
    }
    if (options.push !== false) {
      historyStack.push(currentScreen);
    }
    screens.get(currentScreen)?.classList.remove("is-active");
    currentScreen = name;
    const next = screens.get(name);
    next.classList.add("is-active");
    next.querySelector(".screen-scroll")?.scrollTo({ top: 0 });
    nav.classList.toggle("is-hidden", !rootScreens.has(name));
    navButtons.forEach((button) => {
      button.classList.toggle("is-selected", button.dataset.nav === name);
    });
    refreshIcons();
  }

  function goBack() {
    if (currentScreen === "reminders" && reminderDirty) {
      const leave = window.confirm("提醒设置还没有保存。放弃修改并返回吗？");
      if (!leave) {
        return;
      }
      reminderDirty = false;
    }
    const previous = historyStack.pop();
    if (previous && screens.has(previous)) {
      showScreen(previous, { push: false });
      return;
    }
    if (!rootScreens.has(currentScreen)) {
      showScreen("today", { push: false });
    }
  }

  function showSnackbar(message, actionLabel = "") {
    const bar = document.querySelector(".snackbar");
    const text = bar.querySelector("[data-snackbar-text]");
    const action = bar.querySelector("[data-snackbar-action]");
    text.textContent = message;
    action.textContent = actionLabel;
    action.hidden = !actionLabel;
    bar.hidden = false;
    clearTimeout(snackbarTimer);
    snackbarTimer = setTimeout(() => {
      bar.hidden = true;
    }, 3600);
  }

  function configureEditor(mode, productName = "") {
    editorMode = mode;
    const title = document.querySelector("[data-editor-title]");
    const eyebrow = document.querySelector("[data-editor-eyebrow]");
    const summary = document.querySelector("[data-restock-summary]");
    const nameInput = document.querySelector("#product-name");
    const barcodeInput = document.querySelector("#barcode-value");
    const saveLabel = document.querySelector("[data-save-label]");

    clearEditorErrors();
    document.querySelector("#date-pending").checked = false;
    document.querySelector("#quantity").value = "1";

    if (mode === "restock") {
      const name = productName || "鲜牛奶";
      title.textContent = "补货新批次";
      eyebrow.textContent = "旧批次不会被覆盖";
      summary.hidden = false;
      summary.querySelector("strong").textContent = `补货到${name}`;
      nameInput.value = name;
      nameInput.readOnly = true;
      barcodeInput.value = name === "鲜牛奶" ? "6900000000012" : "";
      saveLabel.textContent = "确认补货";
    } else {
      title.textContent = "新增物品";
      eyebrow.textContent = "手动可直接完成";
      summary.hidden = true;
      nameInput.value = "";
      nameInput.readOnly = false;
      barcodeInput.value = "";
      saveLabel.textContent = "保存入库";
    }
    showScreen("editor");
  }

  function clearEditorErrors() {
    document.querySelectorAll(".field.has-error").forEach((field) => {
      field.classList.remove("has-error");
    });
    document.querySelectorAll("[data-error-for]").forEach((node) => {
      node.textContent = "";
    });
  }

  function setFieldError(inputId, message, errorKey = inputId) {
    const input = document.querySelector(`#${inputId}`);
    input?.closest(".field")?.classList.add("has-error");
    const error = document.querySelector(`[data-error-for="${errorKey}"]`);
    if (error) {
      error.textContent = message;
    }
    return input;
  }

  function saveEditor() {
    clearEditorErrors();
    const nameInput = document.querySelector("#product-name");
    const quantityInput = document.querySelector("#quantity");
    const datePending = document.querySelector("#date-pending").checked;
    const activeMode = document.querySelector("[data-date-mode].is-selected")?.dataset.dateMode;
    const production = document.querySelector("#production-date").value;
    const shelfLife = Number(document.querySelector("#shelf-life").value);
    const directExpiry = document.querySelector("#expiry-date").value;
    let firstInvalid = null;

    if (!nameInput.value.trim()) {
      firstInvalid = setFieldError("product-name", "请填写商品名称。");
    }
    if (!Number.isFinite(Number(quantityInput.value)) || Number(quantityInput.value) <= 0) {
      const invalidQuantity = setFieldError(
        "quantity",
        "新批次数量必须大于 0。",
      );
      firstInvalid ||= invalidQuantity;
    }
    const hasCalculatedDate = activeMode === "calculated" && production && shelfLife > 0;
    const hasDirectDate = activeMode === "direct" && directExpiry;
    if (!datePending && !hasCalculatedDate && !hasDirectDate) {
      document.querySelector('[data-error-for="date"]').textContent =
        "请填写日期，或明确选择“暂时不知道日期”。";
      firstInvalid ||= document.querySelector("#production-date");
    }

    if (firstInvalid) {
      firstInvalid.scrollIntoView({ behavior: "smooth", block: "center" });
      firstInvalid.focus();
      showSnackbar("还有内容需要修正，已定位到第一个问题");
      return;
    }

    const message =
      editorMode === "restock"
        ? `已为${nameInput.value.trim()}新增独立批次`
        : `已加入库存：${nameInput.value.trim()}`;
    historyStack.length = 0;
    showScreen(editorMode === "restock" ? "detail" : "today", { push: false });
    showSnackbar(message, "撤销");
  }

  function updateExpiryPreview() {
    const production = document.querySelector("#production-date").value;
    const shelf = Number(document.querySelector("#shelf-life").value);
    const unit = document.querySelector("#shelf-unit").value;
    const output = document.querySelector("[data-expiry-preview]");
    if (!production || !shelf || shelf <= 0) {
      output.textContent = "请先填写完整日期";
      return;
    }
    const date = new Date(`${production}T12:00:00`);
    if (unit === "天") {
      date.setDate(date.getDate() + shelf);
    } else if (unit === "月") {
      date.setMonth(date.getMonth() + shelf);
    } else {
      date.setFullYear(date.getFullYear() + shelf);
    }
    output.textContent = date.toISOString().slice(0, 10);
  }

  function openFilterSheet() {
    document.querySelector("[data-sheet-backdrop]").hidden = false;
    document.querySelector("[data-filter-sheet]").hidden = false;
  }

  function closeFilterSheet() {
    document.querySelector("[data-sheet-backdrop]").hidden = true;
    document.querySelector("[data-filter-sheet]").hidden = true;
  }

  function showScanner(mode = "auto") {
    const preview = document.querySelector(".camera-preview");
    document.querySelector("[data-scan-heading]").textContent =
      mode === "date" ? "拍日期" : "拍包装识别";
    preview.style.backgroundImage = mode === "date"
      ? 'url("./assets/low-contrast-date.jpg")'
      : 'url("./assets/auto-camera.jpg")';
    preview.setAttribute("aria-label", mode === "date"
      ? "相机中的低对比度喷码日期"
      : "相机中的中文商品包装");
    preview.classList.toggle("date-mode", mode === "date");
    preview.classList.toggle("auto-mode", mode !== "date");
    document.querySelectorAll("[data-auto-scan-only]").forEach((node) => {
      node.hidden = mode === "date";
    });
    document.querySelector(".field-progress").classList.toggle(
      "date-only",
      mode === "date",
    );
    document.querySelector("[data-scan-status-title]").textContent =
      mode === "date" ? "正在寻找低对比度日期" : "画面清楚，正在找日期";
    document.querySelector("[data-scan-status-detail]").textContent =
      mode === "date" ? "尚未形成可采用的日期" : "已读到商品名和条码";
    const useRecognition = document.querySelector("[data-use-recognition]");
    useRecognition.disabled = mode === "date";
    useRecognition.textContent = mode === "date"
      ? "日期清楚后可采用"
      : "使用已识别的 2 项";
    showScreen("scanner");
  }

  document.addEventListener("click", (event) => {
    const go = event.target.closest("[data-go]");
    if (go) {
      showScreen(go.dataset.go);
      return;
    }

    const back = event.target.closest("[data-back]");
    if (back) {
      goBack();
      return;
    }

    const navButton = event.target.closest("[data-nav]");
    if (navButton) {
      historyStack.length = 0;
      showScreen(navButton.dataset.nav, { push: false });
      return;
    }

    if (event.target.closest("[data-start-add]")) {
      configureEditor("add");
      return;
    }

    const restock = event.target.closest("[data-restock]");
    if (restock) {
      configureEditor("restock", restock.dataset.restock);
      return;
    }

    const startScan = event.target.closest("[data-start-scan]");
    if (startScan) {
      showScanner(startScan.dataset.startScan || "auto");
      return;
    }

    if (event.target.closest("[data-return-manual]")) {
      while (historyStack.length && historyStack.at(-1) !== "editor") {
        historyStack.pop();
      }
      showScreen("editor", { push: false });
      showSnackbar("已保留原表单内容");
      return;
    }

    if (event.target.closest("[data-use-recognition]")) {
      showScreen("review");
      return;
    }

    if (event.target.closest("[data-accept-recognition]")) {
      document.querySelector("#product-name").value =
        document.querySelector("#review-name").value.trim();
      document.querySelector("#barcode-value").value =
        document.querySelector("#review-barcode").value.trim();
      while (historyStack.length && historyStack.at(-1) !== "editor") {
        historyStack.pop();
      }
      showScreen("editor", { push: false });
      showSnackbar("已采用名称和条码，日期仍可手动填写");
      return;
    }

    if (event.target.closest("[data-save-item]")) {
      saveEditor();
      return;
    }

    if (event.target.closest("[data-editor-back]")) {
      showSnackbar("草稿已保留，可稍后继续");
      goBack();
      return;
    }

    if (event.target.closest("[data-editor-cancel]")) {
      const confirmed = window.confirm("放弃这次填写？已输入的内容会被删除。");
      if (confirmed) {
        goBack();
      }
      return;
    }

    const modeButton = event.target.closest("[data-date-mode]");
    if (modeButton) {
      document.querySelectorAll("[data-date-mode]").forEach((button) => {
        button.classList.toggle("is-selected", button === modeButton);
      });
      document.querySelectorAll("[data-date-panel]").forEach((panel) => {
        panel.hidden = panel.dataset.datePanel !== modeButton.dataset.dateMode;
      });
      return;
    }

    if (event.target.closest("[data-open-filter]")) {
      openFilterSheet();
      return;
    }

    if (
      event.target.closest("[data-close-filter]") ||
      event.target.matches("[data-sheet-backdrop]")
    ) {
      closeFilterSheet();
      return;
    }

    const filterOption = event.target.closest("[data-filter-option]");
    if (filterOption) {
      selectedFilter = filterOption.dataset.filterOption;
      document.querySelectorAll("[data-filter-option]").forEach((button) => {
        button.classList.toggle("is-selected", button === filterOption);
      });
      return;
    }

    if (event.target.closest("[data-apply-filter]")) {
      document.querySelector("[data-filter-summary]").textContent =
        `${selectedFilter} · 最早到期优先`;
      closeFilterSheet();
      showSnackbar(`已筛选：${selectedFilter}`);
      return;
    }

    const decrement = event.target.closest("[data-decrement]");
    if (decrement) {
      const quantity = document.querySelector("[data-detail-quantity]");
      if (quantity && currentScreen === "detail") {
        quantity.textContent = "1 / 2 盒";
      }
      showSnackbar("已从最早到期批次减少 1 件", "撤销");
      return;
    }

    const focusBox = event.target.closest("[data-focus-box]");
    if (focusBox) {
      const ring = document.querySelector(".focus-ring");
      const box = focusBox.getBoundingClientRect();
      const shell = document.querySelector(".app-shell").getBoundingClientRect();
      ring.style.left = `${box.left - shell.left + box.width / 2 - 27}px`;
      ring.style.top = `${box.top - shell.top + box.height / 2 - 27}px`;
      ring.classList.add("is-visible");
      setTimeout(() => ring.classList.remove("is-visible"), 900);
      showSnackbar("已重新对焦这个区域");
      return;
    }

    const radio = event.target.closest('input[name="barcode-product"]');
    if (radio) {
      const button = document.querySelector("[data-barcode-restock]");
      button.disabled = false;
      button.textContent = `补货到${radio.value}`;
      return;
    }

    if (event.target.closest("[data-barcode-restock]")) {
      const selected = document.querySelector('input[name="barcode-product"]:checked');
      if (selected) {
        configureEditor("restock", selected.value);
      }
      return;
    }

    const reminderMode = event.target.closest("[data-reminder-mode]");
    if (reminderMode) {
      reminderDirty = true;
      document.querySelectorAll("[data-reminder-mode]").forEach((button) => {
        button.classList.toggle("is-selected", button === reminderMode);
      });
      showSnackbar(
        reminderMode.dataset.reminderMode === "smart"
          ? "将使用智能提醒"
          : "将使用固定提前天数",
      );
      return;
    }

    if (event.target.closest("[data-save-reminders]")) {
      reminderDirty = false;
      goBack();
      showSnackbar("提醒设置已保存；无状态变化时不会每天重算");
      return;
    }

    const message = event.target.closest("[data-snackbar]");
    if (message) {
      showSnackbar(message.dataset.snackbar);
    }
  });

  document.querySelector("#production-date").addEventListener("input", updateExpiryPreview);
  document.querySelector("#shelf-life").addEventListener("input", updateExpiryPreview);
  document.querySelector("#shelf-unit").addEventListener("change", updateExpiryPreview);

  document.querySelector("#debug-switch").addEventListener("change", (event) => {
    const share = document.querySelector("[data-share-debug]");
    share.disabled = event.target.checked;
    share.innerHTML = event.target.checked
      ? '<i data-lucide="activity"></i>正在记录本次识别状态'
      : '<i data-lucide="share-2"></i>关闭调试后分享日志';
    refreshIcons();
    showSnackbar(event.target.checked ? "调试模式已开启" : "调试模式已关闭，可分享日志");
  });

  document.querySelector("[data-snackbar-action]").addEventListener("click", () => {
    showSnackbar("已撤销刚才的操作");
  });

  refreshIcons();
  updateExpiryPreview();
})();
