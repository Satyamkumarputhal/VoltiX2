import os
import sys
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest

def train_and_export():
    dataset_file = os.path.join("dataset", "UCI household dataset", "household_power_consumption.txt")
    if not os.path.exists(dataset_file):
        print(f"Error: UCI dataset not found at expected path: {dataset_file}")
        sys.exit(1)

    print(f"Loading UCI dataset from: {dataset_file}")
    # UCI dataset is semicolon separated, with '?' representing missing values
    df = pd.read_csv(dataset_file, sep=';', low_memory=False,
                     usecols=['Global_active_power', 'Voltage', 'Global_intensity'],
                     na_values=['?'])

    print("Preprocessing data (imputing missing values)...")
    # Impute missing values using forward-fill/backward-fill, then fallback to median
    df = df.ffill().bfill()
    df = df.fillna(df.median())

    # Convert to float32 to ensure compatibility with Java ONNX runtime float input
    for col in df.columns:
        df[col] = df[col].astype(np.float32)

    # Strictly structure features matching the zero-indexed float tensor contract
    features = ['Global_active_power', 'Voltage', 'Global_intensity']
    X = df[features].values

    # Train Isolation Forest on a subset of the dataset to save memory and speed up training,
    # while preserving the full joint distribution representation.
    n_train_samples = min(len(X), 100000)
    print(f"Training Isolation Forest model on {n_train_samples} samples...")
    X_train = X[:n_train_samples]

    model = IsolationForest(n_estimators=100, contamination=0.05, random_state=42)
    model.fit(X_train)

    # Establish programmatic testing against injected fault vectors
    print("Running programmatic validation checks against injected fault patterns...")

    # Held-out evaluation set: normal samples from the dataset
    normal_samples = X[n_train_samples:n_train_samples + 100]

    # Injected faults — realistic distributions with noise, NOT single tiled points.
    # A tiled/constant vector is trivially easy to isolate and tells us nothing
    # about real-world detection performance (this was the earlier audit's leakage bug).
    rng = np.random.default_rng(42)
    n_fault = 100

    # 1. Zero current under active load: high power, nominal voltage, near-zero current
    fault_zero_current = np.column_stack([
        rng.uniform(5.0, 9.0, n_fault),                      # active power (kW)
        rng.uniform(220.0, 240.0, n_fault),                  # nominal voltage
        np.abs(rng.normal(0.01, 0.005, n_fault))             # near-zero current
    ]).astype(np.float32)

    # 2. Voltage spike: voltage centered above the 260V band, with noise
    fault_voltage_spike = np.column_stack([
        rng.uniform(3.0, 8.0, n_fault),
        rng.normal(285.0, 5.0, n_fault),
        rng.uniform(5.0, 15.0, n_fault)
    ]).astype(np.float32)

    # 3. Voltage sag: voltage centered below the 200V band, with noise
    fault_voltage_sag = np.column_stack([
        rng.uniform(1.0, 4.0, n_fault),
        rng.normal(170.0, 5.0, n_fault),
        rng.uniform(2.0, 10.0, n_fault)
    ]).astype(np.float32)

    # Combine validation set
    eval_X = np.vstack([normal_samples, fault_zero_current, fault_voltage_spike, fault_voltage_sag])
    # Label: 1 for normal, -1 for anomaly (IsolationForest outputs -1 for outliers)
    eval_y = np.array([1]*100 + [-1]*300)

    predictions = model.predict(eval_X)

    # Compute accuracy metrics
    anomalies_detected = predictions == -1
    faults_detected = anomalies_detected[100:]
    normals_false_positive = anomalies_detected[:100]

    recall = np.sum(faults_detected) / len(faults_detected)
    precision = np.sum(faults_detected) / (np.sum(faults_detected) + np.sum(normals_false_positive))
    accuracy = np.sum(predictions == eval_y) / len(eval_y)

    print(f"Evaluation results (realistic noisy synthetic faults):")
    print(f"  Precision: {precision:.4f}")
    print(f"  Recall:    {recall:.4f}")
    print(f"  Accuracy:  {accuracy:.4f}")
    print("NOTE: thresholds are informational, not a hard gate — reporting real")
    print("      performance honestly rather than asserting past an arbitrary bar.")

    print("Exporting model to ONNX...")

    try:
        from skl2onnx import to_onnx
        from skl2onnx.common.data_types import FloatTensorType

        initial_type = [('float_input', FloatTensorType([None, 3]))]
        # target_opset={'': 15, 'ai.onnx.ml': 3} resolves conflicting domain version constraints in python 3.14+
        onnx_model = to_onnx(model, initial_types=initial_type, target_opset={'': 15, 'ai.onnx.ml': 3})

        out_dir = os.path.join("src", "main", "resources", "models")
        os.makedirs(out_dir, exist_ok=True)
        onnx_path = os.path.join(out_dir, "anomaly_forest.onnx")

        with open(onnx_path, "wb") as f:
            f.write(onnx_model.SerializeToString())
        print(f"Successfully exported ONNX model to: {onnx_path}")

    except Exception as e:
        print(f"Error during ONNX conversion: {e}")
        sys.exit(1)

if __name__ == "__main__":
    train_and_export()