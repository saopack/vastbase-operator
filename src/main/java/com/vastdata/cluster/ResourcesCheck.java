package com.vastdata.cluster;

import com.vastdata.constants.VbConst;
import com.vastdata.service.DbService;
import com.vastdata.service.EventService;
import com.vastdata.service.ResourcesService;
import com.vastdata.vo.CheckResult;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
public class ResourcesCheck {
    @Inject
    private DbService dbService;
    @Inject
    private EventService eventService;
    @Inject
    private ResourcesService resourcesService;
    @Inject
    private ResourcesBuild resourcesBuild;

    public CheckResult checkPV(PersistentVolume pvExisting, PersistentVolume persistentVolume) {
        CheckResult result = new CheckResult();
        boolean match = true;
        StringBuffer reasons = new StringBuffer();
        if (pvExisting == null) {
            match = false;
            reasons.append("pv:{" + persistentVolume.getMetadata().getName() + "}不存在!");
            result.setMatch(match);
            result.setReasons(reasons.toString());
            return result;
        }
        result.setMatch(match);
        result.setReasons(reasons.toString());
        return result;
    }

    public CheckResult checkPVC(PersistentVolumeClaim pvcExisting, PersistentVolumeClaim persistentVolumeClaim) {
        CheckResult result = new CheckResult();
        boolean match = true;
        StringBuffer reasons = new StringBuffer();
        if (pvcExisting == null) {
            match = false;
            reasons.append("pvc:{" + persistentVolumeClaim.getMetadata().getName() + "}不存在!");
            result.setMatch(match);
            result.setReasons(reasons.toString());
            return result;
        }
        result.setMatch(match);
        result.setReasons(reasons.toString());
        return result;
    }

    public CheckResult checkSecret(Secret secretExisting, PersistentVolumeClaim persistentVolumeClaim) {
        CheckResult result = new CheckResult();
        boolean match = true;
        StringBuffer reasons = new StringBuffer();
        if (secretExisting == null) {
            match = false;
            reasons.append("Secret:{" + persistentVolumeClaim.getMetadata().getName() + "}不存在!");
            result.setMatch(match);
            result.setReasons(reasons.toString());
            return result;
        }
        result.setMatch(match);
        result.setReasons(reasons.toString());
        return result;
    }

    public CheckResult checkHeadlessService(Service headlessServiceExisting, Service headlessService) {
        int dbPort = headlessService.getSpec().getPorts().get(0).getPort();
        CheckResult result = new CheckResult();
        boolean match = true;
        StringBuffer reasons = new StringBuffer();
        if (headlessServiceExisting == null) {
            match = false;
            reasons.append("headlessService:{" + headlessService.getMetadata().getName() + "}不存在!");
            result.setMatch(match);
            result.setReasons(reasons.toString());
            return result;
        }
        int dbPortEx = headlessServiceExisting.getSpec().getPorts().get(0).getPort();
        if (dbPort != dbPortEx) {
            match = false;
            reasons.append("headlessService:{" + headlessService.getMetadata().getName() + "}需要更新!");
        }
        result.setMatch(match);
        result.setReasons(reasons.toString());
        return result;
    }

    public CheckResult checkReadWriteService(Service readWriteServiceExisting, Service readWriteService) {
        CheckResult result = new CheckResult();
        int dbPort = readWriteService.getSpec().getPorts().get(0).getPort();
        int nodePort = readWriteService.getSpec().getPorts().get(0).getNodePort();
        boolean match = true;
        StringBuffer reasons = new StringBuffer();
        if (readWriteServiceExisting == null) {
            match = false;
            reasons.append("readwriteService:{" + readWriteService.getMetadata().getName() + "}不存在!");
            result.setMatch(match);
            result.setReasons(reasons.toString());
            return result;
        }
        int dbPortEx = readWriteServiceExisting.getSpec().getPorts().get(0).getPort();
        int nodePortEx = readWriteServiceExisting.getSpec().getPorts().get(0).getNodePort();
        if (dbPort != dbPortEx || nodePort != nodePortEx) {
            match = false;
            reasons.append("readwriteService:{" + readWriteService.getMetadata().getName() + "}需要更新!");
        }
        result.setMatch(match);
        result.setReasons(reasons.toString());
        return result;
    }

