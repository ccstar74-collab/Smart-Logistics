"""Convert a CARLA OpenDRIVE (.xodr) map into lightweight browser JSON.
Usage: python tools/convert_town10hd.py reference/Town10HD.xodr public/data/town10hd-map.json
"""
import sys, math, json, xml.etree.ElementTree as ET
from pathlib import Path

src = Path(sys.argv[1] if len(sys.argv) > 1 else 'reference/Town10HD.xodr')
out = Path(sys.argv[2] if len(sys.argv) > 2 else 'public/data/town10hd-map.json')
root = ET.parse(src).getroot()
roads, allpts = [], []
step = 3.0

for road in root.findall('road'):
    pts = []
    pv = road.find('planView')
    if pv is None:
        continue
    for geom in pv.findall('geometry'):
        x = float(geom.attrib['x']); y = float(geom.attrib['y'])
        hdg = float(geom.attrib['hdg']); length = float(geom.attrib['length'])
        n = max(1, int(math.ceil(length / step)))
        child = list(geom)[0]
        for i in range(n + 1):
            s = length * i / n
            if child.tag == 'line':
                px = x + s * math.cos(hdg); py = y + s * math.sin(hdg)
            elif child.tag == 'arc':
                k = float(child.attrib['curvature'])
                if abs(k) < 1e-12:
                    px = x + s * math.cos(hdg); py = y + s * math.sin(hdg)
                else:
                    px = x + (math.sin(hdg + k*s) - math.sin(hdg)) / k
                    py = y - (math.cos(hdg + k*s) - math.cos(hdg)) / k
            else:
                continue
            if not pts or math.hypot(px-pts[-1][0], py-pts[-1][1]) > 0.05:
                pts.append((px, py)); allpts.append((px, py))
    roads.append({'id': road.attrib['id'], 'name': road.attrib.get('name',''), 'junction': road.attrib.get('junction','-1'), 'points': pts})

minx=min(x for x,y in allpts); maxx=max(x for x,y in allpts)
miny=min(y for x,y in allpts); maxy=max(y for x,y in allpts)
W,H,pad=1000,650,25
scale=min((W-2*pad)/(maxx-minx),(H-2*pad)/(maxy-miny))
offx=(W-(maxx-minx)*scale)/2; offy=(H-(maxy-miny)*scale)/2

def norm(p):
    x,y=p
    return (offx+(x-minx)*scale, H-(offy+(y-miny)*scale))

for r in roads:
    r['displayPoints']=[[round(a,1),round(b,1)] for a,b in map(norm,r.pop('points'))]

payload={'mapName':'Town10HD','source':src.name,'viewBox':[0,0,W,H],
         'bounds':{'minX':minx,'maxX':maxx,'minY':miny,'maxY':maxy},'roads':roads}
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(payload,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print(f'Wrote {out} ({len(roads)} roads)')
