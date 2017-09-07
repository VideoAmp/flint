import R from "ramda";

const getInstanceClusterIdPairs = ([clusterId, cluster]) => {
    const { master, workers } = cluster;
    const masterPair = [master.id, clusterId];
    const workerPairs = R.map(({ id: instanceId }) => [instanceId, clusterId], workers);
    return R.prepend(masterPair, workerPairs);
};

const getInstanceIdClusterIdMap = R.pipe(
    R.toPairs,
    R.map(getInstanceClusterIdPairs),
    R.unnest,
    R.fromPairs,
);

const updateInstanceInCluster = (cluster, instanceId, propToUpdate) => {
    const masterInstanceId = R.path(["master", "id"], cluster);
    if (R.equals(masterInstanceId, instanceId)) {
        return R.assoc(
            "master",
            R.merge(R.prop("master", cluster), propToUpdate),
            cluster,
        );
    }

    const { workers } = cluster;
    const workerToUpdateIndex = R.findIndex(R.propEq("id", instanceId), workers);
    const workerToUpdate = workers[workerToUpdateIndex];
    return R.assoc(
        "workers",
        R.update(
            workerToUpdateIndex,
            R.merge(workerToUpdate, propToUpdate),
            workers,
        ),
        cluster,
    );
};

export default function(clusters, message, propToUpdate) {
    const instanceId = message.instanceId;
    const instanceClusterMap = getInstanceIdClusterIdMap(clusters);
    if (!R.has(instanceId, instanceClusterMap)) {
        return console.log(`Instance with id ${instanceId} not found`);
    }
    const clusterIdToUpdate = R.prop(instanceId, instanceClusterMap);
    const clusterToUpdate = R.prop(clusterIdToUpdate, clusters);
    const updatedCluster = updateInstanceInCluster(
        clusterToUpdate,
        instanceId,
        propToUpdate,
    );

    return R.assoc(updatedCluster.id, updatedCluster, clusters);
}
