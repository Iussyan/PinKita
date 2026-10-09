#!/usr/bin/env python3
"""
build_graph.py
Constructs an immutable region.sqlite database conforming to Section 8.1 of spec.md.
"""

import argparse
import json
import os
import sqlite3
import sys

SCHEMA_SQL = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS metadata (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS nodes (
  node_id INTEGER PRIMARY KEY,
  lat REAL NOT NULL CHECK(lat BETWEEN -90 AND 90),
  lon REAL NOT NULL CHECK(lon BETWEEN -180 AND 180),
  layer INTEGER NOT NULL DEFAULT 0,
  component_id INTEGER NOT NULL,
  barrier TEXT
);

CREATE TABLE IF NOT EXISTS edges (
  edge_id INTEGER PRIMARY KEY,
  from_node INTEGER NOT NULL REFERENCES nodes(node_id),
  to_node INTEGER NOT NULL REFERENCES nodes(node_id),
  osm_way_id TEXT,
  length_m REAL NOT NULL CHECK(length_m > 0),
  cost_s REAL NOT NULL CHECK(cost_s > 0),
  geometry_json TEXT NOT NULL,
  path_class TEXT NOT NULL,
  crossing_kind TEXT,
  access_status TEXT NOT NULL,
  stairs INTEGER NOT NULL DEFAULT 0,
  layer INTEGER NOT NULL DEFAULT 0,
  reviewed INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS edges_from_idx ON edges(from_node);
CREATE INDEX IF NOT EXISTS edges_to_idx ON edges(to_node);

CREATE TABLE IF NOT EXISTS places (
  place_id TEXT PRIMARY KEY,
  osm_ref TEXT,
  name TEXT NOT NULL,
  normalized_name TEXT NOT NULL,
  category TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  building_id TEXT,
  recognition_supported INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS place_aliases (
  place_id TEXT NOT NULL REFERENCES places(place_id),
  alias TEXT NOT NULL,
  normalized_alias TEXT NOT NULL,
  PRIMARY KEY(place_id, normalized_alias)
);

CREATE TABLE IF NOT EXISTS entrances (
  entrance_id TEXT PRIMARY KEY,
  place_id TEXT NOT NULL REFERENCES places(place_id),
  name TEXT,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  graph_node_id INTEGER NOT NULL REFERENCES nodes(node_id),
  access_status TEXT NOT NULL,
  reviewed INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS landmarks (
  landmark_id TEXT PRIMARY KEY,
  place_id TEXT NOT NULL REFERENCES places(place_id),
  display_name TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  recognition_radius_m REAL NOT NULL,
  model_id TEXT NOT NULL,
  reference_count INTEGER NOT NULL
);
"""

def normalize_text(text: str) -> str:
    return " ".join(text.lower().strip().split())

def build_database(district_json_path: str, landmarks_json_path: str, output_sqlite_path: str):
    os.makedirs(os.path.dirname(os.path.abspath(output_sqlite_path)), exist_ok=True)
    if os.path.exists(output_sqlite_path):
        os.remove(output_sqlite_path)

    with open(district_json_path, "r", encoding="utf-8") as f:
        district = json.load(f)

    with open(landmarks_json_path, "r", encoding="utf-8") as f:
        landmarks_data = json.load(f)

    conn = sqlite3.connect(output_sqlite_path)
    cursor = conn.cursor()
    cursor.executescript(SCHEMA_SQL)

    # 1. Populate metadata
    metadata = [
        ("district_id", district.get("district_id", "demo-district")),
        ("district_name", district.get("name", "Demo District")),
        ("schema_version", "1"),
        ("routing_profile", "pedestrian-v1")
    ]
    cursor.executemany("INSERT INTO metadata (key, value) VALUES (?, ?);", metadata)

    # 2. Populate nodes
    nodes_data = []
    node_coords = {}
    for n in district.get("nodes", []):
        nid = n["id"]
        lat = n["lat"]
        lon = n["lon"]
        node_coords[nid] = (lat, lon)
        nodes_data.append((
            nid,
            lat,
            lon,
            n.get("layer", 0),
            n.get("component_id", 1),
            n.get("barrier", None)
        ))
    cursor.executemany(
        "INSERT INTO nodes (node_id, lat, lon, layer, component_id, barrier) VALUES (?, ?, ?, ?, ?, ?);",
        nodes_data
    )

    # 3. Populate edges
    edges_data = []
    walk_speed = 1.3
    for e in district.get("edges", []):
        eid = e["id"]
        from_node = e["from"]
        to_node = e["to"]
        length_m = e["length_m"]
        stairs = 1 if e.get("stairs", False) else 0
        crossing_kind = e.get("crossing_kind", None)

        # Cost calculation per Section 10.3 of spec.md
        base_cost = length_m / walk_speed
        penalty = (20.0 if stairs else 0.0) + (15.0 if crossing_kind else 0.0)
        cost_s = base_cost + penalty

        # Edge geometry
        p1 = node_coords[from_node]
        p2 = node_coords[to_node]
        geom_json = json.dumps([[p1[1], p1[0]], [p2[1], p2[0]]]) # GeoJSON lon, lat

        edges_data.append((
            eid,
            from_node,
            to_node,
            f"way-{eid}",
            length_m,
            cost_s,
            geom_json,
            e.get("path_class", "footway"),
            crossing_kind,
            "yes",
            stairs,
            0,
            1
        ))
    cursor.executemany(
        """INSERT INTO edges (
            edge_id, from_node, to_node, osm_way_id, length_m, cost_s,
            geometry_json, path_class, crossing_kind, access_status, stairs, layer, reviewed
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);""",
        edges_data
    )

    # 4. Populate places, entrances, and landmarks
    for lm in landmarks_data.get("landmarks", []):
        lmid = lm["landmark_id"]
        place_id = f"place-{lmid}"
        name = lm["display_name"]
        norm_name = normalize_text(name)
        lat = lm["lat"]
        lon = lm["lon"]
        entrance_node_id = lm["entrance_node_id"]

        # Place
        cursor.execute(
            """INSERT INTO places (
                place_id, osm_ref, name, normalized_name, category, lat, lon, building_id, recognition_supported
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);""",
            (place_id, f"ref-{place_id}", name, norm_name, "landmark", lat, lon, f"bld-{place_id}", 1)
        )

        # Place alias
        cursor.execute(
            "INSERT INTO place_aliases (place_id, alias, normalized_alias) VALUES (?, ?, ?);",
            (place_id, name, norm_name)
        )

        # Entrance
        entrance_id = f"entrance-{place_id}-1"
        cursor.execute(
            """INSERT INTO entrances (
                entrance_id, place_id, name, lat, lon, graph_node_id, access_status, reviewed
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);""",
            (entrance_id, place_id, f"Main Entrance", lat, lon, entrance_node_id, "yes", 1)
        )

        # Landmark
        cursor.execute(
            """INSERT INTO landmarks (
                landmark_id, place_id, display_name, lat, lon, recognition_radius_m, model_id, reference_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);""",
            (lmid, place_id, name, lat, lon, lm["recognition_radius_m"], "mobilenetv3-embedder-v1", lm["reference_count"])
        )

    conn.commit()
    conn.close()
    print(f"Successfully generated {output_sqlite_path} with {len(nodes_data)} nodes and {len(edges_data)} edges.")

def main():
    parser = argparse.ArgumentParser(description="Build region.sqlite from district and landmark fixtures")
    parser.add_argument("--district", default="fixtures/tiny_graph/district.json", help="Path to district.json")
    parser.add_argument("--landmarks", default="fixtures/recognition/mock_landmarks.json", help="Path to mock_landmarks.json")
    parser.add_argument("--output", default="build/demo/data/region.sqlite", help="Path to output region.sqlite")
    args = parser.parse_args()

    build_database(args.district, args.landmarks, args.output)

if __name__ == "__main__":
    main()
