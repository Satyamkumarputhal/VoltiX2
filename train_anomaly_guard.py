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

    model = IsolationForest(n_estimators=100, contamination=0.02, random_state=42)
    model.fit(X_train)

    # Establish programmatic testing against injected fault vectors
    print("Running programmatic validation checks against injected fault patterns...")

    # Held-out evaluation set: normal samples from the dataset
    normal_samples = X[n_train_samples:n_train_samples + 100]

    # Injected faults:
    # 1. Zero current under active load: High active power (8.0 kW), nominal voltage, zero current
    fault_zero_current = np.tile([8.0, 230.0, 0.0], (100, 1))

    # 2. Voltage spike: voltage outside 200-260V window (290V) with high load
    fault_voltage_spike = np.tile([5.0, 290.0, 20.0], (100, 1))

    # 3. Voltage sag: nominal power/current, voltage outside 200-260V window (180V)
    fault_voltage_sag = np.tile([1.0, 180.0, 4.0], (100, 1))

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

    print(f"Evaluation results:")
    print(f"  Precision: {precision:.4f} (expected >= 0.90)")
    print(f"  Recall:    {recall:.4f} (expected >= 0.90)")
    print(f"  Accuracy:  {accuracy:.4f} (expected >= 0.90)")

    assert precision >= 0.90, f"Precision {precision:.4f} below baseline threshold!"
    assert recall >= 0.90, f"Recall {recall:.4f} below baseline threshold!"
    assert accuracy >= 0.90, f"Accuracy {accuracy:.4f} below baseline threshold!"

    print("Validation thresholds passed successfully. Exporting model to ONNX...")

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
