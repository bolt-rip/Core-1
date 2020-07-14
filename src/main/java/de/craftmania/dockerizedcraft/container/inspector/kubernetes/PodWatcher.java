package de.craftmania.dockerizedcraft.container.inspector.kubernetes;
import net.md_5.bungee.api.ProxyServer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watcher;
import de.craftmania.dockerizedcraft.container.inspector.events.ContainerEvent;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.logging.Logger;

import com.sun.corba.se.spi.orbutil.fsm.Action;

import java.net.InetAddress;
import java.util.*;

public class PodWatcher implements Watcher<Pod> {

    private ProxyServer proxyServer;
    private Logger logger;
    PodWatcher(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Override
    public void eventReceived(Action action, Pod resource) {
        try {
            logger.info("action: "+action);
            logger.info("phase: " +resource.getStatus().getPhase());
            Map<String, String> labels = resource.getMetadata().getLabels();
            logger.info("labels: "+labels.toString());
            logger.info("ready: " + resource.getStatus().getContainerStatuses().get(0).getReady());
            if(!labels.containsKey("dockerizedcraft/enabled") || !labels.get("dockerizedcraft/enabled").equals("true")) return;

            String dockerAction = "nothing";
            if((action.toString().equals("ADDED") || action.toString().equals("MODIFIED")) && resource.getStatus().getPhase().equals("Running") 
                && resource.getStatus().getContainerStatuses().get(0).getReady()){
                dockerAction = "start";
            }
            else if(action.toString().equals("DELETED")){
                dockerAction = "stop";
            }

            ContainerEvent containerEvent = new ContainerEvent(resource.getMetadata().getName(), dockerAction);
            containerEvent.setName(resource.getMetadata().getName());
            logger.info("name: " +resource.getMetadata().getName());
            Map<String ,String > environmentVariables = new HashMap<>();
            for (EnvVar i : resource.getSpec().getContainers().get(0).getEnv()) environmentVariables.put(i.getName(),i.getValue());
            containerEvent.setEnvironmentVariables(environmentVariables);
            logger.info("env:" + environmentVariables);
            containerEvent.setPort(Integer.parseInt(environmentVariables.get("SERVER_PORT")));
            logger.info("port: "+environmentVariables.get("SERVER_PORT"));
            containerEvent.setIp(InetAddress.getByName(resource.getStatus().getPodIP()));
            logger.info("ip: "+resource.getStatus().getPodIP());
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
