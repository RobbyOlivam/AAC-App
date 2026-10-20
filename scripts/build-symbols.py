#!/usr/bin/env python3
"""
Builds the bundled symbol library from Mulberry and OpenMoji, and maps the starter vocabulary
to it. Inputs (not committed): a Mulberry checkout and the OpenMoji npm package.

  git clone --depth 1 https://github.com/mulberrysymbols/mulberry-symbols mulberry
  curl -L https://registry.npmjs.org/openmoji/-/openmoji-17.0.0.tgz | tar xz   # -> package/
  python3 scripts/build-symbols.py mulberry package <svgo-optimized-dir-or-empty>

Outputs:
  app/src/main/assets/symbols/{m,o}/*.svg      symbols (m = Mulberry id, o = OpenMoji hexcode)
  app/src/main/assets/symbols/index.tsv        set, id, name, grammar, tags  (for search)
  core/.../DefaultSymbols.kt                    starter cell id -> library id
"""
import csv, json, os, re, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
mulberry, openmoji, optimized = sys.argv[1], sys.argv[2], (sys.argv[3] if len(sys.argv) > 3 else "")
assets = os.path.join(ROOT, "app/src/main/assets/symbols")

def clean_name(raw):
    n = re.sub(r"_,_to$", "", raw)
    return n.replace("_-_", " ").replace("_", " ").strip()

# ---- Library index ------------------------------------------------------------------------
entries = []
mul_by_name = {}
for r in csv.DictReader(open(os.path.join(mulberry, "scripts/data/symbol-info-en.csv"), encoding="utf-8")):
    if r["rated"].strip() not in ("0", ""):
        continue  # skip symbols Mulberry rates as adult content
    src = os.path.join(mulberry, "EN", r["symbol"] + ".svg")
    if not os.path.exists(src):
        continue
    entries.append(("m", r["symbol-id"], clean_name(r["symbol"]), r["grammar"], (r["tags"] + " " + r["category"]).strip(), src))
    mul_by_name[r["symbol"].lower()] = r["symbol-id"]

om = json.load(open(os.path.join(openmoji, "data/openmoji.json"), encoding="utf-8"))
om_by_emoji = {}
for e in om:
    if e["skintone"] or e["group"] in ("extras-openmoji", "extras-unicode", "component"):
        continue
    src = os.path.join(openmoji, "color/svg", e["hexcode"] + ".svg")
    if not os.path.exists(src):
        continue
    tags = (e["tags"] + " " + e["openmoji_tags"]).replace(",", " ")
    entries.append(("o", e["hexcode"], e["annotation"], "", tags, src))
    om_by_emoji[e["emoji"]] = e["hexcode"]
    om_by_emoji[e["emoji"].replace("️", "")] = e["hexcode"]

for d in ("m", "o"):
    shutil.rmtree(os.path.join(assets, d), ignore_errors=True)
    os.makedirs(os.path.join(assets, d))
with open(os.path.join(assets, "index.tsv"), "w", encoding="utf-8") as f:
    for s, sid, name, grammar, tags, src in entries:
        opt = os.path.join(optimized, s, sid + ".svg") if optimized else ""
        shutil.copy(opt if opt and os.path.exists(opt) else src, os.path.join(assets, s, sid + ".svg"))
        f.write("\t".join(x.replace("\t", " ").replace("\n", " ") for x in (s, sid, name, grammar, tags)) + "\n")
shutil.copy(os.path.join(mulberry, "LICENSE.txt"), os.path.join(assets, "LICENSE-mulberry.txt"))
shutil.copy(os.path.join(openmoji, "LICENSE.txt"), os.path.join(assets, "LICENSE-openmoji.txt"))

# ---- Starter vocabulary mapping ------------------------------------------------------------
vocab_src = open(os.path.join(ROOT, "core/src/main/kotlin/com/robbyolivam/aac/core/DefaultVocabulary.kt"), encoding="utf-8").read()

# Hand-picked symbols where the automatic match would be wrong or missing (word -> Mulberry name).
OVERRIDES = {
    "all done": "finish", "see": "look_,_to", "big": "large", "toy car": "car_toy",
    "hot dog": "hot_dog", "ice cream": "ice_cream",
}

def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")

def mulberry_for(label, wtype):
    if label in OVERRIDES:
        return mul_by_name.get(OVERRIDES[label].lower())
    n = label.lower().replace(" ", "_")
    cands = []
    if wtype == "VERB":
        cands += [n + "_,_to", n + "_1_,_to"]
    cands += [n, n + "_1"]
    # Singular/plural only for real nouns; short words ("is", "us", "this") must not lose their s.
    if wtype in ("NOUN", "PLACE", "PERSON") and len(n) > 3:
        if n.endswith("s"):
            cands += [n[:-1], n[:-1] + "_1"]
        else:
            cands += [n + "s"]
    if wtype != "VERB":
        cands += [n + "_,_to"]
    for c in cands:
        if c in mul_by_name:
            return mul_by_name[c]
    return None

mapping = {}
# Folders: id, name, emoji, then e(...) entries until the closing of that Folder.
for m in re.finditer(r'Folder\(\s*"([^"]+)", "([^"]+)", "([^"]+)", listOf\((.*?)\n\s*\)\n\s*\)', vocab_src, re.S):
    fid, fname, femoji, body = m.groups()
    mapping[f"core.folder-{fid}"] = (fname, "FOLDER", femoji)
    for w in re.finditer(r'e\("([^"]+)", "([^"]+)", ([A-Z]+)', body):
        mapping[f"{fid}.{slug(w.group(1))}"] = (w.group(1), w.group(3), w.group(2))
core_block = vocab_src[vocab_src.index("private val coreColumns"):vocab_src.index("coreLevels")]
for w in re.finditer(r'e\("([^"]+)", "([^"]+)", ([A-Z]+)', core_block):
    mapping[f"core.{slug(w.group(1))}"] = (w.group(1), w.group(3), w.group(2))

result, misses = {}, []
for cid, (label, wtype, emoji) in sorted(mapping.items()):
    mid = mulberry_for(label, wtype)
    if mid:
        result[cid] = "m:" + mid
    elif emoji in om_by_emoji or emoji.replace("️", "") in om_by_emoji:
        result[cid] = "o:" + om_by_emoji.get(emoji, om_by_emoji.get(emoji.replace("️", "")))
    else:
        misses.append(cid)

out = os.path.join(ROOT, "core/src/main/kotlin/com/robbyolivam/aac/core/DefaultSymbols.kt")
with open(out, "w", encoding="utf-8") as f:
    f.write("package com.robbyolivam.aac.core\n\n")
    f.write("// Generated by scripts/build-symbols.py. Starter cell id -> bundled symbol\n")
    f.write("// (m = Mulberry symbol id, o = OpenMoji hexcode).\n")
    f.write("internal val defaultLibrarySymbols: Map<String, String> = mapOf(\n")
    for cid, lid in sorted(result.items()):
        f.write(f'    "{cid}" to "{lid}",\n')
    f.write(")\n")
m_count = sum(1 for v in result.values() if v.startswith("m:"))
print(f"library: {len(entries)} symbols; starter cells: {len(mapping)}, Mulberry {m_count}, OpenMoji {len(result) - m_count}, unmapped {len(misses)}")
print("unmapped:", misses[:40])
