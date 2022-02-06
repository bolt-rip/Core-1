package de.craftmania.dockerizedcraft.container.inspector.kubernetes;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import de.craftmania.dockerizedcraft.container.inspector.events.ContainerEvent;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.config.Configuration;

public class PodWatcher implements Watcher<Pod> {

    private ProxyServer proxyServer;
    private Configuration configuration;
    private Logger logger;
    private Boolean debug;
    private KubernetesClient client;
    private String namespace;

    PodWatcher(ProxyServer proxyServer, Logger logger, Configuration configuration, KubernetesClient client,
            String namespace) {
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
            Map<String, String> annotations = resource.getMetadata().getAnnotations();
            if (labels != null) {
                if (this.debug) {
                    logger.info("[Kubernetes Container Inspector] labels: " + labels.toString());
                }
                if (!labels.containsKey("dockerizedcraft/enabled")
                        || !labels.get("dockerizedcraft/enabled").equals("true"))
                    return;
            } else {
                return;
            }
            String dockerAction = "nothing";
            if ((action.toString().equals("ADDED") || action.toString().equals("MODIFIED"))
                    && resource.getStatus().getPhase().equals("Running")) {
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
            if (annotations.toString().contains("dynamic-hostports.k8s")) {
                environmentVariables.remove("SERVER_PORT");
                String nodeName = resource.getSpec().getNodeName();
                if (nodeName == null)
                    return;
                List<NodeAddress> nodeAddresses = client.nodes().withName(nodeName).get().getStatus().getAddresses();
                List<NodeAddress> nodeExternalAddresses = nodeAddresses.stream()
                        .filter(address -> address.getType().equals("ExternalIP")).collect(Collectors.toList());
                List<NodeAddress> nodeInternalAddresses = nodeAddresses.stream()
                        .filter(address -> address.getType().equals("InternalIP")).collect(Collectors.toList());
                String ip = "0.0.0.0";
                if (nodeExternalAddresses.size() > 0)
                    ip = nodeExternalAddresses.get(0).getAddress();
                else
                    ip = nodeInternalAddresses.get(0).getAddress();
                String port = annotations.get("dynamic-hostports.k8s/25565");
                if (this.debug) {
                    logger.info("[Kubernetes Container Inspector] env:" + environmentVariables);
                    logger.info("[Kubernetes Container Inspector] port: " + port);
                    logger.info("[Kubernetes Container Inspector] ip: " + ip);
                }
                try { 
                    containerEvent.setPort(Integer.parseInt(port));
                    containerEvent.setIp(InetAddress.getByName(ip));
                }
                catch(Exception ex) {
                    if (this.debug) {
                        logger.info("Error while parsing port or IP.");
                    }
                    return;
                }
            } else {
                if (this.debug) {
                    logger.info("[Kubernetes Container Inspector] env:" + environmentVariables);
                    logger.info("[Kubernetes Container Inspector] port: " + environmentVariables.get("SERVER_PORT"));
                    logger.info("[Kubernetes Container Inspector] ip: " + resource.getStatus().getPodIP());
                }
                containerEvent.setPort(Integer.parseInt(environmentVariables.get("SERVER_PORT")));
                containerEvent.setIp(InetAddress.getByName(resource.getStatus().getPodIP()));
            }
            containerEvent.setEnvironmentVariables(environmentVariables);
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
        client.pods().inNamespace(namespace)
                .watch(new PodWatcher(proxyServer, logger, configuration, client, namespace));
    }
}
