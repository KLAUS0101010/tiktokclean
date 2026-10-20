import re, sys, tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import patch_tiktok as P

fails = 0
def check(c, m):
    global fails
    print(("ok   " if c else "FAIL ") + m)
    if not c: fails += 1

SMALI = """.class public Lcom/x/Y;
.method public static a(Landroid/telephony/TelephonyManager;)Ljava/lang/String;
    .locals 2
    invoke-virtual {p0}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
    move-result-object v0
    invoke-virtual/range {p0 .. p0}, Landroid/telephony/TelephonyManager;->getNetworkOperator()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {p0, v1}, Landroid/telephony/TelephonyManager;->getSimState(I)I
    invoke-virtual {v0}, Ljava/util/Locale;->getCountry()Ljava/lang/String;
    invoke-virtual {v0, v1}, Landroid/content/ClipboardManager;->setPrimaryClip(Landroid/content/ClipData;)V
    invoke-virtual {v0}, Ljava/lang/String;->length()I
    invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getDeviceId()Ljava/lang/String;
    const-string v1, "https://app-measurement.com/a"
    return-object v0
.end method
"""

new, n = P.rewrite_calls(SMALI)
check(n == 5, f"5 calls rewritten (got {n})")
check("invoke-static {p0}, Ldev/clean/Hooks;->TelephonyManager_getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;" in new, "plain call -> static hook")
check("invoke-static/range {p0 .. p0}, Ldev/clean/Hooks;->TelephonyManager_getNetworkOperator(Landroid/telephony/TelephonyManager;)Ljava/lang/String;" in new, "range call -> static/range hook")
check("TelephonyManager_getSimState(Landroid/telephony/TelephonyManager;I)I" in new, "overload with int argument")
check("Hooks;->Locale_getCountry(Ljava/util/Locale;)Ljava/lang/String;" in new, "Locale.getCountry")
check("Hooks;->ClipboardManager_setPrimaryClip(Landroid/content/ClipboardManager;Landroid/content/ClipData;)V" in new, "setPrimaryClip")
check("String;->length()I" in new and "getDeviceId" in new, "unrelated calls untouched")

# every table entry must have a matching Java method in Hooks.java
java = (ROOT / "hooks" / "Hooks.java").read_text()
for owner, method, args, ret in P.TABLE:
    simple = P.hook_name(owner, method)
    argc = 1 + (0 if not args else 1)
    pat = re.compile(r"public static \w+ " + re.escape(simple) + r"\(([^)]*)\)")
    sigs = [len([a for a in m.group(1).split(",") if a.strip()]) for m in pat.finditer(java)]
    check(argc in sigs, f"Hooks.java has {simple} with {argc} argument(s)")

# whole-folder run
with tempfile.TemporaryDirectory() as d:
    r = Path(d)
    (r / "smali/com/x").mkdir(parents=True)
    (r / "smali/com/x/Y.smali").write_text(SMALI)
    (r / "smali_classes2/com/google/android/gms/ads/identifier").mkdir(parents=True)
    (r / "smali_classes2/com/google/android/gms/ads/identifier/AdvertisingIdClient.smali").write_text(
        ".class public Lcom/google/android/gms/ads/identifier/AdvertisingIdClient;\n"
        ".method public static getAdvertisingIdInfo(Landroid/content/Context;)Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;\n"
        "    .locals 5\n    const/4 v0, 0x0\n    return-object v0\n.end method\n")
    check(P.main(["p", str(r)]) == 0, "folder run ok")
    out = (r / "smali/com/x/Y.smali").read_text()
    check("0.0.0.0/a" in out, "analytics host replaced")
    check(ZERO := "00000000-0000-0000-0000-000000000000" in (r / "smali_classes2/com/google/android/gms/ads/identifier/AdvertisingIdClient.smali").read_text(), "ad id patched")

with tempfile.TemporaryDirectory() as d:
    r = Path(d); (r / "smali").mkdir(); (r / "smali/A.smali").write_text(".class LA;\n")
    check(P.main(["p", str(r)]) == 1, "not TikTok -> refuses")

print("ALL PASSED" if not fails else f"{fails} FAILED")
sys.exit(1 if fails else 0)
