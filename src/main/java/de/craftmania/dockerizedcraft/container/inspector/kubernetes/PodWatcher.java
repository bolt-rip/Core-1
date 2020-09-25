package de.craftmania.dockerizedcraft.container.inspector.kubernetes;
import net.md_5.bungee.api.ProxyServer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watcher;
import de.craftmania.dockerizedcraft.container.inspector.events.ContainerEvent;
import io.fabric8.kubernetes.client.KubernetesClientException;
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
    PodWatcher(ProxyServer proxyServer, Logger logger, Configuration configuration) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.configuration = configuration;
        this.debug = configuration.getBoolean("debug");
    }

    @Override
    public void eventReceived(Action action, Pod resource) {
        try {
            if (this.debug) {
                logger.info("action: "+action);
                logger.info("phase: " +resource.getStatus().getPhase());
            }
            Map<String, String> labels = resource.getMetadata().getLabels();
            if (this.debug) {
                logger.info("labels: "+labels.toString());
            }
            if(!labels.containsKey("dockerizedcraft/enabled") || !labels.get("dockerizedcraft/enabled").equals("true")) return;

            String dockerAction = "nothing";
            if((action.toString().equals("ADDED") || action.toString().equals("MODIFIED")) && resource.getStatus().getPhase().equals("Running") ){
                if (resource.getStatus().getContainerStatuses().get(0).getReady())
                    dockerAction = "start";
            }
            else if(action.toString().equals("DELETED")){
                dockerAction = "stop";
            }

            ContainerEvent containerEvent = new ContainerEvent(resource.getMetadata().getName(), dockerAction);
            containerEvent.setName(resource.getMetadata().getName());
            if (this.debug) {
                logger.info("name: " +resource.getMetadata().getName());
            }
            Map<String ,String > environmentVariables = new HashMap<>();
            for (EnvVar i : resource.getSpec().getContainers().get(0).getEnv()) environmentVariables.put(i.getName(),i.getValue());
            containerEvent.setEnvironmentVariables(environmentVariables);
            if (this.debug) {
                logger.info("env:" + environmentVariables);
                logger.info("port: "+environmentVariables.get("SERVER_PORT"));
                logger.info("ip: "+resource.getStatus().getPodIP());
            }
            containerEvent.setPort(Integer.parseInt(environmentVariables.get("SERVER_PORT")));
            containerEvent.setIp(InetAddress.getByName(resource.getStatus().getPodIP()));
            if (!dockerAction.equals("nothing"))
                this.proxyServer.getPluginManager().callEvent(containerEvent);
        }catch(java.net.UnknownHostException ex){
            logger.severe(ex.getMessage());
        }

    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.warning("Watcher close due to " + cause);
    }
}
