import urllib.request
import xml.etree.ElementTree as ET
import json
import math
import sqlite3
import os
import zipfile
import hashlib
import sys

def main():
    # Bounding box around Quezon City San Bartolome / Novaliches (covers User GPS 14.663, 121.012)
    # min_lon, min_lat, max_lon, max_lat
    min_lon, min_lat = 121.005, 14.658
    max_lon, max_lat = 121.018, 14.668
    bbox = f"{min_lon},{min_lat},{max_lon},{max_lat}"
    url = f"https://api.openstreetmap.org/api/0.6/map?bbox={bbox}"
    print(f"Fetching real OSM map data for Quezon City from {url}...")
    req = urllib.request.Request(url, headers={'User-Agent': 'LocalARNavigator/1.0'})
    raw = urllib.request.urlopen(req, timeout=45).read()
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

    walkable = {'footway', 'pedestrian', 'path', 'sidewalk', 'steps', 'residential', 'service', 'living_street', 'secondary', 'tertiary', 'primary', 'unclassified'}

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

    SCHEMA_SQL = """
    CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    CREATE TABLE nodes (id INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL, floor_level INTEGER NOT NULL DEFAULT 0, is_virtual INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE edges (id INTEGER PRIMARY KEY AUTOINCREMENT, from_node INTEGER NOT NULL, to_node INTEGER NOT NULL, distance_m REAL NOT NULL, base_cost_s REAL NOT NULL, stairs INTEGER NOT NULL DEFAULT 0, crossing_kind TEXT, bidirectional INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(from_node) REFERENCES nodes(id), FOREIGN KEY(to_node) REFERENCES nodes(id));
    CREATE TABLE places (id TEXT PRIMARY KEY, osm_ref TEXT, name TEXT NOT NULL, normalized_name TEXT NOT NULL, category TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, building_id TEXT, recognition_supported INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE entrances (id TEXT PRIMARY KEY, place_id TEXT NOT NULL, name TEXT, lat REAL NOT NULL, lon REAL NOT NULL, graph_node_id INTEGER NOT NULL, access_status TEXT NOT NULL DEFAULT 'public', reviewed INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(place_id) REFERENCES places(id), FOREIGN KEY(graph_node_id) REFERENCES nodes(id));
    CREATE TABLE landmark_records (id TEXT PRIMARY KEY, place_id TEXT NOT NULL, display_name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, recognition_radius_m REAL NOT NULL, model_id TEXT NOT NULL, reference_count INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(place_id) REFERENCES places(id));
    CREATE TABLE landmark_embeddings (id TEXT PRIMARY KEY, landmark_id TEXT NOT NULL, embedding BLOB NOT NULL, quality_score REAL NOT NULL, captured_at TEXT NOT NULL, FOREIGN KEY(landmark_id) REFERENCES landmark_records(id));
    CREATE INDEX idx_edges_from ON edges(from_node);
    CREATE INDEX idx_edges_to ON edges(to_node);
    CREATE INDEX idx_places_name ON places(normalized_name);
    """
    cur.executescript(SCHEMA_SQL)

    cur.execute("INSERT INTO metadata (key, value) VALUES ('district_id', 'qcu-sanbartolome')")
    cur.execute("INSERT INTO metadata (key, value) VALUES ('version', '1.0.0')")
    cur.execute("INSERT INTO metadata (key, value) VALUES ('district_name', 'Quezon City Novaliches / San Bartolome')")

    node_id_map = {}
    next_id = 1
    for r in used_nodes:
        lat, lon, _ = raw_nodes[r]
        cur.execute("INSERT INTO nodes (id, lat, lon) VALUES (?, ?, ?)", (next_id, lat, lon))
        node_id_map[r] = next_id
        next_id += 1

    edge_count = 0
    for wid, hw, refs, tags in ways:
        stairs = 1 if hw == 'steps' else 0
        crossing = 'zebra' if 'crossing' in tags else None
        for i in range(len(refs) - 1):
            u_osm = refs[i]
            v_osm = refs[i+1]
            u = node_id_map[u_osm]
            v = node_id_map[v_osm]
            dist = haversine(raw_nodes[u_osm], raw_nodes[v_osm])
            cost = dist / 1.3
            cur.execute("INSERT INTO edges (from_node, to_node, distance_m, base_cost_s, stairs, crossing_kind, bidirectional) VALUES (?, ?, ?, ?, ?, ?, 1)",
                        (u, v, dist, cost, stairs, crossing))
            cur.execute("INSERT INTO edges (from_node, to_node, distance_m, base_cost_s, stairs, crossing_kind, bidirectional) VALUES (?, ?, ?, ?, ?, ?, 1)",
                        (v, u, dist, cost, stairs, crossing))
            edge_count += 2

    # Extract real named places/POIs in the area
    place_count = 0
    for nid, (lat, lon, tags) in raw_nodes.items():
        name = tags.get('name')
        if name and ('amenity' in tags or 'shop' in tags or 'tourism' in tags or 'building' in tags or 'historic' in tags or 'leisure' in tags):
            cat = tags.get('amenity') or tags.get('shop') or tags.get('tourism') or tags.get('building') or 'landmark'
            p_id = f"osm-node-{nid}"
            cur.execute("INSERT OR IGNORE INTO places (id, osm_ref, name, normalized_name, category, lat, lon, building_id, recognition_supported) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 1)",
                        (p_id, nid, name, name.lower(), cat, lat, lon))
            # Find nearest node
            if used_nodes:
                nearest_osm = min(used_nodes, key=lambda r: haversine((lat, lon), raw_nodes[r]))
                nearest_node = node_id_map[nearest_osm]
                cur.execute("INSERT OR IGNORE INTO entrances (id, place_id, name, lat, lon, graph_node_id, access_status, reviewed) VALUES (?, ?, ?, ?, ?, ?, 'public', 1)",
                            (f"ent-{nid}", p_id, "Main Entrance", lat, lon, nearest_node))
            place_count += 1

    conn.commit()
    conn.close()

    print(f"Created real SQLite graph: {len(used_nodes)} nodes, {edge_count} edges, {place_count} places")

    # Repack into synthetic-district-1.0.0.zip
    manifest = {
        "districtId": "qcu-sanbartolome",
        "version": "1.0.0",
        "districtName": "Quezon City Novaliches / San Bartolome",
        "files": {
            "region.sqlite": ""
        }
    }
    with open(db_path, 'rb') as f:
        manifest["files"]["region.sqlite"] = hashlib.sha256(f.read()).hexdigest()

    manifest_path = os.path.join(pack_dir, 'manifest.json')
    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, indent=2)

    zip_path = 'app/src/main/assets/packs/synthetic-district-1.0.0.zip'
    with zipfile.ZipFile(zip_path, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        z.write(manifest_path, 'manifest.json')
        z.write(db_path, 'region.sqlite')

    print(f"Repackaged real pack to {zip_path}")

if __name__ == '__main__':
    main()
