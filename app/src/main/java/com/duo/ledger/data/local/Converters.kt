package com.duo.ledger.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun entryTypeToString(value: EntryType): String = value.name

    @TypeConverter
    fun stringToEntryType(value: String): EntryType = EntryType.valueOf(value)
}
