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

                // Output 0 = label (1 = inlier, -1 = outlier) from Isolation Forest predict()
                OptionalDouble labelOpt = parseFirstNumber(result.get(0).getValue());
                double label = labelOpt.orElse(0.0);
                boolean anomalous = (label == -1.0);

                // Output 1 = decision_function score (continuous, negative = more anomalous)
                // sklearn Isolation Forest ONNX exports provide this as the second output.
                double continuousScore;
                if (result.size() > 1) {
                    OptionalDouble decisionScore = parseFirstNumber(result.get(1).getValue());
                    if (decisionScore.isPresent()) {
                        // decision_function: negative values = anomaly, positive = normal
                        // Convert to 0..1 range: more negative → higher anomaly score
                        double raw = decisionScore.getAsDouble();
                        continuousScore = Math.max(0.0, Math.min(1.0, 0.5 - raw));
                    } else {
                        continuousScore = anomalous ? 0.85 : 0.10;
                    }
                } else {
                    // Model only has one output (label only) — synthesize a reasonable score
                    continuousScore = anomalous ? 0.85 : 0.10;
                }

                log.debug("ONNX inference: features=[kW={}, V={}, A={}], label={}, score={}",
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
