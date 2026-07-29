import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const html = readFileSync(new URL("./index.html", import.meta.url), "utf8");
const css = readFileSync(new URL("./styles.css", import.meta.url), "utf8");
const app = readFileSync(new URL("./app.js", import.meta.url), "utf8");
const image = readFileSync(
  new URL("./assets/eye-drops-preview.png", import.meta.url),
);

test("prototype contains every primary product screen", () => {
  for (const screen of [
    "home",
    "inventory",
    "scanner",
    "confirm",
    "detail",
    "settings",
  ]) {
    assert.match(html, new RegExp(`data-screen="${screen}"`));
  }
});

test("prototype works without remote UI assets", () => {
  assert.match(html, /src="\.\/lucide\.min\.js"/);
  assert.doesNotMatch(html, /src="https?:\/\//);
  assert.equal(image.subarray(1, 4).toString("ascii"), "PNG");
});

test("daily inventory actions stay directly available", () => {
  assert.match(html, /data-decrement/);
  assert.match(html, /data-restock/);
  assert.match(html, />补货</);
});

test("blocking date field is shown before stable name and barcode fields", () => {
  const dateIndex = html.indexOf('class="review-row needs-attention"');
  const nameIndex = html.indexOf('data-toast="商品名来自关键帧 1"');
  assert.ok(dateIndex > 0);
  assert.ok(nameIndex > dateIndex);
});

test("recognition state requires confirmation and does not auto-save", () => {
  assert.match(html, /data-save-batch disabled/);
  assert.match(app, /saveButton\.disabled = !dateFound/);
  assert.match(app, /showScreen\("detail"\)/);
  assert.doesNotMatch(app, /localStorage|indexedDB|fetch\(/);
});

test("touch targets and narrow-screen layout have explicit constraints", () => {
  assert.match(css, /\.icon-button,[\s\S]*width: 48px;[\s\S]*height: 48px;/);
  assert.match(css, /\.filter-chip \{[\s\S]*min-height: 48px;/);
  assert.match(css, /\.mode-segment button \{[\s\S]*min-height: 48px;/);
  assert.match(css, /@media \(max-width: 520px\)/);
});
