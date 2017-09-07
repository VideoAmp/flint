import R from "ramda";

export default function(clusters, message) {
    const { clusterId } = message;

    if (!R.has(clusterId, clusters)) {
        return console.log(`Cluster with id ${clusterId} not found`);
    }

    const clusterToUpdate = R.prop(clusterId, clusters);
    const updatedCluster = R.assoc(
        "imageChangeInProgress",
        true,
        clusterToUpdate,
    );

    return R.assoc(updatedCluster.id, updatedCluster, clusters);
}
