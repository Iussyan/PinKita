#!/usr/bin/env python3
"""
package_region.py
Assembles all region files, calculates SHA-256 hashes, generates manifest.json,
and packages the archive into dist/<region_id>-<version>.zip per Section 7 of spec.md.
"""

import argparse
import hashlib
import json
import os
import zipfile

def compute_sha256(filepath: str) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()

def package_region(source_dir: str, district_json_path: str, output_zip_path: str, version: str = "1.0.0"):
    with open(district_json_path, "r", encoding="utf-8") as f:
        district = json.load(f)

    region_id = district.get("district_id", "demo-public-district")
    display_name = district.get("name", "Demo Public District")
    bounds = district.get("bounds", {
        "west": 120.9830,
        "south": 14.5980,
        "east": 120.9880,
        "north": 14.6010
    })

    # Ensure required placeholder files exist for offline map and notices
    map_dir = os.path.join(source_dir, "map")
    os.makedirs(map_dir, exist_ok=True)
    pmtiles_path = os.path.join(map_dir, "map.pmtiles")
    if not os.path.exists(pmtiles_path):
        with open(pmtiles_path, "wb") as f:
            f.write(b"PMTilesMockDataHeader1234567890")

    style_path = os.path.join(map_dir, "style.template.json")
    if not os.path.exists(style_path):
        style_content = {
            "version": 8,
            "name": "LocalOfflineStyle",
            "sources": {
                "local_tiles": {
                    "type": "vector",
                    "url": "pmtiles://{PMTILES_PATH}"
                }
            },
            "layers": []
        }
        with open(style_path, "w", encoding="utf-8") as f:
            json.dump(style_content, f, indent=2)

    licences_dir = os.path.join(source_dir, "licences")
    os.makedirs(licences_dir, exist_ok=True)
    notice_path = os.path.join(licences_dir, "NOTICE.txt")
    if not os.path.exists(notice_path):
        with open(notice_path, "w", encoding="utf-8") as f:
            f.write("Map data (c) OpenStreetMap contributors (ODbL)\nMobileNetV3 model Apache 2.0\n")

    # Inventory all files in source_dir (excluding manifest.json)
    file_entries = []
    all_relative_files = []

    for root, _, files in os.walk(source_dir):
        for file in files:
            if file == "manifest.json":
                continue
            full_path = os.path.join(root, file)
            rel_path = os.path.relpath(full_path, source_dir).replace("\\", "/")
            size_bytes = os.path.getsize(full_path)
            sha = compute_sha256(full_path)

            file_entries.append({
                "path": rel_path,
                "size_bytes": size_bytes,
                "sha256": sha
            })
            all_relative_files.append((full_path, rel_path))

    # Read AI model specs if available
    ai_index_path = os.path.join(source_dir, "ai", "embedding_index.json")
    ai_metadata = None
    if os.path.exists(ai_index_path):
        with open(ai_index_path, "r", encoding="utf-8") as f:
            ai_data = json.load(f)
            ai_metadata = {
                "model_id": ai_data.get("model_id", "mobilenetv3-embedder-v1"),
                "model_path": "models/mobilenetv3_embedder.tflite",
                "model_sha256": ai_data.get("model_sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                "embedding_dimension": ai_data.get("dimension", 128),
                "preprocess_id": ai_data.get("preprocess_id", "rear-camera-center-crop-v1"),
                "embedding_format": "float32-little-endian-l2",
                "calibration_id": "demo-synthetic-v1"
            }

    # Generate manifest.json
    manifest = {
        "schema_version": 1,
        "region_id": region_id,
        "display_name": display_name,
        "pack_version": version,
        "built_at": "2026-10-09T00:00:00Z",
        "bounds": bounds,
        "routing_profile": "pedestrian-v1",
        "ai": ai_metadata,
        "files": file_entries,
        "attributions": [
            {
                "label": "OpenStreetMap contributors",
                "url": "https://www.openstreetmap.org/copyright"
            }
        ]
    }

    manifest_path = os.path.join(source_dir, "manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    all_relative_files.append((manifest_path, "manifest.json"))

    # Create destination ZIP archive
    os.makedirs(os.path.dirname(os.path.abspath(output_zip_path)), exist_ok=True)
    with zipfile.ZipFile(output_zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for full_path, rel_path in all_relative_files:
            zf.write(full_path, arcname=rel_path)

    print(f"Successfully created pack {output_zip_path} ({os.path.getsize(output_zip_path)} bytes) with {len(file_entries)} files.")

def main():
    parser = argparse.ArgumentParser(description="Package region directory into hash-verified ZIP")
    parser.add_argument("--directory", default="build/demo", help="Source directory containing map, data, ai")
    parser.add_argument("--district", default="fixtures/tiny_graph/district.json", help="Path to district.json")
    parser.add_argument("--output", default="dist/synthetic-district-1.0.0.zip", help="Output zip path")
    parser.add_argument("--version", default="1.0.0", help="Pack version")
    args = parser.parse_args()

    package_region(args.directory, args.district, args.output, args.version)

if __name__ == "__main__":
    main()
