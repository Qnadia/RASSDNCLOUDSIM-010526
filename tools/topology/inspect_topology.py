import json

def inspect(path):
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    print("NODES:")
    for n in d["nodes"]:
        t = n.get("type","?")
        bw = n.get("bw", 0)
        print(f"  {n['name']:15s} [{t:10s}] bw={bw:>14,}")
    print("LINKS:")
    for l in d.get("links", []):
        name = l.get("name", l.get("source","?")+"-"+l.get("destination","?"))
        print(f"  {name:35s} upBW={l.get('upBW',0):>12,}")

print("=== dataset-small ===")
inspect(r"e:\Workspace\v2\cloudsimsdn-research\dataset-small\physical.json")