    public CheckResult checkStatefulSet(StatefulSet statefulSetExisting, StatefulSet statefulSet, KubernetesClient client, VastbaseCluster resource) {
        CheckResult result = new CheckResult();
        String namespace = resource.getSpec().getNamespace();
        int port = resource.getSpec().getContainerPort();
        boolean match = true;
        StringBuffer reasons = new StringBuffer();
        if (statefulSetExisting == null) {
            match = false;
            reasons.append("statefulSet:{" + statefulSet.getMetadata().getName() + "}不存在!");
            result.setMatch(match);
            result.setReasons(reasons.toString());
            return result;
        }
        if (!statefulSetExisting.getSpec().getReplicas().equals(statefulSet.getSpec().getReplicas())) {
            int replicas = statefulSet.getSpec().getReplicas();
            match = false;
            reasons.append("statefulSet:{" + statefulSet.getMetadata().getName() + "}:replicas不匹配!");
            //修改数据库配置
            List<Pod> podsList = client.pods().inNamespace(namespace).list().getItems();
            for (Pod pod : podsList) {
                String podName = pod.getMetadata().getName();
                if (!podName.startsWith(resource.getSpec().getContainerName())) {
                    continue;
                }
                int podOrder = Integer.valueOf(podName.substring(podName.length() - 1));
                int order = 0;
                String ip = pod.getStatus().getPodIP();
                for (int i = 0; i < replicas; i++) {
                    if (podOrder != i) {
                        order++;
                        String configName = String.format(VbConst.REPL_CONN_INFO_NAME, order);
                        String configValue = String.format(VbConst.REPL_CONN_INFO_VALUE, ip, port + 2, port + 3, podName.replace(String.valueOf(podOrder), String.valueOf(i)) + "." + resource.getSpec().getHeadlessServiceName(), port + 2, port + 3);
                        String execDbCmd = dbService.generateDBConfigPropParam(configName, configValue, true);
                        dbService.reload(client, namespace, pod.getMetadata().getName(), execDbCmd);
                    }
                }
            }
        }
        result.setMatch(match);
        result.setReasons(reasons.toString());
        return result;
    }

    private CheckResult checkPod(Pod podExisting, Pod pod) {
        CheckResult result = new CheckResult();
        boolean match = true;
        StringBuffer reasons = new StringBuffer();
        if (podExisting == null) {
            match = false;
            reasons.append("pod:{" + pod.getMetadata().getName() + "}不存在!");
            result.setMatch(match);
            result.setReasons(reasons.toString());
            return result;
        }
        result.setMatch(match);
        result.setReasons(reasons.toString());
        return result;
    }

    private String maintainStart(VastbaseCluster resource) {
        return resource.getSpec().getMaintain();
    }

    /*
根据vb spec维护vb数据库集群
方法参数：
	resource：当前CR
方法逻辑：
	1 如果集群标记为维护状态，不做操作
	2 根据cr 副本数量，确保pod与pv pvc service数量
	3 如果当前Spec相对于前一个版本有资源升级，则对Pod进行滚动升级
	4 如果当前Spec相对于前一个版本有数据库配置变更，则修改所有实例的配置参数
完成后，标记CR状态为ready
*/
    public List<Pod> ensureDBCluster(KubernetesClient client, VastbaseCluster resource) {
        String clusterName = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        if ("T".equals(maintainStart(resource))) {
            log.info("[{}:{}]集群维护模式开启", namespace, clusterName);
            eventService.clusterMaintainStart(resource);
            for (Pod pod : client.pods().inNamespace(resource.getSpec().getNamespace()).list().getItems()) {
                dbService.addMaintainFlag(client, pod.getMetadata().getNamespace(), pod.getMetadata().getName(), null);
            }
            return new ArrayList<>();
        } else if ("F".equals(maintainStart(resource))) {
            log.info("[{}:{}]集群维护模式结束", namespace, clusterName);
            eventService.clusterMaintainEnd(resource);
            for (Pod pod : client.pods().inNamespace(resource.getSpec().getNamespace()).list().getItems()) {
                dbService.delMaintainFlag(client, pod.getMetadata().getNamespace(), pod.getMetadata().getName(), null);
            }
        }
        log.info("[{}:{}]开始维护数据库集群", namespace, clusterName);
        if (!ensureSpecCluster(client, resource)) {

        }
        //如果等待所有Pod状态变为running超时
        //	集群pod存在不可用，则抛出错误
        List<Pod> readyPodList = resourcesService.waitPodsRunning(client, resource);
        if (readyPodList.size() < 1) {
            log.info("没有可用的Pod，进程终止");
            return readyPodList;
        }

        //不固定ip流复制连接维护
        ensureReplication(client, resource);
        
        if (cleanupCluster(client, resource)) {

        }
        if (upgradeRequired(client, resource)) {
            upgradeCluster(client, resource, readyPodList);
        }
        if (IsDBConfigChange(client, resource)) {
            updateDBConfig(client, resource);
        }
        return readyPodList;
    }

