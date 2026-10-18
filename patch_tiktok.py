#!/usr/bin/env python3
"""
TikTok Clean patcher. Usage: python3 patch_tiktok.py <folder from `apktool d -r`>

What it does to the *official* TikTok app:
  1. Every call where TikTok asks the phone about SIM / operator / country / system region
     is redirected to our own function (dev.clean.Hooks) that answers with the chosen country.
  2. Copying a link puts a cleaned link on the clipboard (tracking parameters removed).
  3. Third-party analytics host names inside the code are replaced by 0.0.0.0.
  4. The Google advertising-id function returns the "no id" value.
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
ZERO_AD_ID = "00000000-0000-0000-0000-000000000000"
INFO_CLS = "Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;"


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
    if len(argv) != 2:
        print(__doc__)
        return 2
    root = Path(argv[1])
    if not any(root.glob("smali*")):
        print("error: no smali folders in", root)
        return 2

    calls = hosts = ads = files = 0
    per = {}
    for f in smali_files(root):
        text = f.read_text(encoding="utf-8", errors="surrogateescape")
        new, n = rewrite_calls(text)
        calls += n
        for h in HOSTS:
            if h in new:
                hosts += new.count(h)
                new = new.replace(h, DEAD_HOST)
        if f.as_posix().endswith("ads/identifier/AdvertisingIdClient.smali"):
            new, a = patch_ad_id(new)
            ads += a
        if new != text:
            f.write_text(new, encoding="utf-8", errors="surrogateescape")
            files += 1

    print(f"[1] calls redirected to dev.clean.Hooks: {calls}")
    print(f"[2] third-party analytics hosts replaced: {hosts}")
    print(f"[3] advertising-id function replaced: {ads}")
    print(f"    files changed: {files}")
    if calls == 0:
        print("error: found no SIM/region calls. This does not look like TikTok. Nothing is safe to ship.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
