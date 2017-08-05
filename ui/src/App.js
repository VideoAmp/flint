import React from "react";
import R from "ramda";
import ReconnectingWebSocket from "reconnecting-websocket";
import Store from "store";
import Masonry from "react-masonry-component";

import AppBar from "material-ui/AppBar";
import FloatingActionButton from "material-ui/FloatingActionButton";
import ContentAdd from "material-ui/svg-icons/content/add";
import MuiThemeProvider from "material-ui/styles/MuiThemeProvider";
import { amber500 } from "material-ui/styles/colors";
import getMuiTheme from "material-ui/styles/getMuiTheme";

import "./App.css";

import getDockerImages from "./api/getDockerImages";

import Cluster from "./components/Cluster";
import ClusterDialog from "./components/ClusterDialog";

const muiTheme = getMuiTheme({
    palette: {
        primary1Color: amber500,
    },
});


const mapAndReturnObjectValues = R.compose(R.values, R.map);

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
  R.fromPairs
);

const updateInstanceInCluster = (cluster, instanceId, propToUpdate) => {
    const masterInstanceId = R.path(["master", "id"], cluster);
    if (R.equals(masterInstanceId, instanceId)) {
        return R.assoc(
            "master",
            R.merge(R.prop("master", cluster), propToUpdate),
            cluster
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
            workers
        ),
        cluster
    );
};

export default class App extends React.Component {
    serverId = null;
    messageNo = null;

    state = {
        clusterDialogOpen: false,
        clusters: {},
        instanceSpecs: [],
        placementGroups: [],
        subnets: [],
        dockerImages: [],
        socket: null,
        ownerDataSource: Store.get("ownerDataSource"),
        lastOwner: Store.get("lastOwner"),
        serverUrl: "",
        messagingUrl: "",
        endpointsFetchFailed: false,
    };

    getClusters = serverUrl => () => fetch(`${serverUrl}/clusters`)
            .then(response => response.json())
            .then(clusters => this.setState({ clusters: R.indexBy(R.prop("id"), clusters) }));

    getInstanceSpecs = serverUrl => () => fetch(`${serverUrl}/instanceSpecs`)
            .then(response => response.json())
            .then(instanceSpecs => this.setState({ instanceSpecs }))

    getPlacementGroups = serverUrl => () => fetch(`${serverUrl}/placementGroups`)
            .then(response => response.json())
            .then(placementGroups => this.setState({ placementGroups: [null].concat(placementGroups) }))

    getSubnets = serverUrl => () => fetch(`${serverUrl}/subnets`)
            .then(response => response.json())
            .then(subnets => this.setState({ subnets }))

    handleClusterUpdate = cluster => (properties) => {
        const { clusters } = this.state;
        const clusterToUpdate = R.prop(cluster.id, clusters);
        const updatedCluster = R.merge(clusterToUpdate, properties);
        const updatedClusters = R.assoc(updatedCluster.id, updatedCluster, clusters);
        this.setState({ clusters: updatedClusters });
    };

    handleInstanceUpdateMessage = (message, propToUpdate) => {
        const clusters = this.state.clusters;
        const instanceId = message.instanceId;
        const instanceClusterMap = getInstanceIdClusterIdMap(clusters);
        if (!R.has(instanceId, instanceClusterMap)) {
            console.log(`Instance with id ${instanceId} not found`);
            return;
        }
        const clusterIdToUpdate = R.prop(instanceId, instanceClusterMap);
        const clusterToUpdate = R.prop(clusterIdToUpdate, clusters);
        const updatedCluster = updateInstanceInCluster(
            clusterToUpdate,
            instanceId,
            propToUpdate
        );

        const updatedClusters = R.assoc(updatedCluster.id, updatedCluster, clusters);
        this.setState({ clusters: updatedClusters });
    }

    handleMessage = (message) => {
        if (!this.serverId) {
            console.log(`No server id: ${this.serverId}. Initializing message sequence`);
            this.initializeMessageSequence(message);
        } else if (!this.validateMessageSequence(message)) {
            console.log("Refreshing clusters");
            this.getClusters().then(this.initializeMessageSequence(message));
        }

        console.log(message);
        const { clusters } = this.state;
        if (R.propEq("$type", "ClustersAdded", message)) {
            const { clusters: newClusters } = message;
            const updatedClusterState = R.merge(clusters, R.indexBy(R.prop("id"), newClusters));
            this.setState({ clusters: updatedClusterState });
            console.log("Launched new cluster");
        } else if (R.propEq("$type", "WorkersAdded", message)) {
            const { clusterId, workers } = message;
            if (!R.has(clusterId, clusters)) {
                return console.log(`Cluster with id ${clusterId} not found`);
            }
            const clusterToUpdate = R.prop(clusterId, clusters);
            const updatedCluster = R.assoc(
                "workers",
                R.unionWith(R.eqBy(R.prop("id")), R.prop("workers", clusterToUpdate), workers),
                clusterToUpdate
            );

            const updatedClusters = R.assoc(updatedCluster.id, updatedCluster, clusters);
            this.setState({ clusters: updatedClusters });
            console.log("Workers Added");
        } else if (R.propEq("$type", "InstanceState", message)) {
            this.handleInstanceUpdateMessage(message, { state: message.state });
        } else if (R.propEq("$type", "InstanceContainerState", message)) {
            this.handleInstanceUpdateMessage(message, { containerState: message.containerState });
        } else if (R.propEq("$type", "InstanceIpAddress", message)) {
            this.handleInstanceUpdateMessage(message, { ipAddress: message.ipAddress });
        } else if (R.propEq("$type", "DockerImageChangeRequest", message)) {
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

            const updatedClusters = R.assoc(updatedCluster.id, updatedCluster, clusters);
            this.setState({ clusters: updatedClusters });
        } else if (R.propEq("$type", "DockerImageChangeAttempt", message)) {
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

            const updatedClusters = R.assoc(updatedCluster.id, updatedCluster, clusters);
            this.setState({ clusters: updatedClusters });
        } else if (R.propEq("$type", "ClustersRemoved", message)) {
            const { clusterIds: removedClusterIds } = message;
            const updatedClusterState = R.omit(removedClusterIds, clusters);
            this.setState({ clusters: updatedClusterState });
            console.log("Clusters removed");
        } else if (R.propEq("$type", "WorkersRemoved", message)) {
            const { clusterId, workerIds: removedWorkerIds } = message;
            if (!R.has(clusterId, clusters)) {
                return console.log(`Cluster with id ${clusterId} not found`);
            }
            const clusterToUpdate = R.prop(clusterId, clusters);
            const updatedCluster = R.assoc(
                "workers",
                R.reject(
                    worker => R.contains(R.prop("id", worker), removedWorkerIds),
                    R.prop("workers", clusterToUpdate)
                ),
                clusterToUpdate
            );

            const updatedClusters = R.assoc(updatedCluster.id, updatedCluster, clusters);
            this.setState({ clusters: updatedClusters });
            console.log("Workers removed");
        }
        this.messageNo = message.messageNo;
        return undefined;
    }

