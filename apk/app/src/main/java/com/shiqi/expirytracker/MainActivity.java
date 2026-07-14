package com.shiqi.expirytracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 4301;
    private static final int REQUEST_BARCODE_SCAN = 4302;
    private static final int REQUEST_EXCEL_EXPORT = 4303;
    private static final int REQUEST_EXCEL_IMPORT = 4304;
    private static final int REQUEST_DATE_OCR = 4305;
    private static final String PREFS_NAME = "shiqi_android_v0";
    private static final String PREF_NOTIFICATION_PERMISSION_PROMPTED = "notification_permission_prompted_v0";
    private static final String FOOD_ACTION_EDIT = "edit";
    private static final String FOOD_ACTION_DELETE = "delete";
    private static final String FOOD_ACTION_DELETE_CONFIRM = "delete_confirm";
    private static final String FOOD_ACTION_FINISH = "finish";
    private static final String FOOD_ACTION_RESTORE = "restore";
    private static final String FOOD_ACTION_DECREASE_ONE = "decrease_one";
    private static final String FOOD_ACTION_ZERO_REMAINING = "zero_remaining";
    private static final String FOOD_ACTION_REPLENISH = "replenish";
    private static final String FOOD_ACTION_COPY = "copy";
    private static final String FOOD_ACTION_MORE = "more";
    private static final String EXTRA_QA_FORCE_IMPORT_SAVE_FAILURE =
            "com.shiqi.expirytracker.QA_FORCE_IMPORT_SAVE_FAILURE";
    private static final String EXTRA_QA_FORCE_SAVE_FAILURE =
            "com.shiqi.expirytracker.QA_FORCE_SAVE_FAILURE";

    private static final int COLOR_BG = Color.rgb(246, 247, 248);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(28, 34, 31);
    private static final int COLOR_MUTED = Color.rgb(94, 103, 99);
    private static final int COLOR_PRIMARY = Color.rgb(38, 104, 76);
    private static final int COLOR_DANGER = Color.rgb(159, 53, 46);
    private static final int COLOR_LINE = Color.rgb(224, 228, 226);
    private static final int COLOR_SOFT = Color.rgb(239, 242, 241);

    private FoodStore store;
    private List<FoodItem> foods = new ArrayList<FoodItem>();
    private List<FoodItem> lastPersistedFoods = new ArrayList<FoodItem>();
    private ScrollView mainScrollView;
    private LinearLayout stickySearchBar;
    private EditText pinnedSearchInput;
    private LinearLayout filterCard;
    private LinearLayout advancedFilterBody;
    private Button filterToggleButton;
    private LinearLayout dailyBriefingContainer;
    private LinearLayout listContainer;
    private LinearLayout activeFilterBar;
    private LinearLayout activeFilterChipContainer;
    private LinearLayout statusFilterContainer;
    private Button categoryOpenButton;
    private Button locationOpenButton;
    private Button backToTopButton;
    private TextView statsText;
    private TextView reminderStatusText;
    private Button reminderPermissionButton;
    private Button reminderSettingsButton;
    private TextView activeFilterSummary;
    private ReminderSettings reminderSettings = ReminderSettings.defaults();
    private EditText searchInput;
    private final List<String> selectedStatuses = new ArrayList<String>(FoodData.statusFilterValues());
    private final List<String> selectedCategories = new ArrayList<String>();
    private final List<String> selectedLocations = new ArrayList<String>();
    private boolean statusFilterActive = false;
    private boolean categoryFilterActive = false;
    private boolean locationFilterActive = false;
    private String searchQuery = "";
    private String categorySearchQuery = "";
    private String locationSearchQuery = "";
    private boolean activeFilterCollapsed = true;
    private boolean activeStatusAllExpanded = false;
    private boolean activeCategoryAllExpanded = false;
    private boolean activeLocationAllExpanded = false;
    private boolean syncingSearchText = false;
    private boolean filterPanelExpanded = false;
    private boolean qaForceNextImportSaveFailure = false;
    private boolean qaForceNextSaveFailure = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);

        store = new FoodStore(this);
        qaForceNextImportSaveFailure = isDebuggable()
                && getIntent().getBooleanExtra(EXTRA_QA_FORCE_IMPORT_SAVE_FAILURE, false);
        qaForceNextSaveFailure = isDebuggable()
                && getIntent().getBooleanExtra(EXTRA_QA_FORCE_SAVE_FAILURE, false);
        reminderSettings = ReminderScheduler.loadSettings(this);
        ReminderPolicy.useSettings(reminderSettings);
        List<FoodItem> loadedFoods = new ArrayList<FoodItem>(store.loadFoods());
        List<FoodItem> migratedFoods = copyFoods(loadedFoods);
        boolean reminderSnapshotSaveFailed = ReminderPolicy.ensureSmartSchedules(migratedFoods)
                && !store.saveFoods(migratedFoods);
        foods = reminderSnapshotSaveFailed ? loadedFoods : migratedFoods;
        lastPersistedFoods = copyFoods(foods);
        buildScreen();
        renderFoods();
        setupReminderNotifications();
        scheduleQaRecognitionIfRequested();
        if (reminderSnapshotSaveFailed) {
            toast("提醒计划保存失败，原有食品数据未改动");
        }
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

        pinnedSearchInput = input("", "搜索食品", InputType.TYPE_CLASS_TEXT);
        pinnedSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                applySearchText(value == null ? "" : value.toString(), pinnedSearchInput);
            }
        });
        stickySearchBar.addView(pinnedSearchInput, matchWrap());

        activeFilterBar = new LinearLayout(this);
        activeFilterBar.setOrientation(LinearLayout.VERTICAL);
        activeFilterBar.setPadding(dp(14), dp(8), dp(14), dp(8));
        activeFilterBar.setBackground(rounded(COLOR_CARD, 0, COLOR_LINE));
        activeFilterBar.setVisibility(View.GONE);
        root.addView(activeFilterBar, matchWrap());

        mainScrollView = new ScrollView(this);
        mainScrollView.setBackgroundColor(COLOR_BG);
        root.addView(mainScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1
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
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        mainScrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(android.view.Gravity.BOTTOM);
        content.addView(heading, matchWrap());

        TextView title = text("食期管家", 26, COLOR_TEXT, Typeface.BOLD);
        heading.addView(title, weightWrap(1));

        TextView localBadge = text("仅本机", 12, COLOR_MUTED, Typeface.BOLD);
        localBadge.setGravity(android.view.Gravity.CENTER);
        localBadge.setPadding(dp(9), dp(4), dp(9), dp(4));
        localBadge.setBackground(rounded(COLOR_SOFT, dp(8), 0));
        heading.addView(localBadge, wrapWrap());

        TextView subtitle = text("按到期时间管理，需要优先处理的食品一眼可见", 13, COLOR_MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        content.addView(subtitle, matchWrap());

        dailyBriefingContainer = new LinearLayout(this);
        dailyBriefingContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(dailyBriefingContainer, withMargins(matchWrap(), 0, 0, 0, dp(10)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        content.addView(actionRow, matchWrap());

        Button scanButton = button("智能识别", COLOR_PRIMARY);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startDateOcrScanner(); }
        });
        actionRow.addView(scanButton, withMargins(weightWrap(1), 0, 0, dp(8), 0));

        Button addButton = outlineButton("手动新增");
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showFoodForm(null); }
        });
        actionRow.addView(addButton, weightWrap(1));

        LinearLayout utilityRow = new LinearLayout(this);
        utilityRow.setOrientation(LinearLayout.HORIZONTAL);
        utilityRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        content.addView(utilityRow, withMargins(matchWrap(), 0, dp(8), 0, 0));

        Button importButton = utilityButton("导入");
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startExcelImport(); }
        });
        utilityRow.addView(importButton, withMargins(weightWrap(1), 0, 0, dp(8), 0));

        Button exportButton = utilityButton("导出");
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startExcelExport(); }
        });
        utilityRow.addView(exportButton, withMargins(weightWrap(1), 0, 0, dp(8), 0));

        Button reminderUtilityButton = utilityButton("提醒设置");
        reminderUtilityButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showReminderSettingsDialog(); }
        });
        utilityRow.addView(reminderUtilityButton, weightWrap(1));

        content.addView(reminderCard(), withMargins(matchWrap(), 0, 0, 0, dp(10)));

        statsText = text("", 14, COLOR_TEXT, Typeface.BOLD);
        statsText.setPadding(0, dp(4), 0, dp(8));
        content.addView(statsText, matchWrap());

        filterCard = new LinearLayout(this);
        filterCard.setOrientation(LinearLayout.VERTICAL);
        content.addView(filterCard, withMargins(matchWrap(), 0, 0, 0, dp(12)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filterCard.addView(searchRow, matchWrap());

        searchInput = input("", "搜索食品", InputType.TYPE_CLASS_TEXT);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                applySearchText(value == null ? "" : value.toString(), searchInput);
            }
        });
        searchRow.addView(searchInput, withMargins(weightWrap(1), 0, 0, dp(8), 0));

        filterToggleButton = utilityButton("筛选");
        filterToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                filterPanelExpanded = !filterPanelExpanded;
                advancedFilterBody.setVisibility(filterPanelExpanded ? View.VISIBLE : View.GONE);
                filterToggleButton.setText(filterPanelExpanded ? "收起" : "筛选");
            }
        });
        searchRow.addView(filterToggleButton, new LinearLayout.LayoutParams(dp(76), dp(44)));

        advancedFilterBody = new LinearLayout(this);
        advancedFilterBody.setOrientation(LinearLayout.VERTICAL);
        advancedFilterBody.setPadding(0, dp(4), 0, 0);
        advancedFilterBody.setVisibility(View.GONE);
        filterCard.addView(advancedFilterBody, matchWrap());

        advancedFilterBody.addView(label("状态"), matchWrap());
        statusFilterContainer = new LinearLayout(this);
        statusFilterContainer.setOrientation(LinearLayout.VERTICAL);
        advancedFilterBody.addView(statusFilterContainer, matchWrap());

        advancedFilterBody.addView(label("分类"), matchWrap());
        categoryOpenButton = outlineButton("");
        categoryOpenButton.setGravity(android.view.Gravity.CENTER_VERTICAL);
        categoryOpenButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { toggleCategoryPanel(); }
        });
        advancedFilterBody.addView(categoryOpenButton, matchWrap());

        advancedFilterBody.addView(label("存放位置"), matchWrap());
        locationOpenButton = outlineButton("");
        locationOpenButton.setGravity(android.view.Gravity.CENTER_VERTICAL);
        locationOpenButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { toggleLocationPanel(); }
        });
        advancedFilterBody.addView(locationOpenButton, matchWrap());

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer, matchWrap());

        backToTopButton = new Button(this);
        backToTopButton.setVisibility(View.GONE);
        screenRoot.addView(backToTopButton, new FrameLayout.LayoutParams(1, 1));

        renderFilterControls();
        setContentView(screenRoot);
    }

    private View reminderCard() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(10), dp(8));
        row.setBackground(rounded(COLOR_SOFT, dp(8), 0));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showReminderSettingsDialog(); }
        });

        TextView title = text("提醒", 13, COLOR_TEXT, Typeface.BOLD);
        row.addView(title, withMargins(wrapWrap(), 0, 0, dp(10), 0));

        reminderSettingsButton = utilityButton("设置");
        reminderSettingsButton.setVisibility(View.GONE);

        reminderStatusText = text("", 12, COLOR_MUTED, Typeface.NORMAL);
        reminderStatusText.setSingleLine(true);
        reminderStatusText.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(reminderStatusText, weightWrap(1));

        reminderPermissionButton = utilityButton("授权");
        reminderPermissionButton.setMinHeight(dp(32));
        reminderPermissionButton.setMinimumHeight(0);
        reminderPermissionButton.setPadding(dp(10), 0, dp(10), 0);
        reminderPermissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestNotificationPermission(true);
            }
        });
        row.addView(reminderPermissionButton, withMargins(wrapWrap(), 0, 0, dp(6), 0));

        updateReminderStatus();
        return row;
    }

    private void setupReminderNotifications() {
        ReminderScheduler.scheduleDaily(this, reminderSettings);
        if (reminderSettings.enabled) {
            requestNotificationPermission(false);
        }
        updateReminderStatus();
    }

    private void requestNotificationPermission(boolean fromUserAction) {
        if (!reminderSettings.enabled) {
            updateReminderStatus();
            return;
        }

        if (Build.VERSION.SDK_INT < 33) {
            ReminderScheduler.scheduleDaily(this, reminderSettings);
            if (fromUserAction) {
                toast("手机提醒已开启");
            }
            updateReminderStatus();
            return;
        }

        if (ReminderScheduler.canPostNotifications(this)) {
            ReminderScheduler.scheduleDaily(this, reminderSettings);
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

        if (!reminderSettings.enabled) {
            reminderPermissionButton.setVisibility(View.GONE);
            reminderStatusText.setText("已关闭，点击可设置");
            return;
        }

        boolean allowed = ReminderScheduler.canPostNotifications(this);
        reminderPermissionButton.setVisibility(allowed ? View.GONE : View.VISIBLE);
        reminderStatusText.setText(allowed
                ? currentReminderModeLabel() + "已开启"
                : "等待通知授权");
    }

    private String currentReminderModeLabel() {
        return ReminderSettings.validOrDefault(reminderSettings).isSmartMode() ? "智能提醒" : "固定提醒";
    }

    private void showReminderSettingsDialog() {
        final ReminderSettings current = ReminderSettings.validOrDefault(reminderSettings);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(6), dp(16), 0);

        final CheckBox enabledInput = new CheckBox(this);
        enabledInput.setText("启用手机提醒");
        enabledInput.setTextColor(COLOR_TEXT);
        enabledInput.setTextSize(15);
        enabledInput.setChecked(current.enabled);
        form.addView(enabledInput, matchWrap());

        TextView modeLabel = text("提醒日期", 13, COLOR_MUTED, Typeface.BOLD);
        modeLabel.setPadding(0, dp(8), 0, dp(4));
        form.addView(modeLabel, matchWrap());

        final RadioGroup modeInput = new RadioGroup(this);
        modeInput.setOrientation(LinearLayout.HORIZONTAL);

        final RadioButton smartModeInput = new RadioButton(this);
        smartModeInput.setId(View.generateViewId());
        smartModeInput.setText("智能提醒");
        smartModeInput.setTextColor(COLOR_TEXT);
        smartModeInput.setTextSize(14);
        modeInput.addView(smartModeInput, weightWrap(1));

        final RadioButton fixedModeInput = new RadioButton(this);
        fixedModeInput.setId(View.generateViewId());
        fixedModeInput.setText("固定日期");
        fixedModeInput.setTextColor(COLOR_TEXT);
        fixedModeInput.setTextSize(14);
        modeInput.addView(fixedModeInput, weightWrap(1));
        form.addView(modeInput, matchWrap());

        final TextView smartNote = text(
                "新增食品或修改类别、保存方式、日期、开封状态时生成；之后不随剩余天数自动变化，提醒基准日当天始终保留。",
                12,
                COLOR_MUTED,
                Typeface.NORMAL
        );
        smartNote.setLineSpacing(dp(2), 1.0f);
        smartNote.setPadding(dp(10), dp(8), dp(10), dp(8));
        smartNote.setBackground(rounded(COLOR_SOFT, dp(8), 0));
        form.addView(smartNote, withMargins(matchWrap(), 0, dp(4), 0, dp(4)));

        final EditText advanceDaysInput = input(current.advanceDaysText(), "例如 7,3,1,0", InputType.TYPE_CLASS_TEXT);
        final LinearLayout fixedDaysField = formField("固定提前天数（英文逗号分隔，0 表示到期当天）", advanceDaysInput);
        form.addView(fixedDaysField, matchWrap());

        final EditText todaySlotsInput = input(current.todaySlotsText(), "例如 08:30,18:00", InputType.TYPE_CLASS_TEXT);
        addFormField(form, "到期当天提醒时段（英文逗号分隔）", todaySlotsInput);

        modeInput.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean fixed = checkedId == fixedModeInput.getId();
                fixedDaysField.setVisibility(fixed ? View.VISIBLE : View.GONE);
                smartNote.setText(fixed
                        ? "固定模式以最终可食用日期为基准，严格使用下方提前天数，不自动增减提醒日期。"
                        : "新增食品或修改类别、保存方式、日期、开封状态时生成；之后不随剩余天数自动变化，提醒基准日当天始终保留。");
            }
        });
        modeInput.check(current.isSmartMode() ? smartModeInput.getId() : fixedModeInput.getId());

        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.addView(form);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("提醒设置")
                .setView(settingsScroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ReminderSettings next = ReminderSettings.fromInput(
                                enabledInput.isChecked(),
                                modeInput.getCheckedRadioButtonId() == fixedModeInput.getId()
                                        ? ReminderSettings.MODE_FIXED
                                        : ReminderSettings.MODE_SMART,
                                clean(advanceDaysInput),
                                clean(todaySlotsInput)
                        );
                        if (next == null) {
                            toast("请检查提前天数和今日提醒时段格式");
                            return;
                        }

                        applyReminderSettings(next);
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
    }

    private void applyReminderSettings(ReminderSettings settings) {
        reminderSettings = ReminderSettings.validOrDefault(settings);
        ReminderScheduler.saveSettings(this, reminderSettings);
        ReminderPolicy.useSettings(reminderSettings);
        if (reminderSettings.enabled) {
            requestNotificationPermission(false);
        }
        updateReminderStatus();
        renderFoods();
        toast(reminderSettings.enabled ? "提醒设置已保存" : "提醒已关闭，食品数据仍保留");
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
            ReminderScheduler.scheduleDaily(this, reminderSettings);
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
        } else if (requestCode == REQUEST_EXCEL_EXPORT) {
            writeExcelExport(data == null ? null : data.getData());
        } else if (requestCode == REQUEST_EXCEL_IMPORT) {
            readExcelImport(data == null ? null : data.getData());
        } else if (requestCode == REQUEST_DATE_OCR) {
            handleUnifiedRecognitionResult(data);
        }
    }

    private void startBarcodeScanner() {
        Intent intent = new Intent(this, BarcodeScanActivity.class);
        startActivityForResult(intent, REQUEST_BARCODE_SCAN);
    }

    private void startDateOcrScanner() {
        Intent intent = new Intent(this, DateOcrScanActivity.class);
        startActivityForResult(intent, REQUEST_DATE_OCR);
    }

    private void scheduleQaRecognitionIfRequested() {
        if (!isDebuggable() || getIntent() == null) {
            return;
        }
        final String qaVideoPath = FoodItem.cleanText(getIntent().getStringExtra(DateOcrScanActivity.EXTRA_QA_VIDEO_PATH));
        final String qaImagePath = FoodItem.cleanText(getIntent().getStringExtra(DateOcrScanActivity.EXTRA_QA_IMAGE_PATH));
        if (qaVideoPath.length() == 0 && qaImagePath.length() == 0) {
            return;
        }
        getIntent().removeExtra(DateOcrScanActivity.EXTRA_QA_VIDEO_PATH);
        getIntent().removeExtra(DateOcrScanActivity.EXTRA_QA_IMAGE_PATH);

        View anchor = mainScrollView == null ? getWindow().getDecorView() : mainScrollView;
        anchor.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(MainActivity.this, DateOcrScanActivity.class);
                if (qaVideoPath.length() > 0) {
                    intent.putExtra(DateOcrScanActivity.EXTRA_QA_VIDEO_PATH, qaVideoPath);
                }
                if (qaImagePath.length() > 0) {
                    intent.putExtra(DateOcrScanActivity.EXTRA_QA_IMAGE_PATH, qaImagePath);
                }
                startActivityForResult(intent, REQUEST_DATE_OCR);
            }
        });
    }

    private void startExcelExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(FoodExcelExporter.mimeType());
        intent.putExtra(Intent.EXTRA_TITLE, "shiqi-foods-" + exportTimestamp() + ".xlsx");
        try {
            startActivityForResult(intent, REQUEST_EXCEL_EXPORT);
        } catch (ActivityNotFoundException error) {
            toast("当前设备没有可用的文件保存器");
        }
    }

    private void startExcelImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(FoodExcelExporter.mimeType());
        try {
            startActivityForResult(intent, REQUEST_EXCEL_IMPORT);
        } catch (ActivityNotFoundException error) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("*/*");
            try {
                startActivityForResult(Intent.createChooser(fallback, "选择 Excel 文件"), REQUEST_EXCEL_IMPORT);
            } catch (ActivityNotFoundException ignored) {
                toast("当前设备没有可用的文件选择器");
            }
        }
    }

    private void writeExcelExport(Uri uri) {
        if (uri == null) {
            toast("未选择导出位置");
            return;
        }

        OutputStream outputStream = null;
        try {
            outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream == null) {
                toast("无法打开导出文件");
                return;
            }
            FoodExcelExporter.writeWorkbook(outputStream, foods);
            toast("Excel 已导出");
        } catch (IOException error) {
            toast("Excel 导出失败：" + FoodItem.cleanText(error.getMessage()));
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void readExcelImport(Uri uri) {
        if (uri == null) {
            toast("未选择导入文件");
            return;
        }

        InputStream inputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                toast("无法打开导入文件");
                return;
            }
            FoodExcelImporter.ImportPreview preview = FoodExcelImporter.readWorkbook(inputStream);
            showExcelImportPreview(preview);
        } catch (IOException error) {
            toast("Excel 导入失败：" + FoodItem.cleanText(error.getMessage()));
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void showExcelImportPreview(final FoodExcelImporter.ImportPreview preview) {
        if (preview == null) {
            toast("Excel 导入预览失败");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("总行数：").append(preview.totalRows).append('\n');
        message.append("可导入：").append(preview.importableRows).append('\n');
        message.append("错误行：").append(preview.errorRows).append('\n');
        message.append("警告行：").append(preview.warningRows).append('\n');
        if (preview.errorRows > 0) {
            message.append("\n错误行不会导入。可点“查看问题行详情”核对。");
        }
        if (preview.warningRows > 0) {
            message.append("\n存在警告行。警告行可导入，但部分字段会按默认值处理。");
        }
        message.append("\n\n确认前不会写入。覆盖导入会先写最近备份，失败时不会改动现有数据。");

        LinearLayout previewLayout = new LinearLayout(this);
        previewLayout.setOrientation(LinearLayout.VERTICAL);
        previewLayout.setPadding(dp(8), dp(4), dp(8), 0);

        TextView previewText = text(message.toString(), 14, COLOR_TEXT, Typeface.NORMAL);
        previewText.setLineSpacing(dp(2), 1.0f);
        previewLayout.addView(previewText, matchWrap());

        if (preview.errorRows > 0 || preview.warningRows > 0) {
            Button detailsButton = outlineButton("查看问题行详情");
            detailsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showExcelImportRowDetails(preview);
                }
            });
            previewLayout.addView(detailsButton, withMargins(matchWrap(), 0, dp(12), 0, 0));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Excel 导入预览")
                .setView(previewLayout)
                .setNegativeButton("取消", null);

        if (preview.importableRows > 0) {
            builder.setNeutralButton("追加导入", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    applyExcelImport(preview, false);
                }
            });
            builder.setPositiveButton("覆盖导入", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    confirmExcelOverwriteImport(preview);
                }
            });
        } else {
            builder.setPositiveButton("知道了", null);
        }

        builder.show();
    }

    private void appendImportMessages(StringBuilder message, FoodExcelImporter.ImportPreview preview, boolean errors) {
        int appended = 0;
        for (FoodExcelImporter.RowResult row : preview.rows) {
            List<String> items = errors ? row.errors : row.warnings;
            if (items.isEmpty()) {
                continue;
            }
            if (appended >= 20) {
                message.append("\n- 还有更多行，请修正后重试");
                return;
            }
            message.append("\n- 第 ").append(row.rowNumber).append(" 行：").append(joinTexts(items, "；"));
            appended++;
        }
    }

    private void showExcelImportRowDetails(FoodExcelImporter.ImportPreview preview) {
        StringBuilder message = new StringBuilder();
        if (preview.errorRows > 0) {
            message.append("错误行不会导入：");
            appendImportMessages(message, preview, true);
        }
        if (preview.warningRows > 0) {
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append("警告行：");
            appendImportMessages(message, preview, false);
        }
        if (message.length() == 0) {
            message.append("没有错误或警告。");
        }

        new AlertDialog.Builder(this)
                .setTitle("Excel 导入详情")
                .setMessage(message.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    private void confirmExcelOverwriteImport(final FoodExcelImporter.ImportPreview preview) {
        new AlertDialog.Builder(this)
                .setTitle("确认覆盖导入")
                .setMessage("将用 " + preview.importableRows + " 条可导入食品覆盖当前本机 "
                        + foods.size() + " 条食品。\n\n覆盖前会保留最近备份；如果保存失败，当前数据不会被改动。错误行不会导入。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认覆盖", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        applyExcelImport(preview, true);
                    }
                })
                .show();
    }

    private void applyExcelImport(FoodExcelImporter.ImportPreview preview, boolean replaceExisting) {
        List<FoodItem> imported = preview.importableFoods();
        if (imported.isEmpty()) {
            toast("没有可导入的食品");
            return;
        }

        String now = DateRules.nowIsoLike();
        List<FoodItem> nextFoods = new ArrayList<FoodItem>();
        Set<String> importedIds = new HashSet<String>();
        for (FoodItem source : imported) {
            FoodItem item = source.copy();
            item.id = FoodIdGenerator.nextId(foods, importedIds);
            if (item.createdAt.length() == 0) {
                item.createdAt = now;
            }
            item.updatedAt = now;
            item.normalizeQuantityBounds();
            ReminderPolicy.ensureSmartSchedule(item);
            nextFoods.add(item);
        }

        if (!replaceExisting) {
            nextFoods.addAll(foods);
        }

        if (!saveFoodsForImport(nextFoods)) {
            toast("导入失败，当前本地数据未改动");
            return;
        }

        foods = nextFoods;
        lastPersistedFoods = copyFoods(nextFoods);
        ReminderScheduler.scheduleDaily(MainActivity.this, reminderSettings);
        renderFilterControls();
        renderFoods();
        toast((replaceExisting ? "已覆盖导入 " : "已追加导入 ") + imported.size() + " 条食品");
    }

    private boolean saveFoodsForImport(List<FoodItem> nextFoods) {
        if (qaForceNextImportSaveFailure) {
            qaForceNextImportSaveFailure = false;
            return store.saveFoodsForQaForcedFailure(nextFoods);
        }
        return store.saveFoodsForImport(nextFoods);
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private String exportTimestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
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

    private void handleUnifiedRecognitionResult(Intent data) {
        if (data == null) {
            toast("未收到识别候选");
            return;
        }

        Integer shelfLifeValue = data.hasExtra(DateOcrResultPayload.EXTRA_SHELF_LIFE_VALUE)
                ? Integer.valueOf(data.getIntExtra(DateOcrResultPayload.EXTRA_SHELF_LIFE_VALUE, 0))
                : null;
        final FoodItem draft = UnifiedRecognitionPayload.toDraft(
                data.getStringExtra(UnifiedRecognitionPayload.EXTRA_BARCODE),
                data.getStringExtra(UnifiedRecognitionPayload.EXTRA_PRODUCT_NAME),
                data.getStringExtra(UnifiedRecognitionPayload.EXTRA_PRODUCT_CATEGORY),
                data.getStringExtra(UnifiedRecognitionPayload.EXTRA_PRODUCT_NOTES),
                data.getStringExtra(DateOcrResultPayload.EXTRA_PRODUCTION_DATE),
                data.getStringExtra(DateOcrResultPayload.EXTRA_EXPIRY_DATE),
                data.getBooleanExtra(DateOcrResultPayload.EXTRA_EXPIRY_CALCULATED, false),
                shelfLifeValue,
                data.getStringExtra(DateOcrResultPayload.EXTRA_SHELF_LIFE_UNIT)
        );
        if (!UnifiedRecognitionPayload.hasUsableDraft(draft)) {
            String rawText = dateOcrSnippet(data.getStringExtra(DateOcrResultPayload.EXTRA_RAW_TEXT), 260);
            new AlertDialog.Builder(this)
                    .setTitle("没有可填入的候选")
                    .setMessage("已读取到包装文字，但没有形成商品名、条码或日期候选。"
                            + "请返回识别页继续选择更稳定的候选。\n\n原始片段：\n" + rawText)
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }

        showFoodForm(draft, false);
        toast("已填入识别候选，请检查后保存");
    }

    private String dateOcrDraftSummary(FoodItem draft) {
        StringBuilder builder = new StringBuilder();
        if (draft.productionDate.length() > 0) {
            builder.append("生产日期：").append(draft.productionDate).append('\n');
        }
        if (draft.shelfLifeValue != null) {
            builder.append("保质期：")
                    .append(draft.shelfLifeValue)
                    .append(FoodData.shelfLifeUnitLabel(draft.shelfLifeUnit))
                    .append('\n');
        }
        if (draft.expiryDate.length() > 0) {
            builder.append("最终日期：").append(draft.expiryDate).append('\n');
        }
        return builder.toString().trim();
    }

    private String dateOcrSnippet(String value, int maxLength) {
        String text = FoodItem.cleanText(value).replace('\n', ' ');
        if (text.length() == 0) {
            return "暂无";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void renderDailyBriefing() {
        if (dailyBriefingContainer == null) {
            return;
        }

        dailyBriefingContainer.removeAllViews();
        DailyBriefing briefing = ReminderPolicy.dailyBriefing(foods, reminderSettings);

        LinearLayout briefingView = new LinearLayout(this);
        briefingView.setOrientation(LinearLayout.VERTICAL);
        briefingView.setPadding(dp(12), dp(10), dp(12), dp(10));
        briefingView.setBackground(rounded(COLOR_CARD, dp(8), COLOR_LINE));

        TextView title = text("今日", 13, COLOR_MUTED, Typeface.BOLD);
        briefingView.addView(title, matchWrap());

        if (briefing.isEmpty()) {
            TextView empty = text(reminderSettings.enabled ? "没有需要优先处理的食品" : "提醒已关闭，食品仍按日期排序", 15, COLOR_TEXT, Typeface.BOLD);
            empty.setPadding(0, dp(3), 0, 0);
            briefingView.addView(empty, matchWrap());
        } else {
            addBriefingSection(briefingView, "昨日过期", briefing.yesterdayExpired);
            addBriefingSection(briefingView, "今日到期", briefing.todayDue);
            addBriefingSection(briefingView, "临近保质期", briefing.upcoming);
        }

        dailyBriefingContainer.addView(briefingView, matchWrap());
    }

    private void addBriefingSection(LinearLayout card, String title, List<DailyBriefing.Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        TextView line = text(title + "：" + briefingEntryText(entries), 14, COLOR_TEXT, Typeface.BOLD);
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
        renderLocationFilterButtons();
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

        List<Option> locationOptions = FoodData.locationFilterOptions(foods);
        List<String> nextLocations = new ArrayList<String>();
        for (Option option : locationOptions) {
            if (FoodData.ALL.equals(option.value)) {
                continue;
            }
            if (selectedLocations.contains(option.value) && !nextLocations.contains(option.value)) {
                nextLocations.add(option.value);
            }
        }

        selectedLocations.clear();
        int availableLocationCount = locationOptions.size() - 1;
        if (locationFilterActive) {
            if (availableLocationCount > 0 && nextLocations.size() >= availableLocationCount) {
                locationFilterActive = false;
            } else {
                selectedLocations.addAll(nextLocations);
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

        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scrollView.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        statusFilterContainer.addView(scrollView, matchWrap());

        for (final Option option : options) {
            boolean selected = statusFilterActive && selectedStatuses.contains(option.value);
            Button button = filterButton(option.label, selected);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    toggleStatusFilter(option.value);
                }
            });
            row.addView(button, withMargins(wrapWrap(), 0, 0, dp(6), 0));
        }
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

    private void renderLocationFilterButtons() {
        if (locationOpenButton == null) {
            return;
        }

        locationOpenButton.setText(buildLocationOpenButtonText());
    }

    private void toggleLocationPanel() {
        showLocationFilterDialog();
    }

    private String buildCategoryOpenButtonText() {
        String categoryText = !categoryFilterActive
                ? "全部分类"
                : selectedCategories.isEmpty()
                        ? "未选分类"
                        : joinCategoryLabels(selectedCategories, FoodData.categoryFilterOptions(foods));
        return "分类 · " + categoryText;
    }

    private String buildLocationOpenButtonText() {
        String locationText = !locationFilterActive
                ? "全部位置"
                : selectedLocations.isEmpty()
                        ? "未选位置"
                        : joinLocationLabels(selectedLocations, FoodData.locationFilterOptions(foods));
        return "位置 · " + locationText;
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

    private void showLocationFilterDialog() {
        locationSearchQuery = "";

        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setPadding(dp(4), dp(4), dp(4), dp(4));

        final EditText dialogSearchInput = input("", "搜索位置：中文、拼音或首字母", InputType.TYPE_CLASS_TEXT);
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
                dp(300)
        ));

        final Runnable[] refreshOptions = new Runnable[1];
        refreshOptions[0] = new Runnable() {
            @Override
            public void run() {
                renderLocationDialogOptions(optionContainer, refreshOptions[0]);
            }
        };

        Button selectVisibleLocationButton = outlineButton("全部选择");
        selectVisibleLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectVisibleLocations();
                refreshOptions[0].run();
            }
        });
        tools.addView(selectVisibleLocationButton, withMargins(weightWrap(1), 0, 0, dp(8), 0));

        Button clearVisibleLocationButton = outlineButton("取消选择");
        clearVisibleLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearVisibleLocations();
                refreshOptions[0].run();
            }
        });
        tools.addView(clearVisibleLocationButton, weightWrap(1));

        dialogSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                locationSearchQuery = value == null ? "" : value.toString();
                refreshOptions[0].run();
            }
        });

        refreshOptions[0].run();

        new AlertDialog.Builder(this)
                .setTitle("选择存放位置")
                .setView(dialogContent)
                .setPositiveButton("完成", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        locationSearchQuery = "";
                        renderLocationFilterButtons();
                    }
                })
                .show();
    }

    private void renderLocationDialogOptions(final LinearLayout optionContainer, final Runnable refreshOptions) {
        optionContainer.removeAllViews();
        final List<Option> visibleOptions = visibleLocationOptions();

        if (visibleOptions.isEmpty()) {
            TextView empty = text("没有匹配的位置", 14, COLOR_MUTED, Typeface.NORMAL);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            empty.setBackground(rounded(Color.rgb(249, 251, 247), dp(8), COLOR_LINE));
            optionContainer.addView(empty, matchWrap());
            return;
        }

        addFilterButtonGrid(optionContainer, visibleOptions, new FilterButtonBinder() {
            @Override
            public Button createButton(final Option option) {
                boolean active = !locationFilterActive || selectedLocations.contains(option.value);
                Button button = filterButton(option.label, active);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        toggleLocationFilter(option.value);
                        refreshOptions.run();
                    }
                });
                return button;
            }
        });
    }

    private List<Option> visibleLocationOptions() {
        return visibleLocationOptions(locationSearchQuery);
    }

    private List<Option> visibleLocationOptions(String searchText) {
        List<Option> options = FoodData.locationFilterOptions(foods);
        String query = FoodItem.cleanText(searchText);
        List<Option> locationOnlyOptions = new ArrayList<Option>();
        for (Option option : options) {
            if (!FoodData.ALL.equals(option.value)) {
                locationOnlyOptions.add(option);
            }
        }

        if (query.length() == 0) {
            return locationOnlyOptions;
        }

        List<Option> visibleOptions = new ArrayList<Option>();
        for (Option option : locationOnlyOptions) {
            if (FoodData.matchesCategorySearch(option, query)) {
                visibleOptions.add(option);
            }
        }
        return visibleOptions;
    }

    private List<String> locationValues(List<Option> locationOptions) {
        List<String> values = new ArrayList<String>();
        for (Option option : locationOptions) {
            if (!FoodData.ALL.equals(option.value)) {
                values.add(option.value);
            }
        }
        return values;
    }

    private void selectVisibleLocations() {
        List<String> allValues = locationValues(FoodData.locationFilterOptions(foods));
        List<String> visibleValues = locationValues(visibleLocationOptions());

        if (visibleValues.isEmpty()) {
            return;
        }

        if (!locationFilterActive) {
            renderLocationFilterButtons();
            return;
        }

        for (String value : visibleValues) {
            if (!selectedLocations.contains(value)) {
                selectedLocations.add(value);
            }
        }

        if (selectedLocations.size() >= allValues.size()) {
            locationFilterActive = false;
            selectedLocations.clear();
        } else {
            locationFilterActive = true;
        }

        normalizeFilterSelections();
        renderLocationFilterButtons();
        renderFoods();
    }

    private void clearVisibleLocations() {
        List<String> allValues = locationValues(FoodData.locationFilterOptions(foods));
        List<String> visibleValues = locationValues(visibleLocationOptions());

        if (visibleValues.isEmpty()) {
            return;
        }

        if (!locationFilterActive) {
            locationFilterActive = true;
            selectedLocations.addAll(allValues);
        }

        selectedLocations.removeAll(visibleValues);
        normalizeFilterSelections();
        renderLocationFilterButtons();
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

    private void toggleLocationFilter(String location) {
        List<Option> locationOptions = FoodData.locationFilterOptions(foods);
        if (!FoodData.isKnownLocationFilter(location, locationOptions)) {
            return;
        }

        if (FoodData.ALL.equals(location)) {
            locationFilterActive = false;
            selectedLocations.clear();
        } else if (!locationFilterActive) {
            locationFilterActive = true;
            selectedLocations.addAll(locationValues(locationOptions));
            selectedLocations.remove(location);
        } else if (selectedLocations.contains(location)) {
            selectedLocations.remove(location);
        } else {
            selectedLocations.add(location);
        }

        activeLocationAllExpanded = false;
        normalizeFilterSelections();
        renderLocationFilterButtons();
        renderFoods();
    }

    private void removeLocationFilter(String location) {
        if (!locationFilterActive) {
            locationFilterActive = true;
            selectedLocations.clear();
            selectedLocations.addAll(locationValues(FoodData.locationFilterOptions(foods)));
        }
        selectedLocations.remove(location);
        activeLocationAllExpanded = false;
        normalizeFilterSelections();
        renderLocationFilterButtons();
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
                    activeLocationAllExpanded = false;
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

        if (!locationFilterActive) {
            if (activeLocationAllExpanded) {
                final List<Option> locationOptions = FoodData.locationFilterOptions(foods);
                for (final Option option : locationOptions) {
                    if (FoodData.ALL.equals(option.value)) {
                        continue;
                    }

                    chips.add(new ChipSpec(option.label + " x", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            toggleLocationFilter(option.value);
                        }
                    }, null));
                }
            } else {
                chips.add(new ChipSpec("全部位置", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        activeLocationAllExpanded = true;
                        renderActiveFilterBar();
                    }
                }, null));
            }
        } else {
            final List<Option> locationOptions = FoodData.locationFilterOptions(foods);
            if (selectedLocations.isEmpty()) {
                chips.add(new ChipSpec("未选位置", null, null));
            } else {
                for (final String location : selectedLocations) {
                    chips.add(new ChipSpec(locationLabel(location, locationOptions) + " x", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            removeLocationFilter(location);
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
        String locationText = !locationFilterActive
                ? "全部位置"
                : selectedLocations.isEmpty()
                        ? "未选位置"
                        : joinLocationLabels(selectedLocations, FoodData.locationFilterOptions(foods));
        String query = FoodItem.cleanText(searchQuery);
        if (query.length() == 0) {
            return "状态：" + statusText + "；分类：" + categoryText + "；位置：" + locationText;
        }
        return "状态：" + statusText + "；分类：" + categoryText + "；位置：" + locationText + "；搜索：" + query;
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

    private String joinLocationLabels(List<String> values, List<Option> locationOptions) {
        List<String> labels = new ArrayList<String>();
        for (String value : values) {
            labels.add(locationLabel(value, locationOptions));
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

    private String locationLabel(String value, List<Option> locationOptions) {
        for (Option option : locationOptions) {
            if (option.value.equals(value)) {
                return option.label;
            }
        }
        return FoodData.locationLabel(value);
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
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(38));
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTextColor(selected ? Color.WHITE : Color.rgb(55, 67, 62));
        button.setBackground(rounded(selected ? COLOR_PRIMARY : COLOR_SOFT, dp(8), 0));
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
        } else if (FOOD_ACTION_DECREASE_ONE.equals(action)) {
            decreaseFoodRemaining(food);
        } else if (FOOD_ACTION_ZERO_REMAINING.equals(action)) {
            if (fromDialog) {
                zeroFoodRemaining(food);
            } else {
                confirmZeroRemaining(food);
            }
        } else if (FOOD_ACTION_REPLENISH.equals(action)) {
            showReplenishDialog(food);
        } else if (FOOD_ACTION_COPY.equals(action)) {
            copyFood(food);
        } else if (FOOD_ACTION_MORE.equals(action)) {
            showFoodActionsDialog(food);
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
            boolean locationMatches = !locationFilterActive || selectedLocations.contains(FoodData.normalizeLocationValue(food.location));
            if (statusMatches && categoryMatches && locationMatches && FoodData.matchesFoodSearch(food, searchQuery)) {
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
        TextView heading = text("还没有食品", 18, COLOR_TEXT, Typeface.BOLD);
        card.addView(heading, matchWrap());

        TextView copy = text("识别包装或手动新增，保存前都可以确认和修改。", 14, COLOR_MUTED, Typeface.NORMAL);
        copy.setLineSpacing(dp(2), 1.0f);
        copy.setPadding(0, dp(6), 0, dp(10));
        card.addView(copy, matchWrap());

        Button first = button("开始智能识别", COLOR_PRIMARY);
        first.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDateOcrScanner();
            }
        });
        card.addView(first, matchWrap());

        return card;
    }

    private View foodCard(final FoodItem food) {
        final ReminderPlan reminderPlan = ReminderPolicy.planFor(food, reminderSettings);
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

        TextView name = text(food.name, 18, COLOR_TEXT, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(name, weightWrap(1));

        TextView status = text(FoodData.statusLabel(foodStatus(food)), 13, statusColor(food), Typeface.BOLD);
        status.setGravity(android.view.Gravity.CENTER);
        status.setPadding(dp(8), dp(4), dp(8), dp(4));
        status.setBackground(rounded(statusBgColor(food), dp(12), 0));
        top.addView(status, wrapWrap());

        TextView meta = text(
                FoodData.categoryLabel(food.category) + " · " +
                        FoodData.storageLabel(food.storageMethod) + " · " +
                        FoodData.locationLabel(food.location) + " · 剩余 " +
                        formatNumber(food.remainingQuantity) + "/" + formatNumber(food.quantity) + " " + displayUnit(food.unit),
                14,
                COLOR_MUTED,
                Typeface.NORMAL
        );
        meta.setPadding(0, dp(6), 0, 0);
        meta.setMaxLines(2);
        card.addView(meta, matchWrap());

        String dateLine = isNoExpiryFood(food)
                ? "已生产时长：" + DateRules.productionAgeLabel(food.productionDate)
                : "最终可食用日期：" + food.expiryDate;
        TextView expiry = text(dateLine, 15, COLOR_TEXT, Typeface.NORMAL);
        expiry.setPadding(0, dp(6), 0, dp(4));
        card.addView(expiry, matchWrap());

        String afterOpenLine = afterOpenCardLine(food);
        if (afterOpenLine.length() > 0) {
            TextView afterOpen = text(afterOpenLine, 14, COLOR_MUTED, Typeface.NORMAL);
            afterOpen.setPadding(0, 0, 0, dp(6));
            card.addView(afterOpen, matchWrap());
        }

        TextView reminderHint = text(reminderPlan.cardHint, 14, food.isFinished ? COLOR_MUTED : COLOR_PRIMARY, Typeface.BOLD);
        reminderHint.setLineSpacing(dp(2), 1.0f);
        reminderHint.setPadding(0, 0, 0, dp(8));
        card.addView(reminderHint, matchWrap());

        LinearLayout quickActions = new LinearLayout(this);
        quickActions.setOrientation(LinearLayout.HORIZONTAL);
        if (food.isFinished) {
            Button restore = outlineButton("恢复提醒");
            restore.setOnClickListener(new FoodActionClickListener(food, FOOD_ACTION_RESTORE));
            quickActions.addView(restore, withMargins(weightWrap(1), 0, 0, dp(8), 0));
        } else {
            Button decrease = outlineButton("减少 1");
            decrease.setOnClickListener(new FoodActionClickListener(food, FOOD_ACTION_DECREASE_ONE));
            quickActions.addView(decrease, withMargins(weightWrap(1), 0, 0, dp(8), 0));
        }

        Button more = utilityButton("更多操作");
        more.setOnClickListener(new FoodActionClickListener(food, FOOD_ACTION_MORE));
        quickActions.addView(more, weightWrap(1));
        card.addView(quickActions, matchWrap());
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
                .setNeutralButton("更多操作", new FoodDialogActionListener(food, FOOD_ACTION_MORE))
                .setNegativeButton("关闭", null)
                .show();
    }

    private String detailText(FoodItem food) {
        ReminderPlan reminderPlan = ReminderPolicy.planFor(food, reminderSettings);
        StringBuilder builder = new StringBuilder();
        builder.append("分类：").append(FoodData.categoryLabel(food.category)).append('\n');
        builder.append("状态：").append(FoodData.statusLabel(foodStatus(food))).append('\n');
        builder.append("数量：").append(formatNumber(food.quantity)).append(' ').append(displayUnit(food.unit)).append('\n');
        builder.append("剩余：").append(formatNumber(food.remainingQuantity)).append(' ').append(displayUnit(food.unit)).append('\n');
        builder.append("保存方式：").append(FoodData.storageLabel(food.storageMethod)).append('\n');
        builder.append("存放位置：").append(FoodData.locationLabel(food.location)).append('\n');
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
        String afterOpenDetail = afterOpenDetailLine(food);
        if (afterOpenDetail.length() > 0) {
            builder.append(afterOpenDetail).append('\n');
        }
        if (food.isFinished) {
            builder.append("已用完时间：").append(emptyFallback(food.finishedAt, "未记录")).append('\n');
            builder.append("提醒：不再提醒").append('\n');
        } else if (reminderPlan.enabled) {
            builder.append("提醒方式：")
                    .append(ReminderSettings.MODE_SMART.equals(reminderPlan.reminderMode) ? "智能提醒" : "固定日期")
                    .append('\n');
            builder.append("提醒计划：").append(reminderPlan.scheduleReason).append('\n');
            builder.append("提醒日期：").append(formatReminderOffsets(reminderPlan)).append('\n');
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

    private String formatReminderOffsets(ReminderPlan plan) {
        List<String> values = new ArrayList<String>();
        for (Integer offset : plan.offsets) {
            if (offset == null) {
                continue;
            }
            String dueDayLabel = plan.usesAfterOpenDate ? "开封后建议处理日当天" : "到期当天";
            values.add(offset.intValue() == 0 ? dueDayLabel : "提前 " + offset + " 天");
        }
        return values.isEmpty() ? "暂无" : joinTexts(values, "、");
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

    private String afterOpenCardLine(FoodItem food) {
        String recommendedDate = afterOpenRecommendedDate(food);
        if (recommendedDate.length() == 0) {
            return "";
        }
        return "开封后建议处理日：" + recommendedDate;
    }

    private String afterOpenDetailLine(FoodItem food) {
        String openedDate = FoodItem.cleanText(food.openedDate);
        String recommendedDate = afterOpenRecommendedDate(food);
        if (openedDate.length() == 0 && recommendedDate.length() == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("开封信息：");
        if (openedDate.length() > 0) {
            builder.append("开封日期 ").append(openedDate);
        } else {
            builder.append("开封日期未填写");
        }

        if (food.afterOpenShelfLifeValue != null) {
            builder.append("，开封后 ")
                    .append(food.afterOpenShelfLifeValue)
                    .append(FoodData.shelfLifeUnitLabel(food.afterOpenShelfLifeUnit));
        }

        if (recommendedDate.length() > 0) {
            builder.append("，建议处理日 ").append(recommendedDate);
        }
        builder.append("（仅作提醒参考）");
        return builder.toString();
    }

    private String afterOpenRecommendedDate(FoodItem food) {
        if (food == null) {
            return "";
        }
        return DateRules.addAfterOpenShelfLife(
                food.openedDate,
                food.afterOpenShelfLifeValue,
                food.afterOpenShelfLifeUnit
        );
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
        final EditText unitInput = input(displayUnit(draft.unit), "单位，例如 盒、瓶、袋", InputType.TYPE_CLASS_TEXT);

        final Spinner storageInput = spinner(FoodData.STORAGE_METHODS);
        storageInput.setSelection(indexOf(FoodData.STORAGE_METHODS, draft.storageMethod, "room_temp"));

        String normalizedLocation = FoodData.normalizeLocationValue(draft.location);
        boolean customLocation = !FoodData.isKnownLocationValue(normalizedLocation);
        final Spinner locationInput = spinner(FoodData.LOCATION_OPTIONS);
        locationInput.setSelection(indexOf(
                FoodData.LOCATION_OPTIONS,
                customLocation ? "other" : normalizedLocation,
                FoodData.LOCATION_UNSPECIFIED
        ));
        final EditText customLocationInput = input(
                customLocation ? FoodItem.cleanText(draft.location) : "",
                "自定义位置（可选）",
                InputType.TYPE_CLASS_TEXT
        );

        final EditText productionDateInput = dateInput(draft.productionDate, "选择生产日期");
        final EditText shelfLifeInput = input(draft.shelfLifeValue == null ? "" : String.valueOf(draft.shelfLifeValue), "保质期数值", InputType.TYPE_CLASS_NUMBER);
        final Spinner shelfLifeUnitInput = spinner(FoodData.SHELF_LIFE_UNITS);
        shelfLifeUnitInput.setSelection(indexOf(FoodData.SHELF_LIFE_UNITS, draft.shelfLifeUnit, "day"));

        final boolean[] manualExpiryOverride = new boolean[] {
                !"calculated".equals(draft.dateSource) && DateRules.isValidDateString(draft.expiryDate)
        };
        final boolean[] updatingCalculatedExpiry = new boolean[] { false };
        final EditText expiryDateInput = dateInput(draft.expiryDate, "选择最终可食用日期");
        final CheckBox noExpiryInput = new CheckBox(this);
        noExpiryInput.setText("无过期时间");
        noExpiryInput.setTextColor(COLOR_TEXT);
        noExpiryInput.setTextSize(14);
        noExpiryInput.setPadding(0, dp(8), 0, dp(4));
        noExpiryInput.setChecked(isNoExpiryFood(draft));

        final EditText openedDateInput = dateInput(draft.openedDate, "选择开封日期");
        final EditText afterOpenShelfLifeInput = input(
                draft.afterOpenShelfLifeValue == null ? "" : String.valueOf(draft.afterOpenShelfLifeValue),
                "开封后保质期数值",
                InputType.TYPE_CLASS_NUMBER
        );
        final Spinner afterOpenShelfLifeUnitInput = spinner(FoodData.SHELF_LIFE_UNITS);
        afterOpenShelfLifeUnitInput.setSelection(indexOf(FoodData.SHELF_LIFE_UNITS, draft.afterOpenShelfLifeUnit, "day"));

        final EditText notesInput = input(draft.notes, "备注", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notesInput.setMinLines(2);
        notesInput.setGravity(android.view.Gravity.TOP);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(2), dp(2), dp(2), dp(8));

        LinearLayout basicSection = formSection("基本信息");
        addFormField(basicSection, "食品名称", nameInput);
        addFormField(basicSection, "分类", categoryInput);

        LinearLayout amountRow = formRow();
        amountRow.addView(formField("数量", quantityInput), withMargins(weightWrap(1), 0, 0, dp(8), 0));
        amountRow.addView(formField("剩余数量", remainingInput), weightWrap(1));
        basicSection.addView(amountRow, matchWrap());

        addFormField(basicSection, "单位", unitInput);
        addFormField(basicSection, "保存方式", storageInput);
        form.addView(basicSection, withMargins(matchWrap(), 0, 0, 0, dp(4)));

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

        final TextView hint = text("", 12, COLOR_MUTED, Typeface.NORMAL);
        hint.setPadding(0, dp(8), 0, 0);
        dateSection.addView(hint, matchWrap());

        final Button useCalculatedExpiryButton = outlineButton("改用自动计算");
        dateSection.addView(useCalculatedExpiryButton, withMargins(matchWrap(), 0, dp(8), 0, 0));

        final Runnable updateCalculatedExpiry = new Runnable() {
            @Override
            public void run() {
                if (noExpiryInput.isChecked()) {
                    useCalculatedExpiryButton.setVisibility(View.GONE);
                    hint.setText("无过期时间食品不会参与到期提醒；生产日期可留空。");
                    return;
                }

                if (manualExpiryOverride[0]) {
                    useCalculatedExpiryButton.setVisibility(View.VISIBLE);
                    hint.setText("当前为手动日期；也可以改用生产日期和保质期自动计算。");
                    return;
                }

                useCalculatedExpiryButton.setVisibility(View.GONE);
                String shelfLifeText = clean(shelfLifeInput);
                Integer shelfLifeValue = shelfLifeText.length() == 0
                        ? null
                        : parsePositiveInteger(shelfLifeText);
                String shelfLifeUnit = selectedOption(FoodData.SHELF_LIFE_UNITS, shelfLifeUnitInput);
                String calculatedExpiry = DateRules.addShelfLife(
                        clean(productionDateInput),
                        shelfLifeValue,
                        shelfLifeUnit
                );
                updatingCalculatedExpiry[0] = true;
                expiryDateInput.setText(calculatedExpiry);
                updatingCalculatedExpiry[0] = false;
                hint.setText(calculatedExpiry.length() > 0
                        ? "已自动计算；点日期可改为手动日期。"
                        : "填写生产日期和保质期后自动计算最终可食用日期。");
            }
        };

        final Runnable updateNoExpiryViews = new Runnable() {
            @Override
            public void run() {
                boolean noExpiry = noExpiryInput.isChecked();
                shelfLifeRow.setVisibility(noExpiry ? View.GONE : View.VISIBLE);
                expiryDateField.setVisibility(noExpiry ? View.GONE : View.VISIBLE);
                productionAgePreview.setVisibility(noExpiry ? View.VISIBLE : View.GONE);
                productionAgePreview.setText("已生产时长：" + DateRules.productionAgeLabel(clean(productionDateInput)));
            }
        };
        noExpiryInput.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                updateNoExpiryViews.run();
                updateCalculatedExpiry.run();
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
                updateCalculatedExpiry.run();
            }
        });
        watchText(shelfLifeInput, updateCalculatedExpiry);
        expiryDateInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                if (!updatingCalculatedExpiry[0]) {
                    manualExpiryOverride[0] = clean(expiryDateInput).length() > 0;
                    updateCalculatedExpiry.run();
                }
            }
        });
        shelfLifeUnitInput.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateCalculatedExpiry.run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                updateCalculatedExpiry.run();
            }
        });
        useCalculatedExpiryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                manualExpiryOverride[0] = false;
                updateCalculatedExpiry.run();
            }
        });
        updateNoExpiryViews.run();
        updateCalculatedExpiry.run();

        form.addView(dateSection, withMargins(matchWrap(), 0, 0, 0, dp(4)));

        final LinearLayout optionalSection = new LinearLayout(this);
        optionalSection.setOrientation(LinearLayout.VERTICAL);

        LinearLayout locationSection = formSection("存放位置");
        addFormField(locationSection, "位置", locationInput);
        addFormField(locationSection, "自定义位置", customLocationInput);
        optionalSection.addView(locationSection, withMargins(matchWrap(), 0, 0, 0, dp(4)));

        LinearLayout openedSection = formSection("开封信息（可选）");
        TextView openedIntro = text("默认不填。填写后只用于提醒和展示开封后建议处理日，不会自动改最终可食用日期。", 12, COLOR_MUTED, Typeface.NORMAL);
        openedIntro.setPadding(0, dp(8), 0, dp(4));
        openedSection.addView(openedIntro, matchWrap());
        addFormField(openedSection, "开封日期", openedDateInput);

        LinearLayout afterOpenRow = formRow();
        afterOpenRow.addView(formField("开封后保质期数值", afterOpenShelfLifeInput), withMargins(weightWrap(1), 0, 0, dp(8), 0));
        afterOpenRow.addView(formField("开封后保质期单位", afterOpenShelfLifeUnitInput), weightWrap(1));
        openedSection.addView(afterOpenRow, matchWrap());

        final TextView afterOpenPreview = text("", 13, COLOR_PRIMARY, Typeface.BOLD);
        afterOpenPreview.setPadding(dp(10), dp(8), dp(10), dp(8));
        afterOpenPreview.setBackground(rounded(Color.rgb(239, 244, 235), dp(8), 0));
        openedSection.addView(afterOpenPreview, withMargins(matchWrap(), 0, dp(8), 0, 0));

        final Runnable updateAfterOpenPreview = new Runnable() {
            @Override
            public void run() {
                String openedDate = clean(openedDateInput);
                String afterOpenText = clean(afterOpenShelfLifeInput);
                Integer afterOpenValue = afterOpenText.length() == 0 ? null : parsePositiveInteger(afterOpenText);
                String afterOpenUnit = selectedOption(FoodData.SHELF_LIFE_UNITS, afterOpenShelfLifeUnitInput);

                if (openedDate.length() == 0 && afterOpenText.length() == 0) {
                    afterOpenPreview.setText("未填写开封信息，不影响最终可食用日期。");
                } else if (!DateRules.isValidDateString(openedDate)) {
                    afterOpenPreview.setText("请选择有效的开封日期。");
                } else if (afterOpenText.length() == 0) {
                    afterOpenPreview.setText("填写开封后保质期后，可显示建议处理日。");
                } else if (afterOpenValue == null) {
                    afterOpenPreview.setText("开封后保质期需要填写正整数。");
                } else {
                    afterOpenPreview.setText("开封后建议处理日："
                            + DateRules.addAfterOpenShelfLife(openedDate, afterOpenValue, afterOpenUnit));
                }
            }
        };
        watchText(openedDateInput, updateAfterOpenPreview);
        watchText(afterOpenShelfLifeInput, updateAfterOpenPreview);
        afterOpenShelfLifeUnitInput.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateAfterOpenPreview.run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                updateAfterOpenPreview.run();
            }
        });
        updateAfterOpenPreview.run();

        Button clearAfterOpenButton = outlineButton("清空开封信息");
        clearAfterOpenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openedDateInput.setText("");
                afterOpenShelfLifeInput.setText("");
                afterOpenShelfLifeUnitInput.setSelection(indexOf(FoodData.SHELF_LIFE_UNITS, "day", "day"));
                updateAfterOpenPreview.run();
            }
        });
        openedSection.addView(clearAfterOpenButton, withMargins(matchWrap(), 0, dp(8), 0, 0));
        optionalSection.addView(openedSection, withMargins(matchWrap(), 0, 0, 0, dp(4)));

        LinearLayout noteSection = formSection("备注");
        addFormField(noteSection, "备注", notesInput);
        optionalSection.addView(noteSection, matchWrap());

        boolean hasOptionalValues = !FoodData.LOCATION_UNSPECIFIED.equals(normalizedLocation)
                || FoodItem.cleanText(draft.openedDate).length() > 0
                || draft.afterOpenShelfLifeValue != null
                || FoodItem.cleanText(draft.notes).length() > 0;
        optionalSection.setVisibility(hasOptionalValues ? View.VISIBLE : View.GONE);

        final Button optionalToggle = utilityButton(hasOptionalValues ? "收起更多信息" : "更多信息");
        optionalToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean show = optionalSection.getVisibility() != View.VISIBLE;
                optionalSection.setVisibility(show ? View.VISIBLE : View.GONE);
                optionalToggle.setText(show ? "收起更多信息" : "更多信息");
            }
        });
        form.addView(optionalToggle, withMargins(matchWrap(), 0, dp(4), 0, dp(4)));
        form.addView(optionalSection, matchWrap());

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
                                locationInput,
                                customLocationInput,
                                productionDateInput,
                                noExpiryInput,
                                shelfLifeInput,
                                shelfLifeUnitInput,
                                expiryDateInput,
                                manualExpiryOverride[0],
                                openedDateInput,
                                afterOpenShelfLifeInput,
                                afterOpenShelfLifeUnitInput,
                                notesInput
                        );

                        if (saved == null) {
                            return;
                        }

                        ReminderPolicy.ensureSmartSchedule(saved);

                        if (isEdit) {
                            replaceFood(saved);
                        } else {
                            foods.add(0, saved);
                        }

                        if (!saveFoodsRefreshReminders()) {
                            return;
                        }
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
            Spinner locationInput,
            EditText customLocationInput,
            EditText productionDateInput,
            CheckBox noExpiryInput,
            EditText shelfLifeInput,
            Spinner shelfLifeUnitInput,
            EditText expiryDateInput,
            boolean manualExpiryOverride,
            EditText openedDateInput,
            EditText afterOpenShelfLifeInput,
            Spinner afterOpenShelfLifeUnitInput,
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
            if (productionDate.length() > 0 && !DateRules.isValidDateString(productionDate)) {
                toast("生产日期格式不正确，请重新选择日期");
                return null;
            }
            shelfLifeValue = null;
            shelfLifeUnit = "";
            expiryDate = "";
            dateSource = "none";
        } else if (manualExpiryOverride) {
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

        String openedDate = clean(openedDateInput);
        String afterOpenShelfLifeText = clean(afterOpenShelfLifeInput);
        Integer afterOpenShelfLifeValue = afterOpenShelfLifeText.length() == 0
                ? null
                : parsePositiveInteger(afterOpenShelfLifeText);
        if (afterOpenShelfLifeText.length() > 0 && afterOpenShelfLifeValue == null) {
            toast("开封后保质期必须是正整数");
            return null;
        }

        String afterOpenShelfLifeUnit = selectedOption(FoodData.SHELF_LIFE_UNITS, afterOpenShelfLifeUnitInput);
        boolean hasAfterOpenInfo = openedDate.length() > 0 || afterOpenShelfLifeText.length() > 0;
        if (hasAfterOpenInfo) {
            if (!DateRules.isValidDateString(openedDate)) {
                toast("请选择有效的开封日期");
                return null;
            }
            if (afterOpenShelfLifeValue == null) {
                toast("请填写开封后保质期");
                return null;
            }
            if (DateRules.addAfterOpenShelfLife(openedDate, afterOpenShelfLifeValue, afterOpenShelfLifeUnit).length() == 0) {
                toast("开封后建议处理日无法计算，请检查开封信息");
                return null;
            }
        } else {
            openedDate = "";
            afterOpenShelfLifeValue = null;
            afterOpenShelfLifeUnit = "";
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
        item.location = FoodData.resolveLocationValue(
                selectedOption(FoodData.LOCATION_OPTIONS, locationInput),
                clean(customLocationInput)
        );
        item.productionDate = productionDate;
        item.shelfLifeValue = shelfLifeValue;
        item.shelfLifeUnit = shelfLifeValue == null ? "" : shelfLifeUnit;
        item.expiryDate = expiryDate;
        item.dateSource = dateSource;
        item.openedDate = openedDate;
        item.afterOpenShelfLifeValue = afterOpenShelfLifeValue;
        item.afterOpenShelfLifeUnit = afterOpenShelfLifeValue == null ? "" : afterOpenShelfLifeUnit;
        item.notes = clean(notesInput);
        item.createdAt = original == null ? now : original.createdAt;
        item.updatedAt = now;
        item.normalizeQuantityBounds();
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

    private void showFoodActionsDialog(final FoodItem food) {
        final String[] labels;
        final String[] actions;
        if (food.isFinished) {
            labels = new String[] { "复制食品", "恢复提醒", "删除" };
            actions = new String[] { FOOD_ACTION_COPY, FOOD_ACTION_RESTORE, FOOD_ACTION_DELETE_CONFIRM };
        } else {
            labels = new String[] { "减少 1", "剩余归 0", "补货", "复制食品", "编辑", "标记已用完", "删除" };
            actions = new String[] {
                    FOOD_ACTION_DECREASE_ONE,
                    FOOD_ACTION_ZERO_REMAINING,
                    FOOD_ACTION_REPLENISH,
                    FOOD_ACTION_COPY,
                    FOOD_ACTION_EDIT,
                    FOOD_ACTION_FINISH,
                    FOOD_ACTION_DELETE_CONFIRM
            };
        }

        new AlertDialog.Builder(this)
                .setTitle("食品操作")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        runFoodAction(food, actions[which], false);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void decreaseFoodRemaining(FoodItem food) {
        FoodItem updated = food.copy();
        updated.normalizeQuantityBounds();
        if (updated.remainingQuantity <= 0) {
            promptMarkFinishedAfterZero(updated);
            return;
        }

        String now = DateRules.nowIsoLike();
        updated.remainingQuantity = FoodItem.clampedRemainingQuantity(updated.remainingQuantity - 1, updated.quantity);
        updated.updatedAt = now;
        replaceFood(updated);
        if (!saveFoodsRefreshReminders()) {
            return;
        }
        if (updated.remainingQuantity <= 0) {
            toast("剩余已归 0");
            promptMarkFinishedAfterZero(updated);
        } else {
            toast("已减少 1，剩余 " + formatNumber(updated.remainingQuantity) + " " + displayUnit(updated.unit));
        }
    }

    private void confirmZeroRemaining(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle("剩余归 0")
                .setMessage("这只会把剩余数量改为 0，不会删除记录。保存后可以继续选择是否标记为已用完。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认归 0", new FoodDialogActionListener(food, FOOD_ACTION_ZERO_REMAINING))
                .show();
    }

    private void zeroFoodRemaining(FoodItem food) {
        FoodItem updated = food.copy();
        updated.normalizeQuantityBounds();
        updated.remainingQuantity = 0;
        updated.updatedAt = DateRules.nowIsoLike();
        replaceFood(updated);
        if (!saveFoodsRefreshReminders()) {
            return;
        }
        toast("剩余已归 0");
        promptMarkFinishedAfterZero(updated);
    }

    private void promptMarkFinishedAfterZero(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle("剩余已为 0")
                .setMessage("是否标记为已用完？这不会删除食品记录，只会归档并停止提醒。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("标记已用完", new FoodDialogActionListener(food, FOOD_ACTION_FINISH))
                .show();
    }

    private void showReplenishDialog(final FoodItem food) {
        FoodItem current = food.copy();
        current.normalizeQuantityBounds();

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(6), dp(16), 0);

        TextView currentText = text(
                "当前剩余 " + formatNumber(current.remainingQuantity)
                        + "/" + formatNumber(current.quantity)
                        + " " + displayUnit(current.unit),
                13,
                COLOR_MUTED,
                Typeface.NORMAL
        );
        currentText.setPadding(0, 0, 0, dp(8));
        form.addView(currentText, matchWrap());

        final EditText amountInput = input("", "输入本次补货数量", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        addFormField(form, "补货数量", amountInput);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("补货")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Double amount = parseNumber(clean(amountInput), null);
                        if (amount == null || amount.doubleValue() <= 0) {
                            toast("补货数量必须大于 0");
                            return;
                        }

                        FoodItem updated = food.copy();
                        updated.normalizeQuantityBounds();
                        updated.quantity += amount.doubleValue();
                        updated.remainingQuantity += amount.doubleValue();
                        updated.isFinished = false;
                        updated.finishedAt = "";
                        updated.updatedAt = DateRules.nowIsoLike();
                        updated.normalizeQuantityBounds();
                        replaceFood(updated);
                        if (!saveFoodsRefreshReminders()) {
                            return;
                        }
                        dialog.dismiss();
                        toast("已补货 +" + formatNumber(amount.doubleValue()) + " " + displayUnit(updated.unit));
                    }
                });
            }
        });

        dialog.show();
    }

    private void copyFood(FoodItem food) {
        FoodItem copied = food.copyAsNewRecord(newFoodId(), DateRules.nowIsoLike());
        ReminderPolicy.ensureSmartSchedule(copied);
        foods.add(0, copied);
        if (!saveFoodsRefreshReminders()) {
            return;
        }
        toast("已复制食品");
    }

    private static List<FoodItem> copyFoods(List<FoodItem> source) {
        List<FoodItem> copies = new ArrayList<FoodItem>();
        if (source == null) {
            return copies;
        }
        for (FoodItem food : source) {
            copies.add(food.copy());
        }
        return copies;
    }

    private String newFoodId() {
        return FoodIdGenerator.nextId(foods, null);
    }

    private boolean saveFoodsRefreshReminders() {
        boolean saved;
        if (qaForceNextSaveFailure) {
            qaForceNextSaveFailure = false;
            saved = store.saveFoodsForQaForcedFailure(foods);
        } else {
            saved = store.saveFoods(foods);
        }
        if (!saved) {
            foods = copyFoods(lastPersistedFoods);
            ReminderScheduler.scheduleDaily(this, reminderSettings);
            renderFilterControls();
            renderFoods();
            toast("保存失败，原有食品数据已保留");
            return false;
        }
        lastPersistedFoods = copyFoods(foods);
        ReminderScheduler.scheduleDaily(this, reminderSettings);
        renderFilterControls();
        renderFoods();
        return true;
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
        updated.normalizeQuantityBounds();
        updated.remainingQuantity = 0;
        updated.isFinished = true;
        updated.finishedAt = now;
        updated.updatedAt = now;
        replaceFood(updated);
        if (!saveFoodsRefreshReminders()) {
            return;
        }
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
        if (updated.quantity <= 0) {
            updated.quantity = 1;
        }
        if (updated.remainingQuantity <= 0) {
            updated.remainingQuantity = Math.min(1, updated.quantity);
        }
        updated.updatedAt = now;
        updated.normalizeQuantityBounds();
        replaceFood(updated);
        if (!saveFoodsRefreshReminders()) {
            return;
        }
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
        if (!saveFoodsRefreshReminders()) {
            return;
        }
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
        // Keep the floating shortcut hidden: visual QA showed it can cover food-card actions.
        backToTopButton.setVisibility(View.GONE);
    }

    private void updateStickyFilterVisibility(int scrollY) {
        if (filterCard == null || stickySearchBar == null || activeFilterBar == null) {
            return;
        }

        boolean showStickyControls = scrollY > filterCard.getBottom() - dp(6);
        stickySearchBar.setVisibility(showStickyControls ? View.VISIBLE : View.GONE);
        boolean hasActiveFilters = FoodItem.cleanText(searchQuery).length() > 0
                || statusFilterActive
                || categoryFilterActive
                || locationFilterActive;
        activeFilterBar.setVisibility(showStickyControls && hasActiveFilters ? View.VISIBLE : View.GONE);
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
        section.setPadding(dp(10), dp(6), dp(10), dp(10));

        TextView titleView = text(title, 14, COLOR_TEXT, Typeface.BOLD);
        titleView.setPadding(0, dp(2), 0, dp(2));
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
        button.setMinHeight(dp(46));
        button.setBackground(rounded(color, dp(8), 0));
        return button;
    }

    private Button outlineButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(COLOR_PRIMARY);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        button.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));
        return button;
    }

    private Button utilityButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.rgb(55, 67, 62));
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(40));
        button.setMinimumHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(rounded(COLOR_SOFT, dp(8), 0));
        return button;
    }

    private EditText input(String value, String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setText(value == null ? "" : value);
        editText.setHint(hint);
        editText.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        editText.setInputType(inputType);
        editText.setTextColor(COLOR_TEXT);
        editText.setHintTextColor(Color.rgb(128, 135, 132));
        editText.setTextSize(15);
        editText.setMinHeight(dp(44));
        editText.setBackground(rounded(Color.WHITE, dp(6), COLOR_LINE));
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

    private void watchText(EditText input, final Runnable afterChange) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                afterChange.run();
            }
        });
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
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
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
