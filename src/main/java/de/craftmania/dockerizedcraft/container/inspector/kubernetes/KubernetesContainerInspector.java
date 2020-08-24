package de.craftmania.dockerizedcraft.container.inspector.kubernetes;

import de.craftmania.dockerizedcraft.container.inspector.IContainerInspector;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.config.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

public class KubernetesContainerInspector implements IContainerInspector {
    private ProxyServer proxyServer;
    private Logger logger;
    private Configuration configuration;
    private KubernetesClient client;

    public KubernetesContainerInspector(Configuration configuration, ProxyServer proxyServer, Logger logger,
            File dataFolder) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.configuration = configuration;

        Config config = new ConfigBuilder().build();

        if (!configuration.getBoolean("kubernetes.load-from-cluster")) {
            try {
                File file = new File(dataFolder, "kubeconfig.yml");
                if (!file.exists())
                    throw new IOException("Couldn't read file kubeconfig.yml");

                config = Config.fromKubeconfig(new String(Files.readAllBytes(file.toPath())));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.client = new DefaultKubernetesClient(config);
    }

    public void runContainerInspection() {
        this.logger.info("[Kubernetes Container Inspector] Connecting to kubernetes.");
    }

    public void runContainerListener() {
        this.logger.info("[Kubernetes Container Inspector] Running listener.");
        String namespace = configuration.getString("kubernetes.namespace");
        if (namespace == null || namespace.isEmpty())
            this.logger.severe("kubernetes.namespace not set.");
        this.client.pods().inNamespace(namespace).watch(new PodWatcher(proxyServer, logger));
    }
}
