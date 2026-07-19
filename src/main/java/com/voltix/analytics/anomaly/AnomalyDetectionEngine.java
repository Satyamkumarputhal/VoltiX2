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

                // Anomaly decision: trust the model's own predict() label (output 0).
                // This is the model's built-in decision boundary at the trained
                // contamination=0.05 setting — the same documented, validated
                // configuration reported in PLAN_OF_ACTION.md (precision=0.81,
                // recall=0.97, accuracy=0.81 against the full validate_model_
                // performance.py set). Do NOT replace this with a custom threshold
                // on decision_function without re-running that full validation
                // (normal + sag + swell + leak) and confirming an improvement —
                // an earlier attempt at a custom -0.08 threshold was tuned only
                // against a narrow anomaly pattern and measured 0.52 accuracy /
                // 0.36 recall on the full set, worse than this baseline.
                OptionalDouble labelOpt = parseFirstNumber(result.get(0).getValue());
                double label = labelOpt.orElse(0.0);
                boolean anomalous = (label == -1.0);

                // Severity score for storage/display: use the continuous
                // decision_function (output 1) when available so alerts get a
                // varied, meaningful score instead of a flat 1.0 for every
                // anomaly. This does NOT affect the anomalous/normal decision
                // above — only the severity number attached to it.
                double continuousScore;
                if (result.size() > 1) {
                    OptionalDouble decisionScore = parseFirstNumber(result.get(1).getValue());
                    double raw = decisionScore.orElse(anomalous ? -0.15 : 0.05);
                    continuousScore = Math.max(0.0, Math.min(1.0, 0.5 - raw));
                } else {
                    continuousScore = anomalous ? 0.85 : 0.10;
                }

                log.debug("ONNX inference: features=[kW={}, V={}, A={}], label={}, severityScore={}",
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
