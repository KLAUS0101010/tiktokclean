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

# ---- ad filter in the feed ----------------------------------------------------------------
FEED = """.class public Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;
.field public items:Ljava/util/List;
.method public <init>()V
    .locals 1
    return-void
.end method
.method public getItems()Ljava/util/List;
    .locals 2
    iget-object v0, p0, Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;->items:Ljava/util/List;
    return-object v0
.end method
.method public getCount()I
    .locals 1
    const/4 v0, 0x1
    return v0
.end method
"""
USER = """.class public Lcom/x/Feed;
.method public static f(Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;)V
    .locals 3
    iget-object v1, p0, Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;->items:Ljava/util/List;
    iget-object v20, p0, Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;->items:Ljava/util/List;
    iget-object v1, p0, Lcom/other/Thing;->items:Ljava/util/List;
    return-void
.end method
"""
nf, n1 = P.patch_feed_methods(FEED)
nf, n2 = P.patch_feed_reads(nf)
check(n1 == 1 and n2 == 1, f"feed class: 1 method return + 1 field read patched (got {n1},{n2})")
check(nf.count("AdFilter;->filter") == 2, "two filter calls in FeedItemList")
check("return v0" in nf and "return-void" in nf, "other returns untouched")
check("move-result-object v0\n    return-object v0" in nf, "result put back in same register before return")
nu, n3 = P.patch_feed_reads(USER)
check(n3 == 1 and nu.count("AdFilter") == 1, "only the low-register FeedItemList read patched; other classes and v20 untouched")
check(P.patch_feed_methods(USER)[1] == 0, "other classes' methods untouched")
with tempfile.TemporaryDirectory() as d:
    r = Path(d); (r / "smali/com/x").mkdir(parents=True)
    (r / "smali/com/x/Y.smali").write_text(SMALI)
    (r / "smali/com/x/Feed.smali").write_text(USER)
    (r / "smali/F.smali").write_text(FEED)
    check(P.main(["p", str(r)]) == 0, "run with ads on")
    check("AdFilter" in (r / "smali/F.smali").read_text() and "AdFilter" in (r / "smali/com/x/Feed.smali").read_text(), "filter inserted by main")
with tempfile.TemporaryDirectory() as d:
    r = Path(d); (r / "smali/com/x").mkdir(parents=True)
    (r / "smali/com/x/Y.smali").write_text(SMALI); (r / "smali/F.smali").write_text(FEED)
    check(P.main(["p", str(r), "--no-ads"]) == 0 and "AdFilter" not in (r / "smali/F.smali").read_text(), "--no-ads leaves the feed alone")
check(P.main(["p"]) == 2 and P.main(["p", "x", "--bogus"]) == 2, "bad arguments refused")

print("ALL PASSED" if not fails else f"{fails} FAILED")
sys.exit(1 if fails else 0)
