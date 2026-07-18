package com.shiqi.expirytracker;

import org.json.JSONArray;
import org.json.JSONObject;

final class BarcodeProductInfo {
    String barcode = "";
    String gtin14 = "";
    String name = "";
    String brand = "";
    String generalName = "";
    String category = "";
    String specification = "";
    String netContent = "";
    String manufacturer = "";
    String registrationMessage = "";
    String imageUrl = "";
    String source = "";
    boolean found = false;
    boolean registered = false;

    static BarcodeProductInfo fromApiZeroJson(String jsonText) {
        BarcodeProductInfo info = new BarcodeProductInfo();
        try {
            JSONObject root = new JSONObject(jsonText);
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                return info;
            }

            info.barcode = clean(data.optString("barcode", ""));
            info.gtin14 = clean(data.optString("gtin14", ""));
            info.name = clean(data.optString("name", ""));
            info.brand = clean(data.optString("brand", ""));
            info.generalName = clean(data.optString("general_name", ""));
            info.category = clean(data.optString("category", ""));
            info.specification = clean(data.optString("specification", ""));
            info.netContent = clean(data.optString("net_content", ""));
            info.manufacturer = clean(data.optString("manufacturer", ""));
            info.registrationMessage = clean(data.optString("registration_message", ""));
            info.source = "GS1 商品码接口";
            info.found = data.optBoolean("found", false) && info.name.length() > 0;
            info.registered = data.optBoolean("registered", false);

            JSONArray images = data.optJSONArray("images");
            if (images != null && images.length() > 0) {
                info.imageUrl = clean(images.optString(0, ""));
            }
        } catch (Exception ignored) {
            return new BarcodeProductInfo();
        }
        return info;
    }

    static BarcodeProductInfo fromGdsImportJson(String jsonText, String fallbackBarcode) {
        BarcodeProductInfo info = new BarcodeProductInfo();
        info.barcode = clean(fallbackBarcode);
        info.source = "中国商品信息服务平台";

        try {
            JSONObject root = new JSONObject(jsonText);
            int code = root.optInt("Code", root.optInt("code", 0));
            String message = clean(firstString(root, "Msg", "msg", "message"));
            if (code != 1) {
                info.registrationMessage = message.length() > 0
                        ? "中国商品信息服务平台：" + message
                        : "中国商品信息服务平台暂未返回商品信息";
                return info;
            }

            JSONObject data = root.optJSONObject("Data");
            if (data == null) {
                data = root.optJSONObject("data");
            }
            if (data == null) {
                return info;
            }

            JSONArray items = data.optJSONArray("Items");
            if (items == null) {
                items = data.optJSONArray("items");
            }
            if (items == null || items.length() == 0) {
                info.registrationMessage = "中国商品信息服务平台：未查到进口商品报备信息";
                return info;
            }

            JSONObject item = items.optJSONObject(0);
            if (item == null) {
                return info;
            }

            info.gtin14 = clean(firstString(item, "gtin", "Gtin", "gtin14", "GTIN"));
            if (info.gtin14.length() > 0) {
                info.barcode = BarcodeUtils.stripLeadingGtinZero(info.gtin14);
            }
            if (info.barcode.length() == 0) {
                info.barcode = clean(fallbackBarcode);
            }

            info.name = clean(firstString(
                    item,
                    "description_cn",
                    "DescriptionCn",
                    "description",
                    "Description",
                    "goodsName",
                    "productName",
                    "name"
            ));
            info.brand = clean(firstString(item, "brand_cn", "BrandCn", "brand", "Brand"));
            info.category = clean(firstString(item, "gpcName", "gpcname", "GPCName", "category", "categoryName"));
            info.specification = clean(firstString(item, "specification", "Specification", "spec", "Spec"));
            info.netContent = clean(firstString(item, "netContent", "net_content", "content", "Content"));
            info.manufacturer = clean(firstString(item, "realname", "firmname", "firmName", "enterpriseName", "manufacturer"));
            String origin = clean(firstString(item, "origin_name", "originName", "OriginName", "origin"));
            if (origin.length() > 0) {
                info.registrationMessage = "进口商品原产地/市场：" + origin;
            }
            info.imageUrl = normalizeGdsImageUrl(firstString(item, "picfilename", "picFileName", "imageUrl", "imgUrl"));
            info.found = info.name.length() > 0;
            info.registered = info.found;
            if (info.found && info.registrationMessage.length() == 0) {
                info.registrationMessage = "数据来源：中国商品信息服务平台进口商品数据";
            }
        } catch (Exception ignored) {
            return new BarcodeProductInfo();
        }
        return info;
    }

    String displayName() {
        String raw;
        if (name.length() > 0) {
            raw = name;
        } else if (brand.length() > 0 && generalName.length() > 0) {
            raw = brand + generalName;
        } else {
            raw = generalName;
        }
        String cleaned = RecognitionTextCleaner.intelligentProductNameCandidate(raw);
        return cleaned.length() > 0 ? cleaned : RecognitionTextCleaner.cleanProductNameLine(raw);
    }

    String notes() {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "条码", barcode);
        appendLine(builder, "品牌", brand);
        appendLine(builder, "规格", specification.length() > 0 ? specification : netContent);
        appendLine(builder, "商品分类", category.length() > 0 ? category : generalName);
        appendLine(builder, "厂家", manufacturer);
        appendLine(builder, "查询说明", registrationMessage);
        appendLine(builder, "数据来源", source);
        return builder.toString();
    }

    String summary() {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "商品名", displayName());
        appendLine(builder, "条码", barcode);
        appendLine(builder, "品牌", brand);
        appendLine(builder, "规格", specification.length() > 0 ? specification : netContent);
        appendLine(builder, "分类信息", category.length() > 0 ? category : generalName);
        appendLine(builder, "厂家", manufacturer);
        appendLine(builder, "查询说明", registrationMessage);
        appendLine(builder, "数据来源", source);
        return builder.toString();
    }

    private static String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = clean(object.optString(key, ""));
            if (value.length() > 0 && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return "";
    }

    private static String normalizeGdsImageUrl(String value) {
        String text = clean(value);
        if (text.length() == 0 || text.startsWith("http://") || text.startsWith("https://")) {
            return text;
        }
        if (text.startsWith("/")) {
            return "https://oss.gds.org.cn" + text;
        }
        return text;
    }

    private static void appendLine(StringBuilder builder, String label, String value) {
        String cleanValue = clean(value);
        if (cleanValue.length() == 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(label).append("：").append(cleanValue);
    }

    private static String clean(String value) {
        return FoodItem.cleanText(value);
    }
}
