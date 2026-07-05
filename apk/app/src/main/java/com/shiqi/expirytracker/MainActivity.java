package com.shiqi.expirytracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 4301;
    private static final int REQUEST_BARCODE_SCAN = 4302;
    private static final String PREFS_NAME = "shiqi_android_v0";
    private static final String PREF_NOTIFICATION_PERMISSION_PROMPTED = "notification_permission_prompted_v0";
    private static final String FOOD_ACTION_EDIT = "edit";
    private static final String FOOD_ACTION_DELETE = "delete";
    private static final String FOOD_ACTION_DELETE_CONFIRM = "delete_confirm";
    private static final String FOOD_ACTION_FINISH = "finish";
    private static final String FOOD_ACTION_RESTORE = "restore";

    private static final int COLOR_BG = Color.rgb(246, 247, 242);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(34, 42, 34);
    private static final int COLOR_MUTED = Color.rgb(91, 105, 91);
    private static final int COLOR_PRIMARY = Color.rgb(63, 111, 83);
    private static final int COLOR_DANGER = Color.rgb(159, 53, 46);
    private static final int COLOR_LINE = Color.rgb(222, 228, 218);

    private FoodStore store;
    private List<FoodItem> foods = new ArrayList<FoodItem>();
    private ScrollView mainScrollView;
    private LinearLayout stickySearchBar;
    private EditText pinnedSearchInput;
    private LinearLayout filterCard;
    private LinearLayout dailyBriefingContainer;
    private LinearLayout listContainer;
    private LinearLayout activeFilterBar;
    private LinearLayout activeFilterChipContainer;
    private LinearLayout statusFilterContainer;
    private Button categoryOpenButton;
    private Button backToTopButton;
    private TextView statsText;
    private TextView reminderStatusText;
    private Button reminderPermissionButton;
    private TextView activeFilterSummary;
    private EditText searchInput;
    private final List<String> selectedStatuses = new ArrayList<String>(FoodData.statusFilterValues());
    private final List<String> selectedCategories = new ArrayList<String>();
    private boolean statusFilterActive = false;
    private boolean categoryFilterActive = false;
    private String searchQuery = "";
    private String categorySearchQuery = "";
    private boolean activeFilterCollapsed = true;
    private boolean activeStatusAllExpanded = false;
    private boolean activeCategoryAllExpanded = false;
    private boolean syncingSearchText = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);

        store = new FoodStore(this);
        foods = new ArrayList<FoodItem>(store.loadFoods());
        buildScreen();
        renderFoods();
        setupReminderNotifications();
    }

    private void buildScreen() {
        FrameLayout screenRoot = new FrameLayout(this);
        screenRoot.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        screenRoot.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        stickySearchBar = new LinearLayout(this);
        stickySearchBar.setOrientation(LinearLayout.VERTICAL);
        stickySearchBar.setPadding(dp(14), dp(8), dp(14), dp(6));
        stickySearchBar.setBackgroundColor(COLOR_CARD);
        stickySearchBar.setVisibility(View.GONE);
        root.addView(stickySearchBar, matchWrap());

        pinnedSearchInput = input("", "搜索食品：中文、拼音或首字母", InputType.TYPE_CLASS_TEXT);
        pinnedSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                applySearchText(value == null ? "" : value.toString(), pinnedSearchInput);
            }
        });
        stickySearchBar.addView(pinnedSearchInput, matchWrap());

        activeFilterBar = new LinearLayout(this);
        activeFilterBar.setOrientation(LinearLayout.VERTICAL);
        activeFilterBar.setPadding(dp(14), dp(10), dp(14), dp(10));
        activeFilterBar.setBackground(rounded(COLOR_CARD, 0, COLOR_LINE));
        activeFilterBar.setVisibility(View.GONE);
        root.addView(activeFilterBar, matchWrap());

        mainScrollView = new ScrollView(this);
        mainScrollView.setFillViewport(false);
        mainScrollView.setBackgroundColor(COLOR_BG);
        root.addView(mainScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        mainScrollView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View view, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                updateBackToTopVisibility(scrollY);
                updateStickyFilterVisibility(scrollY);
            }
        });
        mainScrollView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    clearSearchFocusForUserScroll();
                }
                return false;
            }
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        mainScrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("食期管家", 28, COLOR_TEXT, Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView subtitle = text("本地安卓版", 14, COLOR_MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(4), 0, dp(10));
        content.addView(subtitle, matchWrap());

        TextView notice = text("当前数据保存在本机应用内，暂不支持多设备同步。", 14, COLOR_MUTED, Typeface.NORMAL);
        notice.setPadding(dp(12), dp(10), dp(12), dp(10));
        notice.setBackground(rounded(Color.rgb(239, 244, 235), dp(8), 0));
        content.addView(notice, withMargins(matchWrap(), 0, 0, 0, dp(14)));

        dailyBriefingContainer = new LinearLayout(this);
        dailyBriefingContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(dailyBriefingContainer, withMargins(matchWrap(), 0, 0, 0, dp(14)));

        content.addView(reminderCard(), withMargins(matchWrap(), 0, 0, 0, dp(14)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        content.addView(actionRow, matchWrap());

        Button addButton = button("新增食品", COLOR_PRIMARY);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFoodForm(null);
            }
        });
        actionRow.addView(addButton, withMargins(weightWrap(1), 0, 0, dp(8), 0));

        Button scanButton = button("扫码识别", Color.rgb(93, 111, 73));
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startBarcodeScanner();
            }
        });
        actionRow.addView(scanButton, weightWrap(1));

        statsText = text("", 15, COLOR_TEXT, Typeface.BOLD);
        statsText.setPadding(0, dp(16), 0, dp(8));
        content.addView(statsText, matchWrap());

        filterCard = card();
        content.addView(filterCard, withMargins(matchWrap(), 0, dp(4), 0, dp(12)));

        TextView filterTitle = text("筛选食品", 17, COLOR_TEXT, Typeface.BOLD);
        filterCard.addView(filterTitle, matchWrap());

        filterCard.addView(label("搜索食品"), withMargins(matchWrap(), 0, dp(10), 0, dp(4)));
        searchInput = input("", "输入中文、拼音或首字母", InputType.TYPE_CLASS_TEXT);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                applySearchText(value == null ? "" : value.toString(), searchInput);
            }
        });
        filterCard.addView(searchInput, matchWrap());

        TextView searchHint = text("搜索只会在当前状态和分类筛选结果内继续匹配。", 12, COLOR_MUTED, Typeface.NORMAL);
        searchHint.setPadding(0, dp(4), 0, dp(6));
        filterCard.addView(searchHint, matchWrap());

        filterCard.addView(label("状态多选"), withMargins(matchWrap(), 0, dp(8), 0, dp(4)));
        statusFilterContainer = new LinearLayout(this);
        statusFilterContainer.setOrientation(LinearLayout.VERTICAL);
        filterCard.addView(statusFilterContainer, matchWrap());

        filterCard.addView(label("分类筛选"), withMargins(matchWrap(), 0, dp(10), 0, dp(4)));
        categoryOpenButton = outlineButton("");
        categoryOpenButton.setGravity(android.view.Gravity.CENTER_VERTICAL);
        categoryOpenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCategoryPanel();
            }
        });
        filterCard.addView(categoryOpenButton, matchWrap());

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer, matchWrap());

        backToTopButton = new Button(this);
        backToTopButton.setText("↑");
        backToTopButton.setTextSize(24);
        backToTopButton.setTextColor(Color.WHITE);
        backToTopButton.setAllCaps(false);
        backToTopButton.setMinWidth(0);
        backToTopButton.setMinHeight(0);
        backToTopButton.setPadding(0, 0, 0, dp(3));
        backToTopButton.setBackground(oval(COLOR_PRIMARY, 0));
        backToTopButton.setVisibility(View.GONE);
        backToTopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scrollToTop();
            }
        });
        FrameLayout.LayoutParams backToTopParams = new FrameLayout.LayoutParams(dp(54), dp(54));
        backToTopParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.RIGHT;
        backToTopParams.setMargins(0, 0, dp(18), dp(18));
        screenRoot.addView(backToTopButton, backToTopParams);

        renderFilterControls();
        setContentView(screenRoot);
    }

    private View reminderCard() {
        LinearLayout card = card();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.addView(top, matchWrap());

        TextView title = text("手机提醒", 16, COLOR_TEXT, Typeface.BOLD);
        top.addView(title, weightWrap(1));

        reminderPermissionButton = outlineButton("开启提醒");
        reminderPermissionButton.setMinHeight(dp(36));
        reminderPermissionButton.setMinimumHeight(0);
        reminderPermissionButton.setPadding(dp(12), 0, dp(12), 0);
        reminderPermissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestNotificationPermission(true);
            }
        });
        top.addView(reminderPermissionButton, wrapWrap());

        reminderStatusText = text("", 13, COLOR_MUTED, Typeface.NORMAL);
        reminderStatusText.setLineSpacing(dp(2), 1.0f);
        reminderStatusText.setPadding(0, dp(8), 0, 0);
        card.addView(reminderStatusText, matchWrap());

        updateReminderStatus();
        return card;
    }

    private void setupReminderNotifications() {
        ReminderScheduler.scheduleDaily(this);
        requestNotificationPermission(false);
        updateReminderStatus();
    }

    private void requestNotificationPermission(boolean fromUserAction) {
        if (Build.VERSION.SDK_INT < 33) {
            ReminderScheduler.scheduleDaily(this);
            if (fromUserAction) {
                toast("手机提醒已开启");
            }
            updateReminderStatus();
            return;
        }

        if (ReminderScheduler.canPostNotifications(this)) {
            ReminderScheduler.scheduleDaily(this);
            if (fromUserAction) {
                toast("手机提醒已开启");
            }
            updateReminderStatus();
            return;
        }

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean prompted = preferences.getBoolean(PREF_NOTIFICATION_PERMISSION_PROMPTED, false);
        if (!fromUserAction && prompted) {
            updateReminderStatus();
            return;
        }

        preferences.edit().putBoolean(PREF_NOTIFICATION_PERMISSION_PROMPTED, true).apply();
        requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, REQUEST_NOTIFICATION_PERMISSION);
    }

    private void updateReminderStatus() {
        if (reminderStatusText == null || reminderPermissionButton == null) {
            return;
        }

        boolean allowed = ReminderScheduler.canPostNotifications(this);
        reminderPermissionButton.setVisibility(allowed ? View.GONE : View.VISIBLE);
        reminderStatusText.setText(allowed
                ? "已开启：" + ReminderScheduler.reminderTimeLabel() + "。已用完食品不会触发通知。"
                : "未开启：点击开启提醒并允许通知权限后，发送每日简报和今日到期提醒。");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateReminderStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ReminderScheduler.scheduleDaily(this);
            toast("手机提醒已开启");
        } else {
            toast("未获得通知权限，暂时无法发送手机提醒");
        }
        updateReminderStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_BARCODE_SCAN) {
            String barcode = data == null ? "" : data.getStringExtra("SCAN_RESULT");
            handleBarcodeValue(barcode);
        }
    }

    private void startBarcodeScanner() {
        Intent intent = new Intent(this, BarcodeScanActivity.class);
        startActivityForResult(intent, REQUEST_BARCODE_SCAN);
    }

    private void handleBarcodeValue(String value) {
        String barcode = BarcodeUtils.extractProductCode(value);
        if (!BarcodeUtils.isSupportedProductCode(barcode)) {
            toast("条码格式不正确，请检查数字和校验位");
            return;
        }

        queryBarcodeProduct(barcode);
    }

    private void queryBarcodeProduct(final String barcode) {
        toast("正在查询商品信息");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BarcodeProductInfo info = BarcodeLookupClient.query(barcode);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleBarcodeLookupResult(barcode, info);
                        }
                    });
                } catch (final Exception exception) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showBarcodeLookupError(barcode, exception);
                        }
                    });
                }
            }
        }).start();
    }

    private void handleBarcodeLookupResult(final String barcode, final BarcodeProductInfo info) {
        if (info == null || !info.found) {
            String message = "条码：" + barcode + "\n可以先手动新增食品，条码会放入备注。";
            if (info != null && info.registrationMessage.length() > 0) {
                message = message + "\n\n" + info.registrationMessage;
            }

            new AlertDialog.Builder(this)
                    .setTitle("未查询到商品信息")
                    .setMessage(message)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("手动新增", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            FoodItem draft = new FoodItem();
                            draft.notes = "条码：" + barcode;
                            showFoodForm(draft, false);
                        }
                    })
                    .show();
            return;
        }

        final String category = BarcodeCategoryClassifier.inferCategory(info);
        String message = info.summary()
                + "\n\n建议分类：" + FoodData.categoryLabel(category)
                + "\n\n请在下一步确认和补充日期后再保存。";

        new AlertDialog.Builder(this)
                .setTitle("识别到商品")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("填入新增表单", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        FoodItem draft = new FoodItem();
                        draft.name = info.displayName();
                        draft.category = category;
                        draft.unit = "件";
                        draft.notes = info.notes();
                        showFoodForm(draft, false);
                    }
                })
                .show();
    }

    private void showBarcodeLookupError(final String barcode, Exception exception) {
        String message = FoodItem.cleanText(exception.getMessage());
        if (message.length() == 0) {
            message = "网络或商品服务暂时不可用。";
        }

        new AlertDialog.Builder(this)
                .setTitle("查询失败")
                .setMessage(message + "\n\n条码：" + barcode)
                .setNegativeButton("取消", null)
                .setPositiveButton("手动新增", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        FoodItem draft = new FoodItem();
                        draft.notes = "条码：" + barcode;
                        showFoodForm(draft, false);
                    }
                })
                .show();
    }

    private void renderDailyBriefing() {
        if (dailyBriefingContainer == null) {
            return;
        }

        dailyBriefingContainer.removeAllViews();
        DailyBriefing briefing = ReminderPolicy.dailyBriefing(foods);

        LinearLayout card = card();
        TextView title = text("今日简报 · " + DailyBriefing.BRIEFING_TIME, 16, COLOR_TEXT, Typeface.BOLD);
        card.addView(title, matchWrap());

        if (briefing.isEmpty()) {
            TextView empty = text("今天没有需要特别处理的食品", 14, COLOR_MUTED, Typeface.NORMAL);
            empty.setPadding(0, dp(8), 0, 0);
            card.addView(empty, matchWrap());
        } else {
            addBriefingSection(card, "昨日过期", briefing.yesterdayExpired);
            addBriefingSection(card, "今日到期", briefing.todayDue);
            addBriefingSection(card, "临近保质期", briefing.upcoming);
        }

        dailyBriefingContainer.addView(card, matchWrap());
    }

    private void addBriefingSection(LinearLayout card, String title, List<DailyBriefing.Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        TextView line = text(title + "：" + briefingEntryText(entries), 14, COLOR_TEXT, Typeface.NORMAL);
        line.setLineSpacing(dp(2), 1.0f);
        line.setPadding(0, dp(8), 0, 0);
        card.addView(line, matchWrap());
    }

    private String briefingEntryText(List<DailyBriefing.Entry> entries) {
        List<String> values = new ArrayList<String>();
        int limit = Math.min(3, entries.size());
        for (int index = 0; index < limit; index++) {
            values.add(entries.get(index).text);
        }

        String text = joinTexts(values, "、");
        if (entries.size() > limit) {
            text += " 等 " + entries.size() + " 件";
        }
        return text;
    }

    private void renderFoods() {
        if (listContainer == null || statsText == null || activeFilterBar == null) {
            return;
        }

        normalizeFilterSelections();
        listContainer.removeAllViews();
        statsText.setText(buildStatsText());
        renderDailyBriefing();
        renderActiveFilterBar();

        List<FoodItem> visibleFoods = filteredSortedFoods();

        if (foods.isEmpty()) {
            listContainer.addView(emptyGuideCard(), matchWrap());
            return;
        }

        if (visibleFoods.isEmpty()) {
            String emptyText = FoodItem.cleanText(searchQuery).length() > 0
                    ? "当前筛选和搜索下没有食品"
                    : "当前筛选下没有食品";
            TextView empty = text(emptyText, 16, COLOR_MUTED, Typeface.NORMAL);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(dp(12), dp(26), dp(12), dp(26));
            empty.setBackground(rounded(COLOR_CARD, dp(8), COLOR_LINE));
            listContainer.addView(empty, matchWrap());
            return;
        }

        for (FoodItem food : visibleFoods) {
            listContainer.addView(foodCard(food), withMargins(matchWrap(), 0, 0, 0, dp(12)));
        }
    }

    private void renderFilterControls() {
        normalizeFilterSelections();
        renderStatusFilterButtons();
        renderCategoryFilterButtons();
        renderActiveFilterBar();
    }

    private void normalizeFilterSelections() {
        List<String> statusValues = FoodData.statusFilterValues();
        List<String> nextStatuses = new ArrayList<String>();
        for (String value : statusValues) {
            if (selectedStatuses.contains(value)) {
                nextStatuses.add(value);
            }
        }

        selectedStatuses.clear();
        if (statusFilterActive) {
            if (nextStatuses.size() >= statusValues.size()) {
                statusFilterActive = false;
                selectedStatuses.addAll(statusValues);
            } else {
                selectedStatuses.addAll(nextStatuses);
            }
        } else {
            selectedStatuses.addAll(statusValues);
        }

        List<Option> categoryOptions = FoodData.categoryFilterOptions(foods);
        List<String> nextCategories = new ArrayList<String>();
        for (Option option : categoryOptions) {
            if (FoodData.ALL.equals(option.value)) {
                continue;
            }
            if (selectedCategories.contains(option.value) && !nextCategories.contains(option.value)) {
                nextCategories.add(option.value);
            }
        }

        selectedCategories.clear();
        int availableCategoryCount = categoryOptions.size() - 1;
        if (categoryFilterActive) {
            if (availableCategoryCount > 0 && nextCategories.size() >= availableCategoryCount) {
                categoryFilterActive = false;
            } else {
                selectedCategories.addAll(nextCategories);
            }
        }
    }

    private void renderStatusFilterButtons() {
        if (statusFilterContainer == null) {
            return;
        }

        statusFilterContainer.removeAllViews();
        List<Option> options = new ArrayList<Option>();
        for (Option option : FoodData.STATUS_FILTERS) {
            if (!FoodData.ALL.equals(option.value)) {
                options.add(option);
            }
        }

        addFilterButtonGrid(statusFilterContainer, options, new FilterButtonBinder() {
            @Override
            public Button createButton(final Option option) {
                boolean selected = statusFilterActive
                        ? selectedStatuses.contains(option.value)
                        : !DateRules.STATUS_FINISHED.equals(option.value);
                Button button = filterButton(option.label, selected);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        toggleStatusFilter(option.value);
                    }
                });
                return button;
            }
        });
    }

    private void renderCategoryFilterButtons() {
        if (categoryOpenButton == null) {
            return;
        }

        categoryOpenButton.setText(buildCategoryOpenButtonText());
    }

    private void toggleCategoryPanel() {
        showCategoryFilterDialog();
    }

    private String buildCategoryOpenButtonText() {
        String categoryText = !categoryFilterActive
                ? "全部分类"
                : selectedCategories.isEmpty()
                        ? "未选分类"
                        : joinCategoryLabels(selectedCategories, FoodData.categoryFilterOptions(foods));
        return "当前分类：" + categoryText + "    选择分类";
    }

    private void showCategoryFilterDialog() {
        categorySearchQuery = "";

        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setPadding(dp(4), dp(4), dp(4), dp(4));

        final EditText dialogSearchInput = input("", "搜索分类：中文、拼音或首字母", InputType.TYPE_CLASS_TEXT);
        dialogContent.addView(dialogSearchInput, matchWrap());

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(0, dp(10), 0, dp(10));
        dialogContent.addView(tools, matchWrap());

        final LinearLayout optionContainer = new LinearLayout(this);
        optionContainer.setOrientation(LinearLayout.VERTICAL);

        ScrollView optionScroll = new ScrollView(this);
        optionScroll.addView(optionContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dialogContent.addView(optionScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(360)
        ));

        final Runnable[] refreshOptions = new Runnable[1];
        refreshOptions[0] = new Runnable() {
            @Override
            public void run() {
                renderCategoryDialogOptions(optionContainer, refreshOptions[0]);
            }
        };

        Button selectVisibleCategoryButton = outlineButton("全部选择");
        selectVisibleCategoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectVisibleCategories();
                refreshOptions[0].run();
            }
        });
        tools.addView(selectVisibleCategoryButton, withMargins(weightWrap(1), 0, 0, dp(8), 0));

        Button clearVisibleCategoryButton = outlineButton("取消选择");
        clearVisibleCategoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearVisibleCategories();
                refreshOptions[0].run();
            }
        });
        tools.addView(clearVisibleCategoryButton, weightWrap(1));

        dialogSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                categorySearchQuery = value == null ? "" : value.toString();
                refreshOptions[0].run();
            }
        });

        refreshOptions[0].run();

        new AlertDialog.Builder(this)
                .setTitle("选择分类")
                .setView(dialogContent)
                .setPositiveButton("完成", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        categorySearchQuery = "";
                        renderCategoryFilterButtons();
                    }
                })
                .show();
    }

    private void renderCategoryDialogOptions(final LinearLayout optionContainer, final Runnable refreshOptions) {
        optionContainer.removeAllViews();
        final List<Option> visibleOptions = visibleCategoryOptions();

        if (visibleOptions.isEmpty()) {
            TextView empty = text("没有匹配的分类", 14, COLOR_MUTED, Typeface.NORMAL);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            empty.setBackground(rounded(Color.rgb(249, 251, 247), dp(8), COLOR_LINE));
            optionContainer.addView(empty, matchWrap());
            return;
        }

        addFilterButtonGrid(optionContainer, visibleOptions, new FilterButtonBinder() {
            @Override
            public Button createButton(final Option option) {
                boolean active = !categoryFilterActive || selectedCategories.contains(option.value);
                Button button = filterButton(option.label, active);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        toggleCategoryFilter(option.value);
                        refreshOptions.run();
                    }
                });
                return button;
            }
        });
    }

    private List<Option> visibleCategoryOptions() {
        return visibleCategoryOptions(categorySearchQuery);
    }

    private List<Option> visibleCategoryOptions(String searchText) {
        List<Option> options = FoodData.categoryFilterOptions(foods);
        String query = FoodItem.cleanText(searchText);
        List<Option> categoryOnlyOptions = new ArrayList<Option>();
        for (Option option : options) {
            if (!FoodData.ALL.equals(option.value)) {
                categoryOnlyOptions.add(option);
            }
        }

        if (query.length() == 0) {
            return categoryOnlyOptions;
        }

        List<Option> visibleOptions = new ArrayList<Option>();
        for (Option option : categoryOnlyOptions) {
            if (FoodData.matchesCategorySearch(option, query)) {
                visibleOptions.add(option);
            }
        }
        return visibleOptions;
    }

    private List<String> categoryValues(List<Option> categoryOptions) {
        List<String> values = new ArrayList<String>();
        for (Option option : categoryOptions) {
            if (!FoodData.ALL.equals(option.value)) {
                values.add(option.value);
            }
        }
        return values;
    }

    private void selectVisibleCategories() {
        List<String> allValues = categoryValues(FoodData.categoryFilterOptions(foods));
        List<String> visibleValues = categoryValues(visibleCategoryOptions());

        if (visibleValues.isEmpty()) {
            return;
        }

        if (!categoryFilterActive) {
            renderCategoryFilterButtons();
            return;
        }

        for (String value : visibleValues) {
            if (!selectedCategories.contains(value)) {
                selectedCategories.add(value);
            }
        }

        if (selectedCategories.size() >= allValues.size()) {
            categoryFilterActive = false;
            selectedCategories.clear();
        } else {
            categoryFilterActive = true;
        }

        normalizeFilterSelections();
        renderCategoryFilterButtons();
        renderFoods();
    }

    private void clearVisibleCategories() {
        List<String> allValues = categoryValues(FoodData.categoryFilterOptions(foods));
        List<String> visibleValues = categoryValues(visibleCategoryOptions());

        if (visibleValues.isEmpty()) {
            return;
        }

        if (!categoryFilterActive) {
            categoryFilterActive = true;
            selectedCategories.addAll(allValues);
        }

        selectedCategories.removeAll(visibleValues);
        normalizeFilterSelections();
        renderCategoryFilterButtons();
        renderFoods();
    }

    private void toggleStatusFilter(String status) {
        if (!FoodData.isKnownStatusFilter(status)) {
            return;
        }

        if (!statusFilterActive && DateRules.STATUS_FINISHED.equals(status)) {
            statusFilterActive = true;
            selectedStatuses.clear();
            selectedStatuses.add(status);
        } else if (!statusFilterActive) {
            statusFilterActive = true;
            selectedStatuses.clear();
            selectedStatuses.addAll(FoodData.statusFilterValues());
            selectedStatuses.remove(status);
        } else if (selectedStatuses.contains(status)) {
            selectedStatuses.remove(status);
        } else {
            selectedStatuses.add(status);
        }

        activeStatusAllExpanded = false;
        normalizeFilterSelections();
        renderStatusFilterButtons();
        renderFoods();
    }

    private void removeStatusFilter(String status) {
        if (!FoodData.isKnownStatusFilter(status)) {
            return;
        }

        if (!statusFilterActive) {
            statusFilterActive = true;
            selectedStatuses.clear();
            selectedStatuses.addAll(FoodData.statusFilterValues());
        }
        selectedStatuses.remove(status);
        activeStatusAllExpanded = false;
        normalizeFilterSelections();
        renderStatusFilterButtons();
        renderFoods();
    }

    private void toggleCategoryFilter(String category) {
        List<Option> categoryOptions = FoodData.categoryFilterOptions(foods);
        if (!FoodData.isKnownCategoryFilter(category, categoryOptions)) {
            return;
        }

        if (FoodData.ALL.equals(category)) {
            categoryFilterActive = false;
            selectedCategories.clear();
        } else if (!categoryFilterActive) {
            categoryFilterActive = true;
            selectedCategories.addAll(categoryValues(categoryOptions));
            selectedCategories.remove(category);
        } else if (selectedCategories.contains(category)) {
            selectedCategories.remove(category);
        } else {
            selectedCategories.add(category);
        }

        activeCategoryAllExpanded = false;
        normalizeFilterSelections();
        renderCategoryFilterButtons();
        renderFoods();
    }

    private void removeCategoryFilter(String category) {
        if (!categoryFilterActive) {
            categoryFilterActive = true;
            selectedCategories.clear();
            selectedCategories.addAll(categoryValues(FoodData.categoryFilterOptions(foods)));
        }
        selectedCategories.remove(category);
        activeCategoryAllExpanded = false;
        normalizeFilterSelections();
        renderCategoryFilterButtons();
        renderFoods();
    }

    private void clearFoodSearch() {
        applySearchText("", null);
    }

    private void applySearchText(String value, EditText source) {
        if (syncingSearchText) {
            return;
        }

        syncingSearchText = true;
        searchQuery = value == null ? "" : value;
        if (searchInput != null && searchInput != source && !searchInput.getText().toString().equals(searchQuery)) {
            searchInput.setText(searchQuery);
        }
        if (pinnedSearchInput != null && pinnedSearchInput != source && !pinnedSearchInput.getText().toString().equals(searchQuery)) {
            pinnedSearchInput.setText(searchQuery);
        }
        syncingSearchText = false;
        renderFoods();
    }

    private void renderActiveFilterBar() {
        if (activeFilterBar == null) {
            return;
        }

        activeFilterBar.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        activeFilterBar.addView(header, matchWrap());

        TextView title = text("当前筛选", 14, COLOR_TEXT, Typeface.BOLD);
        header.addView(title, weightWrap(1));

        TextView resultText = text("共 " + filteredSortedFoods().size() + " 项", 12, COLOR_MUTED, Typeface.BOLD);
        header.addView(resultText, withMargins(wrapWrap(), 0, 0, dp(8), 0));

        Button toggleButton = chipButton(activeFilterCollapsed ? "展开" : "收起", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activeFilterCollapsed = !activeFilterCollapsed;
                if (activeFilterCollapsed) {
                    activeStatusAllExpanded = false;
                    activeCategoryAllExpanded = false;
                }
                renderActiveFilterBar();
            }
        });
        header.addView(toggleButton, wrapWrap());

        activeFilterSummary = text(buildActiveFilterSummary(), 12, COLOR_MUTED, Typeface.NORMAL);
        activeFilterSummary.setPadding(0, dp(4), 0, dp(6));
        activeFilterSummary.setSingleLine(activeFilterCollapsed);
        activeFilterSummary.setEllipsize(activeFilterCollapsed ? TextUtils.TruncateAt.END : null);
        activeFilterBar.addView(activeFilterSummary, matchWrap());

        if (activeFilterCollapsed) {
            return;
        }

        activeFilterChipContainer = new LinearLayout(this);
        activeFilterChipContainer.setOrientation(LinearLayout.VERTICAL);
        activeFilterBar.addView(activeFilterChipContainer, matchWrap());
        renderActiveFilterChips();
    }

    private void renderActiveFilterChips() {
        if (activeFilterChipContainer == null) {
            return;
        }

        List<ChipSpec> chips = new ArrayList<ChipSpec>();
        if (!statusFilterActive) {
            if (activeStatusAllExpanded) {
                for (final String status : FoodData.statusFilterValues()) {
                    if (DateRules.STATUS_FINISHED.equals(status)) {
                        continue;
                    }
                    chips.add(new ChipSpec(FoodData.labelFor(FoodData.STATUS_FILTERS, status, status) + " x", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            removeStatusFilter(status);
                        }
                    }, null));
                }
            } else {
                chips.add(new ChipSpec("在库全部", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        activeStatusAllExpanded = true;
                        renderActiveFilterBar();
                    }
                }, null));
            }
        } else {
            if (selectedStatuses.isEmpty()) {
                chips.add(new ChipSpec("未选状态", null, null));
            } else {
                for (final String status : selectedStatuses) {
                    chips.add(new ChipSpec(FoodData.labelFor(FoodData.STATUS_FILTERS, status, status) + " x", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            removeStatusFilter(status);
                        }
                    }, null));
                }
            }
        }

        if (!categoryFilterActive) {
            if (activeCategoryAllExpanded) {
                final List<Option> categoryOptions = FoodData.categoryFilterOptions(foods);
                for (final Option option : categoryOptions) {
                    if (FoodData.ALL.equals(option.value)) {
                        continue;
                    }

                    chips.add(new ChipSpec(option.label + " x", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            toggleCategoryFilter(option.value);
                        }
                    }, null));
                }
            } else {
                chips.add(new ChipSpec("全部分类", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        activeCategoryAllExpanded = true;
                        renderActiveFilterBar();
                    }
                }, null));
            }
        } else {
            final List<Option> categoryOptions = FoodData.categoryFilterOptions(foods);
            if (selectedCategories.isEmpty()) {
                chips.add(new ChipSpec("未选分类", null, null));
            } else {
                for (final String category : selectedCategories) {
                    chips.add(new ChipSpec(categoryLabel(category, categoryOptions) + " x", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            removeCategoryFilter(category);
                        }
                    }, null));
                }
            }
        }

        addChipGrid(activeFilterChipContainer, chips);
    }

    private String buildActiveFilterSummary() {
        String statusText = !statusFilterActive
                ? "在库全部"
                : selectedStatuses.isEmpty()
                        ? "未选状态"
                        : joinStatusLabels(selectedStatuses);
        String categoryText = !categoryFilterActive
                ? "全部分类"
                : selectedCategories.isEmpty()
                        ? "未选分类"
                        : joinCategoryLabels(selectedCategories, FoodData.categoryFilterOptions(foods));
        String query = FoodItem.cleanText(searchQuery);
        if (query.length() == 0) {
            return "状态：" + statusText + "；分类：" + categoryText;
        }
        return "状态：" + statusText + "；分类：" + categoryText + "；搜索：" + query;
    }

    private boolean allStatusesSelected() {
        return selectedStatuses.size() == FoodData.statusFilterValues().size();
    }

    private String joinStatusLabels(List<String> values) {
        List<String> labels = new ArrayList<String>();
        for (String value : values) {
            labels.add(FoodData.labelFor(FoodData.STATUS_FILTERS, value, value));
        }
        return joinLabels(labels);
    }

    private String joinCategoryLabels(List<String> values, List<Option> categoryOptions) {
        List<String> labels = new ArrayList<String>();
        for (String value : values) {
            labels.add(categoryLabel(value, categoryOptions));
        }
        return joinLabels(labels);
    }

    private String categoryLabel(String value, List<Option> categoryOptions) {
        for (Option option : categoryOptions) {
            if (option.value.equals(value)) {
                return option.label;
            }
        }
        return FoodData.categoryLabel(value);
    }

    private String joinLabels(List<String> labels) {
        StringBuilder builder = new StringBuilder();
        for (String label : labels) {
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(label);
        }
        return builder.length() == 0 ? "全部" : builder.toString();
    }

    private String joinTexts(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(separator);
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    private void addFilterButtonGrid(LinearLayout container, List<Option> options, FilterButtonBinder binder) {
        LinearLayout row = null;
        for (int index = 0; index < options.size(); index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                int bottom = index + 2 >= options.size() ? 0 : dp(8);
                container.addView(row, withMargins(matchWrap(), 0, 0, 0, bottom));
            }

            Button button = binder.createButton(options.get(index));
            int right = index % 2 == 0 && index + 1 < options.size() ? dp(8) : 0;
            row.addView(button, withMargins(weightWrap(1), 0, 0, right, 0));
        }
    }

    private void addChipGrid(LinearLayout container, List<ChipSpec> chips) {
        container.removeAllViews();

        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setFillViewport(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scrollView.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        container.addView(scrollView, matchWrap());

        for (int index = 0; index < chips.size(); index++) {
            ChipSpec chip = chips.get(index);
            Button button = chipButton(chip.label, chip.listener);
            row.addView(button, withMargins(wrapWrap(), 0, 0, dp(6), 0));
        }
    }

    private Button filterButton(String value, boolean selected) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        button.setTextColor(selected ? Color.WHITE : COLOR_PRIMARY);
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? COLOR_PRIMARY : Color.rgb(238, 244, 235)));
        return button;
    }

    private Button chipButton(String value, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(30));
        button.setMinimumHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setTextColor(COLOR_PRIMARY);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(231, 243, 233)));
        if (listener != null) {
            button.setOnClickListener(listener);
        }
        return button;
    }

    private String buildStatsText() {
        int active = 0;
        int today = 0;
        int soon = 0;
        int expired = 0;
        int finished = 0;
        for (FoodItem food : foods) {
            if (food.isFinished) {
                finished++;
                continue;
            }

            active++;
            String status = DateRules.getExpiryStatus(food.expiryDate);
            if (DateRules.STATUS_TODAY.equals(status)) {
                today++;
            }
            if (DateRules.STATUS_SOON.equals(status)) {
                soon++;
            }
            if (DateRules.STATUS_EXPIRED.equals(status)) {
                expired++;
            }
        }

        return "在库 " + active + " 件 · 今日到期 " + today
                + " · 临期 " + soon
                + " · 已过期 " + expired
                + " · 已用完 " + finished;
    }

    private interface FilterButtonBinder {
        Button createButton(Option option);
    }

    private static final class ChipSpec {
        final String label;
        final View.OnClickListener listener;
        final String value;

        ChipSpec(String label, View.OnClickListener listener, String value) {
            this.label = label;
            this.listener = listener;
            this.value = value;
        }
    }

    private final class FoodActionClickListener implements View.OnClickListener {
        private final FoodItem food;
        private final String action;

        FoodActionClickListener(FoodItem food, String action) {
            this.food = food;
            this.action = action;
        }

        @Override
        public void onClick(View view) {
            runFoodAction(food, action, false);
        }
    }

    private final class FoodDialogActionListener implements DialogInterface.OnClickListener {
        private final FoodItem food;
        private final String action;

        FoodDialogActionListener(FoodItem food, String action) {
            this.food = food;
            this.action = action;
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int which) {
            runFoodAction(food, action, true);
        }
    }

    private void runFoodAction(FoodItem food, String action, boolean fromDialog) {
        if (FOOD_ACTION_EDIT.equals(action)) {
            showFoodForm(food);
        } else if (FOOD_ACTION_DELETE_CONFIRM.equals(action)) {
            confirmDelete(food);
        } else if (FOOD_ACTION_DELETE.equals(action)) {
            deleteFood(food);
        } else if (FOOD_ACTION_FINISH.equals(action)) {
            if (fromDialog) {
                markFoodFinished(food);
            } else {
                confirmFinishFood(food);
            }
        } else if (FOOD_ACTION_RESTORE.equals(action)) {
            if (fromDialog) {
                restoreFood(food);
            } else {
                confirmRestoreFood(food);
            }
        }
    }

    private List<FoodItem> filteredSortedFoods() {
        List<FoodItem> result = new ArrayList<FoodItem>();
        for (FoodItem food : foods) {
            String status = foodStatus(food);
            boolean statusMatches = statusFilterActive
                    ? selectedStatuses.contains(status)
                    : !food.isFinished;
            boolean categoryMatches = !categoryFilterActive || selectedCategories.contains(food.category);
            if (statusMatches && categoryMatches && FoodData.matchesFoodSearch(food, searchQuery)) {
                result.add(food);
            }
        }

        Collections.sort(result, new Comparator<FoodItem>() {
            @Override
            public int compare(FoodItem left, FoodItem right) {
                String leftStatus = foodStatus(left);
                String rightStatus = foodStatus(right);
                int statusCompare = statusRank(leftStatus) - statusRank(rightStatus);
                if (statusCompare != 0) {
                    return statusCompare;
                }

                String query = FoodItem.cleanText(searchQuery);
                if (query.length() > 0) {
                    int leftSearchRank = FoodData.foodSearchRank(left, query);
                    int rightSearchRank = FoodData.foodSearchRank(right, query);
                    if (leftSearchRank != rightSearchRank) {
                        return leftSearchRank - rightSearchRank;
                    }
                }

                boolean leftValid = DateRules.isValidDateString(left.expiryDate);
                boolean rightValid = DateRules.isValidDateString(right.expiryDate);

                if (leftValid && rightValid) {
                    return left.expiryDate.compareTo(right.expiryDate);
                }

                if (leftValid) {
                    return -1;
                }

                if (rightValid) {
                    return 1;
                }

                if (left.isFinished && right.isFinished) {
                    return right.finishedAt.compareTo(left.finishedAt);
                }

                return left.name.compareTo(right.name);
            }
        });

        return result;
    }

    private String foodStatus(FoodItem food) {
        return food.isFinished ? DateRules.STATUS_FINISHED : DateRules.getExpiryStatus(food.expiryDate);
    }

    private int statusRank(String status) {
        if (DateRules.STATUS_EXPIRED.equals(status)) {
            return 0;
        }
        if (DateRules.STATUS_TODAY.equals(status)) {
            return 1;
        }
        if (DateRules.STATUS_SOON.equals(status)) {
            return 2;
        }
        if (DateRules.STATUS_NORMAL.equals(status)) {
            return 3;
        }
        if (DateRules.STATUS_UNKNOWN.equals(status)) {
            return 4;
        }
        return 5;
    }

    private View emptyGuideCard() {
        LinearLayout card = card();
        TextView heading = text("还没有食品记录", 20, COLOR_TEXT, Typeface.BOLD);
        card.addView(heading, matchWrap());

        TextView copy = text("可以先添加一件食品。不知道生产日期也没关系，可以直接填写最终可食用日期。食品会按最终可食用日期自动排序。", 15, COLOR_MUTED, Typeface.NORMAL);
        copy.setLineSpacing(dp(2), 1.0f);
        copy.setPadding(0, dp(8), 0, dp(12));
        card.addView(copy, matchWrap());

        Button first = button("新增第一件食品", COLOR_PRIMARY);
        first.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFoodForm(null);
            }
        });
        card.addView(first, matchWrap());

        return card;
    }

    private View foodCard(final FoodItem food) {
        final ReminderPlan reminderPlan = ReminderPolicy.planFor(food);
        LinearLayout card = card();
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFoodDetail(food);
            }
        });

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.addView(top, matchWrap());

        TextView name = text(food.name, 19, COLOR_TEXT, Typeface.BOLD);
        top.addView(name, weightWrap(1));

        TextView status = text(FoodData.statusLabel(foodStatus(food)), 13, statusColor(food), Typeface.BOLD);
        status.setGravity(android.view.Gravity.CENTER);
        status.setPadding(dp(8), dp(4), dp(8), dp(4));
        status.setBackground(rounded(statusBgColor(food), dp(12), 0));
        top.addView(status, wrapWrap());

        TextView meta = text(
                FoodData.categoryLabel(food.category) + " · " +
                        FoodData.storageLabel(food.storageMethod) + " · 剩余 " +
                        formatNumber(food.remainingQuantity) + "/" + formatNumber(food.quantity) + " " + displayUnit(food.unit),
                14,
                COLOR_MUTED,
                Typeface.NORMAL
        );
        meta.setPadding(0, dp(8), 0, 0);
        card.addView(meta, matchWrap());

        String dateLine = isNoExpiryFood(food)
                ? "已生产时长：" + DateRules.productionAgeLabel(food.productionDate)
                : "最终可食用日期：" + food.expiryDate;
        TextView expiry = text(dateLine, 15, COLOR_TEXT, Typeface.NORMAL);
        expiry.setPadding(0, dp(8), 0, dp(6));
        card.addView(expiry, matchWrap());

        TextView reminderHint = text(reminderPlan.cardHint, 14, food.isFinished ? COLOR_MUTED : COLOR_PRIMARY, Typeface.BOLD);
        reminderHint.setLineSpacing(dp(2), 1.0f);
        reminderHint.setPadding(0, 0, 0, dp(10));
        card.addView(reminderHint, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(actions, matchWrap());

        if (food.isFinished) {
            Button restore = outlineButton("恢复提醒");
            restore.setOnClickListener(new FoodActionClickListener(food, FOOD_ACTION_RESTORE));
            actions.addView(restore, withMargins(weightWrap(1), 0, 0, dp(8), 0));

            Button delete = outlineButton("删除");
            delete.setTextColor(COLOR_DANGER);
            delete.setOnClickListener(new FoodActionClickListener(food, FOOD_ACTION_DELETE_CONFIRM));
            actions.addView(delete, weightWrap(1));
        } else {
            Button edit = outlineButton("编辑");
            edit.setOnClickListener(new FoodActionClickListener(food, FOOD_ACTION_EDIT));
            actions.addView(edit, withMargins(weightWrap(1), 0, 0, dp(8), 0));

            Button finish = outlineButton("已用完");
            finish.setOnClickListener(new FoodActionClickListener(food, FOOD_ACTION_FINISH));
            actions.addView(finish, weightWrap(1));

            LinearLayout deleteRow = new LinearLayout(this);
            deleteRow.setOrientation(LinearLayout.HORIZONTAL);
            card.addView(deleteRow, withMargins(matchWrap(), 0, dp(8), 0, 0));

            Button delete = outlineButton("删除");
            delete.setTextColor(COLOR_DANGER);
            delete.setOnClickListener(new FoodActionClickListener(food, FOOD_ACTION_DELETE_CONFIRM));
            deleteRow.addView(delete, weightWrap(1));
        }
        return card;
    }

    private int statusColor(FoodItem food) {
        String status = foodStatus(food);
        if (DateRules.STATUS_EXPIRED.equals(status)) {
            return COLOR_DANGER;
        }
        if (DateRules.STATUS_TODAY.equals(status)) {
            return Color.rgb(152, 86, 26);
        }
        if (DateRules.STATUS_SOON.equals(status)) {
            return Color.rgb(136, 112, 31);
        }
        if (DateRules.STATUS_UNKNOWN.equals(status)) {
            return Color.rgb(74, 80, 89);
        }
        if (DateRules.STATUS_FINISHED.equals(status)) {
            return Color.rgb(74, 80, 89);
        }
        return COLOR_PRIMARY;
    }

    private int statusBgColor(FoodItem food) {
        String status = foodStatus(food);
        if (DateRules.STATUS_EXPIRED.equals(status)) {
            return Color.rgb(253, 235, 232);
        }
        if (DateRules.STATUS_TODAY.equals(status)) {
            return Color.rgb(255, 241, 220);
        }
        if (DateRules.STATUS_SOON.equals(status)) {
            return Color.rgb(250, 244, 211);
        }
        if (DateRules.STATUS_UNKNOWN.equals(status)) {
            return Color.rgb(241, 243, 246);
        }
        if (DateRules.STATUS_FINISHED.equals(status)) {
            return Color.rgb(241, 243, 246);
        }
        return Color.rgb(231, 241, 232);
    }

    private void showFoodDetail(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle(food.name)
                .setMessage(detailText(food))
                .setPositiveButton("编辑", new FoodDialogActionListener(food, FOOD_ACTION_EDIT))
                .setNeutralButton("删除", new FoodDialogActionListener(food, FOOD_ACTION_DELETE_CONFIRM))
                .setNegativeButton("关闭", null)
                .show();
    }

    private String detailText(FoodItem food) {
        ReminderPlan reminderPlan = ReminderPolicy.planFor(food);
        StringBuilder builder = new StringBuilder();
        builder.append("分类：").append(FoodData.categoryLabel(food.category)).append('\n');
        builder.append("状态：").append(FoodData.statusLabel(foodStatus(food))).append('\n');
        builder.append("数量：").append(formatNumber(food.quantity)).append(' ').append(displayUnit(food.unit)).append('\n');
        builder.append("剩余：").append(formatNumber(food.remainingQuantity)).append(' ').append(displayUnit(food.unit)).append('\n');
        builder.append("保存方式：").append(FoodData.storageLabel(food.storageMethod)).append('\n');
        builder.append("生产日期：").append(emptyFallback(food.productionDate, "未填写")).append('\n');
        builder.append("保质期：");
        if (isNoExpiryFood(food)) {
            builder.append("无过期时间");
        } else if (food.shelfLifeValue == null) {
            builder.append("未填写");
        } else {
            builder.append(food.shelfLifeValue).append(FoodData.shelfLifeUnitLabel(food.shelfLifeUnit));
        }
        builder.append('\n');
        if (isNoExpiryFood(food)) {
            builder.append("已生产时长：").append(DateRules.productionAgeLabel(food.productionDate)).append('\n');
            builder.append("最终可食用日期：无过期时间").append('\n');
        } else {
            builder.append("最终可食用日期：").append(food.expiryDate).append('\n');
        }
        if (food.isFinished) {
            builder.append("已用完时间：").append(emptyFallback(food.finishedAt, "未记录")).append('\n');
            builder.append("提醒：不再提醒").append('\n');
        } else if (reminderPlan.enabled) {
            builder.append("提醒等级：").append(reminderPlan.priorityBand)
                    .append("（").append(ReminderPolicy.formattedScore(reminderPlan.priorityScore)).append("）").append('\n');
            builder.append("风险类型：").append(reminderPlan.riskLabel)
                    .append("（").append(reminderPlan.riskReason).append("）").append('\n');
            builder.append("下一次提醒：").append(reminderPlan.detailAdvice).append('\n');
            if (!reminderPlan.dueDayHours.isEmpty()) {
                builder.append("今日提醒时间：").append(joinTexts(reminderPlan.dueDayHours, "、")).append('\n');
            }
        } else {
            builder.append(reminderPlan.disabledReason).append('\n');
        }
        builder.append("日期来源：").append(dateSourceLabel(food.dateSource)).append('\n');
        builder.append("备注：").append(emptyFallback(food.notes, "暂无备注"));
        return builder.toString();
    }

    private boolean isNoExpiryFood(FoodItem food) {
        if (food == null) {
            return false;
        }
        if ("none".equals(food.dateSource)) {
            return true;
        }
        return FoodItem.cleanText(food.expiryDate).length() == 0
                && DateRules.isValidDateString(food.productionDate)
                && food.shelfLifeValue == null;
    }

    private String dateSourceLabel(String dateSource) {
        if ("calculated".equals(dateSource)) {
            return "自动计算";
        }
        if ("none".equals(dateSource)) {
            return "无过期时间";
        }
        return "手动填写";
    }

    private void showFoodForm(final FoodItem editingFood) {
        showFoodForm(editingFood, editingFood != null);
    }

    private void showFoodForm(final FoodItem sourceFood, final boolean isEdit) {
        final FoodItem draft = sourceFood == null ? new FoodItem() : sourceFood.copy();

        final EditText nameInput = input(draft.name, "食品名称", InputType.TYPE_CLASS_TEXT);
        final Spinner categoryInput = spinner(FoodData.CATEGORIES);
        categoryInput.setSelection(indexOf(FoodData.CATEGORIES, draft.category, "other"));

        final EditText quantityInput = input(isEdit ? formatNumber(draft.quantity) : "", "数量", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText remainingInput = input(isEdit ? formatNumber(draft.remainingQuantity) : "", "剩余数量", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText unitInput = input(isEdit ? displayUnit(draft.unit) : "", "单位，例如 盒、瓶、袋", InputType.TYPE_CLASS_TEXT);

        final Spinner storageInput = spinner(FoodData.STORAGE_METHODS);
        storageInput.setSelection(indexOf(FoodData.STORAGE_METHODS, draft.storageMethod, "room_temp"));

        final EditText productionDateInput = dateInput(draft.productionDate, "选择生产日期");
        final EditText shelfLifeInput = input(draft.shelfLifeValue == null ? "" : String.valueOf(draft.shelfLifeValue), "保质期数值", InputType.TYPE_CLASS_NUMBER);
        final Spinner shelfLifeUnitInput = spinner(FoodData.SHELF_LIFE_UNITS);
        shelfLifeUnitInput.setSelection(indexOf(FoodData.SHELF_LIFE_UNITS, draft.shelfLifeUnit, "day"));

        final EditText expiryDateInput = dateInput(draft.expiryDate, "选择最终可食用日期");
        final CheckBox noExpiryInput = new CheckBox(this);
        noExpiryInput.setText("无过期时间");
        noExpiryInput.setTextColor(COLOR_TEXT);
        noExpiryInput.setTextSize(14);
        noExpiryInput.setPadding(0, dp(8), 0, dp(4));
        noExpiryInput.setChecked(isNoExpiryFood(draft));

        final EditText notesInput = input(draft.notes, "备注", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notesInput.setMinLines(2);
        notesInput.setGravity(android.view.Gravity.TOP);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(2), dp(2), dp(2), dp(8));

        TextView intro = text(
                isEdit ? "调整食品信息后会重新按最终可食用日期排序。" : "记录食品后，会按最终可食用日期自动排序。",
                13,
                COLOR_MUTED,
                Typeface.NORMAL
        );
        intro.setPadding(dp(12), dp(10), dp(12), dp(10));
        intro.setBackground(rounded(Color.rgb(239, 244, 235), dp(8), 0));
        form.addView(intro, withMargins(matchWrap(), 0, 0, 0, dp(10)));

        LinearLayout basicSection = formSection("基本信息");
        addFormField(basicSection, "食品名称", nameInput);
        addFormField(basicSection, "分类", categoryInput);

        LinearLayout amountRow = formRow();
        amountRow.addView(formField("数量", quantityInput), withMargins(weightWrap(1), 0, 0, dp(8), 0));
        amountRow.addView(formField("剩余数量", remainingInput), weightWrap(1));
        basicSection.addView(amountRow, matchWrap());

        addFormField(basicSection, "单位", unitInput);
        addFormField(basicSection, "保存方式", storageInput);
        form.addView(basicSection, withMargins(matchWrap(), 0, 0, 0, dp(10)));

        LinearLayout dateSection = formSection("日期信息");
        addFormField(dateSection, "生产日期", productionDateInput);
        dateSection.addView(noExpiryInput, matchWrap());

        final TextView productionAgePreview = text("", 13, COLOR_PRIMARY, Typeface.BOLD);
        productionAgePreview.setPadding(dp(10), dp(8), dp(10), dp(8));
        productionAgePreview.setBackground(rounded(Color.rgb(239, 244, 235), dp(8), 0));
        dateSection.addView(productionAgePreview, withMargins(matchWrap(), 0, dp(4), 0, dp(4)));

        final LinearLayout shelfLifeRow = formRow();
        shelfLifeRow.addView(formField("保质期数值", shelfLifeInput), withMargins(weightWrap(1), 0, 0, dp(8), 0));
        shelfLifeRow.addView(formField("保质期单位", shelfLifeUnitInput), weightWrap(1));
        dateSection.addView(shelfLifeRow, matchWrap());

        final LinearLayout expiryDateField = formField("最终可食用日期", expiryDateInput);
        dateSection.addView(expiryDateField, matchWrap());

        final TextView hint = text("可直接选择最终可食用日期；如果不选择，则使用生产日期 + 保质期计算。", 12, COLOR_MUTED, Typeface.NORMAL);
        hint.setPadding(0, dp(8), 0, 0);
        dateSection.addView(hint, matchWrap());

        final Runnable updateNoExpiryViews = new Runnable() {
            @Override
            public void run() {
                boolean noExpiry = noExpiryInput.isChecked();
                shelfLifeRow.setVisibility(noExpiry ? View.GONE : View.VISIBLE);
                expiryDateField.setVisibility(noExpiry ? View.GONE : View.VISIBLE);
                productionAgePreview.setVisibility(noExpiry ? View.VISIBLE : View.GONE);
                productionAgePreview.setText("已生产时长：" + DateRules.productionAgeLabel(clean(productionDateInput)));
                hint.setText(noExpiry
                        ? "无过期时间食品不会参与到期提醒；保存时只需要生产日期。"
                        : "可直接选择最终可食用日期；如果不选择，则使用生产日期 + 保质期计算。");
            }
        };
        noExpiryInput.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                updateNoExpiryViews.run();
            }
        });
        productionDateInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                if (noExpiryInput.isChecked()) {
                    updateNoExpiryViews.run();
                }
            }
        });
        updateNoExpiryViews.run();

        form.addView(dateSection, withMargins(matchWrap(), 0, 0, 0, dp(10)));

        LinearLayout noteSection = formSection("备注");
        addFormField(noteSection, "备注", notesInput);
        form.addView(noteSection, matchWrap());

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(form);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isEdit ? "编辑食品" : "新增食品")
                .setView(scrollView)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        FoodItem saved = buildFoodFromForm(
                                isEdit ? sourceFood : null,
                                nameInput,
                                categoryInput,
                                quantityInput,
                                remainingInput,
                                unitInput,
                                storageInput,
                                productionDateInput,
                                noExpiryInput,
                                shelfLifeInput,
                                shelfLifeUnitInput,
                                expiryDateInput,
                                notesInput
                        );

                        if (saved == null) {
                            return;
                        }

                        if (isEdit) {
                            replaceFood(saved);
                        } else {
                            foods.add(0, saved);
                        }

                        store.saveFoods(foods);
                        ReminderScheduler.scheduleDaily(MainActivity.this);
                        renderFilterControls();
                        renderFoods();
                        dialog.dismiss();
                        toast(isEdit ? "已更新食品" : "已新增食品");
                    }
                });
            }
        });

        dialog.show();
    }

    private FoodItem buildFoodFromForm(
            FoodItem original,
            EditText nameInput,
            Spinner categoryInput,
            EditText quantityInput,
            EditText remainingInput,
            EditText unitInput,
            Spinner storageInput,
            EditText productionDateInput,
            CheckBox noExpiryInput,
            EditText shelfLifeInput,
            Spinner shelfLifeUnitInput,
            EditText expiryDateInput,
            EditText notesInput
    ) {
        String name = clean(nameInput);
        if (name.length() == 0) {
            toast("请填写食品名称");
            return null;
        }

        Double quantity = parseNumber(clean(quantityInput), 1.0);
        Double remaining = parseNumber(clean(remainingInput), quantity);
        if (quantity == null || quantity.doubleValue() < 0) {
            toast("数量不能小于 0");
            return null;
        }
        if (remaining == null || remaining.doubleValue() < 0) {
            toast("剩余数量不能小于 0");
            return null;
        }
        if (remaining.doubleValue() > quantity.doubleValue()) {
            toast("剩余数量不能大于总数量");
            return null;
        }

        String productionDate = clean(productionDateInput);
        String shelfLifeText = clean(shelfLifeInput);
        Integer shelfLifeValue = shelfLifeText.length() == 0 ? null : parsePositiveInteger(shelfLifeText);
        if (shelfLifeText.length() > 0 && shelfLifeValue == null) {
            toast("保质期必须是正整数");
            return null;
        }

        String shelfLifeUnit = selectedOption(FoodData.SHELF_LIFE_UNITS, shelfLifeUnitInput);
        String manualExpiryDate = clean(expiryDateInput);
        String expiryDate;
        String dateSource;

        if (noExpiryInput.isChecked()) {
            if (!DateRules.isValidDateString(productionDate)) {
                toast("无过期时间食品必须选择生产日期");
                return null;
            }
            shelfLifeValue = null;
            shelfLifeUnit = "";
            expiryDate = "";
            dateSource = "none";
        } else if (manualExpiryDate.length() > 0) {
            if (!DateRules.isValidDateString(manualExpiryDate)) {
                toast("最终可食用日期格式不正确，请重新选择日期");
                return null;
            }
            expiryDate = manualExpiryDate;
            dateSource = "manual";
        } else {
            expiryDate = DateRules.addShelfLife(productionDate, shelfLifeValue, shelfLifeUnit);
            if (expiryDate.length() == 0) {
                toast("请填写最终可食用日期，或填写生产日期和保质期");
                return null;
            }
            dateSource = "calculated";
        }

        String now = DateRules.nowIsoLike();
        FoodItem item = original == null ? new FoodItem() : original.copy();
        item.id = original == null ? "food_" + System.currentTimeMillis() : original.id;
        item.name = name;
        item.category = selectedOption(FoodData.CATEGORIES, categoryInput);
        item.quantity = quantity.doubleValue();
        item.remainingQuantity = remaining.doubleValue();
        item.unit = clean(unitInput).length() > 0 ? clean(unitInput) : "件";
        item.storageMethod = selectedOption(FoodData.STORAGE_METHODS, storageInput);
        item.productionDate = productionDate;
        item.shelfLifeValue = shelfLifeValue;
        item.shelfLifeUnit = shelfLifeValue == null ? "" : shelfLifeUnit;
        item.expiryDate = expiryDate;
        item.dateSource = dateSource;
        item.notes = clean(notesInput);
        item.createdAt = original == null ? now : original.createdAt;
        item.updatedAt = now;
        return item;
    }

    private void replaceFood(FoodItem updated) {
        for (int index = 0; index < foods.size(); index++) {
            if (foods.get(index).id.equals(updated.id)) {
                foods.set(index, updated);
                return;
            }
        }
        foods.add(updated);
    }

    private void confirmFinishFood(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle("标记为已用完")
                .setMessage("“" + food.name + "”会移入已用完记录，不再提醒。不是删除，可以以后查看。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认已用完", new FoodDialogActionListener(food, FOOD_ACTION_FINISH))
                .show();
    }

    private void markFoodFinished(FoodItem food) {
        String now = DateRules.nowIsoLike();
        FoodItem updated = food.copy();
        updated.isFinished = true;
        updated.finishedAt = now;
        updated.updatedAt = now;
        replaceFood(updated);
        store.saveFoods(foods);
        ReminderScheduler.scheduleDaily(this);
        renderFilterControls();
        renderFoods();
        toast("已移入已用完");
    }

    private void confirmRestoreFood(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle("恢复提醒")
                .setMessage("“" + food.name + "”会回到在库食品，重新参与到期提醒。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复提醒", new FoodDialogActionListener(food, FOOD_ACTION_RESTORE))
                .show();
    }

    private void restoreFood(FoodItem food) {
        String now = DateRules.nowIsoLike();
        FoodItem updated = food.copy();
        updated.isFinished = false;
        updated.finishedAt = "";
        updated.updatedAt = now;
        replaceFood(updated);
        store.saveFoods(foods);
        ReminderScheduler.scheduleDaily(this);
        renderFilterControls();
        renderFoods();
        toast("已恢复提醒");
    }

    private void confirmDelete(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle("删除食品")
                .setMessage("确定删除“" + food.name + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new FoodDialogActionListener(food, FOOD_ACTION_DELETE))
                .show();
    }

    private void deleteFood(FoodItem food) {
        List<FoodItem> nextFoods = new ArrayList<FoodItem>();
        for (FoodItem item : foods) {
            if (!item.id.equals(food.id)) {
                nextFoods.add(item);
            }
        }
        foods = nextFoods;
        store.saveFoods(foods);
        ReminderScheduler.scheduleDaily(this);
        renderFilterControls();
        renderFoods();
        toast("已删除食品");
    }

    private void scrollToTop() {
        if (mainScrollView == null) {
            return;
        }
        mainScrollView.smoothScrollTo(0, 0);
    }

    private void updateBackToTopVisibility(int scrollY) {
        if (backToTopButton == null) {
            return;
        }
        backToTopButton.setVisibility(scrollY > dp(320) ? View.VISIBLE : View.GONE);
    }

    private void updateStickyFilterVisibility(int scrollY) {
        if (filterCard == null || stickySearchBar == null || activeFilterBar == null) {
            return;
        }

        boolean showStickyControls = scrollY > filterCard.getBottom() - dp(6);
        stickySearchBar.setVisibility(showStickyControls ? View.VISIBLE : View.GONE);
        activeFilterBar.setVisibility(showStickyControls ? View.VISIBLE : View.GONE);
    }

    private void clearSearchFocusForUserScroll() {
        View focused = getCurrentFocus();
        if (focused != searchInput && focused != pinnedSearchInput) {
            return;
        }

        focused.clearFocus();
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13, COLOR_MUTED, Typeface.BOLD);
        view.setPadding(0, dp(10), 0, dp(4));
        return view;
    }

    private LinearLayout formSection(String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(12), dp(10), dp(12), dp(12));
        section.setBackground(rounded(Color.rgb(250, 251, 248), dp(8), COLOR_LINE));

        TextView titleView = text(title, 15, COLOR_TEXT, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, dp(2));
        section.addView(titleView, matchWrap());
        return section;
    }

    private LinearLayout formRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        return row;
    }

    private LinearLayout formField(String title, View inputView) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = text(title, 12, COLOR_MUTED, Typeface.BOLD);
        labelView.setPadding(0, dp(8), 0, dp(4));
        field.addView(labelView, matchWrap());
        field.addView(inputView, matchWrap());
        return field;
    }

    private void addFormField(LinearLayout parent, String title, View inputView) {
        parent.addView(formField(title, inputView), matchWrap());
    }

    private Button button(String value, int color) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        return button;
    }

    private Button outlineButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(COLOR_PRIMARY);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(238, 244, 235)));
        return button;
    }

    private EditText input(String value, String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setText(value == null ? "" : value);
        editText.setHint(hint);
        editText.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        editText.setInputType(inputType);
        editText.setTextColor(COLOR_TEXT);
        editText.setHintTextColor(Color.rgb(125, 139, 125));
        editText.setTextSize(15);
        editText.setMinHeight(dp(44));
        editText.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));
        editText.setPadding(dp(10), 0, dp(10), 0);
        return editText;
    }

    private EditText dateInput(String value, String hint) {
        final EditText editText = input(value, hint, InputType.TYPE_NULL);
        editText.setFocusable(false);
        editText.setCursorVisible(false);
        editText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker(editText);
            }
        });
        return editText;
    }

    private void showDatePicker(final EditText target) {
        int[] parts = dateParts(FoodItem.cleanText(target.getText().toString()));
        if (parts == null) {
            Calendar calendar = Calendar.getInstance();
            parts = new int[] {
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
            };
        }

        final int[] selectedDate = new int[] { parts[0], parts[1], parts[2] };
        final NumberPicker yearPicker = numberPicker();
        final NumberPicker monthPicker = numberPicker();
        final NumberPicker dayPicker = numberPicker();

        int minYear = Math.min(1990, parts[0]);
        int maxYear = Math.max(2100, parts[0]);
        yearPicker.setMinValue(minYear);
        yearPicker.setMaxValue(maxYear);
        yearPicker.setDisplayedValues(suffixedLabels(minYear, maxYear, "年"));
        yearPicker.setValue(parts[0]);

        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setDisplayedValues(suffixedLabels(1, 12, "月"));
        monthPicker.setValue(parts[1]);

        configureDayPicker(dayPicker, parts[0], parts[1], parts[2]);

        LinearLayout pickerLayout = new LinearLayout(this);
        pickerLayout.setOrientation(LinearLayout.VERTICAL);
        pickerLayout.setPadding(dp(12), dp(4), dp(12), 0);

        final TextView selectedText = text(formatChineseDate(selectedDate[0], selectedDate[1], selectedDate[2]), 16, COLOR_TEXT, Typeface.BOLD);
        selectedText.setGravity(android.view.Gravity.CENTER);
        selectedText.setPadding(0, dp(8), 0, dp(10));
        pickerLayout.addView(selectedText, matchWrap());

        LinearLayout pickerRow = formRow();
        pickerRow.addView(yearPicker, withMargins(weightWrap(1), 0, 0, dp(8), 0));
        pickerRow.addView(monthPicker, withMargins(weightWrap(1), 0, 0, dp(8), 0));
        pickerRow.addView(dayPicker, weightWrap(1));
        pickerLayout.addView(pickerRow, matchWrap());

        yearPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldValue, int newValue) {
                selectedDate[0] = newValue;
                selectedDate[2] = Math.min(selectedDate[2], daysInMonth(selectedDate[0], selectedDate[1]));
                configureDayPicker(dayPicker, selectedDate[0], selectedDate[1], selectedDate[2]);
                updateSelectedDateText(selectedText, selectedDate);
            }
        });

        monthPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldValue, int newValue) {
                selectedDate[1] = newValue;
                selectedDate[2] = Math.min(selectedDate[2], daysInMonth(selectedDate[0], selectedDate[1]));
                configureDayPicker(dayPicker, selectedDate[0], selectedDate[1], selectedDate[2]);
                updateSelectedDateText(selectedText, selectedDate);
            }
        });

        dayPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldValue, int newValue) {
                selectedDate[2] = newValue;
                updateSelectedDateText(selectedText, selectedDate);
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("选择日期")
                .setView(pickerLayout)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        target.setText(String.format(
                                Locale.US,
                                "%04d-%02d-%02d",
                                selectedDate[0],
                                selectedDate[1],
                                selectedDate[2]
                        ));
                    }
                })
                .show();
    }

    private NumberPicker numberPicker() {
        NumberPicker picker = new NumberPicker(this);
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        return picker;
    }

    private void configureDayPicker(NumberPicker dayPicker, int year, int month, int selectedDay) {
        int dayCount = daysInMonth(year, month);
        int safeDay = Math.min(selectedDay, dayCount);
        dayPicker.setDisplayedValues(null);
        if (dayPicker.getMaxValue() < dayCount) {
            dayPicker.setMaxValue(dayCount);
        }
        dayPicker.setMinValue(1);
        if (dayPicker.getMaxValue() > dayCount && dayPicker.getValue() > dayCount) {
            dayPicker.setValue(dayCount);
        }
        dayPicker.setMaxValue(dayCount);
        dayPicker.setValue(safeDay);
        dayPicker.setDisplayedValues(suffixedLabels(1, dayCount, "日"));
    }

    private void updateSelectedDateText(TextView selectedText, int[] selectedDate) {
        selectedText.setText(formatChineseDate(selectedDate[0], selectedDate[1], selectedDate[2]));
    }

    private String formatChineseDate(int year, int month, int day) {
        return String.format(Locale.US, "%04d年%02d月%02d日", year, month, day);
    }

    private String[] suffixedLabels(int start, int end, String suffix) {
        String[] labels = new String[end - start + 1];
        for (int value = start; value <= end; value++) {
            labels[value - start] = value + suffix;
        }
        return labels;
    }

    private int daysInMonth(int year, int month) {
        switch (month) {
            case 2:
                return isLeapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    private boolean isLeapYear(int year) {
        return year % 400 == 0 || (year % 4 == 0 && year % 100 != 0);
    }

    private int[] dateParts(String value) {
        if (!DateRules.isValidDateString(value)) {
            return null;
        }

        return new int[] {
                Integer.parseInt(value.substring(0, 4)),
                Integer.parseInt(value.substring(5, 7)),
                Integer.parseInt(value.substring(8, 10))
        };
    }

    private Spinner spinner(List<Option> options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                labels(options)
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(44));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));
        return spinner;
    }

    private List<String> labels(List<Option> options) {
        List<String> labels = new ArrayList<String>();
        for (Option option : options) {
            labels.add(option.label);
        }
        return labels;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(COLOR_CARD, dp(8), COLOR_LINE));
        return card;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable oval(int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightWrap(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    private LinearLayout.LayoutParams withMargins(LinearLayout.LayoutParams params, int left, int top, int right, int bottom) {
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String clean(EditText editText) {
        return FoodItem.cleanText(editText.getText().toString());
    }

    private Double parseNumber(String value, Double fallback) {
        if (value.length() == 0) {
            return fallback;
        }

        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parsePositiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? Integer.valueOf(parsed) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String selectedOption(List<Option> options, Spinner spinner) {
        int position = spinner.getSelectedItemPosition();
        if (position < 0 || position >= options.size()) {
            return options.get(0).value;
        }
        return options.get(position).value;
    }

    private int indexOf(List<Option> options, String value, String fallback) {
        String target = value == null || value.length() == 0 ? fallback : value;
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).value.equals(target)) {
                return index;
            }
        }
        return 0;
    }

    private String displayUnit(String unit) {
        String text = FoodItem.cleanText(unit);
        if (text.length() == 0 || "piece".equals(text)) {
            return "件";
        }
        return text;
    }

    private String emptyFallback(String value, String fallback) {
        String text = FoodItem.cleanText(value);
        return text.length() == 0 ? fallback : text;
    }

    private String formatNumber(double value) {
        if (Math.rint(value) == value) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
