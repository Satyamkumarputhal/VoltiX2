import os
import sys
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error

def train_and_export():
    # File paths
    london_dir = os.path.join("dataset", "London smart meter dataset")
    households_file = os.path.join(london_dir, "informations_households.csv")
    weather_file = os.path.join(london_dir, "weather_hourly_darksky.csv")
    
    # We will use block_0.csv for training to ensure fast compilation and training time
    block_file = os.path.join(london_dir, "halfhourly_dataset", "halfhourly_dataset", "block_0.csv")

    # Verify files exist
    for f in [households_file, weather_file, block_file]:
        if not os.path.exists(f):
            print(f"Error: Required file not found: {f}")
            sys.exit(1)

    print("Loading households metadata and mapping to Zone IDs...")
    households_df = pd.read_csv(households_file, usecols=['LCLid', 'Acorn_grouped'])
    # Map Acorn_grouped categories to integer Zone IDs
    zone_map = {'Affluent': 1, 'Comfortable': 2, 'Adversity': 3}
    households_df['zone_id'] = households_df['Acorn_grouped'].map(zone_map).fillna(1).astype(np.int32)
    households_df = households_df[['LCLid', 'zone_id']]

    print(f"Loading smart meter consumption data from: {block_file}")
    consumption_df = pd.read_csv(block_file, usecols=['LCLid', 'tstp', 'energy(kWh/hh)'])
    
    # Clean energy consumption column
    consumption_df['energy'] = pd.to_numeric(consumption_df['energy(kWh/hh)'], errors='coerce')
    consumption_df['energy'] = consumption_df['energy'].fillna(0.0)

    # Convert timestamps and aggregate to hourly intervals
    print("Aggregating energy consumption to hourly intervals...")
    consumption_df['timestamp'] = pd.to_datetime(consumption_df['tstp']).dt.floor('h')
    
    # Merge with zone metadata
    merged_df = pd.merge(consumption_df, households_df, on='LCLid', how='inner')
    
    # Group by Zone ID and hourly timestamp, summing the energy consumed
    hourly_df = merged_df.groupby(['zone_id', 'timestamp'])['energy'].sum().reset_index()
    hourly_df = hourly_df.rename(columns={'energy': 'total_kw_consumed'})

    print(f"Loading hourly weather logs from: {weather_file}")
    weather_df = pd.read_csv(weather_file, usecols=['time', 'temperature'])
    weather_df['timestamp'] = pd.to_datetime(weather_df['time']).dt.floor('h')
    weather_df = weather_df[['timestamp', 'temperature']].drop_duplicates(subset=['timestamp'])

    # Join energy consumption and weather logs on timestamp
    print("Joining hourly consumption with weather logs...")
    data_df = pd.merge(hourly_df, weather_df, on='timestamp', how='inner')

    # Sort sequentially for time-series modeling
    data_df = data_df.sort_values(by=['zone_id', 'timestamp']).reset_index(drop=True)

    # Feature Engineering
    data_df['hour_of_day'] = data_df['timestamp'].dt.hour.astype(np.float32)
    data_df['day_of_week'] = data_df['timestamp'].dt.dayofweek.astype(np.float32)
    data_df['temperature'] = data_df['temperature'].astype(np.float32)

    # Align features for predicting load 2 hours in advance:
    # Shift target variable back by 2 hours within each Zone ID
    data_df['target'] = data_df.groupby('zone_id')['total_kw_consumed'].shift(-2)
    
    # Drop rows without 2-hour future target due to shift
    data_df = data_df.dropna(subset=['target'])

    features = ['hour_of_day', 'day_of_week', 'temperature']

    # Sequentially split training and validation sets (80% train, 20% validation)
    split_idx = int(len(data_df) * 0.8)
    train_df = data_df.iloc[:split_idx]
    val_df = data_df.iloc[split_idx:]

    X_train = train_df[features].values.astype(np.float32)
    y_train = train_df['target'].values.astype(np.float32)
    X_val = val_df[features].values.astype(np.float32)
    y_val = val_df['target'].values.astype(np.float32)

    print(f"Training RandomForest Regressor demand forecasting model on {len(X_train)} samples...")
    model = RandomForestRegressor(n_estimators=100, max_depth=10, random_state=42)
    model.fit(X_train, y_train)

    predictions = model.predict(X_val)

    # Evaluate sequentially held-out validation performance
    mae = mean_absolute_error(y_val, predictions)
    rmse = np.sqrt(mean_squared_error(y_val, predictions))

    print(f"Time-Series Validation Metrics:")
    print(f"  MAE:  {mae:.4f}")
    print(f"  RMSE: {rmse:.4f}")

    print("Exporting demand forecaster model to ONNX...")
    try:
        from skl2onnx import to_onnx
        from skl2onnx.common.data_types import FloatTensorType

        initial_type = [('float_input', FloatTensorType([None, 3]))]
        # target_opset={'': 15, 'ai.onnx.ml': 3} resolves conflicting domain version constraints in python 3.14+
        onnx_model = to_onnx(model, initial_types=initial_type, target_opset={'': 15, 'ai.onnx.ml': 3})

        out_dir = os.path.join("src", "main", "resources", "models")
        os.makedirs(out_dir, exist_ok=True)
        onnx_path = os.path.join(out_dir, "load_forecaster.onnx")

        with open(onnx_path, "wb") as f:
            f.write(onnx_model.SerializeToString())
        print(f"Successfully exported ONNX model to: {onnx_path}")

    except Exception as e:
        print(f"Error during ONNX conversion: {e}")
        sys.exit(1)

if __name__ == "__main__":
    train_and_export()
