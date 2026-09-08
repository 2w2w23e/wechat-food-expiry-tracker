package com.shiqi.expirytracker;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                RoomProductEntity.class,
                RoomBatchEntity.class,
                RoomBarcodeEntity.class,
                RoomProductBarcodeEntity.class,
                RoomReminderPlanEntity.class,
                RoomInventoryMetadataEntity.class
        },
        version = 1,
        exportSchema = true
)
abstract class RoomInventoryDatabase extends RoomDatabase {
    static final String DATABASE_NAME = "shiqi_inventory_v1.db";

    private static volatile RoomInventoryDatabase instance;

    abstract RoomInventoryDao inventoryDao();

    static RoomInventoryDatabase get(Context context) {
        RoomInventoryDatabase current = instance;
        if (current != null) {
            return current;
        }
        synchronized (RoomInventoryDatabase.class) {
            current = instance;
            if (current == null) {
                current = Room.databaseBuilder(
                        context.getApplicationContext(),
                        RoomInventoryDatabase.class,
                        DATABASE_NAME
                ).build();
                instance = current;
            }
        }
        return current;
    }

    static void resetForTests(Context context) {
        synchronized (RoomInventoryDatabase.class) {
            if (instance != null) {
                instance.close();
                instance = null;
            }
            context.getApplicationContext().deleteDatabase(DATABASE_NAME);
        }
    }
}
