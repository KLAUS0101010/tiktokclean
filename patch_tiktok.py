#!/usr/bin/env python3
"""
TikTok Clean patcher. Usage: python3 patch_tiktok.py <folder from `apktool d -r`>

What it does to the *official* TikTok app:
  1. Every call where TikTok asks the phone about SIM / operator / country / system region
     is redirected to our own function (dev.clean.Hooks) that answers with the chosen country.
  2. Copying a link puts a cleaned link on the clipboard (tracking parameters removed).
  3. Third-party analytics host names inside the code are replaced by 0.0.0.0.
  4. The Google advertising-id function returns the "no id" value.
  5. Advertising videos are taken out of the feed list (disable with --no-ads).
It cannot hide you from TikTok's own servers (your IP, your account's registration country).
"""
import re
import sys
from pathlib import Path

HOOK_CLASS = "Ldev/clean/Hooks;"
TM = "Landroid/telephony/TelephonyManager;"
SI = "Landroid/telephony/SubscriptionInfo;"
LOC = "Ljava/util/Locale;"
CM = "Landroid/content/ClipboardManager;"
STR = "Ljava/lang/String;"

# (owner, method, argument descriptor, return descriptor)
TABLE = [
    (TM, "getSimCountryIso", "", STR), (TM, "getSimCountryIso", "I", STR),
    (TM, "getNetworkCountryIso", "", STR), (TM, "getNetworkCountryIso", "I", STR),
    (TM, "getSimOperator", "", STR),
    (TM, "getNetworkOperator", "", STR),
    (TM, "getSimOperatorName", "", STR),
    (TM, "getNetworkOperatorName", "", STR),
    (TM, "getSimState", "", "I"), (TM, "getSimState", "I", "I"),
    (TM, "hasIccCard", "", "Z"), (TM, "hasIccCard", "I", "Z"),
    (SI, "getCountryIso", "", STR),
    (SI, "getMccString", "", STR), (SI, "getMncString", "", STR),
    (SI, "getMcc", "", "I"), (SI, "getMnc", "", "I"),
    (LOC, "getCountry", "", STR),
    (CM, "setPrimaryClip", "Landroid/content/ClipData;", "V"),
]
LOOKUP = {t: t for t in TABLE}


def hook_name(owner, method):
    return owner[1:-1].split("/")[-1] + "_" + method


CALL = re.compile(
    r"invoke-virtual(/range)? (\{[^}]*\}), (L[^;]+;)->(\w+)\(([^)]*)\)(\S+)")


def rewrite_calls(text):
    count = 0

    def sub(m):
        nonlocal count
        rng, regs, owner, name, args, ret = m.groups()
        if (owner, name, args, ret) not in LOOKUP:
            return m.group(0)
        count += 1
        return (f"invoke-static{rng or ''} {regs}, {HOOK_CLASS}->{hook_name(owner, name)}"
                f"({owner}{args}){ret}")

    return CALL.sub(sub, text), count


# ---- third-party analytics (same idea as MAX Clean) --------------------------------------
DEAD_HOST = "0.0.0.0"
HOSTS = [
    "app-measurement.com", "firebase-settings.crashlytics.com", "reports.crashlytics.com",
    "api.appsflyer.com", "app.adjust.com", "appmetrica.yandex.ru", "mc.yandex.ru", "sentry.io",
]
AD_HOSTS = ["pangle.io", "pangleglobal.com", "pangolin-sdk-toutiao.com"]   # the ad network of the same company
ZERO_AD_ID = "00000000-0000-0000-0000-000000000000"
INFO_CLS = "Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;"


# ---- ads in the feed -----------------------------------------------------------------------
# TikTok keeps the videos it got from the server in a FeedItemList. Every time the app takes that list
# out, we first remove the entries that say isAd() == true (dev.clean.AdFilter does it).
FEED_CLASS = "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;"
FILTER_CALL = "Ldev/clean/AdFilter;->filter(Ljava/util/List;)Ljava/util/List;"
LOW_REG = re.compile(r"v([0-9]|1[0-5])$")   # registers that a normal invoke can address

_FEED_READ = re.compile(
    r"^([ \t]*)iget-object (v\d+), (\S+), " + re.escape(FEED_CLASS) + r"->\w+:Ljava/util/List;[ \t]*$", re.M)


