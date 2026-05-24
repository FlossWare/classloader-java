package org.flossware.jclassloader.container;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.util.Config;
import org.flossware.jclassloader.ClassSource;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * ClassSource implementation for loading classes from Kubernetes ConfigMaps.
 * Class bytecode is stored as Base64-encoded data in ConfigMap fields.
 * Requires the Kubernetes Java client dependency.
 */
public class KubernetesConfigMapClassSource implements ClassSource {
    private final CoreV1Api api;
    private final String namespace;
    private final String configMapName;

    private KubernetesConfigMapClassSource(CoreV1Api api, String namespace, String configMapName) {
        this.api = Objects.requireNonNull(api, "api cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.configMapName = Objects.requireNonNull(configMapName, "configMapName cannot be null");
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        try {
            V1ConfigMap configMap = api.readNamespacedConfigMap(configMapName, namespace).execute();

            if (configMap.getData() == null) {
                throw new IOException("ConfigMap has no data: " + configMapName);
            }

            String base64Data = configMap.getData().get(className);
            if (base64Data == null) {
                throw new IOException("Class not found in ConfigMap: " + className);
            }

            return Base64.getDecoder().decode(base64Data);

        } catch (ApiException e) {
            throw new IOException("Failed to load class from Kubernetes ConfigMap: " + className, e);
        }
    }

    @Override
    public boolean canLoad(String className) {
        try {
            V1ConfigMap configMap = api.readNamespacedConfigMap(configMapName, namespace).execute();
            return configMap.getData() != null && configMap.getData().containsKey(className);
        } catch (ApiException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "KubernetesConfigMapClassSource[namespace=" + namespace +
               ", configMap=" + configMapName + "]";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ApiClient apiClient;
        private String namespace = "default";
        private String configMapName;

        public Builder apiClient(ApiClient apiClient) {
            this.apiClient = apiClient;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder configMapName(String configMapName) {
            this.configMapName = configMapName;
            return this;
        }

        public KubernetesConfigMapClassSource build() throws IOException {
            if (apiClient == null) {
                try {
                    apiClient = Config.defaultClient();
                } catch (Exception e) {
                    throw new IOException("Failed to create Kubernetes API client", e);
                }
            }

            Objects.requireNonNull(configMapName, "configMapName must be set");

            CoreV1Api api = new CoreV1Api(apiClient);
            return new KubernetesConfigMapClassSource(api, namespace, configMapName);
        }
    }
}
