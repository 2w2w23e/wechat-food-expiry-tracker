(() => {
  const shell = document.querySelector(".app-shell");
  const screens = [...document.querySelectorAll(".screen")];
  const navButtons = [...document.querySelectorAll("[data-nav]")];
  const toast = document.querySelector(".toast");
  const history = ["home"];
  let currentScreen = "home";
  let toastTimer = null;
  let dateFound = false;
  let scanMode = "auto";
  let scanTimer = null;

  const refreshIcons = () => {
    if (window.lucide) {
      window.lucide.createIcons({ attrs: { "aria-hidden": "true" } });
    }
  };

  const showToast = (message) => {
    window.clearTimeout(toastTimer);
    toast.textContent = message;
    toast.classList.add("is-visible");
    toastTimer = window.setTimeout(() => toast.classList.remove("is-visible"), 2200);
  };

  const updateNavigation = (name) => {
    navButtons.forEach((button) => {
      button.classList.toggle("is-selected", button.dataset.nav === name);
    });
  };

  const showScreen = (name, options = {}) => {
    const target = screens.find((screen) => screen.dataset.screen === name);
    if (!target) return;

    screens.forEach((screen) => screen.classList.toggle("is-active", screen === target));
    if (options.push !== false && currentScreen !== name) {
      history.push(name);
    }
    currentScreen = name;
    updateNavigation(name);
    shell.dataset.currentScreen = name;
    refreshIcons();
  };

  const goBack = () => {
    if (history.length > 1) history.pop();
    const previous = history[history.length - 1] || "home";
    showScreen(previous, { push: false });
  };

  const setScanMode = (mode) => {
    scanMode = mode;
    document.querySelectorAll("[data-scan-mode]").forEach((button) => {
      button.classList.toggle("is-selected", button.dataset.scanMode === mode);
    });
    document.querySelector("[data-scan-title]").textContent =
      mode === "date" ? "日期特写" : "自动识别";
    document.querySelector(".name-box").hidden = mode === "date";
    document.querySelector(".barcode-box").hidden = mode === "date";
    document.querySelector(".date-box").classList.toggle("date-box-priority", mode === "date");
  };

  const renderDateState = () => {
    const dateStep = document.querySelector("[data-date-step]");
    const dateLabel = document.querySelector("[data-date-box-label]");
    const scanStatus = document.querySelector("[data-scan-status]");
    const resultButton = document.querySelector("[data-review-result]");
    const reviewValue = document.querySelector("[data-date-review-value]");
    const reviewNote = document.querySelector("[data-date-review-note]");
    const fieldCount = document.querySelector("[data-field-count]");
    const saveButton = document.querySelector("[data-save-batch]");
    const secondary = document.querySelector("[data-confirm-secondary]");
    const subtitle = document.querySelector("[data-confirm-subtitle]");
    const priorityButton = document.querySelector("[data-prioritize-date]");
    const dateEvidence = document.querySelector(".evidence-thumb[data-rescan-date]");
    const dateReviewRow = document.querySelector(".review-row[data-rescan-date]");
    const dateReviewIcon = dateReviewRow.querySelector(".review-icon");

    dateStep.classList.toggle("is-done", dateFound);
    dateStep.querySelector("strong").textContent = dateFound ? "已识别" : "查找中";
    dateLabel.textContent = dateFound ? "有效期至" : "可能的日期";
    document.querySelector(".date-box").classList.toggle("is-recognized", dateFound);
    scanStatus.textContent = dateFound
      ? "名称、条码和日期已齐"
      : "已找到 2 项，继续查找日期";
    resultButton.textContent = dateFound ? "核对全部结果" : "核对已识别的 2 项";
    reviewValue.textContent = dateFound ? "有效期至 2028-06-30" : "尚未找到清楚日期";
    reviewNote.textContent = dateFound
      ? "日期特写 · 3 帧一致"
      : "继续对准喷码、拓印或激光日期";
    fieldCount.textContent = dateFound ? "4 / 4" : "2 / 4";
    saveButton.disabled = !dateFound;
    secondary.textContent = dateFound ? "重新扫描" : "补扫日期";
    subtitle.textContent = dateFound ? "4 项信息已齐，请确认后保存" : "已识别 2 项，日期待补充";
    priorityButton.hidden = dateFound;
    dateEvidence.classList.toggle("is-missing", !dateFound);
    dateEvidence.setAttribute("aria-label", dateFound ? "日期关键帧" : "补扫日期");
    dateEvidence.innerHTML = dateFound
      ? '<img src="./assets/eye-drops-preview.png" alt=""><span>日期</span>'
      : '<i data-lucide="camera"></i><span>补扫日期</span>';
    dateReviewRow.classList.toggle("needs-attention", !dateFound);
    dateReviewIcon.classList.toggle("warning-icon", !dateFound);
    dateReviewIcon.classList.toggle("success-icon", dateFound);
    refreshIcons();
  };

  const beginScan = (mode = "auto") => {
    window.clearTimeout(scanTimer);
    setScanMode(mode);
    renderDateState();
    showScreen("scanner");

    if (mode === "date") {
      document.querySelector("[data-scan-status]").textContent = "请靠近日期并保持稳定";
      scanTimer = window.setTimeout(() => {
        dateFound = true;
        renderDateState();
        showToast("日期关键帧更清楚，已识别有效期");
      }, 1350);
    }
  };

  document.addEventListener("click", (event) => {
    const target = event.target.closest("button");
    if (!target) return;

    if (target.dataset.nav) {
      showScreen(target.dataset.nav);
      return;
    }

    if (target.dataset.go) {
      showScreen(target.dataset.go);
      return;
    }

    if (target.hasAttribute("data-back")) {
      goBack();
      return;
    }

    if (target.dataset.startScan) {
      beginScan(target.dataset.startScan);
      return;
    }

    if (target.hasAttribute("data-restock")) {
      dateFound = false;
      beginScan("date");
      showToast("已复用商品档案，只识别本批次日期");
      return;
    }

    if (target.hasAttribute("data-review-result")) {
      renderDateState();
      showScreen("confirm");
      return;
    }

    if (target.hasAttribute("data-prioritize-date") || target.hasAttribute("data-rescan-date")) {
      beginScan("date");
      return;
    }

    if (target.dataset.scanMode) {
      setScanMode(target.dataset.scanMode);
      if (target.dataset.scanMode === "date" && !dateFound) {
        beginScan("date");
      }
      return;
    }

    if (target.dataset.focusRegion) {
      const box = target.getBoundingClientRect();
      const shellBox = shell.getBoundingClientRect();
      const reticle = document.querySelector("[data-focus-reticle]");
      reticle.style.left = `${box.left - shellBox.left + box.width / 2}px`;
      reticle.style.top = `${box.top - shellBox.top + box.height / 2}px`;
      reticle.classList.remove("is-visible");
      window.requestAnimationFrame(() => reticle.classList.add("is-visible"));
      showToast(target.dataset.focusRegion === "date" ? "正在优先对焦日期" : "正在重新对焦");
      return;
    }

    if (target.hasAttribute("data-decrement")) {
      const row = target.closest(".stock-row, .batch-row");
      row?.classList.add("quantity-updated");
      document.querySelector("[data-detail-quantity]").textContent = "0 盒";
      showToast("已减少 1，可在总览撤销");
      return;
    }

    if (target.dataset.filter) {
      document.querySelectorAll("[data-filter]").forEach((chip) => {
        chip.classList.toggle("is-selected", chip === target);
      });
      document.querySelectorAll(".stock-row").forEach((row) => {
        row.hidden =
          target.dataset.filter !== "all" &&
          row.dataset.stockStatus !== target.dataset.filter;
      });
      return;
    }

    if (target.dataset.filterJump) {
      showScreen("inventory");
      const chip = document.querySelector(`[data-filter="${target.dataset.filterJump}"]`);
      chip?.click();
      return;
    }

    if (target.hasAttribute("data-toggle")) {
      target.classList.toggle("is-on");
      return;
    }

    if (target.hasAttribute("data-save-batch")) {
      document.querySelector("[data-detail-expiry]").textContent = "2028-06-30";
      document.querySelector("[data-batch-date]").textContent = "有效期至 2028-06-30 · 1 盒";
      showScreen("detail");
      showToast("已保存为独立新批次");
      return;
    }

    if (target.dataset.toast) {
      showToast(target.dataset.toast);
    }
  });

  const searchInput = document.querySelector(".search-field input");
  searchInput.addEventListener("input", () => {
    const query = searchInput.value.trim().toLowerCase();
    document.querySelectorAll(".stock-row").forEach((row) => {
      row.hidden = Boolean(query) && !row.textContent.toLowerCase().includes(query);
    });
  });

  renderDateState();
  refreshIcons();
})();
