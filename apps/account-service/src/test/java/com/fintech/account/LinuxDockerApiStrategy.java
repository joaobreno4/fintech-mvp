package com.fintech.account;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.InvalidConfigurationException;
import org.testcontainers.dockerclient.TransportConfig;

import java.io.File;
import java.net.URI;

/**
 * Custom Testcontainers Docker client strategy para Linux/WSL2 + Docker 27+.
 *
 * Root cause: docker-java 3.4.0 negocia a versão da API a partir do seu próprio
 * client-max (1.32). Docker 27+ (Engine 29.x) rejeita qualquer requisição com
 * versão de API abaixo de 1.44 (MinAPIVersion). Esta strategy contorna a
 * negociação quebrada fixando explicitamente a versão da API em 1.47 —
 * dentro do intervalo [1.44, 1.52] que o Docker 29 aceita.
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
