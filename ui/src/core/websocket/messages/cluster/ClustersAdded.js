import R from "ramda";

export default function(clusters, message) {
    const { clusters: newClusters } = message;
    return R.merge(clusters, R.indexBy(R.prop("id"), newClusters));
}
