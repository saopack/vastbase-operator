package com.vastdata.cluster;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("vastbase.vastdata.com")
public class VastbaseCluster extends CustomResource<VastbaseClusterSpec, VastbaseClusterStatus> implements Namespaced {
}

