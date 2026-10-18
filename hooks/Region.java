package dev.clean;

/** The countries the phone can pretend to be in. Pure data, no Android classes. */
public final class Region {
    public final String code, name, iso, mccMnc, operator;

    private Region(String code, String name, String iso, String mccMnc, String operator) {
        this.code = code; this.name = name; this.iso = iso; this.mccMnc = mccMnc; this.operator = operator;
    }

    public static final Region[] ALL = {
            new Region("NL", "Нидерланды", "nl", "20408", "KPN"),
            new Region("DE", "Германия", "de", "26201", "Telekom.de"),
            new Region("KZ", "Казахстан", "kz", "40101", "Beeline KZ"),
            new Region("TR", "Турция", "tr", "28601", "Turkcell"),
            new Region("PL", "Польша", "pl", "26002", "T-Mobile.pl"),
            new Region("GB", "Великобритания", "gb", "23410", "O2 - UK"),
            new Region("US", "США", "us", "310260", "T-Mobile"),
            new Region("FR", "Франция", "fr", "20801", "Orange F"),
            new Region("AE", "ОАЭ", "ae", "42402", "Etisalat"),
    };

    public static final String DEFAULT = "NL";

    public static Region byCode(String code) {
        for (Region r : ALL) if (r.code.equals(code)) return r;
        for (Region r : ALL) if (r.code.equals(DEFAULT)) return r;
        return ALL[0];
    }

    /** MCC is the first three digits of the code. */
    public String mcc() { return mccMnc.substring(0, 3); }
    public String mnc() { return mccMnc.substring(3); }
}
