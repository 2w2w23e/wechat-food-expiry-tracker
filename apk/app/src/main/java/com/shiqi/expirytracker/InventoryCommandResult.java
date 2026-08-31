package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class InventoryCommandResult {
    enum Status {
        SUCCESS,
        VALIDATION_ERROR,
        CONFLICT,
        NOT_FOUND,
        STORAGE_ERROR
    }

    static final class FieldError {
        final String field;
        final String message;

        FieldError(String field, String message) {
            this.field = FoodItem.cleanText(field);
            this.message = FoodItem.cleanText(message);
        }
    }

    final Status status;
    final List<FieldError> errors;
    final List<String> candidateProductProfileIds;
    final FoodItem before;
    final FoodItem after;

    private InventoryCommandResult(
            Status status,
            List<FieldError> errors,
            List<String> candidateProductProfileIds,
            FoodItem before,
            FoodItem after
    ) {
        this.status = status;
        this.errors = immutableErrors(errors);
        this.candidateProductProfileIds = immutableStrings(candidateProductProfileIds);
        this.before = before == null ? null : before.copy();
        this.after = after == null ? null : after.copy();
    }

    static InventoryCommandResult success(FoodItem before, FoodItem after) {
        return new InventoryCommandResult(
                Status.SUCCESS,
                null,
                null,
                before,
                after
        );
    }

    static InventoryCommandResult validation(List<FieldError> errors) {
        return new InventoryCommandResult(
                Status.VALIDATION_ERROR,
                errors,
                null,
                null,
                null
        );
    }

    static InventoryCommandResult conflict(
            String field,
            String message,
            List<String> candidateProductProfileIds
    ) {
        List<FieldError> errors = new ArrayList<FieldError>();
        errors.add(new FieldError(field, message));
        return new InventoryCommandResult(
                Status.CONFLICT,
                errors,
                candidateProductProfileIds,
                null,
                null
        );
    }

    static InventoryCommandResult notFound(String message) {
        List<FieldError> errors = new ArrayList<FieldError>();
        errors.add(new FieldError("global", message));
        return new InventoryCommandResult(
                Status.NOT_FOUND,
                errors,
                null,
                null,
                null
        );
    }

    static InventoryCommandResult storageError() {
        List<FieldError> errors = new ArrayList<FieldError>();
        errors.add(new FieldError("global", "保存失败，原数据未改变，请重试。"));
        return new InventoryCommandResult(
                Status.STORAGE_ERROR,
                errors,
                null,
                null,
                null
        );
    }

    boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    private static List<FieldError> immutableErrors(List<FieldError> values) {
        List<FieldError> copy = new ArrayList<FieldError>();
        if (values != null) {
            copy.addAll(values);
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<String> immutableStrings(List<String> values) {
        List<String> copy = new ArrayList<String>();
        if (values != null) {
            copy.addAll(values);
        }
        return Collections.unmodifiableList(copy);
    }
}
