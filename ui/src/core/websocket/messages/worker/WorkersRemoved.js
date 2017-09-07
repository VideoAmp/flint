import R from "ramda";

export default function(clusters, message) {
    const { clusterId, workerIds: removedWorkerIds } = message;
    if (!R.has(clusterId, clusters)) {
        return console.log(`Cluster with id ${clusterId} not found`);
    }
    const clusterToUpdate = R.prop(clusterId, clusters);
    const updatedCluster = R.assoc(
        "workers",
        R.reject(
            worker => R.contains(R.prop("id", worker), removedWorkerIds),
            R.prop("workers", clusterToUpdate),
        ),
        clusterToUpdate,
    );

    return R.assoc(updatedCluster.id, updatedCluster, clusters);
}
