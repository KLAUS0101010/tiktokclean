package dev.clean;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import java.util.Locale;

/**
 * Compiled separately and added to the app as an extra dex.
 * patch_tiktok.py rewrites TikTok's calls to the Android functions below so they land here.
 * The name of each method here is <AndroidClass>_<method>, the first argument is the object itself.
 */
public final class Hooks {
    private Hooks() {}

    private static volatile boolean told;

    /** One short message the first time TikTok asks the phone about the SIM, so you can see it works. */
    private static void hello() {
        Entry.start();
        if (told) return;
        try {
            final Object app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (!(app instanceof Context)) return;
            told = true;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override public void run() {
                    try { Toast.makeText((Context) app, "TikTok Clean: регион " + Store.region().label(), Toast.LENGTH_LONG).show(); }
                    catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    // ---- TelephonyManager -------------------------------------------------------------
    public static String TelephonyManager_getSimCountryIso(TelephonyManager tm) { hello(); return Store.region().iso; }
    public static String TelephonyManager_getSimCountryIso(TelephonyManager tm, int slot) { hello(); return Store.region().iso; }
    public static String TelephonyManager_getNetworkCountryIso(TelephonyManager tm) { hello(); return Store.region().iso; }
    public static String TelephonyManager_getNetworkCountryIso(TelephonyManager tm, int slot) { hello(); return Store.region().iso; }
    public static String TelephonyManager_getSimOperator(TelephonyManager tm) { hello(); return Store.region().mccMnc; }
    public static String TelephonyManager_getNetworkOperator(TelephonyManager tm) { hello(); return Store.region().mccMnc; }
    public static String TelephonyManager_getSimOperatorName(TelephonyManager tm) { hello(); return Store.region().operator; }
    public static String TelephonyManager_getNetworkOperatorName(TelephonyManager tm) { hello(); return Store.region().operator; }
    public static int TelephonyManager_getSimState(TelephonyManager tm) { return TelephonyManager.SIM_STATE_READY; }
    public static int TelephonyManager_getSimState(TelephonyManager tm, int slot) { return TelephonyManager.SIM_STATE_READY; }
    public static boolean TelephonyManager_hasIccCard(TelephonyManager tm) { return true; }
    public static boolean TelephonyManager_hasIccCard(TelephonyManager tm, int slot) { return true; }

    // ---- SubscriptionInfo -------------------------------------------------------------
    public static String SubscriptionInfo_getCountryIso(SubscriptionInfo si) { return Store.region().iso; }
    public static String SubscriptionInfo_getMccString(SubscriptionInfo si) { return Store.region().mcc(); }
    public static String SubscriptionInfo_getMncString(SubscriptionInfo si) { return Store.region().mnc(); }
    public static int SubscriptionInfo_getMcc(SubscriptionInfo si) { return Integer.parseInt(Store.region().mcc()); }
    public static int SubscriptionInfo_getMnc(SubscriptionInfo si) { return Integer.parseInt(Store.region().mnc()); }

    // ---- Locale: only the country part, the language stays as it is -------------------
    public static String Locale_getCountry(Locale l) {
        Entry.start();
        return Config.CHANGE_SYSTEM_REGION ? Store.region().code : l.getCountry();
    }

    // ---- Clipboard: TikTok links lose their tracking parameters when copied -----------
    public static void ClipboardManager_setPrimaryClip(ClipboardManager cm, ClipData clip) {
        ClipData out = clip;
        try { out = cleanClip(clip); } catch (Throwable ignored) { }
        cm.setPrimaryClip(out);
        try { Download.offer(out); } catch (Throwable ignored) { }
    }

    static ClipData cleanClip(ClipData clip) {
        if (clip == null || clip.getItemCount() == 0) return clip;
        boolean changed = false;
        ClipData.Item[] items = new ClipData.Item[clip.getItemCount()];
        for (int i = 0; i < items.length; i++) {
            ClipData.Item it = clip.getItemAt(i);
            CharSequence text = it.getText();
            String cleaned = text == null ? null : LinkCleaner.cleanText(text.toString());
            if (cleaned != null && !cleaned.equals(text.toString())) { items[i] = new ClipData.Item(cleaned); changed = true; }
            else items[i] = it;
        }
        if (!changed) return clip;
        ClipData out = new ClipData(clip.getDescription(), items[0]);
        for (int i = 1; i < items.length; i++) out.addItem(items[i]);
        return out;
    }
}
