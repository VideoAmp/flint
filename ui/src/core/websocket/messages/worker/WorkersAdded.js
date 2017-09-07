import R from "ramda";

export default function(clusters, message) {
    const { clusterId, workers } = message;
    if (!R.has(clusterId, clusters)) {
        return console.log(`Cluster with id ${clusterId} not found`);
    }
    const clusterToUpdate = R.prop(clusterId, clusters);
    const updatedCluster = R.assoc(
        "workers",
        R.unionWith(R.eqBy(R.prop("id")), R.prop("workers", clusterToUpdate), workers),
        clusterToUpdate,
    );

    return R.assoc(updatedCluster.id, updatedCluster, clusters);
}
