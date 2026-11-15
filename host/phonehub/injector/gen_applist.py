#!/usr/bin/env python3
# gen_applist.py — build pkg<TAB>label for the Pixel's launchable apps.
# Labels via adb are not cleanly available, so: known-app map first, else a heuristic on the package.
#   python3 gen_applist.py <PIXEL_SERIAL> > applist.tsv
import subprocess, sys, re

if len(sys.argv) < 2:
    sys.exit("usage: gen_applist.py <PIXEL_SERIAL> > applist.tsv")
PIXEL = sys.argv[1]

KNOWN = {
 "com.android.chrome":"Chrome","com.android.settings":"Settings","com.android.vending":"Play Store",
 "com.google.android.GoogleCamera":"Camera","com.google.android.apps.docs":"Docs","com.google.android.apps.maps":"Maps",
 "com.google.android.apps.messaging":"Messages","com.google.android.apps.photos":"Photos","com.google.android.gm":"Gmail",
 "com.google.android.youtube":"YouTube","com.google.android.apps.youtube.music":"YT Music","com.google.android.keep":"Keep",
 "com.google.android.calendar":"Calendar","com.google.android.contacts":"Contacts","com.google.android.deskclock":"Clock",
 "com.google.android.dialer":"Phone","com.google.android.apps.nbu.files":"Files","com.anthropic.claude":"Claude",
 "com.google.android.apps.bard":"Gemini","com.facebook.katana":"Facebook","com.facebook.orca":"Messenger",
 "com.instagram.android":"Instagram","com.spotify.music":"Spotify","com.netflix.mediaclient":"Netflix",
 "com.amazon.mShop.android.shopping":"Amazon","com.ubercab":"Uber","com.ubercab.eats":"Uber Eats",
 "com.affirm.central":"Affirm","com.airbnb.android":"Airbnb","com.autozone.mobile":"AutoZone",
 "com.barnesandnoble.app":"Barnes & Noble","com.chick.fil.a":"Chick-fil-A","com.chase.sig.android":"Chase",
 "com.google.android.apps.tasks":"Tasks","com.google.android.apps.tachyon":"Meet","com.whatsapp":"WhatsApp",
 "com.discord":"Discord","com.reddit.frontpage":"Reddit","com.twitter.android":"X","com.X.android":"X",
 "com.google.android.googlequicksearchbox":"Google","com.google.android.apps.wellbeing":"Wellbeing",
}

def clean(pkg):
    if pkg in KNOWN: return KNOWN[pkg]
    parts = pkg.split(".")
    stop = {"com","org","net","io","app","apps","android","mobile","central","client",
            "co","ai","air","us","the","inc","llc","main","go"}
    cand = [p for p in parts if p.lower() not in stop and not p.isdigit()]
    if not cand: cand = parts
    # pick the longest distinctive segment, else the 2nd segment
    seg = max(cand, key=len) if cand else pkg
    seg = re.sub(r'[_]+', ' ', seg)
    return seg[:1].upper() + seg[1:]

def main():
    out = subprocess.run(["adb","-s",PIXEL,"shell",
        "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"],
        capture_output=True, text=True).stdout
    pkgs = sorted(set(m.split("/")[0].strip() for m in re.findall(r'[\w.]+/[\w.$]+', out)))
    for p in pkgs:
        print(f"{p}\t{clean(p)}")

if __name__ == "__main__":
    main()
