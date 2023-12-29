package de.cybso.cp750;

public enum CP750InputMode {
    ANALOG("analog"),
    DIG_1("dig_1"),
    DIG_2("dig_2"),
    DIG_3("dig_3"),
    DIG_4("dig_4"),
    LAST("last"),
    MIC("mic"),
    NON_SYNC("non_sync");

    public final String value;

    CP750InputMode(String value) {
        this.value = value;
    }

    public static CP750InputMode byValue(String value) {
        for (CP750InputMode mode : CP750InputMode.values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }

    static String[] getValues() {
        CP750InputMode[] modes = values();
        String[] result = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            result[i] = modes[i].value;
        }
        return result;
    }

}
