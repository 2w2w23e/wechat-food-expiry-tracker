package com.shiqi.expirytracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.view.Gravity;
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
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
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
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 4301;
    private static final int REQUEST_BARCODE_SCAN = 4302;
    private static final int REQUEST_EXCEL_EXPORT = 4303;
    private static final int REQUEST_EXCEL_IMPORT = 4304;
    private static final int REQUEST_DATE_OCR = 4305;
    private static final int REQUEST_REPLENISHMENT_OCR = 4306;
    private static final int REQUEST_JSON_EXPORT = 4307;
    private static final int REQUEST_JSON_IMPORT = 4308;
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
    private static final String EXTRA_QA_REPLENISHMENT_VIDEO_PATH =
            "com.shiqi.expirytracker.QA_REPLENISHMENT_VIDEO_PATH";

    private static final String STATE_DESTINATION = "main_destination_v2";
    private static final String STATE_DETAIL_PROFILE = "main_detail_profile_v2";
    private static final String STATE_FOOD_EDITOR = "main_food_editor_v2";
    private static final String STATE_DATE_REQUEST_DRAFT = "main_date_request_draft_v2";
    private static final String STATE_DATE_RESULT = "main_date_result_v2";
    private static final int UTILITY_BARCODE = 1;
    private static final int UTILITY_REMINDER = 2;
    private static final int UTILITY_JSON = 3;
    private static final int UTILITY_JSON_FILTER = 4;
    private static final int UTILITY_EXCEL = 5;
    private static final int UTILITY_BACKUP = 6;
    private static final int UTILITY_RECOGNITION = 7;
    private static final int UTILITY_ABOUT = 8;
    private static final int BARCODE_STATE_LOADING = 1;
    private static final int BARCODE_STATE_LOCAL = 2;
    private static final int BARCODE_STATE_ONLINE = 3;
    private static final int BARCODE_STATE_NOT_FOUND = 4;
    private static final int BARCODE_STATE_ERROR = 5;
    private static final long UNDO_BAR_DURATION_MS = 6000L;
    private static final int COLOR_BG = Color.rgb(244, 246, 248);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(23, 33, 43);
    private static final int COLOR_MUTED = Color.rgb(100, 116, 139);
    private static final int COLOR_PRIMARY = Color.rgb(47, 102, 237);
    private static final int COLOR_PRIMARY_SOFT = Color.rgb(235, 241, 255);
    private static final int COLOR_SUCCESS = Color.rgb(23, 132, 93);
    private static final int COLOR_WARNING = Color.rgb(180, 120, 18);
    private static final int COLOR_DANGER = Color.rgb(196, 71, 58);
    private static final int COLOR_LINE = Color.rgb(221, 227, 234);
    private static final int COLOR_SOFT = Color.rgb(240, 243, 247);

    private FoodStore store;
    private List<FoodItem> foods = new ArrayList<FoodItem>();
    private List<FoodItem> lastPersistedFoods = new ArrayList<FoodItem>();
    private FoodJsonTransfer.ExportFilter pendingJsonExportFilter;
    private FrameLayout screenRoot;
    private FrameLayout pageHost;
    private View bottomNavigation;
    private final TextView[] navigationItems = new TextView[4];
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private View undoBar;
    private Runnable hideUndoRunnable;
    private FoodItem pendingUndoFood;
    private FoodItem pendingUndoExpected;
    private boolean productDetailVisible = false;
    private boolean foodEditorVisible = false;
    private FoodEditorViews activeFoodEditor;
    private String visibleProductProfileId = "";
    private int detailReturnDestination = AppNavigation.INVENTORY;
    private int detailReturnScrollY = 0;
    private int currentDestination = AppNavigation.OVERVIEW;
    private TextView overviewTodayValue;
    private TextView overviewSoonValue;
    private TextView overviewExpiredValue;
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
    private ReplenishmentRecognitionTarget pendingReplenishmentRecognitionTarget;
    private String pendingDateRecognitionDraftId = "";
    private Intent pendingDateRecognitionResult;
    private Bundle pendingFoodEditorRestore;
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
    private final List<Integer> utilityPageStack = new ArrayList<Integer>();
    private int utilityReturnDestination = AppNavigation.OVERVIEW;
    private int utilityReturnScrollY = 0;
    private ReminderPageViews activeReminderPage;
    private JsonFilterViews activeJsonFilterPage;
    private BarcodeResolutionState activeBarcodeResolution;
    private FoodItem activeBarcodeRecognizedDraft;
    private BarcodeProductInfo activeBarcodeProductInfo;
    private int activeBarcodeState = BARCODE_STATE_LOADING;
    private String activeBarcodeMessage = "";
    private String selectedBackupPath = "";
    private volatile boolean startupComplete = false;
    private volatile boolean startupDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        currentDestination = AppNavigation.restoreDestination(
                savedInstanceState == null
                        ? AppNavigation.OVERVIEW
                        : savedInstanceState.getInt(STATE_DESTINATION, AppNavigation.OVERVIEW)
        );
        qaForceNextImportSaveFailure = isDebuggable()
                && getIntent().getBooleanExtra(EXTRA_QA_FORCE_IMPORT_SAVE_FAILURE, false);
        qaForceNextSaveFailure = isDebuggable()
                && getIntent().getBooleanExtra(EXTRA_QA_FORCE_SAVE_FAILURE, false);
        reminderSettings = ReminderScheduler.loadSettings(this);
        ReminderPolicy.useSettings(reminderSettings);

        final String restoredDetailProfile = savedInstanceState == null
                ? ""
                : FoodItem.cleanText(savedInstanceState.getString(STATE_DETAIL_PROFILE, ""));
        final Bundle restoredFoodEditor = savedInstanceState == null
                ? null
                : savedInstanceState.getBundle(STATE_FOOD_EDITOR);
        pendingFoodEditorRestore = restoredFoodEditor;
        if (restoredFoodEditor != null) {
            pendingDateRecognitionDraftId = savedInstanceState.getString(STATE_DATE_REQUEST_DRAFT, "");
            pendingDateRecognitionResult = savedInstanceState.getParcelable(STATE_DATE_RESULT);
        }
        showStartupLoading();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FoodStore loadedStore = new FoodStore(MainActivity.this);
                final List<FoodItem> loadedFoods = new ArrayList<FoodItem>(loadedStore.loadFoods());
                List<FoodItem> migratedFoods = copyFoods(loadedFoods);
                boolean depletedChanged = InventoryRules.finishDepletedBatches(
                        migratedFoods,
                        DateRules.nowIsoLike()
                );
                boolean remindersChanged = ReminderPolicy.ensureSmartSchedules(migratedFoods);
                final boolean snapshotSaveFailed = (depletedChanged || remindersChanged)
                        && !loadedStore.saveFoods(migratedFoods);
                final List<FoodItem> visibleFoods = snapshotSaveFailed
                        ? loadedFoods
                        : migratedFoods;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (startupDestroyed || isFinishing()
                                || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                            return;
                        }
                        store = loadedStore;
                        foods = copyFoods(visibleFoods);
                        lastPersistedFoods = copyFoods(visibleFoods);
                        startupComplete = true;
                        buildScreen();
                        if (restoredFoodEditor != null) {
                            pageHost.post(new Runnable() {
                                @Override
                                public void run() {
                                    restoreFoodEditorState(restoredFoodEditor);
                                }
                            });
                        } else if (restoredDetailProfile.length() > 0) {
                            pageHost.post(new Runnable() {
                                @Override
                                public void run() {
                                    showProductDetail(restoredDetailProfile);
                                }
                            });
                        }
                        setupReminderNotifications();
                        scheduleQaRecognitionIfRequested();
                        if (snapshotSaveFailed) {
                            toast("库存状态整理保存失败，原有数据未改动");
                        }
                    }
                });
            }
        }, "shiqi-startup-loader").start();
    }

    private void showStartupLoading() {
        FrameLayout loadingRoot = new FrameLayout(this);
        loadingRoot.setBackgroundColor(COLOR_BG);

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(dp(32), dp(32), dp(32), dp(32));
        loadingRoot.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView title = text("食期管家", 24, COLOR_TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        loading.addView(title, matchWrap());
        TextView status = text("正在读取本机库存", 14, COLOR_MUTED, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(8), 0, dp(16));
        loading.addView(status, matchWrap());
        ProgressBar progress = new ProgressBar(this);
        loading.addView(progress, new LinearLayout.LayoutParams(dp(36), dp(36)));
        setContentView(loadingRoot);
    }

    boolean startupCompleteForTests() {
        return startupComplete;
    }
    private void buildScreen() {
        screenRoot = new FrameLayout(this);
        screenRoot.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        screenRoot.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        bottomNavigation = buildBottomNavigation();
        root.addView(bottomNavigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(65)
        ));

        setContentView(screenRoot);
        showDestination(currentDestination);
    }

    private View buildBottomNavigation() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(Color.WHITE);

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_LINE);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(4), dp(2), dp(4), dp(2));
        wrapper.addView(navigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        int[] destinations = new int[] {
                AppNavigation.OVERVIEW,
                AppNavigation.INVENTORY,
                AppNavigation.SETTINGS
        };
        int[] icons = new int[] {
                R.drawable.ic_v2_overview,
                R.drawable.ic_v2_inventory,
                R.drawable.ic_v2_settings
        };

        for (int index = 0; index < destinations.length; index++) {
            final int destination = destinations[index];
            TextView item = navigationItem(AppNavigation.label(destination), icons[index], destination);
            navigationItems[destination] = item;
            navigation.addView(item, new LinearLayout.LayoutParams(0, dp(60), 1));
        }
        updateBottomNavigation();
        return wrapper;
    }

    private TextView navigationItem(String label, int iconResource, final int destination) {
        TextView item = text(label, 11, COLOR_MUTED, Typeface.NORMAL);
        item.setGravity(Gravity.CENTER);
        item.setMinHeight(dp(56));
        item.setCompoundDrawablePadding(dp(3));
        item.setContentDescription(label);
        Drawable icon = getDrawable(iconResource).mutate();
        icon.setTint(COLOR_MUTED);
        item.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectDestination(destination);
            }
        });
        return item;
    }

    private void selectDestination(int destination) {
        int nextDestination = AppNavigation.persistentDestinationAfterSelection(
                currentDestination,
                destination
        );
        if (destination == AppNavigation.SCAN) {
            startDateOcrScanner();
            updateBottomNavigation();
            return;
        }
        if (nextDestination == currentDestination) {
            scrollToTop();
            return;
        }
        showDestination(nextDestination);
    }

    private void openUtilityPage(int page) {
        if (utilityPageStack.isEmpty()) {
            utilityReturnDestination = currentDestination;
            utilityReturnScrollY = mainScrollView == null ? 0 : mainScrollView.getScrollY();
        }
        if (utilityPageStack.isEmpty()
                || utilityPageStack.get(utilityPageStack.size() - 1).intValue() != page) {
            utilityPageStack.add(Integer.valueOf(page));
        }
        renderUtilityPage();
    }

    private boolean isUtilityPageVisible() {
        return !utilityPageStack.isEmpty() && !foodEditorVisible;
    }

    private int currentUtilityPage() {
        if (utilityPageStack.isEmpty()) {
            return 0;
        }
        return utilityPageStack.get(utilityPageStack.size() - 1).intValue();
    }

    private void renderUtilityPage() {
        if (utilityPageStack.isEmpty()) {
            return;
        }
        productDetailVisible = false;
        visibleProductProfileId = "";
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(View.GONE);
        }
        resetPageViews();
        pageHost.removeAllViews();
        activeReminderPage = null;
        activeJsonFilterPage = null;

        int page = currentUtilityPage();
        if (page == UTILITY_BARCODE) {
            buildBarcodeResolutionPage();
        } else if (page == UTILITY_REMINDER) {
            buildReminderSettingsPage();
        } else if (page == UTILITY_JSON) {
            buildJsonTransferPage();
        } else if (page == UTILITY_JSON_FILTER) {
            buildJsonExportFilterPage();
        } else if (page == UTILITY_EXCEL) {
            buildExcelManagementPage();
        } else if (page == UTILITY_BACKUP) {
            buildBackupRecoveryPage();
        } else if (page == UTILITY_RECOGNITION) {
            buildRecognitionStatusPage();
        } else {
            buildAboutPage();
        }
        repositionUndoBar();
    }

    private void requestCloseUtilityPage() {
        if (currentUtilityPage() == UTILITY_REMINDER && reminderPageHasChanges()) {
            confirmUtilityDiscard("放弃提醒设置？", "当前提醒设置尚未保存。");
            return;
        }
        if (currentUtilityPage() == UTILITY_JSON_FILTER && jsonFilterHasValues()) {
            confirmUtilityDiscard("放弃筛选条件？", "当前填写的导出日期范围尚未使用。");
            return;
        }
        closeUtilityPageImmediately();
    }

    private void confirmUtilityDiscard(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("继续填写", null)
                .setPositiveButton("放弃并返回", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        closeUtilityPageImmediately();
                    }
                })
                .show();
    }

    private void closeUtilityPageImmediately() {
        if (utilityPageStack.isEmpty()) {
            return;
        }
        int closedPage = currentUtilityPage();
        utilityPageStack.remove(utilityPageStack.size() - 1);
        if (closedPage == UTILITY_REMINDER) {
            activeReminderPage = null;
        } else if (closedPage == UTILITY_JSON_FILTER) {
            activeJsonFilterPage = null;
        }
        hideCurrentKeyboard();

        if (!utilityPageStack.isEmpty()) {
            renderUtilityPage();
            return;
        }

        int destination = AppNavigation.restoreDestination(utilityReturnDestination);
        int scrollY = utilityReturnScrollY;
        showDestination(destination);
        restoreScrollPosition(scrollY);
    }

    private void clearUtilitySession() {
        utilityPageStack.clear();
        activeReminderPage = null;
        activeJsonFilterPage = null;
        activeBarcodeResolution = null;
        activeBarcodeRecognizedDraft = null;
        activeBarcodeProductInfo = null;
        activeBarcodeMessage = "";
        selectedBackupPath = "";
    }

    private void showDestination(int destination) {
        clearUtilitySession();
        productDetailVisible = false;
        visibleProductProfileId = "";
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(View.VISIBLE);
        }
        currentDestination = AppNavigation.restoreDestination(destination);
        resetPageViews();
        pageHost.removeAllViews();

        mainScrollView = new ScrollView(this);
        mainScrollView.setFillViewport(true);
        mainScrollView.setClipToPadding(false);
        mainScrollView.setBackgroundColor(COLOR_BG);
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
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        mainScrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        pageHost.addView(mainScrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if (currentDestination == AppNavigation.INVENTORY) {
            buildInventoryPage(content);
        } else if (currentDestination == AppNavigation.SETTINGS) {
            buildSettingsPage(content);
        } else {
            buildOverviewPage(content);
        }

        updateBottomNavigation();
        renderFoods();
        repositionUndoBar();
    }

    private void resetPageViews() {
        stickySearchBar = null;
        pinnedSearchInput = null;
        filterCard = null;
        advancedFilterBody = null;
        filterToggleButton = null;
        dailyBriefingContainer = null;
        listContainer = null;
        activeFilterBar = null;
        activeFilterChipContainer = null;
        statusFilterContainer = null;
        categoryOpenButton = null;
        locationOpenButton = null;
        backToTopButton = null;
        statsText = null;
        reminderStatusText = null;
        reminderPermissionButton = null;
        reminderSettingsButton = null;
        activeFilterSummary = null;
        searchInput = null;
        overviewTodayValue = null;
        overviewSoonValue = null;
        overviewExpiredValue = null;
    }

    private void updateBottomNavigation() {
        for (int destination = 0; destination < navigationItems.length; destination++) {
            TextView item = navigationItems[destination];
            if (item == null) {
                continue;
            }
            boolean selected = destination == currentDestination;
            int color = selected ? COLOR_PRIMARY : COLOR_MUTED;
            item.setTextColor(color);
            item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            Drawable[] drawables = item.getCompoundDrawables();
            if (drawables[1] != null) {
                drawables[1].setTint(color);
            }
        }
    }

    private void addPageHeader(
            LinearLayout content,
            String titleText,
            String subtitleText,
            View action
    ) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(heading, matchWrap());

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        heading.addView(titleGroup, weightWrap(1));

        TextView title = text(titleText, 25, COLOR_TEXT, Typeface.BOLD);
        titleGroup.addView(title, matchWrap());

        TextView subtitle = text(subtitleText, 13, COLOR_MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(3), 0, 0);
        titleGroup.addView(subtitle, matchWrap());

        if (action != null) {
            heading.addView(action, withMargins(wrapWrap(), dp(10), 0, 0, 0));
        }
    }

    private void buildOverviewPage(LinearLayout content) {
        String dateLabel = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date());
        Button addButton = button("新增", COLOR_PRIMARY);
        addButton.setMinWidth(dp(76));
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showFoodForm(null); }
        });
        addPageHeader(content, "今天要处理", dateLabel, addButton);

        LinearLayout summaryRow = new LinearLayout(this);
        summaryRow.setOrientation(LinearLayout.HORIZONTAL);
        summaryRow.setBaselineAligned(false);
        content.addView(summaryRow, withMargins(matchWrap(), 0, dp(18), 0, 0));

        overviewTodayValue = text("0", 24, COLOR_TEXT, Typeface.BOLD);
        overviewSoonValue = text("0", 24, COLOR_TEXT, Typeface.BOLD);
        overviewExpiredValue = text("0", 24, COLOR_DANGER, Typeface.BOLD);
        summaryRow.addView(summaryMetric("今天到期", overviewTodayValue), withMargins(weightWrap(1), 0, 0, dp(6), 0));
        summaryRow.addView(summaryMetric("7 天内", overviewSoonValue), withMargins(weightWrap(1), 0, 0, dp(6), 0));
        summaryRow.addView(summaryMetric("已过期", overviewExpiredValue), weightWrap(1));

        addSectionHeader(content, "优先处理", "查看全部", new View.OnClickListener() {
            @Override public void onClick(View view) { showDestination(AppNavigation.INVENTORY); }
        });
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer, matchWrap());

        addSectionHeader(content, "提醒摘要", "", null);
        dailyBriefingContainer = new LinearLayout(this);
        dailyBriefingContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(dailyBriefingContainer, matchWrap());
    }

    private LinearLayout summaryMetric(String labelText, TextView valueView) {
        LinearLayout metric = card();
        metric.setPadding(dp(10), dp(10), dp(10), dp(10));
        metric.addView(valueView, matchWrap());
        TextView labelView = text(labelText, 12, COLOR_MUTED, Typeface.NORMAL);
        labelView.setPadding(0, dp(3), 0, 0);
        metric.addView(labelView, matchWrap());
        return metric;
    }

    private void addSectionHeader(
            LinearLayout content,
            String titleText,
            String actionText,
            View.OnClickListener action
    ) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(10), 0, dp(6));
        content.addView(header, matchWrap());

        TextView title = text(titleText, 16, COLOR_TEXT, Typeface.BOLD);
        header.addView(title, weightWrap(1));
        if (action != null && FoodItem.cleanText(actionText).length() > 0) {
            TextView actionView = text(actionText, 13, COLOR_PRIMARY, Typeface.BOLD);
            actionView.setGravity(Gravity.CENTER);
            actionView.setMinHeight(dp(48));
            actionView.setMinWidth(dp(64));
            actionView.setOnClickListener(action);
            header.addView(actionView, wrapWrap());
        }
    }

    private void buildInventoryPage(LinearLayout content) {
        Button addButton = button("新增", COLOR_PRIMARY);
        addButton.setMinWidth(dp(76));
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showFoodForm(null); }
        });
        addPageHeader(content, "库存", "按最终日期排序，批次独立管理", addButton);

        filterCard = new LinearLayout(this);
        filterCard.setOrientation(LinearLayout.VERTICAL);
        content.addView(filterCard, withMargins(matchWrap(), 0, dp(16), 0, 0));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        filterCard.addView(searchRow, matchWrap());

        searchInput = input(searchQuery, "搜索名称、条码或批次", InputType.TYPE_CLASS_TEXT);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                applySearchText(value == null ? "" : value.toString(), searchInput);
            }
        });
        searchRow.addView(searchInput, withMargins(weightWrap(1), 0, 0, dp(8), 0));

        filterToggleButton = outlineButton(filterPanelExpanded ? "收起" : "筛选");
        filterToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                filterPanelExpanded = !filterPanelExpanded;
                advancedFilterBody.setVisibility(filterPanelExpanded ? View.VISIBLE : View.GONE);
                filterToggleButton.setText(filterPanelExpanded ? "收起" : "筛选");
            }
        });
        searchRow.addView(filterToggleButton, new LinearLayout.LayoutParams(dp(76), dp(48)));

        activeFilterBar = new LinearLayout(this);
        activeFilterBar.setOrientation(LinearLayout.VERTICAL);
        activeFilterBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        activeFilterBar.setBackground(rounded(COLOR_PRIMARY_SOFT, dp(8), 0));
        activeFilterBar.setVisibility(View.GONE);
        filterCard.addView(activeFilterBar, withMargins(matchWrap(), 0, dp(8), 0, 0));

        advancedFilterBody = new LinearLayout(this);
        advancedFilterBody.setOrientation(LinearLayout.VERTICAL);
        advancedFilterBody.setPadding(0, dp(4), 0, 0);
        advancedFilterBody.setVisibility(filterPanelExpanded ? View.VISIBLE : View.GONE);
        filterCard.addView(advancedFilterBody, matchWrap());

        advancedFilterBody.addView(label("状态"), matchWrap());
        statusFilterContainer = new LinearLayout(this);
        statusFilterContainer.setOrientation(LinearLayout.VERTICAL);
        advancedFilterBody.addView(statusFilterContainer, matchWrap());

        advancedFilterBody.addView(label("分类"), matchWrap());
        categoryOpenButton = outlineButton("");
        categoryOpenButton.setGravity(Gravity.CENTER_VERTICAL);
        categoryOpenButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { toggleCategoryPanel(); }
        });
        advancedFilterBody.addView(categoryOpenButton, matchWrap());

        advancedFilterBody.addView(label("存放位置"), matchWrap());
        locationOpenButton = outlineButton("");
        locationOpenButton.setGravity(Gravity.CENTER_VERTICAL);
        locationOpenButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { toggleLocationPanel(); }
        });
        advancedFilterBody.addView(locationOpenButton, matchWrap());

        statsText = text("", 13, COLOR_MUTED, Typeface.BOLD);
        statsText.setPadding(0, 0, 0, dp(8));
        content.addView(statsText, matchWrap());

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer, matchWrap());
        renderFilterControls();
    }

    private void buildSettingsPage(LinearLayout content) {
        addPageHeader(content, "设置", "规则、本地数据与识别状态", null);

        addSectionHeader(content, "提醒", "", null);
        String permissionState = ReminderScheduler.canPostNotifications(this)
                ? "通知已授权"
                : "等待通知授权";
        content.addView(settingsActionRow(
                "提醒方式",
                currentReminderModeLabel() + " · " + permissionState,
                R.drawable.ic_v2_calendar,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { openUtilityPage(UTILITY_REMINDER); }
                }
        ), matchWrap());

        addSectionHeader(content, "本地数据", "", null);
        content.addView(settingsActionRow(
                "JSON 快速导入导出",
                "跨设备同步批次，可按日期筛选",
                android.R.drawable.ic_menu_share,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { showJsonTransferMenu(); }
                }
        ), matchWrap());
        content.addView(settingsActionRow(
                "Excel 批量管理",
                "预览、校验、导入与导出",
                android.R.drawable.ic_menu_edit,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { openUtilityPage(UTILITY_EXCEL); }
                }
        ), matchWrap());
        RoomMigrationBackup.BackupCatalog catalog = RoomMigrationBackup.verifiedCatalog(
                this,
                FoodStore.CURRENT_SCHEMA_VERSION
        );
        String backupSubtitle = catalog.candidates.isEmpty()
                ? "尚无可恢复的私有备份"
                : "已验证 " + catalog.candidates.size() + " 份本机备份";
        content.addView(settingsActionRow(
                "备份与恢复",
                backupSubtitle,
                android.R.drawable.ic_menu_revert,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { openUtilityPage(UTILITY_BACKUP); }
                }
        ), matchWrap());

        addSectionHeader(content, "识别", "", null);
        content.addView(settingsActionRow(
                "识别状态",
                "中文包装、条码与低对比度日期",
                R.drawable.ic_v2_scan,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { openUtilityPage(UTILITY_RECOGNITION); }
                }
        ), matchWrap());

        addSectionHeader(content, "应用", "", null);
        content.addView(settingsActionRow(
                "版本与说明",
                appVersionLabel(),
                R.drawable.ic_v2_help,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { openUtilityPage(UTILITY_ABOUT); }
                }
        ), matchWrap());
    }
    private String appVersionLabel() {
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
            return "食期管家 " + versionName + " · Android 本地版";
        } catch (PackageManager.NameNotFoundException ignored) {
            return "食期管家 · Android 本地版";
        }
    }

    private View settingsActionRow(
            String titleText,
            String subtitleText,
            int iconResource,
            View.OnClickListener listener
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(12), dp(8), dp(10), dp(8));
        row.setBackground(rounded(Color.WHITE, 0, COLOR_LINE));
        row.setOnClickListener(listener);
        row.setContentDescription(titleText);

        TextView icon = text("", 1, COLOR_PRIMARY, Typeface.NORMAL);
        icon.setGravity(Gravity.CENTER);
        Drawable drawable = getDrawable(iconResource).mutate();
        drawable.setTint(COLOR_PRIMARY);
        icon.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(48)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, withMargins(weightWrap(1), dp(4), 0, dp(6), 0));

        TextView title = text(titleText, 15, COLOR_TEXT, Typeface.BOLD);
        copy.addView(title, matchWrap());
        TextView subtitle = text(subtitleText, 12, COLOR_MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(2), 0, 0);
        copy.addView(subtitle, matchWrap());

        TextView chevron = text("›", 22, COLOR_MUTED, Typeface.NORMAL);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(48)));
        return row;
    }

    private UtilityPageLayout beginUtilityPage(String titleText, String subtitleText) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        pageHost.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.addView(utilityPageHeader(titleText, subtitleText), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72)
        ));
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_LINE);
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(COLOR_BG);
        mainScrollView = scroll;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        return new UtilityPageLayout(root, content, scroll);
    }

    private View utilityPageHeader(String titleText, String subtitleText) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(12), dp(8));
        header.setBackgroundColor(Color.WHITE);

        TextView back = actionIconButton(R.drawable.ic_v2_back, "返回", true);
        back.setBackground(rounded(Color.WHITE, dp(8), 0));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestCloseUtilityPage();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titles, withMargins(weightWrap(1), dp(4), 0, 0, 0));

        TextView title = text(titleText, 19, COLOR_TEXT, Typeface.BOLD);
        title.setMaxLines(1);
        titles.addView(title, matchWrap());

        TextView subtitle = text(subtitleText, 12, COLOR_MUTED, Typeface.NORMAL);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setPadding(0, dp(2), 0, 0);
        titles.addView(subtitle, matchWrap());
        return header;
    }

    private void addUtilityFooter(
            UtilityPageLayout page,
            String secondaryLabel,
            View.OnClickListener secondaryAction,
            String primaryLabel,
            View.OnClickListener primaryAction,
            boolean primaryEnabled
    ) {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_LINE);
        page.root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(16), dp(10), dp(16), dp(10));
        actions.setBackgroundColor(Color.WHITE);
        page.root.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(76)
        ));

        if (FoodItem.cleanText(secondaryLabel).length() > 0) {
            Button secondary = outlineButton(secondaryLabel);
            secondary.setMinHeight(dp(52));
            secondary.setOnClickListener(secondaryAction);
            actions.addView(secondary, withMargins(weightWrap(0.40f), 0, 0, dp(8), 0));
        }

        Button primary = button(primaryLabel, COLOR_PRIMARY);
        primary.setMinHeight(dp(52));
        primary.setEnabled(primaryEnabled);
        primary.setAlpha(primaryEnabled ? 1.0f : 0.45f);
        primary.setOnClickListener(primaryAction);
        actions.addView(primary, weightWrap(0.60f));
        page.primaryButton = primary;
    }

    private TextView noticeBand(String value, int textColor, int backgroundColor, int borderColor) {
        TextView notice = text(value, 13, textColor, Typeface.NORMAL);
        notice.setLineSpacing(dp(2), 1.0f);
        notice.setPadding(dp(12), dp(10), dp(12), dp(10));
        notice.setBackground(rounded(backgroundColor, dp(8), borderColor));
        return notice;
    }

    private View settingsStatusRow(
            String titleText,
            String subtitleText,
            String statusText,
            int statusColor
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(rounded(Color.WHITE, 0, COLOR_LINE));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, weightWrap(1));

        TextView title = text(titleText, 15, COLOR_TEXT, Typeface.BOLD);
        copy.addView(title, matchWrap());
        TextView subtitle = text(subtitleText, 12, COLOR_MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(2), 0, 0);
        subtitle.setMaxLines(2);
        copy.addView(subtitle, matchWrap());

        TextView status = text(statusText, 11, statusColor, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(true);
        status.setPadding(dp(9), dp(5), dp(9), dp(5));
        status.setBackground(rounded(
                statusColor == COLOR_SUCCESS ? Color.rgb(233, 247, 240) : COLOR_PRIMARY_SOFT,
                dp(8),
                0
        ));
        row.addView(status, withMargins(wrapWrap(), dp(8), 0, 0, 0));
        return row;
    }

    private void buildReminderSettingsPage() {
        final ReminderSettings current = ReminderSettings.validOrDefault(reminderSettings);
        final UtilityPageLayout page = beginUtilityPage(
                "提醒设置",
                currentReminderModeLabel() + " · 保存后生效"
        );
        final ReminderPageViews views = new ReminderPageViews();
        views.mode = current.mode;
        activeReminderPage = views;

        addSectionHeader(page.content, "提醒开关", "", null);
        views.enabledInput = new CheckBox(this);
        views.enabledInput.setText("启用手机提醒");
        views.enabledInput.setTextColor(COLOR_TEXT);
        views.enabledInput.setTextSize(15);
        views.enabledInput.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        views.enabledInput.setGravity(Gravity.CENTER_VERTICAL);
        views.enabledInput.setMinHeight(dp(56));
        views.enabledInput.setPadding(dp(8), 0, dp(8), 0);
        views.enabledInput.setChecked(current.enabled);
        views.enabledInput.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));
        page.content.addView(views.enabledInput, matchWrap());

        addSectionHeader(page.content, "提醒日期", "", null);
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        views.smartButton = outlineButton("智能提醒");
        views.fixedButton = outlineButton("固定日期");
        modeRow.addView(views.smartButton, withMargins(weightWrap(1), 0, 0, dp(8), 0));
        modeRow.addView(views.fixedButton, weightWrap(1));
        page.content.addView(modeRow, matchWrap());

        views.smartNote = noticeBand(
                "",
                COLOR_MUTED,
                COLOR_SOFT,
                COLOR_LINE
        );
        page.content.addView(views.smartNote, withMargins(matchWrap(), 0, dp(8), 0, dp(4)));

        views.advanceDaysInput = input(
                current.advanceDaysText(),
                "例如 7,3,1,0",
                InputType.TYPE_CLASS_TEXT
        );
        views.fixedDaysField = formField(
                "固定提前天数（英文逗号分隔，0 表示到期当天）",
                views.advanceDaysInput
        );
        page.content.addView(views.fixedDaysField, matchWrap());

        views.todaySlotsInput = input(
                current.todaySlotsText(),
                "例如 08:30,18:00",
                InputType.TYPE_CLASS_TEXT
        );
        addFormField(
                page.content,
                "到期当天提醒时段（英文逗号分隔）",
                views.todaySlotsInput
        );

        addSectionHeader(page.content, "通知权限", "", null);
        LinearLayout permissionRow = new LinearLayout(this);
        permissionRow.setOrientation(LinearLayout.HORIZONTAL);
        permissionRow.setGravity(Gravity.CENTER_VERTICAL);
        permissionRow.setMinimumHeight(dp(64));
        permissionRow.setPadding(dp(12), dp(8), dp(10), dp(8));
        permissionRow.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));

        views.permissionStatus = text("", 14, COLOR_TEXT, Typeface.BOLD);
        permissionRow.addView(views.permissionStatus, weightWrap(1));
        views.permissionButton = outlineButton("授权");
        views.permissionButton.setMinWidth(0);
        views.permissionButton.setPadding(dp(14), 0, dp(14), 0);
        views.permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestNotificationPermission(true);
            }
        });
        permissionRow.addView(views.permissionButton, withMargins(wrapWrap(), dp(8), 0, 0, 0));
        page.content.addView(permissionRow, matchWrap());

        Button testButton = outlineButton("发送测试提醒");
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendTestReminder();
            }
        });
        page.content.addView(testButton, withMargins(matchWrap(), 0, dp(8), 0, 0));

        views.smartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                views.mode = ReminderSettings.MODE_SMART;
                updateReminderModeControls();
            }
        });
        views.fixedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                views.mode = ReminderSettings.MODE_FIXED;
                updateReminderModeControls();
            }
        });
        updateReminderModeControls();
        updateReminderUtilityPermissionStatus();

        addUtilityFooter(
                page,
                "取消",
                new View.OnClickListener() {
                    @Override public void onClick(View view) { requestCloseUtilityPage(); }
                },
                "保存设置",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ReminderSettings next = ReminderSettings.fromInput(
                                views.enabledInput.isChecked(),
                                views.mode,
                                clean(views.advanceDaysInput),
                                clean(views.todaySlotsInput)
                        );
                        if (next == null) {
                            ReminderSettings advanceProbe = ReminderSettings.fromInput(
                                    views.enabledInput.isChecked(),
                                    views.mode,
                                    clean(views.advanceDaysInput),
                                    ReminderSettings.DEFAULT_TODAY_SLOTS_TEXT
                            );
                            if (advanceProbe == null) {
                                showFieldError(
                                        views.advanceDaysInput,
                                        "请用英文逗号填写非负整数，例如 7,3,1,0",
                                        page.scroll
                                );
                            } else {
                                showFieldError(
                                        views.todaySlotsInput,
                                        "请用英文逗号填写 00:00-23:59 的时间",
                                        page.scroll
                                );
                            }
                            return;
                        }
                        views.saved = true;
                        applyReminderSettings(next);
                        closeUtilityPageImmediately();
                    }
                },
                true
        );
        views.initialFingerprint = reminderPageFingerprint(views);
    }

    private void updateReminderModeControls() {
        ReminderPageViews views = activeReminderPage;
        if (views == null) {
            return;
        }
        boolean smart = ReminderSettings.MODE_SMART.equals(views.mode);
        views.smartButton.setBackground(rounded(
                smart ? COLOR_PRIMARY_SOFT : Color.WHITE,
                dp(8),
                smart ? COLOR_PRIMARY : COLOR_LINE
        ));
        views.smartButton.setTextColor(smart ? COLOR_PRIMARY : COLOR_TEXT);
        views.fixedButton.setBackground(rounded(
                smart ? Color.WHITE : COLOR_PRIMARY_SOFT,
                dp(8),
                smart ? COLOR_LINE : COLOR_PRIMARY
        ));
        views.fixedButton.setTextColor(smart ? COLOR_TEXT : COLOR_PRIMARY);
        views.fixedDaysField.setVisibility(smart ? View.GONE : View.VISIBLE);
        views.smartNote.setText(smart
                ? "新增商品时计算一次；只有类别、保存方式、日期或开封状态改变时才重新计算。"
                : "固定模式只使用设定的提前天数，之后不会随剩余天数变化。");
    }

    private String reminderPageFingerprint(ReminderPageViews views) {
        if (views == null) {
            return "";
        }
        return (views.enabledInput.isChecked() ? "1" : "0")
                + "|" + FoodItem.cleanText(views.mode)
                + "|" + clean(views.advanceDaysInput)
                + "|" + clean(views.todaySlotsInput);
    }

    private boolean reminderPageHasChanges() {
        ReminderPageViews views = activeReminderPage;
        return views != null
                && !views.saved
                && !views.initialFingerprint.equals(reminderPageFingerprint(views));
    }

    private void updateReminderUtilityPermissionStatus() {
        ReminderPageViews views = activeReminderPage;
        if (views == null || views.permissionStatus == null || views.permissionButton == null) {
            return;
        }
        boolean allowed = ReminderScheduler.canPostNotifications(this);
        views.permissionStatus.setText(allowed ? "通知权限已开启" : "通知权限尚未开启");
        views.permissionStatus.setTextColor(allowed ? COLOR_SUCCESS : COLOR_WARNING);
        views.permissionButton.setVisibility(allowed ? View.GONE : View.VISIBLE);
    }

    private void sendTestReminder() {
        if (reminderPageHasChanges()) {
            toast("请先保存当前提醒设置");
            return;
        }
        if (!reminderSettings.enabled) {
            toast("请先启用并保存手机提醒");
            return;
        }
        if (!ReminderScheduler.canPostNotifications(this)) {
            requestNotificationPermission(true);
            return;
        }
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.setAction(ReminderScheduler.ACTION_DAILY_BRIEFING);
        sendBroadcast(intent);
        toast("测试提醒已发送");
    }
    private void buildJsonTransferPage() {
        UtilityPageLayout page = beginUtilityPage(
                "JSON 快速导入导出",
                "跨设备同步独立批次"
        );
        addSectionHeader(page.content, "同步操作", "", null);
        page.content.addView(settingsActionRow(
                "导入 JSON",
                "相同批次编号按更新时间更新，不重复新增",
                android.R.drawable.ic_menu_upload,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { startJsonImport(); }
                }
        ), matchWrap());
        page.content.addView(settingsActionRow(
                "筛选导出与分享",
                "按入库、生产或最终日期选择批次",
                android.R.drawable.ic_menu_share,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { showJsonExportFilter(); }
                }
        ), matchWrap());
        page.content.addView(noticeBand(
                "导入前会先预览。只有更新时间较新的数量和状态会更新；错误文件不会部分写入。",
                COLOR_SUCCESS,
                Color.rgb(233, 247, 240),
                Color.rgb(184, 222, 204)
        ), withMargins(matchWrap(), 0, dp(16), 0, 0));
    }

    private void buildJsonExportFilterPage() {
        UtilityPageLayout page = beginUtilityPage(
                "筛选 JSON 导出",
                "日期边界包含当天"
        );
        final JsonFilterViews views = new JsonFilterViews();
        views.createdFrom = dateInput("", "开始");
        views.createdTo = dateInput("", "结束");
        views.productionFrom = dateInput("", "开始");
        views.productionTo = dateInput("", "结束");
        views.expiryFrom = dateInput("", "开始");
        views.expiryTo = dateInput("", "结束");
        activeJsonFilterPage = views;

        addSectionHeader(page.content, "导出范围", "", null);
        addJsonDateRange(page.content, "入库时间", views.createdFrom, views.createdTo);
        addJsonDateRange(page.content, "生产日期", views.productionFrom, views.productionTo);
        addJsonDateRange(page.content, "最终日期", views.expiryFrom, views.expiryTo);
        page.content.addView(noticeBand(
                "留空表示不限。入库时间按批次首次保存日期计算。",
                COLOR_MUTED,
                COLOR_SOFT,
                COLOR_LINE
        ), withMargins(matchWrap(), 0, dp(10), 0, 0));

        addUtilityFooter(
                page,
                "保存文件",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        FoodJsonTransfer.ExportFilter filter = jsonExportFilter(
                                views.createdFrom, views.createdTo,
                                views.productionFrom, views.productionTo,
                                views.expiryFrom, views.expiryTo
                        );
                        if (validateJsonExportFilter(filter)) {
                            views.submitted = true;
                            startJsonExport(filter);
                        }
                    }
                },
                "直接分享",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        FoodJsonTransfer.ExportFilter filter = jsonExportFilter(
                                views.createdFrom, views.createdTo,
                                views.productionFrom, views.productionTo,
                                views.expiryFrom, views.expiryTo
                        );
                        if (validateJsonExportFilter(filter)) {
                            views.submitted = true;
                            shareJsonExport(filter);
                        }
                    }
                },
                true
        );
    }

    private boolean jsonFilterHasValues() {
        JsonFilterViews views = activeJsonFilterPage;
        if (views == null || views.submitted) {
            return false;
        }
        return clean(views.createdFrom).length() > 0
                || clean(views.createdTo).length() > 0
                || clean(views.productionFrom).length() > 0
                || clean(views.productionTo).length() > 0
                || clean(views.expiryFrom).length() > 0
                || clean(views.expiryTo).length() > 0;
    }

    private void buildExcelManagementPage() {
        UtilityPageLayout page = beginUtilityPage(
                "Excel 批量管理",
                "预览、校验后再写入"
        );
        addSectionHeader(page.content, "批量处理", "", null);
        page.content.addView(settingsActionRow(
                "导入 Excel",
                "支持追加或覆盖，错误行会单独列出",
                android.R.drawable.ic_menu_upload,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { startExcelImport(); }
                }
        ), matchWrap());
        page.content.addView(settingsActionRow(
                "导出 Excel",
                "导出商品档案、条码和每个独立批次",
                android.R.drawable.ic_menu_save,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { startExcelExport(); }
                }
        ), matchWrap());
        page.content.addView(noticeBand(
                "覆盖导入与保存失败都使用事务回滚，当前本机库存不会被部分替换。",
                COLOR_SUCCESS,
                Color.rgb(233, 247, 240),
                Color.rgb(184, 222, 204)
        ), withMargins(matchWrap(), 0, dp(16), 0, 0));
    }

    private void buildBackupRecoveryPage() {
        final UtilityPageLayout page = beginUtilityPage(
                "备份与恢复",
                "私有校验备份"
        );
        addSectionHeader(page.content, "当前库存", "", null);
        page.content.addView(settingsActionRow(
                "创建当前备份",
                InventoryPresentation.group(foods).size() + " 个商品 · "
                        + foods.size() + " 个批次",
                android.R.drawable.ic_menu_save,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        createCurrentPrivateBackup();
                    }
                }
        ), matchWrap());

        final RoomMigrationBackup.BackupCatalog catalog =
                RoomMigrationBackup.verifiedCatalog(
                        this,
                        FoodStore.CURRENT_SCHEMA_VERSION
                );
        addSectionHeader(page.content, "可恢复备份", "", null);
        if (catalog.candidates.isEmpty()) {
            selectedBackupPath = "";
            page.content.addView(noticeBand(
                    "尚无通过长度、哈希和数据格式校验的备份。",
                    COLOR_MUTED,
                    COLOR_SOFT,
                    COLOR_LINE
            ), matchWrap());
        } else {
            boolean selectedExists = false;
            for (RoomMigrationBackup.BackupCandidate candidate : catalog.candidates) {
                if (candidate.file.getAbsolutePath().equals(selectedBackupPath)) {
                    selectedExists = true;
                    break;
                }
            }
            if (!selectedExists) {
                selectedBackupPath = catalog.candidates.get(0).file.getAbsolutePath();
            }

            RadioGroup group = new RadioGroup(this);
            group.setOrientation(RadioGroup.VERTICAL);
            for (RoomMigrationBackup.BackupCandidate candidate : catalog.candidates) {
                final String path = candidate.file.getAbsolutePath();
                RadioButton option = new RadioButton(this);
                option.setId(View.generateViewId());
                option.setTag(path);
                option.setText(
                        readableBackupTime(candidate.createdAt)
                                + "\n" + candidate.productCount + " 个商品 · "
                                + candidate.batchCount + " 个批次 · "
                                + formatBackupSize(candidate.payloadLength)
                                + " · 校验 " + shortBackupHash(candidate.payloadSha256)
                );
                option.setTextColor(COLOR_TEXT);
                option.setTextSize(14);
                option.setGravity(Gravity.CENTER_VERTICAL);
                option.setMinHeight(dp(72));
                option.setMaxLines(3);
                option.setLineSpacing(dp(2), 1.0f);
                option.setPadding(dp(8), dp(8), dp(8), dp(8));
                option.setBackground(rounded(Color.WHITE, 0, COLOR_LINE));
                option.setChecked(path.equals(selectedBackupPath));
                group.addView(option, matchWrap());
            }
            group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                    View selected = radioGroup.findViewById(checkedId);
                    Object tag = selected == null ? null : selected.getTag();
                    selectedBackupPath = tag instanceof String ? (String) tag : "";
                    page.primaryButton.setEnabled(selectedBackupPath.length() > 0);
                    page.primaryButton.setAlpha(selectedBackupPath.length() > 0 ? 1.0f : 0.45f);
                }
            });
            page.content.addView(group, matchWrap());
            page.content.addView(noticeBand(
                    "已默认选择最新一份；恢复前请核对时间和校验编号。",
                    COLOR_MUTED,
                    COLOR_SOFT,
                    COLOR_LINE
            ), withMargins(matchWrap(), 0, dp(8), 0, 0));
        }

        if (catalog.rejectedFiles > 0) {
            page.content.addView(noticeBand(
                    catalog.rejectedFiles + " 个文件未通过校验，已自动排除。",
                    COLOR_WARNING,
                    Color.rgb(255, 248, 229),
                    Color.rgb(235, 205, 137)
            ), withMargins(matchWrap(), 0, dp(10), 0, 0));
        }
        page.content.addView(noticeBand(
                "恢复前会自动备份当前库存。恢复失败或文件被篡改时，当前数据保持不变。",
                COLOR_SUCCESS,
                Color.rgb(233, 247, 240),
                Color.rgb(184, 222, 204)
        ), withMargins(matchWrap(), 0, dp(12), 0, 0));

        addUtilityFooter(
                page,
                "刷新",
                new View.OnClickListener() {
                    @Override public void onClick(View view) { renderUtilityPage(); }
                },
                "恢复所选备份",
                new View.OnClickListener() {
                    @Override public void onClick(View view) { confirmSelectedBackupRestore(); }
                },
                selectedBackupPath.length() > 0
        );
    }

    private void createCurrentPrivateBackup() {
        try {
            String raw = FoodStoreMigration.serialize(
                    copyFoods(foods),
                    FoodStore.CURRENT_SCHEMA_VERSION
            );
            RoomMigrationBackup.BackupDescriptor descriptor =
                    RoomMigrationBackup.ensureBackup(
                            this,
                            raw,
                            FoodStore.CURRENT_SCHEMA_VERSION
                    );
            selectedBackupPath = descriptor.file.getAbsolutePath();
            renderUtilityPage();
            toast("当前库存备份已创建并校验");
        } catch (Exception error) {
            String message = FoodItem.cleanText(error.getMessage());
            toast(message.length() == 0 ? "备份创建失败" : "备份创建失败：" + message);
        }
    }

    private void confirmSelectedBackupRestore() {
        RoomMigrationBackup.BackupCandidate selected = selectedBackupCandidate();
        if (selected == null) {
            toast("请选择一个通过校验的备份");
            return;
        }
        final File file = selected.file;
        new AlertDialog.Builder(this)
                .setTitle("恢复这个备份？")
                .setMessage(
                        "目标时间：" + readableBackupTime(selected.createdAt) + "\n"
                                + "校验编号：" + shortBackupHash(selected.payloadSha256) + "\n"
                                + "内容：" + selected.productCount + " 个商品、"
                                + selected.batchCount + " 个批次。\n\n"
                                + "当前库存会先建立校验备份；恢复失败时不会改动当前数据。"
                )
                .setNegativeButton("取消", null)
                .setPositiveButton("确认恢复", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        restoreVerifiedBackup(file);
                    }
                })
                .show();
    }

    private RoomMigrationBackup.BackupCandidate selectedBackupCandidate() {
        RoomMigrationBackup.BackupCatalog catalog =
                RoomMigrationBackup.verifiedCatalog(
                        this,
                        FoodStore.CURRENT_SCHEMA_VERSION
                );
        for (RoomMigrationBackup.BackupCandidate candidate : catalog.candidates) {
            if (candidate.file.getAbsolutePath().equals(selectedBackupPath)) {
                return candidate;
            }
        }
        return null;
    }

    private void restoreVerifiedBackup(File file) {
        boolean forceFailure = qaForceNextImportSaveFailure;
        qaForceNextImportSaveFailure = false;
        RoomMigrationBackup.RestoreResult result = RoomMigrationBackup.restore(
                this,
                store,
                foods,
                file,
                forceFailure
        );
        if (!result.success) {
            toast(result.message.length() == 0
                    ? "恢复失败，当前库存未改动"
                    : result.message);
            return;
        }

        foods = copyFoods(result.restoredFoods);
        dismissUndoBar();
        lastPersistedFoods = copyFoods(result.restoredFoods);
        ReminderScheduler.scheduleDaily(this, reminderSettings);
        renderUtilityPage();
        toast("备份已恢复：" + foods.size() + " 个批次");
    }

    private String readableBackupTime(String value) {
        String cleanValue = FoodItem.cleanText(value);
        if (cleanValue.length() >= 16) {
            return cleanValue.substring(0, 16).replace('T', ' ');
        }
        return cleanValue.length() == 0 ? "时间未知" : cleanValue;
    }

    private String shortBackupHash(String value) {
        String hash = FoodItem.cleanText(value);
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }

    private String formatBackupSize(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0d);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0d * 1024.0d));
    }
    private void buildRecognitionStatusPage() {
        UtilityPageLayout page = beginUtilityPage(
                "识别状态",
                "本地包装识别组件"
        );
        boolean debugEnabled = RecognitionDebugStore.isEnabled(this);
        addSectionHeader(page.content, "当前能力", "", null);
        page.content.addView(settingsStatusRow(
                "中文包装文字",
                "中文与拉丁文字协同识别",
                "已就绪",
                COLOR_SUCCESS
        ), matchWrap());
        page.content.addView(settingsStatusRow(
                "喷码与低对比度日期",
                "文字定位、局部增强和日期专用识别",
                "已就绪",
                COLOR_SUCCESS
        ), matchWrap());
        page.content.addView(settingsStatusRow(
                "条码与商品信息",
                "本地历史优先，未命中时再查询",
                "已启用",
                COLOR_SUCCESS
        ), matchWrap());
        page.content.addView(settingsStatusRow(
                "识别诊断",
                "帧、对焦、文字区域、模型耗时与最终字段",
                debugEnabled ? "记录中" : "已关闭",
                debugEnabled ? COLOR_SUCCESS : COLOR_PRIMARY
        ), matchWrap());

        addSectionHeader(page.content, "真机调试", "", null);
        final CheckBox debugToggle = new CheckBox(this);
        debugToggle.setText("开启识别调试模式");
        debugToggle.setTextColor(COLOR_TEXT);
        debugToggle.setTextSize(15);
        debugToggle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        debugToggle.setGravity(Gravity.CENTER_VERTICAL);
        debugToggle.setMinHeight(dp(58));
        debugToggle.setPadding(dp(10), 0, dp(10), 0);
        debugToggle.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));
        debugToggle.setChecked(debugEnabled);
        final boolean[] updatingToggle = new boolean[] { false };
        debugToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (updatingToggle[0]) {
                    return;
                }
                if (checked) {
                    try {
                        RecognitionDebugStore.enable(
                                MainActivity.this,
                                recognitionDebugEnvironment()
                        );
                        toast("调试模式已开启，下一次识别会记录完整状态");
                        renderUtilityPage();
                    } catch (IOException error) {
                        updatingToggle[0] = true;
                        debugToggle.setChecked(false);
                        updatingToggle[0] = false;
                        toast("无法开启调试模式：" + FoodItem.cleanText(error.getMessage()));
                    }
                    return;
                }
                updatingToggle[0] = true;
                debugToggle.setChecked(true);
                updatingToggle[0] = false;
                showDisableRecognitionDebugDialog();
            }
        });
        page.content.addView(debugToggle, matchWrap());

        String shareSubtitle = RecognitionDebugStore.hasShareableLog(this)
                ? "已生成本机日志，可发送到 QQ、微信或其他应用"
                : "完成一次识别后可分享";
        page.content.addView(settingsActionRow(
                "分享调试日志",
                shareSubtitle,
                android.R.drawable.ic_menu_share,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        shareRecognitionDebugLog();
                    }
                }
        ), withMargins(matchWrap(), 0, dp(8), 0, 0));
        page.content.addView(noticeBand(
                "日志只在开启期间写入本机，包含设备状态、相机与模型耗时、识别文字和最终字段；分享前由你确认接收应用。",
                COLOR_MUTED,
                Color.rgb(241, 245, 249),
                COLOR_LINE
        ), withMargins(matchWrap(), 0, dp(12), 0, 0));

        addUtilityFooter(
                page,
                "扫码查找",
                new View.OnClickListener() {
                    @Override public void onClick(View view) { startBarcodeScanner(); }
                },
                "开始包装识别",
                new View.OnClickListener() {
                    @Override public void onClick(View view) { startDateOcrScanner(); }
                },
                true
        );
    }

    private void showDisableRecognitionDebugDialog() {
        new AlertDialog.Builder(this)
                .setTitle("关闭识别调试模式？")
                .setMessage("关闭后不再记录新状态。可以先把现有日志分享到 QQ、微信或其他应用。")
                .setPositiveButton("关闭并分享", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        RecognitionDebugStore.disable(MainActivity.this);
                        renderUtilityPage();
                        shareRecognitionDebugLog();
                    }
                })
                .setNeutralButton("仅关闭", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        RecognitionDebugStore.disable(MainActivity.this);
                        renderUtilityPage();
                        toast("调试模式已关闭，已有日志仍保留在本机");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void shareRecognitionDebugLog() {
        try {
            File file = RecognitionDebugStore.buildShareFile(this, "");
            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, contentUri);
            share.putExtra(Intent.EXTRA_SUBJECT, "食期管家识别调试日志");
            share.setClipData(ClipData.newRawUri("识别调试日志", contentUri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享到 QQ、微信或其他应用"));
        } catch (IOException | IllegalArgumentException | ActivityNotFoundException error) {
            toast("日志分享失败：" + FoodItem.cleanText(error.getMessage()));
        }
    }

    private String recognitionDebugEnvironment() {
        return "device=" + Build.MANUFACTURER + "/" + Build.MODEL
                + "; sdk=" + Build.VERSION.SDK_INT
                + "; app=" + appVersionLabel()
                + "; package=" + getPackageName()
                + "; screen=" + getResources().getDisplayMetrics().widthPixels
                + "x" + getResources().getDisplayMetrics().heightPixels
                + "; density=" + getResources().getDisplayMetrics().densityDpi;
    }

    private void buildAboutPage() {
        UtilityPageLayout page = beginUtilityPage(
                "版本与说明",
                "食期管家 Android 本地版"
        );
        addSectionHeader(page.content, "应用", "", null);
        page.content.addView(settingsStatusRow(
                "当前版本",
                appVersionLabel(),
                "本地版",
                COLOR_PRIMARY
        ), matchWrap());
        page.content.addView(settingsStatusRow(
                "库存数据",
                "Room 数据库与私有校验备份",
                "仅本机",
                COLOR_SUCCESS
        ), matchWrap());
        page.content.addView(settingsStatusRow(
                "包装图片",
                "识别过程默认不上传图片或库存内容",
                "本地处理",
                COLOR_SUCCESS
        ), matchWrap());
        page.content.addView(noticeBand(
                "升级安装会沿用原数据。卸载应用会删除私有库存，请先使用 JSON 或 Excel 导出。",
                COLOR_WARNING,
                Color.rgb(255, 248, 229),
                Color.rgb(235, 205, 137)
        ), withMargins(matchWrap(), 0, dp(16), 0, 0));
    }
    private void setButtonIcon(Button button, int iconResource, int tint) {
        button.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        button.setGravity(Gravity.CENTER);
        Drawable icon = getDrawable(iconResource).mutate();
        icon.setTint(tint);
        icon.setBounds(0, 0, dp(18), dp(18));
        SpannableString content = new SpannableString("\uFFFC  " + button.getText());
        content.setSpan(
                new ImageSpan(icon, ImageSpan.ALIGN_BASELINE),
                0,
                1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        button.setText(content);
    }

    private View reminderCard() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(12), dp(8), dp(10), dp(8));
        row.setBackground(rounded(COLOR_CARD, dp(8), COLOR_LINE));
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
        reminderPermissionButton.setMinHeight(dp(48));
        reminderPermissionButton.setMinimumHeight(dp(48));
        reminderPermissionButton.setPadding(dp(10), 0, dp(10), 0);
        reminderPermissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestNotificationPermission(true);
            }
        });
        row.addView(reminderPermissionButton, withMargins(wrapWrap(), 0, 0, dp(6), 0));

        TextView settingsHint = text("设置 ›", 12, COLOR_PRIMARY, Typeface.BOLD);
        row.addView(settingsHint, wrapWrap());

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
        if (!reminderSettings.enabled && !fromUserAction) {
            updateReminderStatus();
            updateReminderUtilityPermissionStatus();
            return;
        }

        if (Build.VERSION.SDK_INT < 33) {
            if (reminderSettings.enabled) {
                ReminderScheduler.scheduleDaily(this, reminderSettings);
            }
            if (fromUserAction) {
                toast(reminderSettings.enabled ? "手机提醒已开启" : "通知权限已可用");
            }
            updateReminderStatus();
            updateReminderUtilityPermissionStatus();
            return;
        }

        if (ReminderScheduler.canPostNotifications(this)) {
            if (reminderSettings.enabled) {
                ReminderScheduler.scheduleDaily(this, reminderSettings);
            }
            if (fromUserAction) {
                toast(reminderSettings.enabled ? "手机提醒已开启" : "通知权限已可用");
            }
            updateReminderStatus();
            updateReminderUtilityPermissionStatus();
            return;
        }

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean prompted = preferences.getBoolean(PREF_NOTIFICATION_PERMISSION_PROMPTED, false);
        if (!fromUserAction && prompted) {
            updateReminderStatus();
            updateReminderUtilityPermissionStatus();
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
        openUtilityPage(UTILITY_REMINDER);
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
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_DESTINATION, currentDestination);
        if (foodEditorVisible && activeFoodEditor != null) {
            outState.putBundle(STATE_FOOD_EDITOR, captureFoodEditorState(activeFoodEditor));
        } else if (pendingFoodEditorRestore != null) {
            outState.putBundle(STATE_FOOD_EDITOR, new Bundle(pendingFoodEditorRestore));
        }
        outState.putString(STATE_DATE_REQUEST_DRAFT, pendingDateRecognitionDraftId);
        if (pendingDateRecognitionResult != null) {
            outState.putParcelable(STATE_DATE_RESULT, pendingDateRecognitionResult);
        }
        if (productDetailVisible) {
            outState.putString(STATE_DETAIL_PROFILE, visibleProductProfileId);
        }
        super.onSaveInstanceState(outState);
    }

    private Bundle captureFoodEditorState(FoodEditorViews editor) {
        Bundle state = new Bundle();
        state.putString("draft_id", editor.draftId);
        FoodItem source = editor.sourceFood == null ? new FoodItem() : editor.sourceFood;
        state.putInt("mode", editor.mode);
        state.putBoolean("protect", editor.protectInitialContent);
        state.putString("initial_fingerprint", editor.initialFingerprint);
        state.putString("source_id", FoodItem.cleanText(source.id));
        state.putString("profile_id", FoodItem.cleanText(source.productProfileId));
        state.putString("created_at", FoodItem.cleanText(source.createdAt));
        state.putString("updated_at", FoodItem.cleanText(source.updatedAt));
        state.putBoolean("finished", source.isFinished);
        state.putString("finished_at", FoodItem.cleanText(source.finishedAt));

        state.putString("name", editor.nameInput.getText().toString());
        state.putString("category", selectedOption(FoodData.CATEGORIES, editor.categoryInput));
        state.putString("barcode", editor.barcodeInput.getText().toString());
        state.putString("quantity", editor.quantityInput.getText().toString());
        state.putString("remaining", editor.remainingInput.getText().toString());
        state.putString("unit", editor.unitInput.getText().toString());
        state.putString("storage", selectedOption(FoodData.STORAGE_METHODS, editor.storageInput));
        state.putString("location", selectedOption(FoodData.LOCATION_OPTIONS, editor.locationInput));
        state.putString("custom_location", editor.customLocationInput.getText().toString());
        state.putString("production_date", editor.productionDateInput.getText().toString());
        state.putBoolean("date_pending", editor.datePendingInput.isChecked());
        state.putBoolean("no_expiry", editor.explicitNoExpiryInput.isChecked());
        state.putString("shelf_life", editor.shelfLifeInput.getText().toString());
        state.putString("shelf_unit", selectedOption(
                FoodData.SHELF_LIFE_UNITS,
                editor.shelfLifeUnitInput
        ));
        state.putString("expiry_date", editor.expiryDateInput.getText().toString());
        state.putBoolean("manual_expiry", editor.manualExpiryOverride[0]);
        state.putString("opened_date", editor.openedDateInput.getText().toString());
        state.putString("after_open", editor.afterOpenShelfLifeInput.getText().toString());
        state.putString("after_open_unit", selectedOption(
                FoodData.SHELF_LIFE_UNITS,
                editor.afterOpenShelfLifeUnitInput
        ));
        state.putString("notes", editor.notesInput.getText().toString());

        state.putBoolean("return_detail", editor.returnToProductDetail);
        state.putString("return_profile", editor.returnProductProfileId);
        state.putInt("return_destination", editor.returnDestination);
        state.putInt("return_scroll", editor.returnScrollY);
        int[] utilityPages = new int[editor.returnUtilityPages.size()];
        for (int index = 0; index < editor.returnUtilityPages.size(); index++) {
            utilityPages[index] = editor.returnUtilityPages.get(index);
        }
        state.putIntArray("return_utilities", utilityPages);
        state.putInt("editor_scroll", editor.scrollView == null ? 0 : editor.scrollView.getScrollY());
        return state;
    }

    private void restoreFoodEditorState(Bundle state) {
        if (state == null) {
            return;
        }
        int mode = state.getInt("mode", FoodEditorSession.MODE_CREATE);
        boolean isEdit = mode == FoodEditorSession.MODE_EDIT;
        boolean isReplenishment = mode == FoodEditorSession.MODE_REPLENISH;
        FoodItem draft = isEdit ? findFoodBatch(state.getString("source_id", "")) : null;
        draft = draft == null ? new FoodItem() : draft.copy();
        draft.id = state.getString("source_id", draft.id);
        draft.productProfileId = state.getString("profile_id", draft.productProfileId);
        draft.createdAt = state.getString("created_at", draft.createdAt);
        draft.updatedAt = state.getString("updated_at", draft.updatedAt);
        draft.isFinished = state.getBoolean("finished", draft.isFinished);
        draft.finishedAt = state.getString("finished_at", draft.finishedAt);
        draft.name = state.getString("name", "");
        draft.category = state.getString("category", "other");
        draft.barcode = state.getString("barcode", "");
        Double quantity = parseNumber(state.getString("quantity", ""), Double.valueOf(1d));
        Double remaining = parseNumber(state.getString("remaining", ""), quantity);
        draft.quantity = quantity == null ? 1d : quantity.doubleValue();
        draft.remainingQuantity = remaining == null ? draft.quantity : remaining.doubleValue();
        draft.unit = state.getString("unit", "件");
        draft.storageMethod = state.getString("storage", "room_temp");
        draft.location = FoodData.resolveLocationValue(
                state.getString("location", FoodData.LOCATION_UNSPECIFIED),
                state.getString("custom_location", "")
        );
        draft.productionDate = state.getString("production_date", "");
        String shelfText = state.getString("shelf_life", "");
        draft.shelfLifeValue = shelfText.length() == 0
                ? null
                : parsePositiveInteger(shelfText);
        draft.shelfLifeUnit = state.getString("shelf_unit", "day");
        draft.expiryDate = state.getString("expiry_date", "");
        draft.dateSource = state.getBoolean("no_expiry", false)
                ? "none"
                : state.getBoolean("date_pending", false)
                ? "unknown"
                : state.getBoolean("manual_expiry", false) ? "manual" : "calculated";
        draft.openedDate = state.getString("opened_date", "");
        String afterOpenText = state.getString("after_open", "");
        draft.afterOpenShelfLifeValue = afterOpenText.length() == 0
                ? null
                : parsePositiveInteger(afterOpenText);
        draft.afterOpenShelfLifeUnit = state.getString("after_open_unit", "day");
        draft.notes = state.getString("notes", "");

        showFoodForm(draft, isEdit, isReplenishment);
        FoodEditorViews editor = activeFoodEditor;
        if (editor == null) {
            return;
        }
        editor.draftId = state.getString("draft_id", editor.draftId);
        editor.nameInput.setText(state.getString("name", ""));
        editor.barcodeInput.setText(state.getString("barcode", ""));
        editor.quantityInput.setText(state.getString("quantity", ""));
        editor.remainingInput.setText(state.getString("remaining", ""));
        editor.unitInput.setText(state.getString("unit", ""));
        editor.customLocationInput.setText(state.getString("custom_location", ""));
        editor.productionDateInput.setText(state.getString("production_date", ""));
        editor.shelfLifeInput.setText(state.getString("shelf_life", ""));
        editor.expiryDateInput.setText(state.getString("expiry_date", ""));
        editor.manualExpiryOverride[0] = state.getBoolean("manual_expiry", false);
        editor.datePendingInput.setChecked(state.getBoolean("date_pending", false));
        editor.explicitNoExpiryInput.setChecked(state.getBoolean("no_expiry", false));
        editor.openedDateInput.setText(state.getString("opened_date", ""));
        editor.afterOpenShelfLifeInput.setText(state.getString("after_open", ""));
        editor.notesInput.setText(state.getString("notes", ""));
        editor.protectInitialContent = state.getBoolean("protect", false);
        if (editor.subtitleView != null) {
            editor.subtitleView.setText(foodEditorSubtitle(editor));
        }
        editor.initialFingerprint = state.getString(
                "initial_fingerprint",
                foodEditorFingerprint(editor)
        );
        editor.returnToProductDetail = state.getBoolean("return_detail", false);
        editor.returnProductProfileId = state.getString("return_profile", "");
        editor.returnDestination = AppNavigation.restoreDestination(
                state.getInt("return_destination", AppNavigation.OVERVIEW)
        );
        editor.returnScrollY = state.getInt("return_scroll", 0);
        editor.returnUtilityPages.clear();
        int[] utilityPages = state.getIntArray("return_utilities");
        if (utilityPages != null) {
            for (int utilityPage : utilityPages) {
                editor.returnUtilityPages.add(utilityPage);
            }
        }
        restoreScrollPosition(state.getInt("editor_scroll", 0));
        pendingFoodEditorRestore = null;
        if (editor.draftId.equals(pendingDateRecognitionDraftId)) {
            pendingReplenishmentRecognitionTarget = editor.recognitionTarget;
            applyPendingDateRecognitionResult();
        } else {
            clearPendingDateRecognition();
        }
    }

    private FoodItem findFoodBatch(String batchId) {
        String wanted = FoodItem.cleanText(batchId);
        for (FoodItem food : foods) {
            if (wanted.equals(FoodItem.cleanText(food.id))) {
                return food;
            }
        }
        return null;
    }

    @Override
    public void onBackPressed() {
        if (foodEditorVisible) {
            requestCloseFoodEditor();
            return;
        }
        if (productDetailVisible) {
            returnFromProductDetail();
            return;
        }
        if (isUtilityPageVisible()) {
            requestCloseUtilityPage();
            return;
        }
        if (currentDestination != AppNavigation.OVERVIEW) {
            showDestination(AppNavigation.OVERVIEW);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateReminderStatus();
        updateReminderUtilityPermissionStatus();
    }

    @Override
    protected void onDestroy() {
        startupDestroyed = true;
        dismissUndoBar();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (reminderSettings.enabled) {
                ReminderScheduler.scheduleDaily(this, reminderSettings);
            }
            toast(reminderSettings.enabled ? "手机提醒已开启" : "通知权限已开启");
        } else {
            toast("未获得通知权限，暂时无法发送手机提醒");
        }
        updateReminderStatus();
        updateReminderUtilityPermissionStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_JSON_EXPORT) {
                pendingJsonExportFilter = null;
            }
            if (requestCode == REQUEST_REPLENISHMENT_OCR) {
                clearPendingDateRecognition();
            }
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
        } else if (requestCode == REQUEST_REPLENISHMENT_OCR) {
            handleReplenishmentRecognitionResult(data);
        } else if (requestCode == REQUEST_JSON_EXPORT) {
            FoodJsonTransfer.ExportFilter filter = pendingJsonExportFilter;
            pendingJsonExportFilter = null;
            writeJsonExport(data == null ? null : data.getData(), filter);
        } else if (requestCode == REQUEST_JSON_IMPORT) {
            readJsonImport(data == null ? null : data.getData());
        }
    }

    private void startBarcodeScanner() {
        Intent intent = new Intent(this, DateOcrScanActivity.class);
        intent.putExtra(DateOcrScanActivity.EXTRA_BARCODE_LOOKUP_ONLY, true);
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
        List<FoodItem> imported = preview.importableFoods(replaceExisting, foods);
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

        lastPersistedFoods = copyFoods(foods);
        ReminderScheduler.scheduleDaily(MainActivity.this, reminderSettings);
        renderFilterControls();
        renderFoods();
        toast((replaceExisting ? "已覆盖导入 " : "已追加导入 ") + imported.size() + " 条食品");
    }

    private boolean saveFoodsForImport(List<FoodItem> nextFoods) {
        try {
            String raw = FoodStoreMigration.serialize(
                    copyFoods(foods),
                    FoodStore.CURRENT_SCHEMA_VERSION
            );
            RoomMigrationBackup.BackupDescriptor backup =
                    RoomMigrationBackup.ensureBackup(
                            this,
                            raw,
                            FoodStore.CURRENT_SCHEMA_VERSION
                    );
            if (backup.file == null || !backup.file.exists()) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }
        InventoryCommandResult result = inventoryCommandService(true)
                .replaceAllValidated(nextFoods);
        if (!result.isSuccess()) {
            showCommandError(result);
        }
        return result.isSuccess();
    }
    private void showJsonTransferMenu() {
        openUtilityPage(UTILITY_JSON);
    }

    private void showJsonExportFilter() {
        openUtilityPage(UTILITY_JSON_FILTER);
    }
    private void addJsonDateRange(
            LinearLayout parent,
            String label,
            final EditText from,
            final EditText to
    ) {
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(label, 14, COLOR_TEXT, Typeface.BOLD);
        titleRow.addView(title, weightWrap(1));
        Button clear = utilityButton("清空");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                from.setText("");
                to.setText("");
            }
        });
        titleRow.addView(clear, fixed(68, 40));
        parent.addView(titleRow, matchWrap());

        LinearLayout range = new LinearLayout(this);
        range.setOrientation(LinearLayout.HORIZONTAL);
        range.setGravity(Gravity.CENTER_VERTICAL);
        range.addView(from, weightWrap(1));
        TextView separator = text("至", 14, COLOR_MUTED, Typeface.NORMAL);
        separator.setGravity(Gravity.CENTER);
        range.addView(separator, fixed(40, 44));
        range.addView(to, weightWrap(1));
        parent.addView(range, withMargins(matchWrap(), 0, 0, 0, dp(8)));
    }

    private FoodJsonTransfer.ExportFilter jsonExportFilter(
            EditText createdFrom,
            EditText createdTo,
            EditText productionFrom,
            EditText productionTo,
            EditText expiryFrom,
            EditText expiryTo
    ) {
        return new FoodJsonTransfer.ExportFilter(
                clean(createdFrom),
                clean(createdTo),
                clean(productionFrom),
                clean(productionTo),
                clean(expiryFrom),
                clean(expiryTo)
        );
    }

    private boolean validateJsonExportFilter(FoodJsonTransfer.ExportFilter filter) {
        try {
            filter.validate();
            int count = 0;
            for (FoodItem food : foods) {
                if (filter.matches(food)) {
                    count++;
                }
            }
            if (count == 0) {
                toast("筛选范围内没有可导出的批次");
                return false;
            }
            return true;
        } catch (IllegalArgumentException error) {
            toast(FoodItem.cleanText(error.getMessage()));
            return false;
        }
    }

    private void startJsonExport(FoodJsonTransfer.ExportFilter filter) {
        pendingJsonExportFilter = filter;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(FoodJsonTransfer.MIME_TYPE);
        intent.putExtra(Intent.EXTRA_TITLE, FoodJsonTransfer.fileName(DateRules.nowIsoLike()));
        try {
            startActivityForResult(intent, REQUEST_JSON_EXPORT);
        } catch (ActivityNotFoundException error) {
            pendingJsonExportFilter = null;
            toast("当前设备没有可用的文件保存器");
        }
    }

    private void writeJsonExport(Uri uri, FoodJsonTransfer.ExportFilter filter) {
        if (uri == null) {
            toast("未选择导出位置");
            return;
        }
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                toast("无法打开导出文件");
                return;
            }
            FoodJsonTransfer.ExportResult result = FoodJsonTransfer.write(
                    outputStream,
                    foods,
                    filter,
                    DateRules.nowIsoLike()
            );
            toast("已导出 " + result.count + " 个批次");
        } catch (IOException | IllegalArgumentException error) {
            toast("JSON 导出失败：" + FoodItem.cleanText(error.getMessage()));
        }
    }

    private void shareJsonExport(FoodJsonTransfer.ExportFilter filter) {
        File directory = new File(getCacheDir(), "shared-transfers");
        if (!directory.exists() && !directory.mkdirs()) {
            toast("无法准备分享文件");
            return;
        }
        File file = new File(directory, FoodJsonTransfer.fileName(DateRules.nowIsoLike()));
        try (OutputStream outputStream = new FileOutputStream(file)) {
            FoodJsonTransfer.ExportResult result = FoodJsonTransfer.write(
                    outputStream,
                    foods,
                    filter,
                    DateRules.nowIsoLike()
            );
            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(FoodJsonTransfer.MIME_TYPE);
            share.putExtra(Intent.EXTRA_STREAM, contentUri);
            share.putExtra(Intent.EXTRA_SUBJECT, "食期管家批次数据");
            share.setClipData(ClipData.newRawUri("食期管家 JSON", contentUri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享 " + result.count + " 个批次"));
        } catch (IOException | IllegalArgumentException | ActivityNotFoundException error) {
            toast("JSON 分享失败：" + FoodItem.cleanText(error.getMessage()));
        }
    }

    private void startJsonImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(FoodJsonTransfer.MIME_TYPE);
        try {
            startActivityForResult(intent, REQUEST_JSON_IMPORT);
        } catch (ActivityNotFoundException error) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("*/*");
            try {
                startActivityForResult(Intent.createChooser(fallback, "选择 JSON 文件"), REQUEST_JSON_IMPORT);
            } catch (ActivityNotFoundException ignored) {
                toast("当前设备没有可用的文件选择器");
            }
        }
    }

    private void readJsonImport(Uri uri) {
        if (uri == null) {
            toast("未选择导入文件");
            return;
        }
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                toast("无法打开导入文件");
                return;
            }
            showJsonImportPreview(FoodJsonTransfer.read(inputStream, foods));
        } catch (IOException error) {
            toast("JSON 导入失败：" + FoodItem.cleanText(error.getMessage()));
        }
    }

    private void showJsonImportPreview(final FoodJsonTransfer.ImportPreview preview) {
        StringBuilder message = new StringBuilder();
        message.append("文件批次：").append(preview.total).append('\n');
        message.append("新增：").append(preview.additions).append('\n');
        message.append("更新数量/状态：").append(preview.updates).append('\n');
        message.append("内容相同：").append(preview.unchanged).append('\n');
        message.append("保留本机较新数据：").append(preview.conflicts).append('\n');
        message.append("错误：").append(preview.errors.size());
        if (!preview.errors.isEmpty()) {
            message.append("\n\n文件有错误，为避免部分导入，本次不会写入：");
            int limit = Math.min(8, preview.errors.size());
            for (int index = 0; index < limit; index++) {
                message.append("\n- ").append(preview.errors.get(index));
            }
        } else if (preview.conflicts > 0) {
            message.append("\n\n较旧或时间相同但内容不同的批次不会覆盖本机，可继续同步其他批次。");
        }
        message.append("\n\n确认前不会写入；保存失败时本机数据不会改变。");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("JSON 同步预览")
                .setMessage(message.toString())
                .setNegativeButton("取消", null);
        if (preview.canApply()) {
            builder.setPositiveButton("确认同步", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    applyJsonImport(preview);
                }
            });
        } else {
            builder.setPositiveButton("知道了", null);
        }
        builder.show();
    }

    private void applyJsonImport(FoodJsonTransfer.ImportPreview preview) {
        List<FoodItem> nextFoods = copyFoods(preview.mergedFoods);
        if (!saveFoodsForImport(nextFoods)) {
            toast("同步失败，当前本地数据未改动");
            return;
        }
        lastPersistedFoods = copyFoods(foods);
        ReminderScheduler.scheduleDaily(MainActivity.this, reminderSettings);
        renderFilterControls();
        renderFoods();
        toast("同步完成：新增 " + preview.additions + "，更新 " + preview.updates);
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private String exportTimestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
    }

    private void showBarcodeResolution(String barcode, FoodItem recognizedDraft) {
        String normalized = BarcodeUtils.digitsOnly(barcode);
        List<FoodItem> profiles = ProductProfileIndex.fromFoods(foods)
                .findByBarcode(normalized);
        activeBarcodeResolution = BarcodeResolutionState.fromMatches(
                normalized,
                profiles
        );
        activeBarcodeRecognizedDraft = recognizedDraft == null
                ? null
                : recognizedDraft.copy();
        activeBarcodeProductInfo = null;
        activeBarcodeMessage = "";
        activeBarcodeState = profiles.isEmpty()
                ? BARCODE_STATE_LOADING
                : BARCODE_STATE_LOCAL;
        openUtilityPage(UTILITY_BARCODE);
        if (profiles.isEmpty()) {
            queryBarcodeProduct(normalized);
        }
    }

    private void buildBarcodeResolutionPage() {
        final BarcodeResolutionState state = activeBarcodeResolution;
        String barcode = state == null ? "" : state.barcode();
        final UtilityPageLayout page = beginUtilityPage(
                "扫码结果",
                barcode.length() == 0 ? "等待有效条码" : "条码 " + barcode
        );
        if (state == null || barcode.length() == 0) {
            page.content.addView(noticeBand(
                    "没有读取到可用条码。",
                    COLOR_WARNING,
                    Color.rgb(255, 248, 229),
                    Color.rgb(235, 205, 137)
            ), matchWrap());
            addUtilityFooter(
                    page,
                    "",
                    null,
                    "返回",
                    new View.OnClickListener() {
                        @Override public void onClick(View view) { requestCloseUtilityPage(); }
                    },
                    true
            );
            return;
        }

        if (activeBarcodeState == BARCODE_STATE_LOCAL) {
            buildLocalBarcodeMatches(page, state);
            return;
        }
        if (activeBarcodeState == BARCODE_STATE_ONLINE && activeBarcodeProductInfo != null) {
            buildOnlineBarcodeResult(page, state);
            return;
        }
        if (activeBarcodeState == BARCODE_STATE_LOADING) {
            addSectionHeader(page.content, "正在查询", "", null);
            page.content.addView(noticeBand(
                    "正在核对本机商品档案和商品码信息…",
                    COLOR_PRIMARY,
                    COLOR_PRIMARY_SOFT,
                    Color.rgb(184, 202, 246)
            ), matchWrap());
            addUtilityFooter(
                    page,
                    "作为新商品",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            showNewProductForBarcode(state.barcode(), activeBarcodeRecognizedDraft);
                        }
                    },
                    "查询中",
                    null,
                    false
            );
            return;
        }

        boolean failed = activeBarcodeState == BARCODE_STATE_ERROR;
        addSectionHeader(page.content, failed ? "查询暂不可用" : "未找到商品档案", "", null);
        String message = FoodItem.cleanText(activeBarcodeMessage);
        if (message.length() == 0) {
            message = failed
                    ? "网络或商品信息服务暂时不可用，条码已保留。"
                    : "这个条码尚未在本机或商品信息服务中找到。";
        }
        page.content.addView(noticeBand(
                message,
                failed ? COLOR_WARNING : COLOR_MUTED,
                failed ? Color.rgb(255, 248, 229) : COLOR_SOFT,
                failed ? Color.rgb(235, 205, 137) : COLOR_LINE
        ), matchWrap());
        addUtilityFooter(
                page,
                "重新查询",
                new View.OnClickListener() {
                    @Override public void onClick(View view) { retryBarcodeLookup(); }
                },
                "新增商品",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showNewProductForBarcode(state.barcode(), activeBarcodeRecognizedDraft);
                    }
                },
                true
        );
    }

    private void buildLocalBarcodeMatches(
            final UtilityPageLayout page,
            final BarcodeResolutionState state
    ) {
        int count = state.matchCount();
        addSectionHeader(
                page.content,
                count == 1 ? "已找到商品" : "请选择要补货的商品",
                "",
                null
        );
        if (count > 1) {
            page.content.addView(noticeBand(
                    "同一条码对应 " + count + " 个商品档案，不会自动替你选择。",
                    COLOR_WARNING,
                    Color.rgb(255, 248, 229),
                    Color.rgb(235, 205, 137)
            ), withMargins(matchWrap(), 0, 0, 0, dp(8)));
        }

        final RadioGroup choices = new RadioGroup(this);
        choices.setOrientation(RadioGroup.VERTICAL);
        for (FoodItem profile : state.profiles()) {
            String key = BarcodeResolutionState.profileKey(profile);
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(key);
            option.setText(profile.name + "\n" + barcodeProfileSummary(profile));
            option.setTextColor(COLOR_TEXT);
            option.setTextSize(14);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setMinHeight(dp(76));
            option.setMaxLines(4);
            option.setLineSpacing(dp(2), 1.0f);
            option.setPadding(dp(8), dp(8), dp(8), dp(8));
            option.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));
            option.setChecked(key.equals(state.selectedProfileKey()));
            choices.addView(option, withMargins(matchWrap(), 0, 0, 0, dp(8)));
        }
        choices.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                View selected = radioGroup.findViewById(checkedId);
                Object tag = selected == null ? null : selected.getTag();
                boolean valid = tag instanceof String
                        && state.selectProfile((String) tag);
                page.primaryButton.setEnabled(valid);
                page.primaryButton.setAlpha(valid ? 1.0f : 0.45f);
            }
        });
        page.content.addView(choices, matchWrap());

        addUtilityFooter(
                page,
                "作为新商品",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showNewProductForBarcode(state.barcode(), activeBarcodeRecognizedDraft);
                    }
                },
                "补货新批次",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        FoodItem selected = state.selectedProfile();
                        if (selected == null) {
                            toast("请先选择要补货的商品");
                            return;
                        }
                        showReplenishmentForm(selected, activeBarcodeRecognizedDraft);
                    }
                },
                state.canReplenish()
        );
    }

    private String barcodeProfileSummary(FoodItem profile) {
        InventoryPresentation.ProductGroup group = InventoryPresentation.find(
                foods,
                profile.productProfileId
        );
        String category = FoodData.categoryLabel(profile.category);
        String storage = FoodData.storageLabel(profile.storageMethod);
        if (group == null) {
            return category + " · " + storage;
        }
        String expiry = DateRules.isValidDateString(group.nearestExpiryDate)
                ? "最近到期 " + group.nearestExpiryDate
                : "未记录到期日";
        return category + " · " + storage + "\n"
                + group.activeBatchCount + " 个在库批次 · " + expiry;
    }

    private void buildOnlineBarcodeResult(
            final UtilityPageLayout page,
            final BarcodeResolutionState state
    ) {
        BarcodeProductInfo info = activeBarcodeProductInfo;
        addSectionHeader(page.content, "查询到商品信息", "", null);
        page.content.addView(settingsStatusRow(
                info.displayName(),
                barcodeOnlineSummary(info),
                "待确认",
                COLOR_PRIMARY
        ), matchWrap());
        page.content.addView(noticeBand(
                "商品信息只会填入新增表单，生产日期、保质期和数量仍需确认后保存。",
                COLOR_SUCCESS,
                Color.rgb(233, 247, 240),
                Color.rgb(184, 222, 204)
        ), withMargins(matchWrap(), 0, dp(10), 0, 0));
        addUtilityFooter(
                page,
                "重新查询",
                new View.OnClickListener() {
                    @Override public void onClick(View view) { retryBarcodeLookup(); }
                },
                "填入新增表单",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showNewProductForBarcode(state.barcode(), activeBarcodeRecognizedDraft);
                    }
                },
                true
        );
    }

    private String barcodeOnlineSummary(BarcodeProductInfo info) {
        List<String> parts = new ArrayList<String>();
        if (FoodItem.cleanText(info.brand).length() > 0) {
            parts.add(info.brand);
        }
        String specification = FoodItem.cleanText(info.specification).length() > 0
                ? info.specification
                : info.netContent;
        if (FoodItem.cleanText(specification).length() > 0) {
            parts.add(specification);
        }
        if (FoodItem.cleanText(info.source).length() > 0) {
            parts.add(info.source);
        }
        return parts.isEmpty() ? "商品码信息" : joinTexts(parts, " · ");
    }

    private void retryBarcodeLookup() {
        BarcodeResolutionState state = activeBarcodeResolution;
        if (state == null) {
            return;
        }
        activeBarcodeState = BARCODE_STATE_LOADING;
        activeBarcodeProductInfo = null;
        activeBarcodeMessage = "";
        renderUtilityPage();
        queryBarcodeProduct(state.barcode());
    }

    private boolean isActiveBarcode(String barcode) {
        return activeBarcodeResolution != null
                && activeBarcodeResolution.barcode().equals(BarcodeUtils.digitsOnly(barcode));
    }
    private void handleBarcodeValue(String value) {
        String barcode = BarcodeUtils.extractProductCode(value);
        if (!BarcodeUtils.isSupportedProductCode(barcode)) {
            toast("条码格式不正确，请检查数字和校验位");
            return;
        }
        showBarcodeResolution(barcode, null);
    }
    private void showNewProductForBarcode(String barcode, FoodItem recognizedDraft) {
        FoodItem draft = recognizedDraft == null ? new FoodItem() : recognizedDraft.copy();
        draft.productProfileId = "";
        draft.barcode = BarcodeUtils.digitsOnly(barcode);

        BarcodeProductInfo info = activeBarcodeProductInfo;
        if (info != null && info.found && isActiveBarcode(barcode)) {
            String onlineName = info.displayName();
            if (FoodItem.cleanText(draft.name).length() == 0 && onlineName.length() > 0) {
                draft.name = onlineName;
            }
            String inferredCategory = BarcodeCategoryClassifier.inferCategory(info);
            if (FoodItem.cleanText(draft.category).length() == 0
                    || "other".equals(draft.category)) {
                draft.category = inferredCategory;
            }
            if (FoodItem.cleanText(draft.unit).length() == 0) {
                draft.unit = "件";
            }
            draft.notes = mergeBarcodeNotes(draft.notes, info.notes());
        }
        showFoodForm(draft, false);
    }

    private String mergeBarcodeNotes(String current, String onlineNotes) {
        String left = FoodItem.cleanText(current);
        String right = FoodItem.cleanText(onlineNotes);
        if (right.length() == 0 || left.contains(right)) {
            return left;
        }
        return left.length() == 0 ? right : left + "\n" + right;
    }

    private void showReplenishmentForm(FoodItem profile, FoodItem recognizedDraft) {
        FoodItem draft = ReplenishmentDraftFactory.createDraft(profile);
        draft = ReplenishmentRecognitionMerge.mergeDates(draft, recognizedDraft);
        showFoodForm(draft, false, true);
    }

    private void queryBarcodeProduct(final String barcode) {
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

    private void handleBarcodeLookupResult(String barcode, BarcodeProductInfo info) {
        if (!isActiveBarcode(barcode)) {
            return;
        }
        if (info == null || !info.found) {
            activeBarcodeProductInfo = null;
            activeBarcodeState = BARCODE_STATE_NOT_FOUND;
            activeBarcodeMessage = info == null
                    ? ""
                    : FoodItem.cleanText(info.registrationMessage);
        } else {
            activeBarcodeProductInfo = info;
            activeBarcodeState = BARCODE_STATE_ONLINE;
            activeBarcodeMessage = "";
        }
        if (isUtilityPageVisible() && currentUtilityPage() == UTILITY_BARCODE) {
            renderUtilityPage();
        }
    }

    private void showBarcodeLookupError(String barcode, Exception exception) {
        if (!isActiveBarcode(barcode)) {
            return;
        }
        activeBarcodeProductInfo = null;
        activeBarcodeState = BARCODE_STATE_ERROR;
        activeBarcodeMessage = exception == null
                ? ""
                : FoodItem.cleanText(exception.getMessage());
        if (isUtilityPageVisible() && currentUtilityPage() == UTILITY_BARCODE) {
            renderUtilityPage();
        }
    }
    private void handleUnifiedRecognitionResult(Intent data) {
        if (data == null) {
            toast("未收到识别候选");
            return;
        }

        final FoodItem draft = recognitionDraftFromIntent(data);
        if (!UnifiedRecognitionPayload.hasUsableDraft(draft)) {
            showFoodForm(draft, false);
            toast("识别结果不完整，请手动补充后保存");
            return;
        }

        if (BarcodeUtils.isSupportedProductCode(draft.barcode)) {
            showBarcodeResolution(draft.barcode, draft);
        } else {
            showFoodForm(draft, false);
        }
    }

    private FoodItem recognitionDraftFromIntent(Intent data) {
        if (data == null) {
            return new FoodItem();
        }
        Integer shelfLifeValue = data != null && data.hasExtra(DateOcrResultPayload.EXTRA_SHELF_LIFE_VALUE)
                ? Integer.valueOf(data.getIntExtra(DateOcrResultPayload.EXTRA_SHELF_LIFE_VALUE, 0))
                : null;
        return UnifiedRecognitionPayload.toDraft(
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
    }

    private void handleReplenishmentRecognitionResult(Intent data) {
        if (pendingDateRecognitionDraftId.length() == 0 || data == null) {
            clearPendingDateRecognition();
            toast("补货表单已关闭，未回填识别结果");
            return;
        }
        pendingDateRecognitionResult = new Intent(data);
        applyPendingDateRecognitionResult();
    }

    private void clearPendingDateRecognition() {
        pendingDateRecognitionDraftId = "";
        pendingDateRecognitionResult = null;
        pendingReplenishmentRecognitionTarget = null;
    }

    private void beginDateRecognition(FoodEditorViews editor) {
        if (!foodEditorVisible || activeFoodEditor != editor) {
            return;
        }
        clearPendingDateRecognition();
        pendingDateRecognitionDraftId = editor.draftId;
        pendingReplenishmentRecognitionTarget = editor.recognitionTarget;
    }

    private void applyPendingDateRecognitionResult() {
        if (pendingDateRecognitionResult == null || pendingFoodEditorRestore != null) {
            return;
        }
        if (!foodEditorVisible || activeFoodEditor == null
                || !activeFoodEditor.draftId.equals(pendingDateRecognitionDraftId)) {
            clearPendingDateRecognition();
            return;
        }
        ReplenishmentRecognitionTarget target = pendingReplenishmentRecognitionTarget;
        if (target == null) {
            return;
        }
        if (target != activeFoodEditor.recognitionTarget) {
            clearPendingDateRecognition();
            return;
        }
        Intent data = pendingDateRecognitionResult;
        clearPendingDateRecognition();

        FoodItem recognized = recognitionDraftFromIntent(data);
        if (!ReplenishmentRecognitionMerge.hasRecognizedDate(recognized)) {
            toast("未识别到本批次日期，请在当前表单手动填写");
            return;
        }

        target.datePendingInput.setChecked(false);
        target.explicitNoExpiryInput.setChecked(false);
        if (DateRules.isValidDateString(recognized.productionDate)) {
            target.productionDateInput.setText(recognized.productionDate);
        }
        if (recognized.shelfLifeValue != null && recognized.shelfLifeValue.intValue() > 0) {
            target.shelfLifeInput.setText(String.valueOf(recognized.shelfLifeValue));
            target.shelfLifeUnitInput.setSelection(indexOf(
                    FoodData.SHELF_LIFE_UNITS,
                    recognized.shelfLifeUnit,
                    "day"
            ));
        }

        boolean directExpiry = DateRules.isValidDateString(recognized.expiryDate)
                && !"calculated".equals(recognized.dateSource);
        target.manualExpiryOverride[0] = directExpiry;
        if (directExpiry) {
            target.updatingCalculatedExpiry[0] = true;
            target.expiryDateInput.setText(recognized.expiryDate);
            target.updatingCalculatedExpiry[0] = false;
        }
        target.updateNoExpiryViews.run();
        target.updateCalculatedExpiry.run();
        toast("已回填本批次日期，请核对后保存");
    }

    private static final class UtilityPageLayout {
        final LinearLayout root;
        final LinearLayout content;
        final ScrollView scroll;
        Button primaryButton;

        UtilityPageLayout(LinearLayout root, LinearLayout content, ScrollView scroll) {
            this.root = root;
            this.content = content;
            this.scroll = scroll;
        }
    }

    private static final class ReminderPageViews {
        CheckBox enabledInput;
        Button smartButton;
        Button fixedButton;
        String mode = ReminderSettings.MODE_SMART;
        TextView smartNote;
        LinearLayout fixedDaysField;
        EditText advanceDaysInput;
        EditText todaySlotsInput;
        TextView permissionStatus;
        Button permissionButton;
        String initialFingerprint = "";
        boolean saved;
    }

    private static final class JsonFilterViews {
        EditText createdFrom;
        EditText createdTo;
        EditText productionFrom;
        EditText productionTo;
        EditText expiryFrom;
        EditText expiryTo;
        boolean submitted;
    }
    private static final class ReplenishmentRecognitionTarget {
        final EditText productionDateInput;
        final EditText shelfLifeInput;
        final Spinner shelfLifeUnitInput;
        final EditText expiryDateInput;
        final CheckBox datePendingInput;
        final CheckBox explicitNoExpiryInput;
        final boolean[] manualExpiryOverride;
        final boolean[] updatingCalculatedExpiry;
        final Runnable updateCalculatedExpiry;
        final Runnable updateNoExpiryViews;

        ReplenishmentRecognitionTarget(
                EditText productionDateInput,
                EditText shelfLifeInput,
                Spinner shelfLifeUnitInput,
                EditText expiryDateInput,
                CheckBox datePendingInput,
                CheckBox explicitNoExpiryInput,
                boolean[] manualExpiryOverride,
                boolean[] updatingCalculatedExpiry,
                Runnable updateCalculatedExpiry,
                Runnable updateNoExpiryViews
        ) {
            this.productionDateInput = productionDateInput;
            this.shelfLifeInput = shelfLifeInput;
            this.shelfLifeUnitInput = shelfLifeUnitInput;
            this.expiryDateInput = expiryDateInput;
            this.datePendingInput = datePendingInput;
            this.explicitNoExpiryInput = explicitNoExpiryInput;
            this.manualExpiryOverride = manualExpiryOverride;
            this.updatingCalculatedExpiry = updatingCalculatedExpiry;
            this.updateCalculatedExpiry = updateCalculatedExpiry;
            this.updateNoExpiryViews = updateNoExpiryViews;
        }
    }

    private static final class FoodEditorViews {
        String draftId = UUID.randomUUID().toString();
        ReplenishmentRecognitionTarget recognitionTarget;
        FoodItem sourceFood;
        boolean isEdit;
        int mode;
        boolean protectInitialContent;
        String initialFingerprint = "";
        boolean returnToProductDetail;
        String returnProductProfileId = "";
        int returnDestination = AppNavigation.OVERVIEW;
        int returnScrollY;
        List<Integer> returnUtilityPages = new ArrayList<Integer>();
        EditText nameInput;
        Spinner categoryInput;
        EditText barcodeInput;
        EditText quantityInput;
        EditText remainingInput;
        EditText unitInput;
        Spinner storageInput;
        Spinner locationInput;
        EditText customLocationInput;
        EditText productionDateInput;
        CheckBox datePendingInput;
        CheckBox explicitNoExpiryInput;
        EditText shelfLifeInput;
        Spinner shelfLifeUnitInput;
        EditText expiryDateInput;
        boolean[] manualExpiryOverride;
        EditText openedDateInput;
        EditText afterOpenShelfLifeInput;
        Spinner afterOpenShelfLifeUnitInput;
        EditText notesInput;
        ScrollView scrollView;
        TextView subtitleView;
        Button cancelButton;
        Button saveButton;
    }

    private static final class EditorFieldError {
        final EditText field;
        final String message;

        EditorFieldError(EditText field, String message) {
            this.field = field;
            this.message = message;
        }
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
            TextView empty = text(reminderSettings.enabled ? "没有需要优先处理的商品" : "提醒已关闭，商品仍按日期排序", 15, COLOR_TEXT, Typeface.BOLD);
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
        if (currentDestination == AppNavigation.OVERVIEW) {
            renderOverview();
            return;
        }
        if (currentDestination == AppNavigation.SETTINGS) {
            updateReminderStatus();
            return;
        }
        renderInventoryFoods();
    }

    private void renderOverview() {
        if (listContainer == null) {
            return;
        }

        int today = 0;
        int soon = 0;
        int expired = 0;
        for (FoodItem food : foods) {
            if (InventoryRules.isFinished(food)) {
                continue;
            }
            String status = DateRules.getExpiryStatus(food.expiryDate);
            if (DateRules.STATUS_TODAY.equals(status)) {
                today++;
            } else if (DateRules.STATUS_SOON.equals(status)) {
                soon++;
            } else if (DateRules.STATUS_EXPIRED.equals(status)) {
                expired++;
            }
        }
        if (overviewTodayValue != null) {
            overviewTodayValue.setText(String.valueOf(today));
        }
        if (overviewSoonValue != null) {
            overviewSoonValue.setText(String.valueOf(soon));
        }
        if (overviewExpiredValue != null) {
            overviewExpiredValue.setText(String.valueOf(expired));
        }

        renderDailyBriefing();
        listContainer.removeAllViews();
        List<FoodItem> priorityFoods = overviewPriorityFoods();
        if (priorityFoods.isEmpty()) {
            listContainer.addView(emptyGuideCard(), matchWrap());
            return;
        }

        int limit = Math.min(4, priorityFoods.size());
        for (int index = 0; index < limit; index++) {
            listContainer.addView(
                    overviewFoodRow(priorityFoods.get(index)),
                    withMargins(matchWrap(), 0, 0, 0, dp(8))
            );
        }
    }

    private List<FoodItem> overviewPriorityFoods() {
        List<FoodItem> result = new ArrayList<FoodItem>();
        for (FoodItem food : foods) {
            if (!InventoryRules.isFinished(food)) {
                result.add(food);
            }
        }
        Collections.sort(result, new Comparator<FoodItem>() {
            @Override
            public int compare(FoodItem left, FoodItem right) {
                int statusCompare = statusRank(foodStatus(left)) - statusRank(foodStatus(right));
                if (statusCompare != 0) {
                    return statusCompare;
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
                return left.name.compareTo(right.name);
            }
        });
        return result;
    }

    private View overviewFoodRow(final FoodItem food) {
        LinearLayout row = card();
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showFoodDetail(food); }
        });

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(top, matchWrap());

        TextView name = text(food.name, 16, COLOR_TEXT, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(name, weightWrap(1));

        TextView status = text(FoodData.statusLabel(foodStatus(food)), 12, statusColor(food), Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(3), dp(8), dp(3));
        status.setBackground(rounded(statusBgColor(food), dp(8), 0));
        top.addView(status, wrapWrap());

        String dateText = isNoExpiryFood(food) ? "无过期时间" : emptyFallback(food.expiryDate, "日期待补充");
        TextView meta = text(
                dateText + " · 剩余 " + formatNumber(food.remainingQuantity) + " " + displayUnit(food.unit),
                13,
                COLOR_MUTED,
                Typeface.NORMAL
        );
        meta.setPadding(0, dp(5), 0, 0);
        row.addView(meta, matchWrap());
        return row;
    }

    private void renderInventoryFoods() {
        if (listContainer == null || statsText == null || activeFilterBar == null) {
            return;
        }

        normalizeFilterSelections();
        listContainer.removeAllViews();
        boolean hasActiveFilters = FoodItem.cleanText(searchQuery).length() > 0
                || statusFilterActive
                || categoryFilterActive
                || locationFilterActive;
        activeFilterBar.setVisibility(hasActiveFilters ? View.VISIBLE : View.GONE);
        if (hasActiveFilters) {
            renderActiveFilterBar();
        }

        List<FoodItem> visibleFoods = filteredSortedFoods();
        List<InventoryPresentation.ProductGroup> visibleProducts =
                InventoryPresentation.group(visibleFoods);
        statsText.setText(buildStatsText(visibleProducts));
        if (foods.isEmpty()) {
            listContainer.addView(emptyGuideCard(), matchWrap());
            return;
        }
        if (visibleProducts.isEmpty()) {
            TextView empty = text("当前搜索和筛选下没有商品", 15, COLOR_MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(28), dp(12), dp(28));
            empty.setBackground(rounded(COLOR_CARD, dp(8), COLOR_LINE));
            listContainer.addView(empty, matchWrap());
            return;
        }

        for (InventoryPresentation.ProductGroup product : visibleProducts) {
            listContainer.addView(
                    productRow(product),
                    withMargins(matchWrap(), 0, 0, 0, dp(8))
            );
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

        addFilterButtonGrid(statusFilterContainer, options, new FilterButtonBinder() {
            @Override
            public Button createButton(final Option option) {
                boolean selected = statusFilterActive && selectedStatuses.contains(option.value);
                Button button = filterButton(option.label, selected);
                button.setSingleLine(false);
                button.setMaxLines(2);
                button.setGravity(Gravity.CENTER);
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
        button.setMinHeight(dp(48));
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTextColor(selected ? Color.WHITE : COLOR_TEXT);
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
        button.setMinHeight(dp(48));
        button.setMinimumHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setTextColor(COLOR_PRIMARY);
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY_SOFT));
        if (listener != null) {
            button.setOnClickListener(listener);
        }
        return button;
    }

    private String buildStatsText(List<InventoryPresentation.ProductGroup> products) {
        int productCount = products == null ? 0 : products.size();
        int batchCount = 0;
        if (products != null) {
            for (InventoryPresentation.ProductGroup product : products) {
                batchCount += product.batches.size();
            }
        }
        return productCount + " 种商品 · " + batchCount + " 个批次 · 最早到期优先";
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
                    : !InventoryRules.isFinished(food);
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

                if (InventoryRules.isFinished(left) && InventoryRules.isFinished(right)) {
                    return right.finishedAt.compareTo(left.finishedAt);
                }

                return left.name.compareTo(right.name);
            }
        });

        return result;
    }

    private String foodStatus(FoodItem food) {
        return InventoryRules.isFinished(food)
                ? DateRules.STATUS_FINISHED
                : DateRules.getExpiryStatus(food.expiryDate);
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
        TextView heading = text("还没有商品", 18, COLOR_TEXT, Typeface.BOLD);
        card.addView(heading, matchWrap());

        TextView copy = text("新增后会按到期日显示在这里。", 14, COLOR_MUTED, Typeface.NORMAL);
        copy.setLineSpacing(dp(2), 1.0f);
        copy.setPadding(0, dp(6), 0, 0);
        card.addView(copy, matchWrap());

        return card;
    }

    private View productRow(final InventoryPresentation.ProductGroup product) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(88));
        row.setPadding(dp(10), dp(10), dp(8), dp(10));
        row.setBackground(rounded(COLOR_CARD, dp(8), COLOR_LINE));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setGravity(Gravity.CENTER_VERTICAL);
        main.setMinimumHeight(dp(64));
        main.setContentDescription("查看" + product.name + "的商品详情");
        main.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showProductDetail(product.profileId);
            }
        });
        row.addView(main, weightWrap(1));

        String initial = product.name.length() == 0 ? "物" : product.name.substring(0, 1);
        TextView thumb = text(initial, 17, COLOR_TEXT, Typeface.BOLD);
        thumb.setGravity(Gravity.CENTER);
        thumb.setBackground(rounded(categoryThumbColor(product.category), dp(8), 0));
        main.addView(thumb, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        main.addView(copy, withMargins(weightWrap(1), dp(10), 0, dp(8), 0));

        TextView name = text(product.name, 16, COLOR_TEXT, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name, matchWrap());

        String batchLabel = product.activeBatchCount > 0
                ? product.activeBatchCount + " 个批次"
                : product.finishedBatchCount + " 个已用完批次";
        TextView quantity = text(
                batchLabel + " · 剩余 " + formatNumber(product.totalRemainingQuantity)
                        + " " + displayUnit(product.unit),
                12,
                COLOR_MUTED,
                Typeface.NORMAL
        );
        quantity.setPadding(0, dp(3), 0, 0);
        quantity.setMaxLines(1);
        quantity.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(quantity, matchWrap());

        String expiryLabel = DateRules.isValidDateString(product.nearestExpiryDate)
                ? FoodData.statusLabel(product.status) + " · 最早 " + product.nearestExpiryDate
                : product.allActiveNoExpiry
                        ? "无过期时间"
                        : product.activeBatchCount > 0 ? "日期待补充" : "已归档";
        TextView expiry = text(
                expiryLabel,
                12,
                statusColor(product.status),
                Typeface.BOLD
        );
        expiry.setPadding(0, dp(2), 0, 0);
        expiry.setMaxLines(1);
        expiry.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(expiry, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(actions, wrapWrap());

        final boolean canDecrease = product.decrementTarget() != null;
        TextView decrease = actionIconButton(
                R.drawable.ic_v2_minus,
                product.name + "减少 1",
                canDecrease
        );
        if (canDecrease) {
            decrease.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    decreaseProductStock(product.profileId);
                }
            });
        }
        actions.addView(decrease, new LinearLayout.LayoutParams(dp(48), dp(48)));

        Button replenish = outlineButton("补货");
        replenish.setMinWidth(0);
        replenish.setMinimumWidth(0);
        replenish.setPadding(dp(8), 0, dp(8), 0);
        replenish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                replenishProduct(product.profileId);
            }
        });
        actions.addView(replenish, withMargins(new LinearLayout.LayoutParams(dp(64), dp(48)), dp(6), 0, 0, 0));
        return row;
    }

    private TextView actionIconButton(int iconResource, String description, boolean enabled) {
        TextView button = text("", 1, enabled ? COLOR_TEXT : COLOR_MUTED, Typeface.NORMAL);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setContentDescription(description);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1.0f : 0.45f);
        button.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));
        Drawable icon = getDrawable(iconResource).mutate();
        icon.setTint(enabled ? COLOR_TEXT : COLOR_MUTED);
        icon.setBounds(0, 0, dp(20), dp(20));
        button.setCompoundDrawables(icon, null, null, null);
        return button;
    }

    private int categoryThumbColor(String category) {
        if ("dairy".equals(category) || "produce".equals(category)) {
            return Color.rgb(226, 244, 235);
        }
        if ("beverage".equals(category) || "frozen".equals(category)) {
            return Color.rgb(226, 237, 255);
        }
        if ("snack".equals(category) || "staple".equals(category)) {
            return Color.rgb(255, 241, 214);
        }
        if ("condiment".equals(category) || "meat_egg_seafood".equals(category)) {
            return Color.rgb(252, 231, 231);
        }
        return Color.rgb(235, 232, 247);
    }
    private int statusColor(FoodItem food) {
        return statusColor(foodStatus(food));
    }

    private int statusColor(String status) {
        if (DateRules.STATUS_EXPIRED.equals(status)) {
            return COLOR_DANGER;
        }
        if (DateRules.STATUS_TODAY.equals(status) || DateRules.STATUS_SOON.equals(status)) {
            return COLOR_WARNING;
        }
        if (DateRules.STATUS_UNKNOWN.equals(status) || DateRules.STATUS_FINISHED.equals(status)) {
            return Color.rgb(74, 80, 89);
        }
        return COLOR_SUCCESS;
    }

    private int statusBgColor(FoodItem food) {
        return statusBgColor(foodStatus(food));
    }

    private int statusBgColor(String status) {
        if (DateRules.STATUS_EXPIRED.equals(status)) {
            return Color.rgb(253, 235, 232);
        }
        if (DateRules.STATUS_TODAY.equals(status) || DateRules.STATUS_SOON.equals(status)) {
            return Color.rgb(255, 246, 224);
        }
        if (DateRules.STATUS_UNKNOWN.equals(status) || DateRules.STATUS_FINISHED.equals(status)) {
            return Color.rgb(241, 243, 246);
        }
        return Color.rgb(232, 247, 240);
    }
    private void showFoodDetail(final FoodItem food) {
        String profileId = InventoryPresentation.profileKey(food);
        if (profileId.length() == 0) {
            toast("这个批次缺少商品档案编号，请先编辑并保存");
            return;
        }
        showProductDetail(profileId);
    }

    private void showProductDetail(String profileId) {
        String normalizedProfileId = FoodItem.cleanText(profileId);
        InventoryPresentation.ProductGroup product =
                InventoryPresentation.find(foods, normalizedProfileId);
        if (product == null) {
            toast("商品档案不存在或已删除");
            return;
        }
        if (!productDetailVisible) {
            detailReturnDestination = currentDestination;
            detailReturnScrollY = mainScrollView == null ? 0 : mainScrollView.getScrollY();
        }
        productDetailVisible = true;
        visibleProductProfileId = normalizedProfileId;
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(View.GONE);
        }
        resetPageViews();
        renderProductDetail();
    }

    private void renderProductDetail() {
        if (!productDetailVisible || pageHost == null) {
            return;
        }
        final InventoryPresentation.ProductGroup product =
                InventoryPresentation.find(foods, visibleProductProfileId);
        if (product == null) {
            returnFromProductDetail();
            toast("最后一个批次已删除，已返回库存");
            return;
        }

        pageHost.removeAllViews();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        pageHost.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.addView(productDetailHeader(product), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        mainScrollView = new ScrollView(this);
        mainScrollView.setFillViewport(true);
        mainScrollView.setClipToPadding(false);
        root.addView(mainScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(28));
        mainScrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        content.addView(productIdentity(product), matchWrap());
        content.addView(productSummary(product), withMargins(matchWrap(), 0, dp(16), 0, 0));

        addSectionHeader(content, "批次", "", null);

        for (FoodItem batch : product.batches) {
            content.addView(
                    productBatchRow(batch),
                    withMargins(matchWrap(), 0, 0, 0, dp(8))
            );
        }

        Button replenish = button("补货新批次", COLOR_PRIMARY);
        replenish.setMinHeight(dp(52));
        replenish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                replenishProduct(product.profileId);
            }
        });
        content.addView(replenish, withMargins(matchWrap(), 0, dp(14), 0, 0));


        repositionUndoBar();
    }

    private View productDetailHeader(final InventoryPresentation.ProductGroup product) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.setBackgroundColor(Color.WHITE);

        TextView back = actionIconButton(R.drawable.ic_v2_back, "返回库存", true);
        back.setBackground(rounded(Color.WHITE, dp(8), 0));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                returnFromProductDetail();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("商品详情", 20, COLOR_TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, withMargins(weightWrap(1), dp(4), 0, dp(4), 0));

        final FoodItem representative = product.representativeBatch();
        TextView more = actionIconButton(R.drawable.ic_v2_more, "更多操作", representative != null);
        more.setBackground(rounded(Color.WHITE, dp(8), 0));
        if (representative != null) {
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showFoodActionsDialog(representative);
                }
            });
        }
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    private View productIdentity(InventoryPresentation.ProductGroup product) {
        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.HORIZONTAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setPadding(0, dp(4), 0, dp(16));

        String initial = product.name.length() == 0 ? "物" : product.name.substring(0, 1);
        TextView thumb = text(initial, 22, COLOR_TEXT, Typeface.BOLD);
        thumb.setGravity(Gravity.CENTER);
        thumb.setBackground(rounded(categoryThumbColor(product.category), dp(8), 0));
        identity.addView(thumb, new LinearLayout.LayoutParams(dp(60), dp(60)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        identity.addView(copy, withMargins(weightWrap(1), dp(12), 0, 0, 0));

        String eyebrowText = FoodData.categoryLabel(product.category)
                + (product.activeBatchCount > 0 ? " · 在库" : " · 已用完");
        TextView eyebrow = text(eyebrowText, 12, COLOR_MUTED, Typeface.BOLD);
        copy.addView(eyebrow, matchWrap());

        TextView name = text(product.name, 20, COLOR_TEXT, Typeface.BOLD);
        name.setPadding(0, dp(2), 0, 0);
        name.setMaxLines(3);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name, matchWrap());

        String barcodeText = FoodItem.cleanText(product.barcode).length() == 0
                ? "未记录条码"
                : "条码 " + product.barcode;
        TextView barcode = text(barcodeText, 12, COLOR_MUTED, Typeface.NORMAL);
        barcode.setPadding(0, dp(3), 0, 0);
        copy.addView(barcode, matchWrap());
        return identity;
    }

    private View productSummary(InventoryPresentation.ProductGroup product) {
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setBaselineAligned(false);
        summary.setPadding(dp(4), dp(10), dp(4), dp(10));
        summary.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));

        summary.addView(detailMetric(
                "剩余数量",
                formatNumber(product.totalRemainingQuantity) + " " + displayUnit(product.unit),
                COLOR_TEXT
        ), weightWrap(1));
        summary.addView(detailDivider(), new LinearLayout.LayoutParams(dp(1), dp(42)));
        summary.addView(detailMetric(
                "在库批次",
                String.valueOf(product.activeBatchCount),
                COLOR_TEXT
        ), weightWrap(1));
        summary.addView(detailDivider(), new LinearLayout.LayoutParams(dp(1), dp(42)));
        summary.addView(detailMetric(
                "最近到期",
                DateRules.isValidDateString(product.nearestExpiryDate)
                        ? product.nearestExpiryDate
                        : product.allActiveNoExpiry ? "无过期时间" : "待补充",
                statusColor(product.status)
        ), weightWrap(1));
        return summary;
    }

    private View detailMetric(String labelText, String valueText, int valueColor) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);
        metric.setPadding(dp(6), 0, dp(6), 0);

        TextView label = text(labelText, 11, COLOR_MUTED, Typeface.NORMAL);
        label.setGravity(Gravity.CENTER);
        metric.addView(label, matchWrap());
        TextView value = text(valueText, 14, valueColor, Typeface.BOLD);
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, dp(4), 0, 0);
        value.setMaxLines(2);
        value.setEllipsize(TextUtils.TruncateAt.END);
        metric.addView(value, matchWrap());
        return metric;
    }

    private View detailDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_LINE);
        return divider;
    }

    private View productBatchRow(final FoodItem batch) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(titleRow, matchWrap());

        TextView title = text(
                "批次 " + InventoryPresentation.displayBatchId(batch.id),
                15,
                COLOR_TEXT,
                Typeface.BOLD
        );
        titleRow.addView(title, weightWrap(1));
        String status = foodStatus(batch);
        TextView statusView = text(FoodData.statusLabel(status), 11, statusColor(status), Typeface.BOLD);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(8), dp(3), dp(8), dp(3));
        statusView.setBackground(rounded(statusBgColor(status), dp(8), 0));
        titleRow.addView(statusView, wrapWrap());

        String dateLine = isNoExpiryFood(batch)
                ? "无过期时间"
                : DateRules.isValidDateString(batch.expiryDate)
                        ? "最终日期 " + batch.expiryDate
                        : "日期待补充";
        TextView date = text(dateLine, 14, COLOR_TEXT, Typeface.BOLD);
        date.setPadding(0, dp(6), 0, 0);
        row.addView(date, matchWrap());

        String quantityLine = "剩余 " + formatNumber(batch.remainingQuantity)
                + "/" + formatNumber(batch.quantity) + " " + displayUnit(batch.unit)
                + " · " + FoodData.storageLabel(batch.storageMethod)
                + " · " + FoodData.locationLabel(batch.location);
        TextView quantity = text(quantityLine, 12, COLOR_MUTED, Typeface.NORMAL);
        quantity.setPadding(0, dp(3), 0, 0);
        quantity.setMaxLines(2);
        row.addView(quantity, matchWrap());

        String dates = batchDateEvidenceLine(batch);
        if (dates.length() > 0) {
            TextView evidence = text(dates, 12, COLOR_MUTED, Typeface.NORMAL);
            evidence.setPadding(0, dp(3), 0, 0);
            evidence.setMaxLines(2);
            row.addView(evidence, matchWrap());
        }

        String notes = FoodItem.cleanText(batch.notes);
        if (notes.length() > 0) {
            TextView note = text("备注 " + notes, 12, COLOR_TEXT, Typeface.NORMAL);
            note.setPadding(0, dp(5), 0, 0);
            note.setMaxLines(4);
            note.setLineSpacing(dp(2), 1.0f);
            row.addView(note, matchWrap());
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        actions.setPadding(0, dp(8), 0, 0);
        row.addView(actions, matchWrap());

        if (!InventoryRules.isFinished(batch)) {
            boolean canDecrease = batch.remainingQuantity > 0;
            TextView decrease = actionIconButton(
                    R.drawable.ic_v2_minus,
                    batch.name + "当前批次减少 1",
                    canDecrease
            );
            if (canDecrease) {
                decrease.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        decreaseBatchWithUndo(batch);
                    }
                });
            }
            actions.addView(decrease, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }

        Button edit = outlineButton(InventoryRules.isFinished(batch) ? "查看编辑" : "编辑");
        edit.setMinWidth(0);
        edit.setMinimumWidth(0);
        edit.setPadding(dp(12), 0, dp(12), 0);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFoodForm(batch);
            }
        });
        actions.addView(edit, withMargins(wrapWrap(), dp(6), 0, 0, 0));

        TextView more = actionIconButton(R.drawable.ic_v2_more, "批次更多操作", true);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFoodActionsDialog(batch);
            }
        });
        actions.addView(more, withMargins(new LinearLayout.LayoutParams(dp(48), dp(48)), dp(6), 0, 0, 0));
        return row;
    }

    private String batchDateEvidenceLine(FoodItem batch) {
        List<String> parts = new ArrayList<String>();
        if (DateRules.isValidDateString(batch.productionDate)) {
            parts.add("生产 " + batch.productionDate);
        }
        if (batch.shelfLifeValue != null && DateRules.isShelfLifeUnit(batch.shelfLifeUnit)) {
            parts.add("保质期 " + batch.shelfLifeValue + FoodData.shelfLifeUnitLabel(batch.shelfLifeUnit));
        }
        if (DateRules.isValidDateString(batch.openedDate)) {
            parts.add("已开封 " + batch.openedDate);
            String suggestedDate = DateRules.addAfterOpenShelfLife(
                    batch.openedDate,
                    batch.afterOpenShelfLifeValue,
                    batch.afterOpenShelfLifeUnit
            );
            if (DateRules.isValidDateString(suggestedDate)) {
                parts.add("开封后建议 " + suggestedDate);
            }
        }
        return joinTexts(parts, " · ");
    }


    private void returnFromProductDetail() {
        final int destination = AppNavigation.restoreDestination(detailReturnDestination);
        final int scrollY = detailReturnScrollY;
        showDestination(destination);
        if (mainScrollView != null && scrollY > 0) {
            mainScrollView.post(new Runnable() {
                @Override
                public void run() {
                    mainScrollView.scrollTo(0, scrollY);
                }
            });
        }
        repositionUndoBar();
    }

    private void replenishProduct(String profileId) {
        InventoryPresentation.ProductGroup product = InventoryPresentation.find(foods, profileId);
        FoodItem representative = product == null ? null : product.representativeBatch();
        if (representative == null) {
            toast("没有可复用的商品档案");
            return;
        }
        showReplenishDialog(representative);
    }

    private void decreaseProductStock(String profileId) {
        InventoryPresentation.ProductGroup product = InventoryPresentation.find(foods, profileId);
        FoodItem target = product == null ? null : product.decrementTarget();
        if (target == null) {
            toast("当前商品没有可减少的在库数量");
            return;
        }
        decreaseBatchWithUndo(target);
    }

    private void decreaseBatchWithUndo(FoodItem requestedBatch) {
        String batchId = requestedBatch == null ? "" : requestedBatch.id;
        InventoryCommandResult result = inventoryCommandService().decrement(batchId, 1d);
        if (!commitInventoryCommand(result, true)) {
            return;
        }
        FoodItem before = result.before;
        FoodItem updated = result.after;
        String message = InventoryRules.isFinished(updated)
                ? "已用完并停止提醒"
                : "已减少 1，剩余 " + formatNumber(updated.remainingQuantity)
                        + " " + displayUnit(updated.unit);
        showUndoBar(before, updated, message);
    }

    private FoodItem findFoodById(String id) {
        String wanted = FoodItem.cleanText(id);
        for (FoodItem item : foods) {
            if (wanted.equals(FoodItem.cleanText(item.id))) {
                return item.copy();
            }
        }
        return null;
    }

    private void showUndoBar(FoodItem before, FoodItem expected, String message) {
        dismissUndoBar();
        if (screenRoot == null || before == null || expected == null) {
            return;
        }
        pendingUndoFood = before.copy();
        pendingUndoExpected = expected.copy();
        final FoodItem undoToken = pendingUndoFood;

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(6), dp(8), dp(6));
        bar.setBackground(rounded(COLOR_TEXT, dp(8), 0));
        bar.setElevation(dp(8));

        TextView copy = text(message, 13, Color.WHITE, Typeface.NORMAL);
        copy.setMaxLines(2);
        bar.addView(copy, weightWrap(1));

        TextView undo = text("撤销", 14, Color.rgb(159, 196, 255), Typeface.BOLD);
        undo.setGravity(Gravity.CENTER);
        undo.setMinWidth(dp(64));
        undo.setMinHeight(dp(48));
        undo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (pendingUndoFood == undoToken) {
                    undoPendingDecrement();
                }
            }
        });
        bar.addView(undo, wrapWrap());

        undoBar = bar;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        params.leftMargin = dp(12);
        params.rightMargin = dp(12);
        screenRoot.addView(bar, params);
        repositionUndoBar();

        hideUndoRunnable = new Runnable() {
            @Override
            public void run() {
                dismissUndoBar();
            }
        };
        uiHandler.postDelayed(hideUndoRunnable, UNDO_BAR_DURATION_MS);
    }

    private void undoPendingDecrement() {
        FoodItem restore = pendingUndoFood;
        FoodItem expected = pendingUndoExpected;
        dismissUndoBar();
        if (restore == null || expected == null) {
            return;
        }
        for (int index = 0; index < foods.size(); index++) {
            FoodItem current = foods.get(index);
            if (!expected.id.equals(current.id)) {
                continue;
            }
            try {
                if (!expected.toJson().toString().equals(current.toJson().toString())) {
                    return;
                }
            } catch (org.json.JSONException error) {
                return;
            }
            FoodItem restored = restore.copy();
            restored.updatedAt = DateRules.nowIsoLike();
            foods.set(index, restored);
            if (saveFoodsRefreshReminders()) {
                toast("已撤销本次减量");
            }
            return;
        }
        // A deleted batch must never be recreated by a stale undo action.
    }

    private void repositionUndoBar() {
        if (undoBar == null) {
            return;
        }
        ViewGroup.LayoutParams rawParams = undoBar.getLayoutParams();
        if (!(rawParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rawParams;
        params.bottomMargin = productDetailVisible && !foodEditorVisible ? dp(12) : dp(77);
        undoBar.setLayoutParams(params);
    }

    private void dismissUndoBar() {
        if (hideUndoRunnable != null) {
            uiHandler.removeCallbacks(hideUndoRunnable);
            hideUndoRunnable = null;
        }
        if (undoBar != null && screenRoot != null) {
            screenRoot.removeView(undoBar);
        }
        undoBar = null;
        pendingUndoFood = null;
        pendingUndoExpected = null;
    }

    private boolean isNoExpiryFood(FoodItem food) {
        return food != null && "none".equals(food.dateSource);
    }
    private void showFoodForm(final FoodItem editingFood) {
        showFoodForm(editingFood, editingFood != null);
    }

    private void showFoodForm(final FoodItem sourceFood, final boolean isEdit) {
        showFoodForm(sourceFood, isEdit, false);
    }

    private void showFoodForm(
            final FoodItem sourceFood,
            final boolean isEdit,
            final boolean isReplenishment
    ) {
        final FoodItem draft = sourceFood == null ? new FoodItem() : sourceFood.copy();

        final EditText nameInput = input(draft.name, "商品名称", InputType.TYPE_CLASS_TEXT);
        final Spinner categoryInput = spinner(FoodData.CATEGORIES);
        categoryInput.setSelection(indexOf(FoodData.CATEGORIES, draft.category, "other"));
        final EditText barcodeInput = input(
                BarcodeUtils.digitsOnly(draft.barcode),
                "商品条码（可选）",
                InputType.TYPE_CLASS_NUMBER
        );

        boolean prefillReplenishmentQuantity = isReplenishment && draft.quantity > 0d;
        final EditText quantityInput = input(
                isEdit || prefillReplenishmentQuantity ? formatNumber(draft.quantity) : "1",
                isReplenishment ? "\u672c\u6279\u6b21\u5165\u5e93\u6570\u91cf" : "\u6570\u91cf",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        final EditText remainingInput = input(
                isEdit || prefillReplenishmentQuantity ? formatNumber(draft.remainingQuantity) : "",
                isReplenishment ? "\u9ed8\u8ba4\u540c\u5165\u5e93\u6570\u91cf" : "\u5269\u4f59\u6570\u91cf",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
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
        final CheckBox datePendingInput = new CheckBox(this);
        datePendingInput.setText("暂时不知道完整日期，稍后补充");
        datePendingInput.setTextColor(COLOR_TEXT);
        datePendingInput.setTextSize(14);
        datePendingInput.setMinHeight(dp(48));
        datePendingInput.setPadding(0, dp(4), 0, dp(4));
        datePendingInput.setChecked(isEdit && "unknown".equals(draft.dateSource));

        final CheckBox explicitNoExpiryInput = new CheckBox(this);
        explicitNoExpiryInput.setText("确认这个商品没有过期时间");
        explicitNoExpiryInput.setTextColor(COLOR_TEXT);
        explicitNoExpiryInput.setTextSize(14);
        explicitNoExpiryInput.setMinHeight(dp(48));
        explicitNoExpiryInput.setPadding(0, dp(4), 0, dp(4));
        explicitNoExpiryInput.setChecked(isNoExpiryFood(draft));

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

        LinearLayout basicSection = formSection("商品");
        addFormField(basicSection, "商品名称", nameInput);
        addFormField(basicSection, "商品条码（可选）", barcodeInput);
        if (isEdit && FoodItem.cleanText(draft.productProfileId).length() > 0) {
            TextView profileEditHint = text(
                    "名称、分类和单位属于商品资料，修改后会同步到这个商品的所有批次。",
                    12,
                    COLOR_MUTED,
                    Typeface.NORMAL
            );
            profileEditHint.setPadding(0, 0, 0, dp(8));
            basicSection.addView(profileEditHint, matchWrap());
        }
        form.addView(basicSection, withMargins(matchWrap(), 0, 0, 0, dp(4)));

        LinearLayout dateSection = formSection(isReplenishment ? "本批次日期" : "日期");

        final Button dateCameraButton = outlineButton(
                isReplenishment ? "相机识别本批次日期" : "相机识别日期"
        );
        dateCameraButton.setMinHeight(dp(48));
        dateSection.addView(dateCameraButton, withMargins(matchWrap(), 0, 0, 0, dp(8)));

        addFormField(dateSection, "生产日期", productionDateInput);
        dateSection.addView(datePendingInput, matchWrap());

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
                if (datePendingInput.isChecked() || explicitNoExpiryInput.isChecked()) {
                    useCalculatedExpiryButton.setVisibility(View.GONE);
                    hint.setText(explicitNoExpiryInput.isChecked()
                            ? "已明确标记为没有过期时间，不会参与到期提醒。"
                            : "日期暂未补全，仍可保存；商品会显示在“日期待补充”中。");
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
                boolean dateIncomplete = datePendingInput.isChecked()
                        || explicitNoExpiryInput.isChecked();
                shelfLifeRow.setVisibility(dateIncomplete ? View.GONE : View.VISIBLE);
                expiryDateField.setVisibility(dateIncomplete ? View.GONE : View.VISIBLE);
                productionAgePreview.setVisibility(dateIncomplete ? View.VISIBLE : View.GONE);
                productionAgePreview.setText(explicitNoExpiryInput.isChecked()
                        ? "无过期时间；已生产时长："
                                + DateRules.productionAgeLabel(clean(productionDateInput))
                        : "日期待补充；可以先保存，之后再编辑补全。");
            }
        };
        datePendingInput.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked && explicitNoExpiryInput.isChecked()) {
                    explicitNoExpiryInput.setChecked(false);
                }
                updateNoExpiryViews.run();
                updateCalculatedExpiry.run();
            }
        });
        explicitNoExpiryInput.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked && datePendingInput.isChecked()) {
                    datePendingInput.setChecked(false);
                }
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
                if (datePendingInput.isChecked() || explicitNoExpiryInput.isChecked()) {
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
                final String currentManualDate = clean(expiryDateInput);
                String shelfText = clean(shelfLifeInput);
                Integer shelfValue = shelfText.length() == 0
                        ? null
                        : parsePositiveInteger(shelfText);
                final String calculated = DateRules.addShelfLife(
                        clean(productionDateInput),
                        shelfValue,
                        selectedOption(FoodData.SHELF_LIFE_UNITS, shelfLifeUnitInput)
                );
                if (calculated.length() == 0) {
                    if (!DateRules.isValidDateString(clean(productionDateInput))) {
                        showFieldError(productionDateInput, "请先选择生产日期", activeFoodEditor == null ? null : activeFoodEditor.scrollView);
                    } else {
                        showFieldError(shelfLifeInput, "请先填写有效保质期", activeFoodEditor == null ? null : activeFoodEditor.scrollView);
                    }
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("改用自动计算？")
                        .setMessage("当前手动日期 " + currentManualDate
                                + " 将替换为自动计算结果 " + calculated + "。")
                        .setNegativeButton("继续手动填写", null)
                        .setPositiveButton("确认替换", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which) {
                                manualExpiryOverride[0] = false;
                                updateCalculatedExpiry.run();
                            }
                        })
                        .show();
            }
        });
        updateNoExpiryViews.run();
        updateCalculatedExpiry.run();

        form.addView(dateSection, withMargins(matchWrap(), 0, 0, 0, dp(4)));

        LinearLayout amountSection = formSection(isReplenishment ? "本批次数量" : "数量与保存");
        if (isEdit) {
            LinearLayout amountRow = formRow();
            amountRow.addView(formField("入库数量", quantityInput), withMargins(weightWrap(1), 0, 0, dp(8), 0));
            amountRow.addView(formField("当前剩余", remainingInput), weightWrap(1));
            amountSection.addView(amountRow, matchWrap());
        } else {
            addFormField(amountSection, isReplenishment ? "本批次入库数量" : "数量", quantityInput);
        }
        addFormField(amountSection, "保存方式", storageInput);
        form.addView(amountSection, withMargins(matchWrap(), 0, 0, 0, dp(4)));

        final LinearLayout optionalSection = new LinearLayout(this);
        optionalSection.setOrientation(LinearLayout.VERTICAL);

        LinearLayout detailSection = formSection("商品资料");
        addFormField(detailSection, "分类", categoryInput);
        addFormField(detailSection, "单位", unitInput);
        detailSection.addView(explicitNoExpiryInput, matchWrap());
        TextView noExpiryHint = text(
                "仅在包装明确说明没有有效期时使用；看不清日期请在上方选择“暂时不知道”。",
                12,
                COLOR_MUTED,
                Typeface.NORMAL
        );
        noExpiryHint.setPadding(0, 0, 0, dp(4));
        detailSection.addView(noExpiryHint, matchWrap());
        optionalSection.addView(detailSection, withMargins(matchWrap(), 0, 0, 0, dp(4)));

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

        boolean hasOptionalValues = !"other".equals(FoodData.normalizeCategoryValue(draft.category))
                || !"件".equals(displayUnit(draft.unit))
                || explicitNoExpiryInput.isChecked()
                || !FoodData.LOCATION_UNSPECIFIED.equals(normalizedLocation)
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

        final FoodEditorViews editor = new FoodEditorViews();
        editor.sourceFood = sourceFood;
        editor.isEdit = isEdit;
        editor.mode = FoodEditorSession.resolveMode(isEdit, isReplenishment);
        editor.protectInitialContent = !isEdit
                && !isReplenishment
                && hasMeaningfulEditorDraft(sourceFood);
        editor.nameInput = nameInput;
        editor.categoryInput = categoryInput;
        editor.barcodeInput = barcodeInput;
        editor.quantityInput = quantityInput;
        editor.remainingInput = remainingInput;
        editor.unitInput = unitInput;
        editor.storageInput = storageInput;
        editor.locationInput = locationInput;
        editor.customLocationInput = customLocationInput;
        editor.productionDateInput = productionDateInput;
        editor.datePendingInput = datePendingInput;
        editor.explicitNoExpiryInput = explicitNoExpiryInput;
        editor.shelfLifeInput = shelfLifeInput;
        editor.shelfLifeUnitInput = shelfLifeUnitInput;
        editor.expiryDateInput = expiryDateInput;
        editor.manualExpiryOverride = manualExpiryOverride;
        editor.openedDateInput = openedDateInput;
        editor.afterOpenShelfLifeInput = afterOpenShelfLifeInput;
        editor.afterOpenShelfLifeUnitInput = afterOpenShelfLifeUnitInput;
        editor.notesInput = notesInput;
        editor.recognitionTarget = new ReplenishmentRecognitionTarget(
                productionDateInput, shelfLifeInput, shelfLifeUnitInput, expiryDateInput,
                datePendingInput, explicitNoExpiryInput, manualExpiryOverride,
                updatingCalculatedExpiry, updateCalculatedExpiry, updateNoExpiryViews
        );

        showFoodEditorPage(form, editor);

        dateCameraButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    beginDateRecognition(editor);
                    Intent intent = new Intent(MainActivity.this, DateOcrScanActivity.class);
                    intent.putExtra(DateOcrScanActivity.EXTRA_DATE_ONLY_MODE, true);
                    if (isDebuggable() && getIntent() != null) {
                        String qaVideoPath = FoodItem.cleanText(
                                getIntent().getStringExtra(EXTRA_QA_REPLENISHMENT_VIDEO_PATH)
                        );
                        if (qaVideoPath.length() > 0) {
                            intent.putExtra(DateOcrScanActivity.EXTRA_QA_VIDEO_PATH, qaVideoPath);
                        }
                    }
                    startActivityForResult(intent, REQUEST_REPLENISHMENT_OCR);
                }
            });

        editor.saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveFoodEditor(editor);
            }
        });
        editor.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestCloseFoodEditor();
            }
        });
        editor.initialFingerprint = foodEditorFingerprint(editor);
    }

    private void showFoodEditorPage(LinearLayout form, FoodEditorViews editor) {
        editor.returnToProductDetail = productDetailVisible;
        editor.returnProductProfileId = visibleProductProfileId;
        editor.returnDestination = productDetailVisible
                ? detailReturnDestination
                : (utilityPageStack.isEmpty() ? currentDestination : utilityReturnDestination);
        editor.returnScrollY = mainScrollView == null ? 0 : mainScrollView.getScrollY();
        editor.returnUtilityPages = new ArrayList<Integer>(utilityPageStack);

        foodEditorVisible = true;
        activeFoodEditor = editor;
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(View.GONE);
        }
        resetPageViews();
        pageHost.removeAllViews();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        pageHost.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.addView(foodEditorHeader(editor), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72)
        ));
        View headerDivider = new View(this);
        headerDivider.setBackgroundColor(COLOR_LINE);
        root.addView(headerDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        editor.scrollView = new ScrollView(this);
        editor.scrollView.setFillViewport(true);
        editor.scrollView.setClipToPadding(false);
        editor.scrollView.setBackgroundColor(Color.WHITE);
        mainScrollView = editor.scrollView;
        form.setPadding(dp(16), dp(10), dp(16), dp(28));
        editor.scrollView.addView(form, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(editor.scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        root.addView(foodEditorFooter(editor), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(77)
        ));
        repositionUndoBar();
    }

    private View foodEditorHeader(final FoodEditorViews editor) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(12), dp(8));
        header.setBackgroundColor(Color.WHITE);

        TextView back = actionIconButton(R.drawable.ic_v2_back, "返回", true);
        back.setBackground(rounded(Color.WHITE, dp(8), 0));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestCloseFoodEditor();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titles, withMargins(weightWrap(1), dp(4), 0, 0, 0));

        TextView title = text(FoodEditorSession.title(editor.mode), 19, COLOR_TEXT, Typeface.BOLD);
        title.setMaxLines(1);
        titles.addView(title, matchWrap());

        TextView subtitle = text(foodEditorSubtitle(editor), 12, COLOR_MUTED, Typeface.NORMAL);
        editor.subtitleView = subtitle;
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setPadding(0, dp(2), 0, 0);
        titles.addView(subtitle, matchWrap());
        return header;
    }

    private String foodEditorSubtitle(FoodEditorViews editor) {
        FoodItem source = editor.sourceFood;
        String name = source == null ? "" : FoodItem.cleanText(source.name);
        if (editor.mode == FoodEditorSession.MODE_REPLENISH) {
            return name.length() > 0 ? name + " · 建立独立批次" : "建立独立批次";
        }
        if (editor.mode == FoodEditorSession.MODE_EDIT) {
            String batchId = source == null ? "" : InventoryPresentation.displayBatchId(source.id);
            return name.length() > 0 ? name + " · 批次 " + batchId : "修改批次信息";
        }
        if (editor.protectInitialContent) {
            return "识别结果待核对，保存后才会入库";
        }
        return "填写商品与批次信息";
    }

    private View foodEditorFooter(FoodEditorViews editor) {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setBackgroundColor(Color.WHITE);

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_LINE);
        footer.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(16), dp(10), dp(16), dp(10));
        footer.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(76)
        ));

        editor.cancelButton = outlineButton("取消");
        editor.cancelButton.setMinHeight(dp(52));
        actions.addView(editor.cancelButton, withMargins(weightWrap(0.36f), 0, 0, dp(8), 0));

        editor.saveButton = button(FoodEditorSession.primaryAction(editor.mode), COLOR_PRIMARY);
        editor.saveButton.setMinHeight(dp(52));
        actions.addView(editor.saveButton, weightWrap(0.64f));
        return footer;
    }

    private void saveFoodEditor(FoodEditorViews editor) {
        if (!foodEditorVisible || activeFoodEditor != editor) {
            return;
        }
        FoodItem saved = buildFoodFromForm(
                editor.sourceFood,
                editor.isEdit,
                editor.nameInput,
                editor.categoryInput,
                editor.barcodeInput,
                editor.quantityInput,
                editor.remainingInput,
                editor.unitInput,
                editor.storageInput,
                editor.locationInput,
                editor.customLocationInput,
                editor.productionDateInput,
                editor.datePendingInput,
                editor.explicitNoExpiryInput,
                editor.shelfLifeInput,
                editor.shelfLifeUnitInput,
                editor.expiryDateInput,
                editor.manualExpiryOverride[0],
                editor.openedDateInput,
                editor.afterOpenShelfLifeInput,
                editor.afterOpenShelfLifeUnitInput,
                editor.notesInput
        );
        if (saved == null) {
            return;
        }

        ReminderPolicy.ensureSmartSchedule(saved);
        final InventoryCommandService commandService = inventoryCommandService();
        InventoryCommandResult result;
        if (editor.mode == FoodEditorSession.MODE_EDIT) {
            result = commandService.edit(editor.sourceFood.id, saved);
        } else if (editor.mode == FoodEditorSession.MODE_REPLENISH) {
            result = commandService.replenish(editor.sourceFood.productProfileId, saved);
        } else {
            result = commandService.add(saved);
        }

        if (result.status == InventoryCommandResult.Status.CONFLICT
                && "barcode".equals(firstCommandErrorField(result))) {
            showBarcodeConflictDialog(editor, saved, commandService, result);
            return;
        }
        completeFoodEditorCommand(editor, result);
    }

    private InventoryCommandService inventoryCommandService() {
        return inventoryCommandService(false);
    }

    private InventoryCommandService inventoryCommandService(final boolean importSave) {
        return new InventoryCommandService(new InventoryRepository() {
            @Override
            public List<FoodItem> loadFoods() {
                return copyFoods(foods);
            }

            @Override
            public boolean saveFoods(List<FoodItem> nextFoods) {
                boolean saved;
                if (importSave && qaForceNextImportSaveFailure) {
                    qaForceNextImportSaveFailure = false;
                    saved = store.saveFoodsForQaForcedFailure(nextFoods);
                } else if (qaForceNextSaveFailure) {
                    qaForceNextSaveFailure = false;
                    saved = store.saveFoodsForQaForcedFailure(nextFoods);
                } else {
                    saved = store.saveFoods(nextFoods);
                }
                if (saved) {
                    dismissUndoBar();
                    foods = copyFoods(nextFoods);
                }
                return saved;
            }
        }, InventoryCommandService.systemTimeSource());
    }

    private void showBarcodeConflictDialog(
            final FoodEditorViews editor,
            final FoodItem draft,
            final InventoryCommandService commandService,
            InventoryCommandResult conflict
    ) {
        final List<String> profileIds = conflict.candidateProductProfileIds;
        if (profileIds.isEmpty()) {
            showCommandError(conflict);
            return;
        }

        if (profileIds.size() == 1) {
            final String profileId = profileIds.get(0);
            String productName = productNameForProfile(profileId);
            new AlertDialog.Builder(this)
                    .setTitle("这个条码已经入过库")
                    .setMessage("可复用“" + productName
                            + "”的商品资料并建立新批次；生产日期和保质期仍按当前表单保存。")
                    .setNegativeButton("返回检查", null)
                    .setNeutralButton("作为另一种商品", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            completeFoodEditorCommand(
                                    editor,
                                    commandService.addAsSeparateProduct(draft)
                            );
                        }
                    })
                    .setPositiveButton("改为补货", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            completeFoodEditorCommand(
                                    editor,
                                    commandService.replenish(profileId, draft)
                            );
                        }
                    })
                    .show();
            return;
        }

        final String[] labels = new String[profileIds.size()];
        for (int index = 0; index < profileIds.size(); index++) {
            labels[index] = productNameForProfile(profileIds.get(index));
        }
        final int[] selected = new int[] { 0 };
        new AlertDialog.Builder(this)
                .setTitle("选择要补货的商品")
                .setSingleChoiceItems(labels, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        selected[0] = which;
                    }
                })
                .setNegativeButton("返回检查", null)
                .setNeutralButton("作为另一种商品", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        completeFoodEditorCommand(
                                editor,
                                commandService.addAsSeparateProduct(draft)
                        );
                    }
                })
                .setPositiveButton("补货新批次", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        completeFoodEditorCommand(
                                editor,
                                commandService.replenish(
                                        profileIds.get(selected[0]),
                                        draft
                                )
                        );
                    }
                })
                .show();
    }

    private String productNameForProfile(String productProfileId) {
        String wanted = FoodItem.cleanText(productProfileId);
        for (FoodItem food : foods) {
            if (wanted.equals(FoodItem.cleanText(food.productProfileId))) {
                return emptyFallback(food.name, "未命名商品");
            }
        }
        return "本机已有商品";
    }

    private void completeFoodEditorCommand(
            FoodEditorViews editor,
            InventoryCommandResult result
    ) {
        if (!commitInventoryCommand(result, false)) {
            return;
        }
        FoodItem persisted = result.after;
        if (editor.returnToProductDetail) {
            editor.returnProductProfileId = persisted == null
                    ? editor.returnProductProfileId
                    : persisted.productProfileId;
        }
        finishFoodEditor();
        if (editor.mode == FoodEditorSession.MODE_EDIT) {
            toast("已保存修改");
        } else if (editor.mode == FoodEditorSession.MODE_REPLENISH) {
            toast("新批次已入库");
        } else {
            toast("商品已新增");
        }
    }

    private String firstCommandErrorField(InventoryCommandResult result) {
        return result == null || result.errors.isEmpty()
                ? ""
                : result.errors.get(0).field;
    }

    private void showCommandError(InventoryCommandResult result) {
        String message = result == null || result.errors.isEmpty()
                ? "操作没有完成，请重试"
                : result.errors.get(0).message;
        toast(message);
    }

    private boolean commitInventoryCommand(
            InventoryCommandResult result,
            boolean refreshVisibleContent
    ) {
        if (result == null || !result.isSuccess()) {
            showCommandError(result);
            return false;
        }
        lastPersistedFoods = copyFoods(foods);
        ReminderScheduler.scheduleDaily(this, reminderSettings);
        if (refreshVisibleContent) {
            refreshVisibleContentAfterDataChange();
        }
        return true;
    }

    private void requestCloseFoodEditor() {
        final FoodEditorViews editor = activeFoodEditor;
        if (!foodEditorVisible || editor == null) {
            return;
        }
        boolean confirm = FoodEditorSession.shouldConfirmDiscard(
                editor.initialFingerprint,
                foodEditorFingerprint(editor),
                editor.protectInitialContent
        );
        if (!confirm) {
            finishFoodEditor();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("放弃未保存内容？")
                .setMessage("当前填写或识别的内容尚未保存。返回后这些修改会丢失。")
                .setNegativeButton("继续填写", null)
                .setPositiveButton("放弃并返回", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        finishFoodEditor();
                    }
                })
                .show();
    }

    private void finishFoodEditor() {
        FoodEditorViews editor = activeFoodEditor;
        if (!foodEditorVisible || editor == null) {
            return;
        }
        foodEditorVisible = false;
        activeFoodEditor = null;
        clearPendingDateRecognition();
        hideCurrentKeyboard();

        if (editor.returnToProductDetail
                && InventoryPresentation.find(foods, editor.returnProductProfileId) != null) {
            productDetailVisible = true;
            visibleProductProfileId = editor.returnProductProfileId;
            if (bottomNavigation != null) {
                bottomNavigation.setVisibility(View.GONE);
            }
            resetPageViews();
            renderProductDetail();
            restoreScrollPosition(editor.returnScrollY);
            return;
        }

        if (!editor.returnUtilityPages.isEmpty()) {
            productDetailVisible = false;
            utilityPageStack.clear();
            utilityPageStack.addAll(editor.returnUtilityPages);
            refreshActiveBarcodeAfterInventoryChange();
            renderUtilityPage();
            restoreScrollPosition(editor.returnScrollY);
            return;
        }

        productDetailVisible = false;
        showDestination(editor.returnDestination);
        restoreScrollPosition(editor.returnScrollY);
    }

    private void refreshActiveBarcodeAfterInventoryChange() {
        BarcodeResolutionState current = activeBarcodeResolution;
        if (current == null) {
            return;
        }
        List<FoodItem> profiles = ProductProfileIndex.fromFoods(foods)
                .findByBarcode(current.barcode());
        if (profiles.isEmpty()) {
            return;
        }
        activeBarcodeResolution = BarcodeResolutionState.fromMatches(
                current.barcode(),
                profiles
        );
        activeBarcodeProductInfo = null;
        activeBarcodeMessage = "";
        activeBarcodeState = BARCODE_STATE_LOCAL;
    }

    private void restoreScrollPosition(final int scrollY) {
        if (mainScrollView == null || scrollY <= 0) {
            return;
        }
        mainScrollView.post(new Runnable() {
            @Override
            public void run() {
                mainScrollView.scrollTo(0, scrollY);
            }
        });
    }

    private void hideCurrentKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        focused.clearFocus();
    }

    private boolean hasMeaningfulEditorDraft(FoodItem source) {
        if (source == null) {
            return false;
        }
        return FoodItem.cleanText(source.name).length() > 0
                || FoodItem.cleanText(source.barcode).length() > 0
                || FoodItem.cleanText(source.productionDate).length() > 0
                || FoodItem.cleanText(source.expiryDate).length() > 0
                || source.shelfLifeValue != null
                || FoodItem.cleanText(source.notes).length() > 0;
    }

    private String foodEditorFingerprint(FoodEditorViews editor) {
        StringBuilder value = new StringBuilder();
        appendEditorFingerprint(value, clean(editor.nameInput));
        appendEditorFingerprint(value, selectedOption(FoodData.CATEGORIES, editor.categoryInput));
        appendEditorFingerprint(value, clean(editor.barcodeInput));
        appendEditorFingerprint(value, clean(editor.quantityInput));
        appendEditorFingerprint(value, clean(editor.remainingInput));
        appendEditorFingerprint(value, clean(editor.unitInput));
        appendEditorFingerprint(value, selectedOption(FoodData.STORAGE_METHODS, editor.storageInput));
        appendEditorFingerprint(value, selectedOption(FoodData.LOCATION_OPTIONS, editor.locationInput));
        appendEditorFingerprint(value, clean(editor.customLocationInput));
        appendEditorFingerprint(value, clean(editor.productionDateInput));
        appendEditorFingerprint(value, editor.datePendingInput.isChecked() ? "1" : "0");
        appendEditorFingerprint(value, editor.explicitNoExpiryInput.isChecked() ? "1" : "0");
        appendEditorFingerprint(value, clean(editor.shelfLifeInput));
        appendEditorFingerprint(value, selectedOption(FoodData.SHELF_LIFE_UNITS, editor.shelfLifeUnitInput));
        appendEditorFingerprint(value, clean(editor.expiryDateInput));
        appendEditorFingerprint(value, editor.manualExpiryOverride[0] ? "1" : "0");
        appendEditorFingerprint(value, clean(editor.openedDateInput));
        appendEditorFingerprint(value, clean(editor.afterOpenShelfLifeInput));
        appendEditorFingerprint(value, selectedOption(
                FoodData.SHELF_LIFE_UNITS,
                editor.afterOpenShelfLifeUnitInput
        ));
        appendEditorFingerprint(value, clean(editor.notesInput));
        return value.toString();
    }

    private void appendEditorFingerprint(StringBuilder builder, String fieldValue) {
        String normalized = fieldValue == null ? "" : fieldValue;
        builder.append(normalized.length()).append(':').append(normalized).append('|');
    }

    private FoodItem buildFoodFromForm(
            FoodItem original,
            boolean isEdit,
            EditText nameInput,
            Spinner categoryInput,
            EditText barcodeInput,
            EditText quantityInput,
            EditText remainingInput,
            EditText unitInput,
            Spinner storageInput,
            Spinner locationInput,
            EditText customLocationInput,
            EditText productionDateInput,
            CheckBox datePendingInput,
            CheckBox explicitNoExpiryInput,
            EditText shelfLifeInput,
            Spinner shelfLifeUnitInput,
            EditText expiryDateInput,
            boolean manualExpiryOverride,
            EditText openedDateInput,
            EditText afterOpenShelfLifeInput,
            Spinner afterOpenShelfLifeUnitInput,
            EditText notesInput
    ) {
        List<EditorFieldError> fieldErrors = new ArrayList<EditorFieldError>();
        String name = clean(nameInput);
        if (name.length() == 0) {
            addEditorFieldError(fieldErrors, nameInput, "请填写商品名称");
        }

        String barcode = BarcodeUtils.digitsOnly(clean(barcodeInput));
        if (barcode.length() > 0 && !BarcodeUtils.isSupportedProductCode(barcode)) {
            addEditorFieldError(fieldErrors, barcodeInput, "商品条码格式或校验位不正确");
        }

        Double quantity = parseNumber(clean(quantityInput), null);
        Double remaining = parseNumber(clean(remainingInput), quantity);
        if (quantity == null || quantity.doubleValue() <= 0d) {
            addEditorFieldError(fieldErrors, quantityInput, "数量必须大于 0");
        }
        if (remaining == null || remaining.doubleValue() < 0d) {
            addEditorFieldError(fieldErrors, remainingInput, "剩余数量不能小于 0");
        } else if (quantity != null && remaining.doubleValue() > quantity.doubleValue()) {
            addEditorFieldError(fieldErrors, remainingInput, "剩余数量不能大于总数量");
        } else if (!isEdit && remaining.doubleValue() <= 0d) {
            addEditorFieldError(fieldErrors, remainingInput, "新增或补货批次的数量必须大于 0");
        }

        String productionDate = clean(productionDateInput);
        String productionError = FoodValidationRules.productionDateError(
                productionDate,
                DateRules.todayString()
        );
        if (productionError.length() > 0) {
            addEditorFieldError(fieldErrors, productionDateInput, productionError);
        }

        String shelfLifeText = clean(shelfLifeInput);
        Integer shelfLifeValue = shelfLifeText.length() == 0
                ? null
                : parsePositiveInteger(shelfLifeText);
        if (shelfLifeText.length() > 0 && shelfLifeValue == null) {
            addEditorFieldError(fieldErrors, shelfLifeInput, "保质期必须是正整数");
        }

        String shelfLifeUnit = selectedOption(FoodData.SHELF_LIFE_UNITS, shelfLifeUnitInput);
        String manualExpiryDate = clean(expiryDateInput);
        String expiryDate;
        String dateSource;

        if (explicitNoExpiryInput.isChecked()) {
            shelfLifeValue = null;
            shelfLifeUnit = "";
            expiryDate = "";
            dateSource = "none";
        } else if (datePendingInput.isChecked()) {
            shelfLifeValue = null;
            shelfLifeUnit = "";
            expiryDate = "";
            dateSource = "unknown";
        } else if (manualExpiryOverride) {
            String expiryError = FoodValidationRules.expiryDateError(
                    productionDate,
                    manualExpiryDate
            );
            if (expiryError.length() > 0) {
                addEditorFieldError(fieldErrors, expiryDateInput, expiryError);
            }
            expiryDate = manualExpiryDate;
            dateSource = "manual";
        } else {
            expiryDate = DateRules.addShelfLife(productionDate, shelfLifeValue, shelfLifeUnit);
            if (expiryDate.length() == 0) {
                if (!DateRules.isValidDateString(productionDate)) {
                    addEditorFieldError(fieldErrors, productionDateInput, "请选择生产日期");
                }
                if (shelfLifeValue == null) {
                    addEditorFieldError(
                            fieldErrors,
                            shelfLifeInput,
                            "请填写保质期，或直接填写最终可食用日期"
                    );
                }
            }
            dateSource = "calculated";
        }

        String openedDate = clean(openedDateInput);
        String afterOpenShelfLifeText = clean(afterOpenShelfLifeInput);
        Integer afterOpenShelfLifeValue = afterOpenShelfLifeText.length() == 0
                ? null
                : parsePositiveInteger(afterOpenShelfLifeText);
        if (afterOpenShelfLifeText.length() > 0 && afterOpenShelfLifeValue == null) {
            addEditorFieldError(fieldErrors, afterOpenShelfLifeInput, "开封后保质期必须是正整数");
        }

        String afterOpenShelfLifeUnit = selectedOption(
                FoodData.SHELF_LIFE_UNITS,
                afterOpenShelfLifeUnitInput
        );
        boolean hasAfterOpenInfo = openedDate.length() > 0 || afterOpenShelfLifeText.length() > 0;
        if (hasAfterOpenInfo) {
            String openedError = FoodValidationRules.openedDateError(
                    productionDate,
                    openedDate,
                    expiryDate,
                    DateRules.todayString()
            );
            if (openedError.length() > 0) {
                addEditorFieldError(fieldErrors, openedDateInput, openedError);
            }
            if (afterOpenShelfLifeValue == null) {
                addEditorFieldError(fieldErrors, afterOpenShelfLifeInput, "请填写开封后保质期");
            } else if (DateRules.addAfterOpenShelfLife(
                    openedDate,
                    afterOpenShelfLifeValue,
                    afterOpenShelfLifeUnit
            ).length() == 0) {
                addEditorFieldError(fieldErrors, afterOpenShelfLifeInput, "开封后建议处理日无法计算");
            }
        } else {
            openedDate = "";
            afterOpenShelfLifeValue = null;
            afterOpenShelfLifeUnit = "";
        }

        if (!fieldErrors.isEmpty()) {
            showFoodFieldErrors(fieldErrors);
            return null;
        }

        String now = DateRules.nowIsoLike();
        FoodItem item = original == null ? new FoodItem() : original.copy();
        item.id = isEdit && original != null ? original.id : newFoodId();
        item.barcode = barcode;
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
        item.afterOpenShelfLifeUnit = afterOpenShelfLifeValue == null
                ? ""
                : afterOpenShelfLifeUnit;
        item.notes = clean(notesInput);
        item.createdAt = isEdit && original != null ? original.createdAt : now;
        item.updatedAt = now;
        item.productProfileId = isEdit && original != null
                ? FoodItem.cleanText(original.productProfileId)
                : ReplenishmentDraftFactory.resolveProductProfileId(
                        original,
                        item,
                        "profile_" + UUID.randomUUID().toString()
                );
        if (!isEdit) {
            item.isFinished = false;
            item.finishedAt = "";
            item.smartReminderOffsets.clear();
            item.smartReminderFingerprint = "";
            item.smartReminderPlannedDaysLeft = Integer.MIN_VALUE;
            item.smartReminderPlannedOn = "";
        } else if (item.remainingQuantity <= 0d) {
            InventoryRules.finishIfDepleted(item, now);
        }
        item.normalizeQuantityBounds();
        return item;
    }

    private void addEditorFieldError(
            List<EditorFieldError> errors,
            EditText field,
            String message
    ) {
        for (EditorFieldError existing : errors) {
            if (existing.field == field) {
                return;
            }
        }
        errors.add(new EditorFieldError(field, message));
    }

    private void showFoodFieldErrors(List<EditorFieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return;
        }
        for (EditorFieldError error : errors) {
            if (error.field != null) {
                error.field.setError(error.message);
            }
        }
        EditorFieldError first = errors.get(0);
        showFieldError(
                first.field,
                first.message,
                activeFoodEditor == null ? null : activeFoodEditor.scrollView
        );
        toast("请检查 " + errors.size() + " 处信息");
    }

    private void showFieldError(EditText field, String message, final ScrollView scrollView) {
        if (field == null) {
            toast(message);
            return;
        }
        field.setError(message);
        field.requestFocus();
        field.announceForAccessibility(message);
        if (scrollView != null) {
            final EditText target = field;
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    scrollView.smoothScrollTo(0, Math.max(0, target.getTop() - dp(24)));
                }
            });
        }
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
        if (InventoryRules.isFinished(food)) {
            labels = new String[] { "补货", "复制当前批次", "恢复在库", "删除" };
            actions = new String[] { FOOD_ACTION_REPLENISH, FOOD_ACTION_COPY, FOOD_ACTION_RESTORE, FOOD_ACTION_DELETE_CONFIRM };
        } else {
            labels = new String[] { "剩余归 0", "补货", "复制当前批次", "编辑", "标记已用完", "删除" };
            actions = new String[] {
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
        decreaseBatchWithUndo(food);
    }
    private void confirmZeroRemaining(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle("标记本批次已用完？")
                .setMessage("剩余数量会归 0，并立即停止这个批次的到期提醒。记录仍可在“已用完”筛选中恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认归 0", new FoodDialogActionListener(food, FOOD_ACTION_ZERO_REMAINING))
                .show();
    }

    private void zeroFoodRemaining(FoodItem food) {
        InventoryCommandResult result = inventoryCommandService().finish(
                food == null ? "" : food.id
        );
        if (!commitInventoryCommand(result, true)) {
            return;
        }
        toast("已用完并停止提醒");
    }

    private void showReplenishDialog(final FoodItem food) {
        showReplenishmentForm(food, null);
    }

    private void copyFood(final FoodItem food) {
        final FoodItem current = findFoodById(food == null ? "" : food.id);
        if (current == null) {
            toast("这个批次已不存在");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("复制为独立新批次？")
                .setMessage("将复制当前批次的日期和备注，并新建独立批次。入库与剩余数量会先按 "
                        + formatNumber(current.quantity) + " " + displayUnit(current.unit)
                        + " 填入；下一页仍可修改，确认保存前不会写入库存。")
                .setNegativeButton("取消", null)
                .setPositiveButton("进入确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        FoodItem draft = current.copy();
                        draft.id = "";
                        draft.createdAt = "";
                        draft.updatedAt = "";
                        draft.isFinished = false;
                        draft.finishedAt = "";
                        draft.remainingQuantity = draft.quantity;
                        draft.smartReminderOffsets.clear();
                        draft.smartReminderFingerprint = "";
                        draft.smartReminderPlannedDaysLeft = Integer.MIN_VALUE;
                        draft.smartReminderPlannedOn = "";
                        showFoodForm(draft, false, true);
                    }
                })
                .show();
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
            refreshVisibleContentAfterDataChange();
            toast("保存失败，原有食品数据已保留");
            return false;
        }
        lastPersistedFoods = copyFoods(foods);
        ReminderScheduler.scheduleDaily(this, reminderSettings);
        refreshVisibleContentAfterDataChange();
        dismissUndoBar();
        return true;
    }

    private void refreshVisibleContentAfterDataChange() {
        if (foodEditorVisible) {
            return;
        }
        if (productDetailVisible) {
            renderProductDetail();
            return;
        }
        renderFilterControls();
        renderFoods();
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
        InventoryCommandResult result = inventoryCommandService().finish(
                food == null ? "" : food.id
        );
        if (!commitInventoryCommand(result, true)) {
            return;
        }
        toast("已移入已用完");
    }

    private void confirmRestoreFood(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle("恢复在库")
                .setMessage("“" + food.name + "”会回到在库食品；若剩余数量为 0，将恢复为 1，并重新参与到期提醒。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复在库", new FoodDialogActionListener(food, FOOD_ACTION_RESTORE))
                .show();
    }

    private void restoreFood(FoodItem food) {
        InventoryCommandResult result = inventoryCommandService().restore(
                food == null ? "" : food.id
        );
        if (!commitInventoryCommand(result, true)) {
            return;
        }
        toast("已恢复在库");
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
        InventoryCommandResult result = inventoryCommandService().deleteBatch(
                food == null ? "" : food.id
        );
        if (!commitInventoryCommand(result, true)) {
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
        if (focused == null || (focused != searchInput && focused != pinnedSearchInput)) {
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
        button.setMinHeight(dp(48));
        button.setBackground(rounded(color, dp(8), 0));
        return button;
    }

    private Button outlineButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(COLOR_PRIMARY);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE));
        return button;
    }

    private Button utilityButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
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
        editText.setMinHeight(dp(48));
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
        spinner.setMinimumHeight(dp(48));
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

    private LinearLayout.LayoutParams fixed(int widthDp, int heightDp) {
        return new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp));
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
