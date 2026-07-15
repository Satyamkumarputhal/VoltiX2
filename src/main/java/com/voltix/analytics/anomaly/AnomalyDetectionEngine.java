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
                OptionalDouble parsedScore = parseFirstNumber(result.get(0).getValue());
                double score = parsedScore.orElse(0.0);
                boolean anomalous;
                if (score == 1.0) {
                    anomalous = false;
                } else if (score == -1.0) {
                    anomalous = true;
                } else {
                    anomalous = score < 0.0 || score >= 0.5;
                }
                return new AnomalyResult(Math.abs(score), anomalous, "ONNX");
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
