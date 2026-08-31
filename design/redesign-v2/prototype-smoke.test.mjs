import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const html = readFileSync(new URL("./index.html", import.meta.url), "utf8");
const css = readFileSync(new URL("./styles.css", import.meta.url), "utf8");
const app = readFileSync(new URL("./app.js", import.meta.url), "utf8");
const cameraImage = readFileSync(new URL("./assets/auto-camera.jpg", import.meta.url));
const dateImage = readFileSync(
  new URL("./assets/low-contrast-date.jpg", import.meta.url),
);

test("prototype contains stable destinations and complete task screens", () => {
  for (const screen of [
    "today",
    "inventory",
    "settings",
    "detail",
    "editor",
    "scanner",
    "review",
    "evidence",
    "barcode",
    "reminders",
    "transfer",
    "excel",
    "excel-preview",
    "backup",
    "recognition-settings",
  ]) {
    assert.match(html, new RegExp(`data-screen="${screen}"`));
  }
  assert.equal((html.match(/data-nav="/g) ?? []).length, 3);
  assert.doesNotMatch(html, /data-nav="scanner"/);
});

test("prototype works without remote UI assets", () => {
  assert.match(html, /src="\.\/lucide\.min\.js"/);
  assert.doesNotMatch(html, /src="https?:\/\//);
  assert.equal(cameraImage.subarray(0, 2).toString("hex"), "ffd8");
  assert.equal(dateImage.subarray(0, 2).toString("hex"), "ffd8");
});

test("manual entry is primary and recognition is optional", () => {
  assert.match(html, /data-start-add/);
  assert.match(html, /手动可直接完成/);
  assert.match(html, /不再识别，返回填写/);
  assert.match(html, /使用已识别的 2 项/);
  assert.match(app, /showScreen\("editor"/);
});

test("partial recognition can continue and every suggested field is editable", () => {
  assert.match(html, /id="review-name" type="text"/);
  assert.match(html, /id="review-barcode" type="text"/);
  assert.match(html, /采用并继续填写/);
  assert.match(html, /日期未识别/);
  assert.match(html, /不会自动填成“无过期时间”/);
  assert.match(app, /已采用名称和条码，日期仍可手动填写/);
});

test("daily inventory actions and multi-batch replenishment stay explicit", () => {
  assert.match(html, /data-decrement/);
  assert.match(html, /data-restock/);
  assert.match(html, /补货为新批次/);
  assert.match(html, /同一个条码可能属于不同商品/);
  assert.match(html, /data-barcode-restock disabled/);
});

test("editor validates without discarding other input", () => {
  assert.match(app, /新批次数量必须大于 0/);
  assert.match(app, /请填写商品名称/);
  assert.match(app, /暂时不知道日期/);
  assert.match(app, /scrollIntoView/);
  assert.doesNotMatch(app, /localStorage\.clear|indexedDB\.deleteDatabase/);
});

test("data operations show preview and rollback promises", () => {
  assert.match(html, /错误行/);
  assert.match(html, /写入或读回失败时完整回滚/);
  assert.match(html, /相同批次 UUID/);
  assert.match(html, /SHA-256/);
});

test("touch targets and narrow-screen layout have explicit constraints", () => {
  assert.match(css, /\.icon-button \{[\s\S]*width: 48px;[\s\S]*height: 48px;/);
  assert.match(css, /\.button \{[\s\S]*min-height: 48px;/);
  assert.match(css, /\.filter-grid button \{[\s\S]*min-height: 48px;/);
  assert.match(css, /@media \(max-width: 380px\)/);
  assert.match(css, /@media \(max-height: 680px\)/);
});
