package androidx.preference;

import java.util.Set;

public abstract class PreferenceDataStore {
    public void putString(String key, String value) {}
    public void putStringSet(String key, Set<String> values) {}
    public void putInt(String key, int value) {}
    public void putLong(String key, long value) {}
    public void putFloat(String key, float value) {}
    public void putBoolean(String key, boolean value) {}

    public String getString(String key, String defValue) { return defValue; }
    public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
    public int getInt(String key, int defValue) { return defValue; }
    public long getLong(String key, long defValue) { return defValue; }
    public float getFloat(String key, float defValue) { return defValue; }
    public boolean getBoolean(String key, boolean defValue) { return defValue; }
}

