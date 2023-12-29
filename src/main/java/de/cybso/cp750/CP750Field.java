package de.cybso.cp750;

import java.util.*;

public enum CP750Field {

    SYSINFO_VERSION("cp750.sysinfo.version"),
    SYS_FADER("cp750.sys.fader", 0, 100),
    SYS_MUTE("cp750.sys.mute", 0, 1),
    SYS_INPUT_MODE("cp750.sys.input_mode", CP750InputMode.getValues());

    private final Set<String> allowedValues;
    private final String key;
    private final boolean hasRange;
    private final int rangeFrom;
    private final int rangeTo;

    CP750Field(String key, String... allowedValues) {
        Set<String> set = new TreeSet<>();
        set.add("?");
        set.addAll(Arrays.asList(allowedValues));
        this.allowedValues = Collections.unmodifiableSet(set);
        this.key = key;
        this.hasRange = false;
        this.rangeFrom = 0;
        this.rangeTo = 0;
    }

    CP750Field(String key, int from, int to) {
        this.key = key;
        this.allowedValues = new HashSet<>();
        this.allowedValues.add("?");
        for (int i = from; i <= to; i++) {
            this.allowedValues.add("" + i);
        }
        this.hasRange = true;
        this.rangeTo = to;
        this.rangeFrom = from;
    }

    public static CP750Field byKey(String key) {
        for (CP750Field c : CP750Field.values()) {
            if (c.key.equals(key)) {
                return c;
            }
        }
        return null;
    }

    public boolean isAllowedValue(String v) {
        return this.allowedValues.contains(v);
    }

    public String getKey() {
        return key;
    }

    public Set<String> getAllowedValues() {
        return this.allowedValues;
    }

    public boolean isRange() {
        return this.hasRange;
    }

    public int getRangeFrom() {
        return this.rangeFrom;
    }

    public int getRangeTo() {
        return this.rangeTo;
    }

}
