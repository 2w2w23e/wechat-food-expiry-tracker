package com.shiqi.expirytracker;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;

final class BarcodeLookupClient {
    private static final String APIZERO_ENDPOINT = "https://v1.apizero.cn/api/barcode-gs1";
    private static final String GDS_IMPORT_ENDPOINT =
            "https://bff.gds.org.cn/gds/searching-api/ImportProduct/GetImportProductDataForGtin";

    private BarcodeLookupClient() {}

    static BarcodeProductInfo query(String barcode) throws Exception {
        String code = BarcodeUtils.digitsOnly(barcode);
        BarcodeProductInfo primary = null;
        Exception primaryException = null;

        try {
            primary = queryApiZero(code);
            if (primary != null && primary.found) {
                return primary;
            }
        } catch (Exception exception) {
            primaryException = exception;
        }

        BarcodeProductInfo official = queryGdsImport(code);
        if (official != null && official.found) {
            return official;
        }

        if (primary != null) {
            if (official != null && official.registrationMessage.length() > 0) {
                if (primary.registrationMessage.length() > 0) {
                    primary.registrationMessage = primary.registrationMessage + "\n" + official.registrationMessage;
                } else {
                    primary.registrationMessage = official.registrationMessage;
                }
            }
            return primary;
        }

        if (official != null) {
            return official;
        }

        if (primaryException != null) {
            throw primaryException;
        }
        throw new IllegalStateException("商品信息查询失败");
    }

    private static BarcodeProductInfo queryApiZero(String code) throws Exception {
        URL url = new URL(APIZERO_ENDPOINT + "?code=" + URLEncoder.encode(code, "UTF-8"));
        String body = getJson(url, 10000, 12000);
        return BarcodeProductInfo.fromApiZeroJson(body);
    }

    private static BarcodeProductInfo queryGdsImport(String code) throws Exception {
        String gtin14 = BarcodeUtils.toGtin14(code);
        if (gtin14.length() == 0 || gtin14.startsWith("069")) {
            return null;
        }

        String urlText = GDS_IMPORT_ENDPOINT
                + "?PageSize=5"
                + "&PageIndex=1"
                + "&Gtin=" + URLEncoder.encode(gtin14, "UTF-8")
                + "&Description="
                + "&Brand="
                + "&AndOr=1";
        try {
            String body = getJson(new URL(urlText), 7000, 9000);
            return BarcodeProductInfo.fromGdsImportJson(body, code);
        } catch (Exception exception) {
            BarcodeProductInfo info = new BarcodeProductInfo();
            info.barcode = code;
            info.gtin14 = gtin14;
            info.source = "中国商品信息服务平台";
            info.registrationMessage = "中国商品信息服务平台暂时不可用或访问过于频繁，请稍后重试。";
            return info;
        }
    }

    private static String getJson(URL url, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Android FoodExpiryTracker");
        connection.setRequestProperty("Referer", "https://www.gds.org.cn/");

        int status = connection.getResponseCode();
        InputStream inputStream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readFully(inputStream);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            throw new IllegalStateException("商品信息查询失败：" + status);
        }
        return body;
    }

    private static String readFully(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }
}