    /*
集群升级
方法参数：
	cluster：当前CR
方法逻辑：
	遍历集群Pod
		如果当前Pod的资源（镜像、CPU、内存、带宽）与CR.Spec不一致
			如果是Standby，则升级该实例Pod
			如果是Primary，则记录IP
	升级Primary
		重新查询集群Pod数组
		如果有Standby实例，则通过比较LSN选择新的Primary，做主从切换，然后升级原主Pod并启动为Standby
		如果没有，则直接升级Primary所在Pod并启动为Primary
*/
    private void upgradeCluster(KubernetesClient client, VastbaseCluster resource, List<Pod> readyPodList) {
        String nameSpace = resource.getSpec().getNamespace();
        String crName = resource.getMetadata().getName();
        Pod primaryPod = new Pod();
        log.info("[{}:{}]开始升级", nameSpace, resource.getSpec().getContainerName());
        for (Pod pod : readyPodList) {
            String podName = pod.getMetadata().getName();
            //升级前删除lable
            resourcesService.delRoleLabelToPod(client,pod);
            boolean run = dbService.checkDBState(client, resource, pod);
            if (!run) {
                continue;
            } else {
                //分主备
                if (!dbService.checkDBRepl(client, nameSpace, podName)) {
                    if (upgradePod(client, resource, pod)) {
                        log.info("[{}:{}]Pod {}升级失败", nameSpace, crName, podName);
                    }
                } else {
                    primaryPod = pod;
                }
            }
        }
        //TODO 主的需要在备机中查询lsn最大的一个做主从切换 实现滚动升级
        List<Pod> podRebuildList = client.pods().inNamespace(nameSpace).list().getItems();
        podRebuildList.remove(primaryPod);
        Pod selectedPod = dbService.findPodWithLargestLSN(client, podRebuildList, resource);
        //主从切换
        dbService.switchPrimary(client,resource,selectedPod,primaryPod);
        if (upgradePod(client, resource, primaryPod)) {
            log.info("[{}:{}]Pod {}升级失败", nameSpace, crName, primaryPod.getMetadata().getName());
        }
    }
    
    private boolean upgradePod(KubernetesClient client, VastbaseCluster resource, Pod pod) {
        String podName = pod.getMetadata().getName();
        String nameSpace = resource.getSpec().getNamespace();
        boolean deleted = resourcesService.deletePod(client, nameSpace, podName);
        if (!deleted) {
            return false;
        }
        resourcesService.ensurePodResource(client, resource, podName);
        boolean running = resourcesService.waitPodRunning(client, resource, podName);
        if (!running) {
            return false;
        }
        //升级完成后添加lable,默认添加standby
        resourcesService.addRoleLabelToPod(client,pod,false);
        return true;
    }

