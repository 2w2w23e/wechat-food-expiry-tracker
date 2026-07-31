package com.shiqi.expirytracker;

public final class FoodEditorSessionTest {
    private int passed;

    public static void main(String[] args) {
        new FoodEditorSessionTest().run();
    }

    private void run() {
        expect(
                FoodEditorSession.MODE_CREATE,
                FoodEditorSession.resolveMode(false, false),
                "create mode"
        );
        expect(
                FoodEditorSession.MODE_EDIT,
                FoodEditorSession.resolveMode(true, false),
                "edit mode wins"
        );
        expect(
                FoodEditorSession.MODE_REPLENISH,
                FoodEditorSession.resolveMode(false, true),
                "replenishment mode"
        );
        expect("新增商品", FoodEditorSession.title(FoodEditorSession.MODE_CREATE), "create title");
        expect("保存修改", FoodEditorSession.primaryAction(FoodEditorSession.MODE_EDIT), "edit action");
        expect(
                "确认入库",
                FoodEditorSession.primaryAction(FoodEditorSession.MODE_REPLENISH),
                "replenishment action"
        );
        expect(
                false,
                FoodEditorSession.shouldConfirmDiscard("same", "same", false),
                "unchanged blank create can close directly"
        );
        expect(
                true,
                FoodEditorSession.shouldConfirmDiscard("before", "after", false),
                "changed form requires confirmation"
        );
        expect(
                true,
                FoodEditorSession.shouldConfirmDiscard("recognized", "recognized", true),
                "recognized prefill is protected even before edits"
        );
        System.out.println("FoodEditorSessionTest: " + passed + " passed");
    }

    private void expect(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    message + " expected=<" + expected + "> actual=<" + actual + ">"
            );
        }
        passed++;
    }
}
