#!/usr/bin/env python3
"""
build_embeddings.py
Generates embeddings.f32, embedding_index.json, thresholds.json, and model.json
adhering to Section 15.4 and 15.7 of spec.md.
"""

import argparse
import json
import os
import struct
import numpy as np

def build_ai_pack(landmarks_json_path: str, output_dir: str, dimension: int = 1024):
    os.makedirs(output_dir, exist_ok=True)

    with open(landmarks_json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    landmarks = data.get("landmarks", [])

    rows = []
    vector_list = []
    thresholds = {}

    np.random.seed(42) # Deterministic generation for synthetic fixtures

    row_index = 0
    for lm in landmarks:
        lmid = lm["landmark_id"]
        ref_count = lm.get("reference_count", 15)
        thresh = lm.get("threshold", 0.72)
        thresholds[lmid] = thresh

        # Base vector for this landmark
        base_vec = np.random.randn(dimension).astype(np.float32)
        base_vec /= np.linalg.norm(base_vec)

        for i in range(ref_count):
            ref_id = f"{lmid}-ref-{i+1:02d}"
            # Small perturbation around base vector
            noise = np.random.randn(dimension).astype(np.float32) * 0.1
            vec = base_vec + noise
            vec /= np.linalg.norm(vec)

            vector_list.append(vec)
            rows.append({
                "row": row_index,
                "landmark_id": lmid,
                "reference_id": ref_id
            })
            row_index += 1

    # 1. Write embeddings.f32 (float32 little-endian row-major)
    f32_path = os.path.join(output_dir, "embeddings.f32")
    with open(f32_path, "wb") as f:
        for vec in vector_list:
            f.write(vec.tobytes())

    # 2. Write embedding_index.json
    index_path = os.path.join(output_dir, "embedding_index.json")
    index_data = {
        "schema_version": 1,
        "model_id": "mobilenetv3-embedder-v1",
        "model_sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "preprocess_id": "rear-camera-center-crop-v1",
        "dimension": dimension,
        "vector_count": len(vector_list),
        "rows": rows
    }
    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(index_data, f, indent=2)

    # 3. Write thresholds.json
    thresholds_path = os.path.join(output_dir, "thresholds.json")
    thresh_data = {
        "schema_version": 1,
        "model_id": "mobilenetv3-embedder-v1",
        "default_threshold": 0.70,
        "landmark_thresholds": thresholds,
        "calibrated_margin": 0.08,
        "temporal_rule": {
            "window_size": 5,
            "min_qualifying_frames": 3,
            "min_span_duration_ms": 1000,
            "expiry_duration_ms": 3000
        }
    }
    with open(thresholds_path, "w", encoding="utf-8") as f:
        json.dump(thresh_data, f, indent=2)

    # 4. Write model.json
    model_json_path = os.path.join(output_dir, "model.json")
    model_meta = {
        "model_id": "mobilenetv3-embedder-v1",
        "input_width": 224,
        "input_height": 224,
        "color_space": "RGB",
        "normalization": "none_or_standard",
        "dimension": dimension
    }
    with open(model_json_path, "w", encoding="utf-8") as f:
        json.dump(model_meta, f, indent=2)

    print(f"Generated AI pack in {output_dir}: {len(vector_list)} vectors of dimension {dimension}.")

def main():
    parser = argparse.ArgumentParser(description="Generate embeddings.f32 and index files")
    parser.add_argument("--landmarks", default="fixtures/recognition/mock_landmarks.json", help="Path to mock_landmarks.json")
    parser.add_argument("--output", default="build/demo/ai", help="Output directory for AI files")
    parser.add_argument("--dimension", type=int, default=1024, help="Embedding dimension")
    args = parser.parse_args()

    build_ai_pack(args.landmarks, args.output, args.dimension)

if __name__ == "__main__":
    main()