    private boolean upgradeRequired(KubernetesClient client, VastbaseCluster resource) {
        VastbaseCluster resourceEx = client.resources(VastbaseCluster.class).inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).get();
        if (!resource.getSpec().getImage().equals(resourceEx.getSpec().getImage())) {
            return true;
        }
        if (!resource.getSpec().getResourceLimits().get("cpu").equals(resourceEx.getSpec().getResourceLimits().get("cpu"))) {
            return true;
        }
        if (!resource.getSpec().getResourceLimits().get("memory").equals(resourceEx.getSpec().getResourceLimits().get("memory"))) {
            return true;
        }
        return false;
    }

    private void updateDBConfig(KubernetesClient client, VastbaseCluster resource) {
        String clusterName = resource.getMetadata().getName();
        String namespace = resource.getSpec().getNamespace();
        log.info("[{}:{}]开始更新数据配置", namespace, clusterName);
    }

    private void ensureReplication(KubernetesClient client, VastbaseCluster resource) {
        String clusterName = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        var spec = resource.getSpec();
        int port = resource.getSpec().getContainerPort();
        List<Pod> podList = client.pods().inNamespace(spec.getNamespace()).withLabel("origin", "vastbase").list().getItems();
        for (Pod pod : podList) {
            String podName = pod.getMetadata().getName();
            if (!podName.startsWith(resource.getSpec().getContainerName())) {
                continue;
            }
            int podOrder = Integer.valueOf(podName.substring(podName.length() - 1));
            int order = 0;
            String ip = pod.getStatus().getPodIP();
            for (int i = 0; i < podList.size(); i++) {
                if (podOrder != i) {
                    String otherPodIp = podList.get(i).getStatus().getPodIP();
                    order++;
                    String configName = String.format(VbConst.REPL_CONN_INFO_NAME, order);
                    String configValue = String.format(VbConst.REPL_CONN_INFO_VALUE, ip, port + 2, port + 3, otherPodIp, port + 2, port + 3);
                    String execDbCmd = dbService.generateDBConfigPropParam(configName, configValue, true);
                    dbService.reload(client, namespace, pod.getMetadata().getName(), execDbCmd);
                }
            }
        }
        log.info("[{}:{}]更新数据库流复制信息!", namespace, clusterName);
    }
    
    private boolean IsDBConfigChange(KubernetesClient client, VastbaseCluster resource) {
        
        return false;
    }

    private boolean cleanupCluster(KubernetesClient client, VastbaseCluster resource) {
        String clusterName = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        log.info("[{}:{}]开始清理多余数据库实例", namespace, clusterName);
        List<Pod> podList = client.pods().inNamespace(namespace).withLabel("origin", "vastbase").list().getItems();
        int podNum = podList.size();
        while (podNum > resource.getSpec().getReplicas()) {
            client.pods().inNamespace(namespace).withName("vastbase-" + (podNum - 1)).delete();
            podNum--;
        }
        return true;
    }


    /*
根据CR.Spec配置集群
方法参数：
	cluster：当前CR
方法逻辑：
	确保CR.Spec.replicas对应pod的数量
	根据Pod的实际情况（是否新建，是否新建PVC）进行分组
	等待所有Pod达到running状态
	在running状态的Pod中选出一个实例Primary
	将其他实例配置为Standby
	更新所有Pod的Label
*/
    private boolean ensureSpecCluster(KubernetesClient client, VastbaseCluster resource) {
        int replicas = resource.getSpec().getReplicas();
        String containerName = resource.getSpec().getContainerName();
        //pv,pvc
        for (int i = 0; i < replicas; i++) {
            String podName = containerName + "-" + i;
            CheckResult checkResult = new CheckResult();
            // pv创建
            PersistentVolume persistentVolume = resourcesBuild.buildPersistentVolume(resource, containerName + "-" + i, "data");
            // pvc创建
            PersistentVolumeClaim persistentVolumeClaim = resourcesBuild.buildPersistentVolumeClaim(resource, containerName + "-" + i, "data");
            var pvResource = client.resources(PersistentVolume.class).withName(persistentVolume.getMetadata().getName());
            var pvcResource = client.resources(PersistentVolumeClaim.class).withName(persistentVolumeClaim.getMetadata().getName());
            var pvcExisting = pvcResource.get();
            var pvExisting = pvResource.get();
            checkResult = checkPV(pvExisting, persistentVolume);
            if (!checkResult.isMatch()) {
                log.info("persistentVolume name of {} was created!", persistentVolume.getMetadata().getName());
                pvResource.createOrReplace(persistentVolume);
            }
            checkResult = checkPVC(pvcExisting, persistentVolumeClaim);
            if (!checkResult.isMatch()) {
                log.info("persistentVolumeClaim name of {} was created!", persistentVolumeClaim.getMetadata().getName());
                pvcResource.createOrReplace(persistentVolumeClaim);
            }
            // pod 通信service创建
            Service service = resourcesBuild.buildHeadlessService(resource, podName);
            var serviceResource = client.resources(Service.class).withName(podName);
            var serviceExisting = serviceResource.get();
            checkResult = checkHeadlessService(serviceExisting, service);
            if (!checkResult.isMatch()) {
                log.info("service name of {} was created!", service.getMetadata().getName());
                serviceResource.createOrReplace(service);
            }
            // pod创建            
            Pod pod = resourcesBuild.buildPod(resource, podName);
            var podResource = client.resources(Pod.class).withName(podName);
            var podExisting = podResource.get();
            checkResult = checkPod(podExisting, pod);
            if (!checkResult.isMatch()) {
                log.info("pod name of {} was created!", pod.getMetadata().getName());
                podResource.createOrReplace(pod);
            }
        }
        return true;
    }

    /*
    确保有唯一的Primary实例
    */
    public CheckResult ensurePrimary(KubernetesClient client, VastbaseCluster resource, List<Pod> podList) {
        VastbaseClusterSpec vbspec = resource.getSpec();
        CheckResult result = new CheckResult();
        boolean match = true;
        log.info("[{}:{}]开始维护数据库主节点", vbspec.getNamespace(), vbspec.getContainerName());
        Pod podPrimary = new Pod();
        List<Pod> primaryPodList = new ArrayList<>();
        for (Pod pod : podList) {
            if (dbService.checkDBRepl(client, vbspec.getNamespace(), pod.getMetadata().getName())) {
                primaryPodList.add(pod);
            }
        }
        //根据Primary的数目进行处理
        //多主：主集群有超过一个Primary
        //一主：主集群有一个Primary
        //无主：主集群没有Primary

        if (primaryPodList.size() > 1) {
            processMultiplePrimary(client, primaryPodList, resource);
        } else if (primaryPodList.size() == 1) {

        } else if (primaryPodList.size() < 1) {
            podPrimary = processNoPrimary(client, podList, resource);
            primaryPodList.add(podPrimary);
            configDBInstance(client, resource, podList, podPrimary);
        }
        //更新PodLabel
        if (updatePodLabels(client, resource, podList)) {
            log.info("[{}:{}]更新集群pod-lable标注主从完成", vbspec.getNamespace(), vbspec.getContainerName());
        }
        result.setMatch(match);
        return result;
    }

    private void configDBInstance(KubernetesClient client, VastbaseCluster resource, List<Pod> podList, Pod podPrimary) {
        boolean flag = dbService.ConfigDB(client, resource, podList, podPrimary);
        if (!flag) {
            eventService.InstanceConfigFail(resource, podPrimary.getMetadata().getName(), "");
        } else {
            eventService.InstanceSetPrimary(resource, podPrimary.getMetadata().getName(), "");
        }
    }

    /*
    处理无主问题
    方法参数：
        resource：当前CR
        podList：当前集群所有Pod的数组
    方法逻辑：
        将Pod数组根据DB状态分组，Standby实例一组，其他一组
        将其他组所有不在newPVCPods中的实例启动为Standby，并加入Standby组
        如果Standby组不为空则选出LSN最大者，否则指定为pods的第一个元素
        将选定的实例配置为Primary
    */
    private Pod processNoPrimary(KubernetesClient client, List<Pod> podList, VastbaseCluster resource) {
        VastbaseClusterSpec vbspec = resource.getSpec();
        log.info("[{}:{}]当前数据库实例中没有主节点", vbspec.getNamespace(), resource.getMetadata().getName());
        Pod selectedPod = new Pod();
        if (podList.size() > 0) {
            selectedPod = dbService.findPodWithLargestLSN(client, podList, resource);
            if (selectedPod == null) {
                selectedPod = podList.get(0);
            }
        }
        return selectedPod;
    }

    /*
    处理多主问题
    方法参数：
        cluster：当前CR
        primaryPods：实例角色为Primary的Pod数组
        ipArray：ip数组，包括CR.Spec.IpList的所有IP和已存在的Pod的IP
    对于主集群，确保只有一个Primary
    方法逻辑：
        如果存在与CR.Status.Primary匹配的Pod，则以此为Primary，将其他主实例重启为Pending，由后续逻辑处理
        如果不存在与CR.Status.Primary匹配的Pod，查询所有Primary的LSN,选择最大的一个作为Primary，其余重启为Pending
        如果CR有IpList或RemoteIpList的改变，唯一的Primary需要重新配置
    对于同城集群，确保没有Primary
    方法逻辑：
        将所有Primary重启为Standby
    */
    private Pod processMultiplePrimary(KubernetesClient client, List<Pod> podList, VastbaseCluster resource) {
        VastbaseClusterSpec vbspec = resource.getSpec();
        log.info("[{}:{}]当前数据库实例中有多个主节点", vbspec.getNamespace(), resource.getMetadata().getName());
        Pod selectedPod = new Pod();
        if (podList.size() > 0) {
            selectedPod = dbService.findPodWithLargestLSN(client, podList, resource);
            if (selectedPod == null) {
                selectedPod = podList.get(0);
            }
        }
        return selectedPod;
    }

    private boolean updatePodLabels(KubernetesClient client, VastbaseCluster resource, List<Pod> podList) {
        VastbaseClusterSpec vbspec = resource.getSpec();
        log.info("[{}:{}]开始维护pod lable", vbspec.getNamespace(), resource.getMetadata().getName());
        for (Pod pod : podList) {
            if (dbService.checkDBRepl(client, vbspec.getNamespace(), pod.getMetadata().getName())) {
                addRoleLabelToPod(client, pod, true);
            } else {
                addRoleLabelToPod(client, pod, false);
            }
        }
        return true;
    }

    private void addRoleLabelToPod(KubernetesClient client, Pod pod, boolean b) {
        resourcesService.addRoleLabelToPod(client, pod, b);
    }
}
