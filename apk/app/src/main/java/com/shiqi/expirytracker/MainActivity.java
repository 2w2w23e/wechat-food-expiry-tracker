package com.shiqi.expirytracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int COLOR_BG = Color.rgb(246, 247, 242);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(34, 42, 34);
    private static final int COLOR_MUTED = Color.rgb(91, 105, 91);
    private static final int COLOR_PRIMARY = Color.rgb(63, 111, 83);
    private static final int COLOR_DANGER = Color.rgb(159, 53, 46);
    private static final int COLOR_LINE = Color.rgb(222, 228, 218);

    private FoodStore store;
    private List<FoodItem> foods = new ArrayList<FoodItem>();
    private LinearLayout listContainer;
    private TextView statsText;
    private Spinner statusSpinner;
    private Spinner categorySpinner;
    private String selectedStatus = FoodData.ALL;
    private String selectedCategory = FoodData.ALL;

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
    }

    private void buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("食期管家", 28, COLOR_TEXT, Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView subtitle = text("Android APK 本地版", 14, COLOR_MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(4), 0, dp(10));
        content.addView(subtitle, matchWrap());

        TextView notice = text("当前数据保存在本机 App 内，暂不支持多设备同步。", 14, COLOR_MUTED, Typeface.NORMAL);
        notice.setPadding(dp(12), dp(10), dp(12), dp(10));
        notice.setBackground(rounded(Color.rgb(239, 244, 235), dp(8), 0));
        content.addView(notice, withMargins(matchWrap(), 0, 0, 0, dp(14)));

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

        Button sampleButton = button("加载示例数据", Color.rgb(93, 111, 73));
        sampleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadSampleFoods();
            }
        });
        actionRow.addView(sampleButton, weightWrap(1));

        statsText = text("", 15, COLOR_TEXT, Typeface.BOLD);
        statsText.setPadding(0, dp(16), 0, dp(8));
        content.addView(statsText, matchWrap());

        LinearLayout filterCard = card();
        content.addView(filterCard, withMargins(matchWrap(), 0, dp(4), 0, dp(12)));

        TextView filterTitle = text("筛选", 17, COLOR_TEXT, Typeface.BOLD);
        filterCard.addView(filterTitle, matchWrap());

        statusSpinner = spinner(FoodData.STATUS_FILTERS);
        categorySpinner = spinner(FoodData.categoryFilterOptions());

        filterCard.addView(label("状态"), withMargins(matchWrap(), 0, dp(10), 0, dp(4)));
        filterCard.addView(statusSpinner, matchWrap());
        filterCard.addView(label("分类"), withMargins(matchWrap(), 0, dp(10), 0, dp(4)));
        filterCard.addView(categorySpinner, matchWrap());

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer, matchWrap());

        statusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedStatus = FoodData.STATUS_FILTERS.get(position).value;
                renderFoods();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedStatus = FoodData.ALL;
                renderFoods();
            }
        });

        final List<Option> categoryOptions = FoodData.categoryFilterOptions();
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCategory = categoryOptions.get(position).value;
                renderFoods();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedCategory = FoodData.ALL;
                renderFoods();
            }
        });

        setContentView(scrollView);
    }

    private void renderFoods() {
        if (listContainer == null || statsText == null) {
            return;
        }

        listContainer.removeAllViews();
        statsText.setText(buildStatsText());

        List<FoodItem> visibleFoods = filteredSortedFoods();

        if (foods.isEmpty()) {
            listContainer.addView(emptyGuideCard(), matchWrap());
            return;
        }

        if (visibleFoods.isEmpty()) {
            TextView empty = text("当前筛选下没有食品", 16, COLOR_MUTED, Typeface.NORMAL);
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

    private String buildStatsText() {
        int soon = 0;
        int expired = 0;
        for (FoodItem food : foods) {
            String status = DateRules.getExpiryStatus(food.expiryDate);
            if (DateRules.STATUS_SOON.equals(status)) {
                soon++;
            }
            if (DateRules.STATUS_EXPIRED.equals(status)) {
                expired++;
            }
        }

        return "共 " + foods.size() + " 件食品 · 即将到期 " + soon + " · 已过期 " + expired;
    }

    private List<FoodItem> filteredSortedFoods() {
        List<FoodItem> result = new ArrayList<FoodItem>();
        for (FoodItem food : foods) {
            String status = DateRules.getExpiryStatus(food.expiryDate);
            boolean statusMatches = FoodData.ALL.equals(selectedStatus) || selectedStatus.equals(status);
            boolean categoryMatches = FoodData.ALL.equals(selectedCategory) || selectedCategory.equals(food.category);
            if (statusMatches && categoryMatches) {
                result.add(food);
            }
        }

        Collections.sort(result, new Comparator<FoodItem>() {
            @Override
            public int compare(FoodItem left, FoodItem right) {
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

        Button sample = outlineButton("加载示例数据");
        sample.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadSampleFoods();
            }
        });
        card.addView(sample, withMargins(matchWrap(), 0, dp(10), 0, 0));
        return card;
    }

    private View foodCard(final FoodItem food) {
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

        TextView status = text(FoodData.statusLabel(DateRules.getExpiryStatus(food.expiryDate)), 13, statusColor(food), Typeface.BOLD);
        status.setGravity(android.view.Gravity.CENTER);
        status.setPadding(dp(8), dp(4), dp(8), dp(4));
        status.setBackground(rounded(statusBgColor(food), dp(12), 0));
        top.addView(status, wrapWrap());

        TextView meta = text(
                FoodData.categoryLabel(food.category) + " · " +
                        FoodData.storageLabel(food.storageMethod) + " · 剩余 " +
                        formatNumber(food.remainingQuantity) + "/" + formatNumber(food.quantity) + " " + food.unit,
                14,
                COLOR_MUTED,
                Typeface.NORMAL
        );
        meta.setPadding(0, dp(8), 0, 0);
        card.addView(meta, matchWrap());

        TextView expiry = text("最终可食用日期：" + food.expiryDate, 15, COLOR_TEXT, Typeface.NORMAL);
        expiry.setPadding(0, dp(8), 0, dp(10));
        card.addView(expiry, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(actions, matchWrap());

        Button edit = outlineButton("编辑");
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFoodForm(food);
            }
        });
        actions.addView(edit, withMargins(weightWrap(1), 0, 0, dp(8), 0));

        Button delete = outlineButton("删除");
        delete.setTextColor(COLOR_DANGER);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDelete(food);
            }
        });
        actions.addView(delete, weightWrap(1));
        return card;
    }

    private int statusColor(FoodItem food) {
        String status = DateRules.getExpiryStatus(food.expiryDate);
        if (DateRules.STATUS_EXPIRED.equals(status)) {
            return COLOR_DANGER;
        }
        if (DateRules.STATUS_TODAY.equals(status)) {
            return Color.rgb(152, 86, 26);
        }
        if (DateRules.STATUS_SOON.equals(status)) {
            return Color.rgb(136, 112, 31);
        }
        return COLOR_PRIMARY;
    }

    private int statusBgColor(FoodItem food) {
        String status = DateRules.getExpiryStatus(food.expiryDate);
        if (DateRules.STATUS_EXPIRED.equals(status)) {
            return Color.rgb(253, 235, 232);
        }
        if (DateRules.STATUS_TODAY.equals(status)) {
            return Color.rgb(255, 241, 220);
        }
        if (DateRules.STATUS_SOON.equals(status)) {
            return Color.rgb(250, 244, 211);
        }
        return Color.rgb(231, 241, 232);
    }

    private void loadSampleFoods() {
        if (!foods.isEmpty()) {
            toast("已有食品记录，示例数据不会覆盖");
            return;
        }

        foods = FoodData.sampleFoods();
        store.saveFoods(foods);
        renderFoods();
        toast("已加载示例数据");
    }

    private void showFoodDetail(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle(food.name)
                .setMessage(detailText(food))
                .setPositiveButton("编辑", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        showFoodForm(food);
                    }
                })
                .setNeutralButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        confirmDelete(food);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private String detailText(FoodItem food) {
        StringBuilder builder = new StringBuilder();
        builder.append("分类：").append(FoodData.categoryLabel(food.category)).append('\n');
        builder.append("状态：").append(FoodData.statusLabel(DateRules.getExpiryStatus(food.expiryDate))).append('\n');
        builder.append("数量：").append(formatNumber(food.quantity)).append(' ').append(food.unit).append('\n');
        builder.append("剩余：").append(formatNumber(food.remainingQuantity)).append(' ').append(food.unit).append('\n');
        builder.append("保存方式：").append(FoodData.storageLabel(food.storageMethod)).append('\n');
        builder.append("生产日期：").append(emptyFallback(food.productionDate, "未填写")).append('\n');
        builder.append("保质期：");
        if (food.shelfLifeValue == null) {
            builder.append("未填写");
        } else {
            builder.append(food.shelfLifeValue).append(FoodData.shelfLifeUnitLabel(food.shelfLifeUnit));
        }
        builder.append('\n');
        builder.append("最终可食用日期：").append(food.expiryDate).append('\n');
        builder.append("日期来源：").append("calculated".equals(food.dateSource) ? "自动计算" : "手动填写").append('\n');
        builder.append("备注：").append(emptyFallback(food.notes, "暂无备注"));
        return builder.toString();
    }

    private void showFoodForm(final FoodItem editingFood) {
        final boolean isEdit = editingFood != null;
        final FoodItem draft = isEdit ? editingFood.copy() : new FoodItem();

        final EditText nameInput = input(draft.name, "食品名称", InputType.TYPE_CLASS_TEXT);
        final Spinner categoryInput = spinner(FoodData.CATEGORIES);
        categoryInput.setSelection(indexOf(FoodData.CATEGORIES, draft.category, "other"));

        final EditText quantityInput = input(isEdit ? formatNumber(draft.quantity) : "", "数量", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText remainingInput = input(isEdit ? formatNumber(draft.remainingQuantity) : "", "剩余数量", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText unitInput = input(isEdit ? draft.unit : "", "单位，例如 盒、瓶、袋", InputType.TYPE_CLASS_TEXT);

        final Spinner storageInput = spinner(FoodData.STORAGE_METHODS);
        storageInput.setSelection(indexOf(FoodData.STORAGE_METHODS, draft.storageMethod, "room_temp"));

        final EditText productionDateInput = input(draft.productionDate, "生产日期 YYYY-MM-DD", InputType.TYPE_CLASS_DATETIME);
        final EditText shelfLifeInput = input(draft.shelfLifeValue == null ? "" : String.valueOf(draft.shelfLifeValue), "保质期数值", InputType.TYPE_CLASS_NUMBER);
        final Spinner shelfLifeUnitInput = spinner(FoodData.SHELF_LIFE_UNITS);
        shelfLifeUnitInput.setSelection(indexOf(FoodData.SHELF_LIFE_UNITS, draft.shelfLifeUnit, "day"));

        final EditText expiryDateInput = input(draft.expiryDate, "最终可食用日期 YYYY-MM-DD", InputType.TYPE_CLASS_DATETIME);
        final EditText notesInput = input(draft.notes, "备注", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notesInput.setMinLines(2);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(4));
        form.addView(label("食品名称"), matchWrap());
        form.addView(nameInput, matchWrap());
        form.addView(label("分类"), matchWrap());
        form.addView(categoryInput, matchWrap());
        form.addView(label("数量"), matchWrap());
        form.addView(quantityInput, matchWrap());
        form.addView(label("剩余数量"), matchWrap());
        form.addView(remainingInput, matchWrap());
        form.addView(label("单位"), matchWrap());
        form.addView(unitInput, matchWrap());
        form.addView(label("保存方式"), matchWrap());
        form.addView(storageInput, matchWrap());
        form.addView(label("生产日期 + 保质期"), matchWrap());
        form.addView(productionDateInput, matchWrap());
        form.addView(shelfLifeInput, matchWrap());
        form.addView(shelfLifeUnitInput, matchWrap());
        form.addView(label("最终可食用日期"), matchWrap());
        form.addView(expiryDateInput, matchWrap());
        form.addView(label("备注"), matchWrap());
        form.addView(notesInput, matchWrap());

        TextView hint = text("可直接填写最终可食用日期；如果不填写，则使用生产日期 + 保质期计算。", 13, COLOR_MUTED, Typeface.NORMAL);
        hint.setPadding(0, dp(8), 0, 0);
        form.addView(hint, matchWrap());

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
                                isEdit ? editingFood : null,
                                nameInput,
                                categoryInput,
                                quantityInput,
                                remainingInput,
                                unitInput,
                                storageInput,
                                productionDateInput,
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

        if (manualExpiryDate.length() > 0) {
            if (!DateRules.isValidDateString(manualExpiryDate)) {
                toast("最终可食用日期格式不正确，请使用 YYYY-MM-DD");
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
        item.unit = clean(unitInput).length() > 0 ? clean(unitInput) : "piece";
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

    private void confirmDelete(final FoodItem food) {
        new AlertDialog.Builder(this)
                .setTitle("删除食品")
                .setMessage("确定删除“" + food.name + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        deleteFood(food);
                    }
                })
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
        renderFoods();
        toast("已删除食品");
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
        return editText;
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
