package de.craftmania.dockerizedcraft.container.inspector.kubernetes;

import io.fabric8.kubernetes.client.*;
import net.md_5.bungee.api.ProxyServer;
import io.fabric8.kubernetes.api.model.Pod;
import de.craftmania.dockerizedcraft.container.inspector.events.ContainerEvent;
import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.logging.Logger;

import com.sun.corba.se.spi.orbutil.fsm.Action;

import net.md_5.bungee.config.Configuration;

import java.net.InetAddress;
import java.util.*;

public class PodWatcher implements Watcher<Pod> {

    private ProxyServer proxyServer;
    private Configuration configuration;
    private Logger logger;
    private Boolean debug;
    private KubernetesClient client;
    private String namespace;

    PodWatcher(ProxyServer proxyServer, Logger logger, Configuration configuration, KubernetesClient client, String namespace) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.configuration = configuration;
        this.debug = configuration.getBoolean("debug");
        this.client = client;
        this.namespace = namespace;
    }

    @Override
    public void eventReceived(Action action, Pod resource) {
        try {
            if (this.debug) {
                logger.info("[Kubernetes Container Inspector] action: " + action);
                logger.info("[Kubernetes Container Inspector] phase: " + resource.getStatus().getPhase());
            }

            if (this.debug) {
                logger.info("[Kubernetes Container Inspector] name: " + resource.getMetadata().getName());
            }

            Map<String, String> labels = resource.getMetadata().getLabels();
            if (labels != null) {
                if (this.debug) {
                    logger.info("[Kubernetes Container Inspector] labels: " + labels.toString());
                }
                if (!labels.containsKey("dockerizedcraft/enabled") || !labels.get("dockerizedcraft/enabled").equals("true"))
                    return;
            } else {
                return;
            }
            String dockerAction = "nothing";
            if ((action.toString().equals("ADDED") || action.toString().equals("MODIFIED")) && resource.getStatus().getPhase().equals("Running")) {
                if (resource.getStatus().getContainerStatuses().get(0).getReady())
                    dockerAction = "start";
            } else if (action.toString().equals("DELETED")) {
                dockerAction = "stop";
            }

            ContainerEvent containerEvent = new ContainerEvent(resource.getMetadata().getName(), dockerAction);
            containerEvent.setName(resource.getMetadata().getName());
            Map<String, String> environmentVariables = new HashMap<>();
            for (EnvVar i : resource.getSpec().getContainers().get(0).getEnv())
                environmentVariables.put(i.getName(), i.getValue());
            containerEvent.setEnvironmentVariables(environmentVariables);
            if (this.debug) {
                logger.info("[Kubernetes Container Inspector] env:" + environmentVariables);
                logger.info("[Kubernetes Container Inspector] port: " + environmentVariables.get("SERVER_PORT"));
                logger.info("[Kubernetes Container Inspector] ip: " + resource.getStatus().getPodIP());
            }
            containerEvent.setPort(Integer.parseInt(environmentVariables.get("SERVER_PORT")));
            containerEvent.setIp(InetAddress.getByName(resource.getStatus().getPodIP()));
            if (!dockerAction.equals("nothing"))
                this.proxyServer.getPluginManager().callEvent(containerEvent);
        } catch (java.net.UnknownHostException ex) {
            logger.severe(ex.getMessage());
        }

    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.warning("[Kubernetes Container Inspector] Watcher close due to " + cause);
        logger.warning("[Kubernetes Container Inspector] Rewatching resources...");
        client.pods().inNamespace(namespace).watch(new PodWatcher(proxyServer, logger, configuration, client, namespace));
    }
}
