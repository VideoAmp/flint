import R from "ramda";

export default function(clusters, message) {
    const { clusterIds: removedClusterIds } = message;
    return R.omit(removedClusterIds, clusters);
}