def patch_feed_reads(text):
    """After every read of a List field of FeedItemList, filter the list."""
    n = 0

    def sub(m):
        nonlocal n
        indent, dst = m.group(1), m.group(2)
        if not LOW_REG.match(dst):
            return m.group(0)
        n += 1
        return (m.group(0) + f"\n{indent}invoke-static {{{dst}}}, {FILTER_CALL}"
                f"\n{indent}move-result-object {dst}")

    return _FEED_READ.sub(sub, text), n


_METHOD = re.compile(r"(\.method [^\n]*\)Ljava/util/List;\n)(.*?)(\.end method)", re.S)
_RET = re.compile(r"^([ \t]*)return-object (v\d+)[ \t]*$", re.M)


def patch_feed_methods(text):
    """Inside FeedItemList itself: methods that return a List filter it before returning."""
    if not re.search(r"^\.class [^\n]*" + re.escape(FEED_CLASS) + r"$", text, re.M):
        return text, 0
    n = 0

    def body(m):
        nonlocal n
        head, code, end = m.groups()
        if "<init>" in head or " abstract " in head or " native " in head:
            return m.group(0)

        def ret(r):
            nonlocal n
            indent, reg = r.groups()
            if not LOW_REG.match(reg):
                return r.group(0)
            n += 1
            return (f"{indent}invoke-static {{{reg}}}, {FILTER_CALL}\n{indent}move-result-object {reg}\n"
                    f"{indent}return-object {reg}")

        return head + _RET.sub(ret, code) + end

    return _METHOD.sub(body, text), n


def smali_files(root: Path):
    return [p for d in root.glob("smali*") if d.is_dir() for p in d.rglob("*.smali")]


def patch_ad_id(text):
    body = (
        "    .locals 3\n"
        f"    new-instance v0, {INFO_CLS}\n"
        f'    const-string v1, "{ZERO_AD_ID}"\n'
        "    const/4 v2, 0x1\n"
        f"    invoke-direct {{v0, v1, v2}}, {INFO_CLS}-><init>(Ljava/lang/String;Z)V\n"
        "    return-object v0\n"
    )
    pat = re.compile(
        r"(\.method [^\n]*\bgetAdvertisingIdInfo\(Landroid/content/Context;\)" + re.escape(INFO_CLS) + r"\n)(.*?)(\.end method)",
        re.DOTALL)
    return pat.subn(lambda m: m.group(1) + body + m.group(3), text)


def main(argv):
    args = [a for a in argv[1:] if not a.startswith("--")]
    flags = {a for a in argv[1:] if a.startswith("--")}
    if len(args) != 1 or flags - {"--no-ads"}:
        print(__doc__)
        return 2
    ads_on = "--no-ads" not in flags
    root = Path(args[0])
    if not any(root.glob("smali*")):
        print("error: no smali folders in", root)
        return 2

    calls = hosts = ads = files = feed = 0
    per = {}
    for f in smali_files(root):
        text = f.read_text(encoding="utf-8", errors="surrogateescape")
        new, n = rewrite_calls(text)
        calls += n
        for h in HOSTS + (AD_HOSTS if ads_on else []):
            if h in new:
                hosts += new.count(h)
                new = new.replace(h, DEAD_HOST)
        if ads_on and FEED_CLASS in new:
            new, a1 = patch_feed_reads(new)
            new, a2 = patch_feed_methods(new)
            feed += a1 + a2
        if f.as_posix().endswith("ads/identifier/AdvertisingIdClient.smali"):
            new, a = patch_ad_id(new)
            ads += a
        if new != text:
            f.write_text(new, encoding="utf-8", errors="surrogateescape")
            files += 1

    print(f"[1] calls redirected to dev.clean.Hooks: {calls}")
    print(f"[2] third-party analytics hosts replaced: {hosts}")
    print(f"[3] advertising-id function replaced: {ads}")
    if ads_on:
        print(f"[4] ad filter placed at {feed} spot(s) where the app reads its video list")
        if feed == 0:
            print("    WARNING: FeedItemList was not found or has an unexpected shape in this TikTok version,"
                  " so ads are NOT removed. Send me report.txt and I will adapt the patch.")
    print(f"    files changed: {files}")
    if calls == 0:
        print("error: found no SIM/region calls. This does not look like TikTok. Nothing is safe to ship.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
