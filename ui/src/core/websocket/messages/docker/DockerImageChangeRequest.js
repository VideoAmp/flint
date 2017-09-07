import R from "ramda";

export default function(clusters, message) {
    const { clusterId, dockerImage, error } = message;

    if (!R.has(clusterId, clusters)) {
        return console.log(`Cluster with id ${clusterId} not found`);
    }

    if (error) {
        return console.log(`Failed to change Docker image for cluster with id ${clusterId}: ${error}`);
    }

    const clusterToUpdate = R.prop(clusterId, clusters);
    const updatedCluster = R.assoc(
        "dockerImage",
        dockerImage,
        R.assoc("imageChangeInProgress", false, clusterToUpdate),
    );

    return R.assoc(updatedCluster.id, updatedCluster, clusters);
}
