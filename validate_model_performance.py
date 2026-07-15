import numpy as np
import onnxruntime as ort
from sklearn.metrics import classification_report

def run_ml_audit():
    # Load model
    model_path = "src/main/resources/models/anomaly_forest.onnx"
    print(f"Loading ONNX model from: {model_path}")
    session = ort.InferenceSession(model_path)
    
    np.random.seed(42)
    n_samples = 100
    
    # 1. Normal operations (P = V * I / 1000 + small noise)
    normal_voltage = np.random.uniform(220.0, 240.0, n_samples)
    normal_current = np.random.uniform(2.0, 15.0, n_samples)
    normal_power = (normal_voltage * normal_current / 1000.0) + np.random.normal(0, 0.05, n_samples)
    normal = np.column_stack([normal_power, normal_voltage, normal_current]).astype(np.float32)
    
    # 2. Injected Anomalies with noise/variance
    # A. Voltage sags (voltage centered at 170V, well below 200V limit)
    sag_voltage = np.random.normal(170.0, 5.0, n_samples)
    sag_current = np.random.uniform(2.0, 10.0, n_samples)
    sag_power = (sag_voltage * sag_current / 1000.0) + np.random.normal(0, 0.05, n_samples)
    sag = np.column_stack([sag_power, sag_voltage, sag_current]).astype(np.float32)
    
    # B. Voltage swells (voltage centered at 285V, well above 260V limit)
    swell_voltage = np.random.normal(285.0, 5.0, n_samples)
    swell_current = np.random.uniform(5.0, 15.0, n_samples)
    swell_power = (swell_voltage * swell_current / 1000.0) + np.random.normal(0, 0.05, n_samples)
    swell = np.column_stack([swell_power, swell_voltage, swell_current]).astype(np.float32)
    
    # C. Zero current under active load (high power, normal voltage, zero current +/- small noise)
    leak_voltage = np.random.uniform(220.0, 240.0, n_samples)
    leak_current = np.abs(np.random.normal(0.01, 0.005, n_samples)) # near-zero current
    leak_power = np.random.uniform(5.0, 9.0, n_samples) # high power load
    leak = np.column_stack([leak_power, leak_voltage, leak_current]).astype(np.float32)
    
    X_val = np.vstack([normal, sag, swell, leak])
    y_true = np.array([1] * n_samples + [-1] * (3 * n_samples)) # 1: normal, -1: anomalous
    
    # Predict
    inputs = {session.get_inputs()[0].name: X_val}
    y_pred, _ = session.run(None, inputs)
    y_pred = y_pred.flatten()
    
    # Compute metrics
    report = classification_report(y_true, y_pred, output_dict=True)
    precision = report['-1']['precision']
    recall = report['-1']['recall']
    accuracy = report['accuracy']
    
    print("\n=========================================")
    print("ML SECURITY AUDIT METRICS (WITH DISTRIBUTED NOISE)")
    print("=========================================")
    print(f"Anomaly Precision : {precision:.4f} (Threshold >= 0.95)")
    print(f"Anomaly Recall    : {recall:.4f} (Threshold >= 0.95)")
    print(f"Overall Accuracy  : {accuracy:.4f} (Threshold >= 0.95)")
    print("=========================================")
    
    assert precision >= 0.95, "ML Anomaly Precision validation failed!"
    assert recall >= 0.95, "ML Anomaly Recall validation failed!"
    assert accuracy >= 0.95, "ML Anomaly Accuracy validation failed!"
    print("\nSuccess: Model meets production readiness criteria.")

if __name__ == "__main__":
    run_ml_audit()
