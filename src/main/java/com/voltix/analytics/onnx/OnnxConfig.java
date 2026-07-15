package com.voltix.analytics.onnx;

import ai.onnxruntime.OrtEnvironment;
import com.voltix.platform.config.VoltixProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class OnnxConfig {

    @Bean(destroyMethod = "close")
    public OrtEnvironment ortEnvironment() {
        return OrtEnvironment.getEnvironment();
    }

    @Bean
    public OnnxSessionPool anomalySessionPool(OrtEnvironment environment,
                                              ResourceLoader resourceLoader,
                                              VoltixProperties properties) {
        return new OnnxSessionPool(
                environment,
                resourceLoader.getResource(properties.getAnalytics().getAnomalyModel()),
                properties.getAnalytics().getOnnxSessionPoolSize());
    }

    @Bean
    public OnnxSessionPool loadForecasterSessionPool(OrtEnvironment environment,
                                                     ResourceLoader resourceLoader,
                                                     VoltixProperties properties) {
        return new OnnxSessionPool(
                environment,
                resourceLoader.getResource(properties.getAnalytics().getLoadForecasterModel()),
                properties.getAnalytics().getOnnxSessionPoolSize());
    }
}
