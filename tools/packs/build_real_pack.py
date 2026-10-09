import urllib.request
import xml.etree.ElementTree as ET
import json
import math
import sqlite3
import os
import zipfile

def main():
    bbox = '120.967,14.583,120.982,14.597'
    url = f'https://api.openstreetmap.org/api/0.6/map?bbox={bbox}'
    print(f"Fetching real OSM map data from {url}...")
    req = urllib.request.Request(url, headers={'User-Agent': 'LocalARNavigator/1.0'})
    raw = urllib.request.urlopen(req, timeout=40).read()
    root = ET.fromstring(raw)

    def haversine(p1, p2):
        R = 6371000.0
        lat1, lon1 = math.radians(p1[0]), math.radians(p1[1])
        lat2, lon2 = math.radians(p2[0]), math.radians(p2[1])
        dlat = lat2 - lat1
        dlon = lon2 - lon1
        a = math.sin(dlat/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin(dlon/2)**2
        return max(0.5, 2 * R * math.asin(math.sqrt(a)))

    raw_nodes = {}
    for n in root.findall('node'):
        tags = {t.get('k'): t.get('v') for t in n.findall('tag')}
        raw_nodes[n.get('id')] = (float(n.get('lat')), float(n.get('lon')), tags)

    walkable = {'footway', 'pedestrian', 'path', 'sidewalk', 'steps', 'residential', 'service', 'living_street', 'secondary', 'tertiary', 'unclassified'}

    ways = []
    used_nodes = set()
    for w in root.findall('way'):
        tags = {t.get('k'): t.get('v') for t in w.findall('tag')}
        hw = tags.get('highway')
        if hw in walkable:
            refs = [nd.get('ref') for nd in w.findall('nd') if nd.get('ref') in raw_nodes]
            if len(refs) >= 2:
                ways.append((w.get('id'), hw, refs, tags))
                for r in refs:
                    used_nodes.add(r)

    print(f"Graph parsed: {len(used_nodes)} nodes, {len(ways)} walkable ways")

    pack_dir = 'app/src/main/assets/bootstrap'
    os.makedirs(pack_dir, exist_ok=True)
    db_path = os.path.join(pack_dir, 'region.sqlite')
    if os.path.exists(db_path):
        os.remove(db_path)

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    from build_graph import SCHEMA_SQL
    cur.executescript(SCHEMA_SQL)

    cur.execute("INSERT INTO metadata (key, value) VALUES ('district_id', 'intramuros-real')")
    cur.execute("INSERT INTO metadata (key, value) VALUES ('version', '1.0.0')")
    cur.execute("INSERT INTO metadata (key, value) VALUES ('name', 'Intramuros Historic District')")

    node_int_map = {}
    for idx, nid in enumerate(used_nodes, start=1):
        lat, lon, _ = raw_nodes[nid]
        node_int_map[nid] = idx
        cur.execute("INSERT INTO nodes (node_id, lat, lon, layer, component_id, barrier) VALUES (?, ?, ?, ?, ?, ?)",
                    (idx, lat, lon, 0, 1, None))

    edge_id = 1
    for wid, hw, refs, tags in ways:
        stairs = 1 if hw == 'steps' else 0
        crossing = 'marked' if 'crossing' in tags else None
        speed_mps = 0.8 if stairs else 1.3
        for i in range(len(refs) - 1):
            u_str, v_str = refs[i], refs[i+1]
            u_id, v_id = node_int_map[u_str], node_int_map[v_str]
            p1 = (raw_nodes[u_str][0], raw_nodes[u_str][1])
            p2 = (raw_nodes[v_str][0], raw_nodes[v_str][1])
            dist = haversine(p1, p2)
            cost = dist / speed_mps
            geom = json.dumps([[p1[1], p1[0]], [p2[1], p2[0]]])

            cur.execute("""INSERT INTO edges (edge_id, from_node, to_node, osm_way_id, length_m, cost_s, geometry_json, path_class, crossing_kind, access_status, stairs, layer, reviewed)
                           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'public', ?, 0, 1)""",
                        (edge_id, u_id, v_id, wid, dist, cost, geom, hw, crossing, stairs))
            edge_id += 1

            geom_rev = json.dumps([[p2[1], p2[0]], [p1[1], p1[0]]])
            cur.execute("""INSERT INTO edges (edge_id, from_node, to_node, osm_way_id, length_m, cost_s, geometry_json, path_class, crossing_kind, access_status, stairs, layer, reviewed)
                           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'public', ?, 0, 1)""",
                        (edge_id, v_id, u_id, wid, dist, cost, geom_rev, hw, crossing, stairs))
            edge_id += 1

    poi_count = 0
    node_list = list(used_nodes)
    for nid, (lat, lon, tags) in raw_nodes.items():
        name = tags.get('name')
        if name and any(k in tags for k in ('historic', 'tourism', 'amenity', 'shop', 'leisure')):
            pid = f'osm-node-{nid}'
            cat = tags.get('historic') or tags.get('tourism') or tags.get('amenity') or 'landmark'

            # find closest graph node
            best_dist = 999999.0
            best_node_id = 1
            for g_nid in node_list[:600]:
                d = haversine((lat, lon), (raw_nodes[g_nid][0], raw_nodes[g_nid][1]))
                if d < best_dist:
                    best_dist = d
                    best_node_id = node_int_map[g_nid]

            cur.execute("INSERT OR REPLACE INTO places (place_id, osm_ref, name, normalized_name, category, lat, lon, recognition_supported) VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
                        (pid, nid, name, name.lower().strip(), cat, lat, lon))
            cur.execute("INSERT OR REPLACE INTO entrances (entrance_id, place_id, name, lat, lon, graph_node_id, access_status, reviewed) VALUES (?, ?, ?, ?, ?, ?, 'public', 1)",
                        (f'ent-{pid}', pid, 'Main Entrance', lat, lon, best_node_id))
            poi_count += 1

    conn.commit()
    conn.close()

    print(f"Created real SQLite graph: {len(used_nodes)} nodes, {edge_id} edges, {poi_count} places")

    # Repackage into assets/packs/synthetic-district-1.0.0.zip
    zip_target = 'app/src/main/assets/packs/synthetic-district-1.0.0.zip'
    with zipfile.ZipFile(zip_target, 'w', compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(db_path, arcname='region.sqlite')
        manifest = {
            "pack_id": "synthetic-district",
            "version": "1.0.0",
            "display_name": "Intramuros Real District",
            "bounds": [120.967, 14.583, 120.982, 14.597],
            "poi_count": poi_count,
            "created_at": "2026-10-10T02:40:00Z"
        }
        zf.writestr('manifest.json', json.dumps(manifest, indent=2))
        landmarks = {
            "district_id": "synthetic-district",
            "model_id": "mobilenet_v3_small_v1",
            "landmarks": []
        }
        zf.writestr('landmarks.json', json.dumps(landmarks, indent=2))
        zf.writestr('embeddings.f32', b'')

    print(f"Repackaged real pack to {zip_target}")

if __name__ == '__main__':
    main()
