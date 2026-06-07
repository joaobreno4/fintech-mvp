package com.fintech.transaction;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.InvalidConfigurationException;
import org.testcontainers.dockerclient.TransportConfig;

import java.io.File;
import java.net.URI;

/**
 * Custom Testcontainers Docker client strategy for Linux/WSL2 + Docker 27+.
 *
 * Root cause: docker-java 3.4.0 negotiates API version starting from its own
 * hardcoded client-max (1.32). Docker 27+ (Engine 29.x) rejects any request
 * with API version below 1.44 (MinAPIVersion). This strategy bypasses the
 * broken negotiation by explicitly pinning the API version to 1.47 —
 * within the [1.44, 1.52] range that Docker 29 accepts.
 *
 * Implementation notes:
 * - Uses Testcontainers' shaded DefaultDockerClientConfig (same class the
 *   built-in strategies use internally) with withApiVersion("1.47") added.
 * - ZerodepDockerHttpClient (zero-dep transport) is the transport already
 *   used by Testcontainers 1.20.x, so no extra dependency is needed.
 * - The shaded DockerClientImpl.getInstance(config, httpClient) overload
 *   returns com.github.dockerjava.api.DockerClient (the non-shaded public
 *   interface), so there are no type-compatibility issues.
 */
public class LinuxDockerApiStrategy extends DockerClientProviderStrategy {

    private static final URI SOCKET_URI = URI.create("unix:///var/run/docker.sock");

    @Override
    public String getDescription() {
        return "Unix socket with API v1.47 (Docker 27+/WSL2 fix for docker-java 3.4.x)";
    }

    @Override
    protected boolean isApplicable() {
        return new File("/var/run/docker.sock").exists();
    }

    @Override
    protected int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public TransportConfig getTransportConfig() throws InvalidConfigurationException {
        return TransportConfig.builder()
                .dockerHost(SOCKET_URI)
                .build();
    }

    @Override
    public DockerClient getDockerClient() {
        org.testcontainers.shaded.com.github.dockerjava.core.DefaultDockerClientConfig config =
                org.testcontainers.shaded.com.github.dockerjava.core.DefaultDockerClientConfig
                        .createDefaultConfigBuilder()
                        .withDockerHost(SOCKET_URI.toString())
                        .withApiVersion("1.47")
                        .build();

        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(SOCKET_URI)
                .build();

        return org.testcontainers.shaded.com.github.dockerjava.core.DockerClientImpl
                .getInstance(config, httpClient);
    }
}
