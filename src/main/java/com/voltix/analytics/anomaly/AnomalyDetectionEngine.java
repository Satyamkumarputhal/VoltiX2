package com.voltix.analytics.anomaly;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.voltix.analytics.onnx.OnnxSessionPool;
import com.voltix.telemetry.dto.TelemetryPacket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.OptionalDouble;

@Service
public class AnomalyDetectionEngine {
    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionEngine.class);

    /**
     * Calibrated decision threshold on the Isolation Forest's decision_function output.
     * <p>
     * The model's own predict() label (output 0) uses an internal threshold of 0.0 on
     * the decision_function, which was empirically found to misclassify ~68% of
     * legitimate normal readings (voltage 220-240V, current 2-15A, P=V*I/1000+noise)
     * as anomalous — see validate_model_performance.py and the calibration analysis
     * that produced this constant. A threshold of -0.08 on the raw decision_function
     * score gives a ~0% false-positive rate on normal readings while still catching
     * ~82-84% of the deliberate zero-current-under-load ("leak") anomaly pattern.
     * More negative decision_function values indicate a stronger anomaly signal.
     */
    private static final double DECISION_THRESHOLD = -0.08;

    private final OrtEnvironment environment;
    private final OnnxSessionPool sessionPool;
    private final RulesFallbackEngine fallbackEngine;
    private final Timer inferenceTimer;

    public AnomalyDetectionEngine(OrtEnvironment environment,
                                  @Qualifier("anomalySessionPool") OnnxSessionPool sessionPool,
                                  RulesFallbackEngine fallbackEngine,
                                  MeterRegistry meterRegistry) {
        this.environment = environment;
        this.sessionPool = sessionPool;
        this.fallbackEngine = fallbackEngine;
        this.inferenceTimer = Timer.builder("voltix.analytics.onnx.inference.latency")
                .description("Track 1 ONNX anomaly inference latency")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public AnomalyResult evaluate(TelemetryPacket packet) {
        if (!sessionPool.available()) {
            return fallbackEngine.evaluate(packet);
        }
        return inferenceTimer.record(() -> evaluateWithOnnx(packet));
    }

    private AnomalyResult evaluateWithOnnx(TelemetryPacket packet) {
        OrtSession session = null;
        try {
            session = sessionPool.borrowSession().orElse(null);
            if (session == null) {
                return fallbackEngine.evaluate(packet);
            }

            String inputName = session.getInputNames().iterator().next();
            float[][] features = new float[][]{{
                    packet.getKwConsumed().floatValue(),
                    packet.getVoltage().floatValue(),
                    packet.getCurrent().floatValue()
            }};

            try (OnnxTensor tensor = OnnxTensor.createTensor(environment, features);
                 OrtSession.Result result = session.run(Map.of(inputName, tensor))) {

                // Output 1 = decision_function score (continuous, negative = more anomalous).
                // We deliberately do NOT trust output 0 (the model's built-in predict()
                // label) for the anomalous decision — its internal threshold of 0.0
                // produces an unacceptable false-positive rate on legitimate normal
                // readings (see DECISION_THRESHOLD javadoc). Instead we re-derive the
                // anomalous decision from the raw score using our calibrated threshold.
                if (result.size() > 1) {
                    OptionalDouble decisionScore = parseFirstNumber(result.get(1).getValue());
                    if (decisionScore.isPresent()) {
                        double raw = decisionScore.getAsDouble();
                        boolean anomalous = raw < DECISION_THRESHOLD;
                        // Convert to a 0..1 severity score for storage/display:
                        // more negative raw score → higher anomaly severity.
                        double continuousScore = Math.max(0.0, Math.min(1.0, 0.5 - raw));

                        log.debug("ONNX inference: features=[kW={}, V={}, A={}], decisionScore={}, anomalous={}",
                                packet.getKwConsumed(), packet.getVoltage(), packet.getCurrent(),
                                raw, anomalous);

                        return new AnomalyResult(continuousScore, anomalous, "ONNX");
                    }
                }

                // Model only exposes the binary label (no decision_function output) —
                // fall back to the label directly since we have no continuous score to calibrate.
                OptionalDouble labelOpt = parseFirstNumber(result.get(0).getValue());
                double label = labelOpt.orElse(0.0);
                boolean anomalous = (label == -1.0);
                double continuousScore = anomalous ? 0.85 : 0.10;

                log.debug("ONNX inference (label-only): features=[kW={}, V={}, A={}], label={}, score={}",
                        packet.getKwConsumed(), packet.getVoltage(), packet.getCurrent(),
                        label, continuousScore);

                return new AnomalyResult(continuousScore, anomalous, "ONNX");
            }
        } catch (Exception ex) {
            log.warn("ONNX anomaly inference failed. Falling back to deterministic rules.", ex);
            return fallbackEngine.evaluate(packet);
        } finally {
            sessionPool.returnSession(session);
        }
    }

    private OptionalDouble parseFirstNumber(Object value) {
        if (value instanceof Number number) {
            return OptionalDouble.of(number.doubleValue());
        }
        if (value != null && value.getClass().isArray() && Array.getLength(value) > 0) {
            return parseFirstNumber(Array.get(value, 0));
        }
        return OptionalDouble.empty();
    }
}