    initializeMessageSequence = (message) => {
        const { serverId, messageNo } = message;
        this.serverId = serverId;
        this.messageNo = messageNo - 1;
    }

    validateMessageSequence = (message) => {
        const { serverId, messageNo } = message;

        if (this.serverId !== serverId) {
            console.log(`Server id changed. Old: ${this.serverId}. New: ${serverId}.`);
            return false;
        } else if (messageNo !== (this.messageNo + 1)) {
            console.log(`Message out of sequence. Old: ${this.messageNo}. New: ${messageNo}.`);
            return false;
        }

        return true;
    }

    componentDidMount() {
        fetch("endpoints.json")
        .then(response => response.json())
        .then((endpoints) => {
            const { serverUrl, messagingUrl } = endpoints;
            const socket = new ReconnectingWebSocket(`${messagingUrl}/messaging`);
            socket.onopen = () => {
                getDockerImages(serverUrl)
                    .then(sortedImages => this.setState({ dockerImages: sortedImages }))
                    .then(this.getInstanceSpecs(serverUrl))
                    .then(this.getPlacementGroups(serverUrl))
                    .then(this.getSubnets(serverUrl))
                    .then(this.getClusters(serverUrl));
                this.setState({ socket });
            };
            socket.onmessage = ({ data }) => {
                const message = JSON.parse(data);
                this.handleMessage(message);
            };
            this.setState({ serverUrl, messagingUrl });
        })
      .catch(() => this.setState({ endpointsFetchFailed: true }));
    }

    handleClusterDialogOpen = () => {
        this.setState({ clusterDialogOpen: true });
    };

    handleClusterDialogClose = (owner) => {
        const updatedState = { clusterDialogOpen: false };

        const isOwner = !R.isNil(owner) && R.is(String, owner);
        if (isOwner) {
            const { ownerDataSource } = this.state;
            const updatedOwnerDataSource = R.union([owner], ownerDataSource);
            Store.set("ownerDataSource", updatedOwnerDataSource);
            Store.set("lastOwner", owner);
            this.setState(R.merge({ ownerDataSource: updatedOwnerDataSource, lastOwner: owner }, updatedState));
        } else {
            this.setState(updatedState);
        }
    };

    render() {
        let appPaneContents = [];

        if (this.state.endpointsFetchFailed) {
            appPaneContents = [<p key="unknown-endpoints">{ "Oh noes! Couldn't find server endpoints!" }</p>];
        } else if (this.state.socket) {
            const clusterContainer =
                <div key="cluster-container" className="cluster-container">
                    <Masonry className="clusters">
                        {
                            mapAndReturnObjectValues(cluster =>
                                <div className="cluster" key={cluster.id}>
                                     <Cluster
                                        data={cluster}
                                        handleClusterUpdate={this.handleClusterUpdate(cluster)}
                                        instanceSpecs={this.state.instanceSpecs}
                                        dockerImages={this.state.dockerImages}
                                        socket={this.state.socket} />
                                </div>,
                                this.state.clusters
                            )
                        }
                    </Masonry>
                </div>;
            const clusterDialog =
                <ClusterDialog
                    key="cluster-dialog"
                    openState={this.state.clusterDialogOpen}
                    close={this.handleClusterDialogClose}
                    socket={this.state.socket}
                    instanceSpecs={this.state.instanceSpecs}
                    placementGroups={this.state.placementGroups}
                    subnets={this.state.subnets}
                    dockerImages={this.state.dockerImages}
                    ownerDataSource={this.state.ownerDataSource}
                    defaultOwner={this.state.lastOwner}
                    serverUrl={this.state.serverUrl}
                />;
            const newClusterButton =
                <FloatingActionButton key="cluster-add" className="fab" onClick={this.handleClusterDialogOpen}>
                    <ContentAdd />
                </FloatingActionButton>;
            appPaneContents = [clusterContainer, clusterDialog, newClusterButton];
        }
        return (
            <div>
                <MuiThemeProvider muiTheme={muiTheme}>
                    <div>
                        <AppBar title="Flint" showMenuIconButton={false}/>
                        { appPaneContents }
                    </div>
                </MuiThemeProvider>
            </div>
        );
    }
}
