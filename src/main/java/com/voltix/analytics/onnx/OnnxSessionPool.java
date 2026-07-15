package com.voltix.analytics.onnx;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

public class OnnxSessionPool {
    private static final Logger log = LoggerFactory.getLogger(OnnxSessionPool.class);

    private final GenericObjectPool<OrtSession> pool;

    public OnnxSessionPool(OrtEnvironment environment, Resource modelResource, int poolSize) {
        if (!modelResource.exists()) {
            log.warn("ONNX model {} was not found. Analytics will use rules fallback.", modelResource);
            this.pool = null;
            return;
        }

        GenericObjectPoolConfig<OrtSession> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(poolSize);
        config.setMaxIdle(poolSize);
        config.setMinIdle(0);
        config.setBlockWhenExhausted(true);
        config.setMaxWait(Duration.ofMillis(20));
        this.pool = new GenericObjectPool<>(new SessionFactory(environment, modelResource), config);
    }

    public Optional<OrtSession> borrowSession() throws Exception {
        if (pool == null) {
            return Optional.empty();
        }
        return Optional.of(pool.borrowObject());
    }

    public void returnSession(OrtSession session) {
        if (pool != null && session != null) {
            pool.returnObject(session);
        }
    }

    public boolean available() {
        return pool != null;
    }

    @PreDestroy
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }

    private static class SessionFactory extends BasePooledObjectFactory<OrtSession> {
        private final OrtEnvironment environment;
        private final Resource modelResource;

        private SessionFactory(OrtEnvironment environment, Resource modelResource) {
            this.environment = environment;
            this.modelResource = modelResource;
        }

        @Override
        public OrtSession create() throws OrtException, IOException {
            return environment.createSession(modelResource.getContentAsByteArray(), new OrtSession.SessionOptions());
        }

        @Override
        public PooledObject<OrtSession> wrap(OrtSession session) {
            return new DefaultPooledObject<>(session);
        }

        @Override
        public void destroyObject(PooledObject<OrtSession> pooledObject) throws Exception {
            pooledObject.getObject().close();
        }
    }
}
