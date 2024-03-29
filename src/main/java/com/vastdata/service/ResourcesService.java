package com.vastdata.service;

import com.vastdata.cluster.ResourcesBuild;
import com.vastdata.cluster.VastbaseCluster;
import com.vastdata.constants.VbConst;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.fabric8.kubernetes.client.internal.readiness.Readiness.isPodReady;

@Slf4j
@ApplicationScoped
public class ResourcesService {

    @Inject
    private ResourcesBuild resourcesBuild;

    public void addRoleLabelToPod(KubernetesClient client, Pod pod, boolean b) {
        if (b) {
            client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).edit(p -> new PodBuilder(pod).editMetadata().addToLabels("role", VbConst.PG_DB_ROLE_PRIMARY).endMetadata().build());
        } else {
            client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).edit(p -> new PodBuilder(pod).editMetadata().addToLabels("role", VbConst.PG_DB_ROLE_STANDBY).endMetadata().build());
        }
    }

    public void delRoleLabelToPod(KubernetesClient client, Pod pod) {
        client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).edit(p -> new PodBuilder(pod).editMetadata().removeFromLabels("role").endMetadata().build());
    }
    
    public List<Pod> waitPodsRunning(KubernetesClient client, VastbaseCluster resource) {
        var spec = resource.getSpec();
        int retryCount = 0;
        while (true) {
            List<Pod> readyPodList = new ArrayList<>();
            List<Pod> podList = client.pods().inNamespace(spec.getNamespace()).withLabel("origin", "vastbase").list().getItems();
            if (podList != null && podList.size() > 0) {
                for (Pod pod : podList) {
                    String podName = pod.getMetadata().getName();
                    log.info("[{}:{}]Pod {}处于{}阶段", spec.getNamespace(), spec.getContainerName(), podName, pod.getStatus().getPhase());
                    if (!isPodReady(pod)) {
                        break;
                    }
                    readyPodList.add(pod);
                    if (podList.size() == readyPodList.size()) {
                        return readyPodList;
                    }
                }
            }
            if (retryCount > 5) {
                log.info("[{}:{}]Pod持续{}秒，超时", spec.getNamespace(), spec.getContainerName(), 60);
                return readyPodList;
            } else {
                retryCount++;
                log.info("[{}:{}]Pod 启动未完成，将于{}秒后进行第{}次重试", spec.getNamespace(), spec.getContainerName(), 10, retryCount);
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public boolean deletePod(KubernetesClient client, String nameSpace, String podName) {
        return client.pods().inNamespace(nameSpace).withName(podName).delete();
    }

    public void ensurePodResource(KubernetesClient client, VastbaseCluster resource, String podName) {
        ensurePod(client, resource, podName);
    }

    private void ensurePod(KubernetesClient client, VastbaseCluster resource, String podName) {
        // pod创建            
        Pod pod = resourcesBuild.buildPod(resource, podName);
        client.pods().create(pod);
    }

    public boolean waitPodRunning(KubernetesClient client, VastbaseCluster resource, String podName) {
        var spec = resource.getSpec();
        int retryCount = 0;
        while (true) {
            Pod pod = client.pods().inNamespace(spec.getNamespace()).withName(podName).get();
            log.info("[{}:{}]Pod {}处于{}阶段", spec.getNamespace(), spec.getContainerName(), podName, pod.getStatus().getPhase());
            if (isPodReady(pod)) {
                return true;
            }
            if (retryCount > 5) {
                log.info("[{}:{}]Pod持续{}秒，超时", spec.getNamespace(), spec.getContainerName(), 60);
                return false;
            } else {
                retryCount++;
                log.info("[{}:{}]Pod 启动未完成，将于{}秒后进行第{}次重试", spec.getNamespace(), spec.getContainerName(), 10, retryCount);
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
