#!/usr/bin/env python3
"""
TikTok Clean patcher. Usage: python3 patch_tiktok.py <folder from `apktool d -r`>

What it does to the *official* TikTok app:
  1. Every call where TikTok asks the phone about SIM / operator / country / system region
     is redirected to our own function (dev.clean.Hooks) that answers with the chosen country.
  2. Copying a link puts a cleaned link on the clipboard (tracking parameters removed).
  3. Third-party analytics host names inside the code are replaced by 0.0.0.0.
  4. The Google advertising-id function returns the "no id" value.
  6. Switches for the profile banner and comment repost are forced on (menu can turn off).
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


# ---- experiment flags: profile banner, comment repost ---------------------------------------
# TikTok reads a named server setting with a call like  reader("profile_bg_in_allow_list", default)  that
# returns the answer. Names of the reader class and method change with every TikTok release, so we do not
# look for them: we look for the flag's *name* (a string), find the call that is given that string, and
# put our adjust() after it. Only flags listed in hooks/Flags.java are touched.
FLAG_KEYS = ["profile_bg_in_allow_list", "profile_bg_enable_consumption_group",
             "tt_comment_repost", "tt_comment_repost_interaction", "tt_audio_comment_repost", "enable_self_repost"]
FLAGS_CLASS = "Ldev/clean/Flags;"

_METHOD_ANY = re.compile(r"(\.method [^\n]*\n)(.*?)(\.end method)", re.S)
_LOCALS = re.compile(r"^([ \t]*)\.locals (\d+)[ \t]*$", re.M)
_CONST = re.compile(r"^[ \t]*const-string (v\d+), \"([^\"]+)\"[ \t]*$")
_INVOKE = re.compile(r"^[ \t]*invoke-static \{(v\d+)(?:, (v\d+))?\}, L[^;]+;->[^(\s]+\(Ljava/lang/String;([ZI])\)([ZI])[ \t]*$")
_MOVE = re.compile(r"^([ \t]*)move-result (v\d+)[ \t]*$")


def patch_flags(text):
    """Returns (new text, {key: number of places}). Methods are changed one by one."""
    found = {}
    if not any(k in text for k in FLAG_KEYS):
        return text, found

    def per_method(m):
        head, body, end = m.groups()
        if not any(k in body for k in FLAG_KEYS):
            return m.group(0)
        lm = _LOCALS.search(body)
        if not lm:
            return m.group(0)
        spare = int(lm.group(2))                 # the register we add: v<locals>
        if spare > 15:
            return m.group(0)
        lines = body.split("\n")
        out, i, changed = [], 0, False
        keyreg = {}                               # register -> flag name, while it still holds it
        while i < len(lines):
            line = lines[i]
            out.append(line)
            cm = _CONST.match(line)
            if cm:
                if cm.group(2) in FLAG_KEYS:
                    keyreg[cm.group(1)] = cm.group(2)
                else:
                    keyreg.pop(cm.group(1), None)
            else:
                im = _INVOKE.match(line)
                if im and im.group(1) in keyreg and im.group(3) == im.group(4) and i + 1 < len(lines):
                    mv = _MOVE.match(lines[i + 1])
                    reg = mv.group(2) if mv else None
                    if mv and int(reg[1:]) <= 15 and reg != f"v{spare}" and im.group(2) is not None:
                        key, t = keyreg[im.group(1)], im.group(3)
                        out.append(lines[i + 1])
                        ind = mv.group(1)
                        out.append(f'{ind}const-string v{spare}, "{key}"')
                        out.append(f"{ind}invoke-static {{v{spare}, {reg}}}, {FLAGS_CLASS}->adjust(Ljava/lang/String;{t}){t}")
                        out.append(f"{ind}move-result {reg}")
                        found[key] = found.get(key, 0) + 1
                        changed = True
                        i += 1
            i += 1
        if not changed:
            return m.group(0)
        new_body = "\n".join(out)
        new_body = _LOCALS.sub(lambda l: f"{l.group(1)}.locals {int(l.group(2)) + 1}", new_body, count=1)
        return head + new_body + end

    return _METHOD_ANY.sub(per_method, text), found


# ---- feature switches: hook the top of the app's flag readers ---------------------------------------
# TikTok reads every server-side switch through a few small methods of one obfuscated class, one method per
# value type, each taking (key, default). Whatever the class is called in this version, its shape is the same,
# so we find it by shape and put one question at the top of every such method: "does the mod want to answer
# this key?" (Flags.forcedX returns null for every key we do not care about, so nothing else changes).
_READER_SIG = re.compile(r"\((Ljava/lang/String;)(Z|I|J|F|D|Ljava/lang/String;)\)(Z|I|J|F|D|Ljava/lang/String;)$")
_FORCED = {
    "Z": ("forcedBoolean", "Ljava/lang/Boolean;", "booleanValue()Z", ""),
    "I": ("forcedInt", "Ljava/lang/Integer;", "intValue()I", ""),
    "J": ("forcedLong", "Ljava/lang/Long;", "longValue()J", "-wide"),
    "F": ("forcedFloat", "Ljava/lang/Float;", "floatValue()F", ""),
    "D": ("forcedDouble", "Ljava/lang/Double;", "doubleValue()D", "-wide"),
    "Ljava/lang/String;": ("forcedString", "Ljava/lang/String;", None, "-object"),
}
_READER_METHOD = re.compile(r"^\.method ([^\n]*?) ?([^\s(]+)(\([^)\n]*\)\S+)[ \t]*$", re.M)
FLAG_HOOK_MARK = "# clean: flag reader"


def reader_methods(text):
    """Names of the (String, T) -> T methods of a class, with their descriptors."""
    out = []
    for m in _READER_METHOD.finditer(text):
        mods, name, desc = m.groups()
        if "abstract" in mods or "native" in mods or "constructor" in mods or name.startswith("<"):
            continue
        sm = _READER_SIG.match(desc)
        if sm and sm.group(2) == sm.group(3):
            out.append((name, desc))
    return out


def is_reader_class(text):
    types = {d[-1] if not d.endswith("Ljava/lang/String;") else "S" for _, d in reader_methods(text)}
    return len(types) >= 3


def patch_flag_readers(text):
    """Returns (new text, number of reader methods hooked)."""
    if FLAG_HOOK_MARK in text or not is_reader_class(text):
        return text, 0
    count = [0]

    def per_method(m):
        head, body, end = m.groups()
        hm = _READER_METHOD.match(head.rstrip("\n"))
        if not hm:
            return m.group(0)
        mods, name, desc = hm.groups()
        sm = _READER_SIG.match(desc)
        if "abstract" in mods or "native" in mods or name.startswith("<") or not sm or sm.group(2) != sm.group(3):
            return m.group(0)
        t = sm.group(2)
        lm = _LOCALS.search(body)
        if not lm:
            return m.group(0)
        old = int(lm.group(2))
        static = " static" in " " + mods + " "
        key = old + 2 + (0 if static else 1)           # register of the key after we add two locals
        if old + 1 > 15 or key > 15:
            return m.group(0)
        fn, box, unbox, kind = _FORCED[t]
        a, b = f"v{old}", f"v{old + 1}"
        lines = body.split("\n")
        # insert before the first real instruction or label, after .locals / .param / .annotation blocks
        i = next(k for k, l in enumerate(lines) if _LOCALS.match(l)) + 1
        depth = 0
        while i < len(lines):
            s = lines[i].strip()
            if s.startswith(".annotation"):
                depth += 1
            elif s.startswith(".end annotation"):
                depth = max(0, depth - 1)
            elif depth == 0 and s and (not s.startswith(".") or s.startswith(".line")):
                break
            i += 1
        ind = "    "
        ins = [
            f"{ind}{FLAG_HOOK_MARK}",
            f"{ind}invoke-static {{p{0 if static else 1}}}, {FLAGS_CLASS}->{fn}(Ljava/lang/String;){box}",
            f"{ind}move-result-object {a}",
            f"{ind}if-eqz {a}, :clean_flag",
        ]
        if unbox:
            ins += [f"{ind}invoke-virtual {{{a}}}, {box}->{unbox}",
                    f"{ind}move-result{kind} {a}",
                    f"{ind}return{kind} {a}"]
        else:
            ins += [f"{ind}return-object {a}"]
        ins += [f"{ind}:clean_flag"]
        lines[i:i] = ins
        new_body = "\n".join(lines)
        new_body = _LOCALS.sub(lambda l: f"{l.group(1)}.locals {int(l.group(2)) + 2}", new_body, count=1)
        count[0] += 1
        return head + new_body + end

    return _METHOD_ANY.sub(per_method, text), count[0]


# ---- early start --------------------------------------------------------------------------------------
_APP_ONCREATE = re.compile(r"(\.method [^\n]*\bonCreate\(\)V\n)(.*?)(\.end method)", re.S)
BOOT_CALL = "invoke-static {}, Ldev/clean/Hooks;->boot()V"


def patch_application(text):
    """Start the mod at the top of Application.onCreate. Returns (text, 0 or 1)."""
    if not re.search(r"^\.super Landroid/app/Application;", text, re.M) or BOOT_CALL in text:
        return text, 0
    n = [0]

    def per(m):
        head, body, end = m.groups()
        lines = body.split("\n")
        i = 0
        while i < len(lines) and (not lines[i].strip() or lines[i].strip().startswith(".") and not lines[i].strip().startswith(".line")):
            i += 1
        lines.insert(i, "    " + BOOT_CALL)
        n[0] = 1
        return head + "\n".join(lines) + end
    return _APP_ONCREATE.sub(per, text, count=1), n[0]


# ---- Google sign-in ---------------------------------------------------------------------------
# Signing the app with our own key changes its certificate, and Google's sign-in through Play Services checks
# package name + certificate, so the button ends in "developer error". TikTok has a second way in that it uses
# when Play Services is missing: Google sign-in in the browser, which does not check the certificate. The app
# chooses between the two by asking the class below whether Play Services is available. We answer "no".
GOOGLE_AUTH_FILE = "com/bytedance/lobby/google/GoogleAuth.smali"
_IS_AVAILABLE = re.compile(r"(\.method [^\n]*\bisAvailable\(\)Z\n)(.*?)(\.end method)", re.S)


def patch_google_login(text):
    return _IS_AVAILABLE.subn(
        lambda m: m.group(1) + "    .locals 1\n    const/4 v0, 0x0\n    return v0\n" + m.group(3), text)


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
    flags = {}
    google = 0
    readers = []
    boot = 0
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
        if f.as_posix().endswith(GOOGLE_AUTH_FILE):
            new, g = patch_google_login(new)
            google += g
        new, fl = patch_flags(new)
        for k, v in fl.items():
            flags[k] = flags.get(k, 0) + v
        new, ap = patch_application(new)
        boot += ap
        new, rd = patch_flag_readers(new)
        if rd:
            readers.append((f.name, rd))
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
    print(f"[5] feature switches (banner, comment repost) hooked: {sum(flags.values())} place(s)")
    for k in FLAG_KEYS:
        print(f"      {k}: {flags.get(k, 0)}")
    print(f"    flag readers hooked at the top: {sum(n for _, n in readers)} method(s) in {len(readers)} class(es)")
    for name, n in readers[:6]:
        print(f"      {name}: {n}")
    if not readers and not flags:
        print("    WARNING: neither the flag-reader class nor any switch call was found in this TikTok version, banner/repost cannot be switched on."
              " Send me the lines of [5] and report.txt.")
    print(f"[5b] mod started from Application.onCreate: {boot}")
    print(f"[6] Google sign-in switched to the browser way (works with a re-signed app): {google}")
    if not google:
        print("    WARNING: GoogleAuth.isAvailable() was not found in this TikTok version, Google sign-in may fail with a developer error."
              " Use phone/email sign-in or send me report.txt.")
    print(f"    files changed: {files}")
    if calls == 0:
        print("error: found no SIM/region calls. This does not look like TikTok. Nothing is safe to ship.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
