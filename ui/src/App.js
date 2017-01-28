import React from "react";
import R from "ramda";
import ReconnectingWebSocket from "reconnecting-websocket";
import Store from "store";

import AppBar from "material-ui/AppBar";
import FloatingActionButton from "material-ui/FloatingActionButton";
import ContentAdd from "material-ui/svg-icons/content/add";
import MuiThemeProvider from "material-ui/styles/MuiThemeProvider";
import { yellow700 } from "material-ui/styles/colors";
import getMuiTheme from "material-ui/styles/getMuiTheme";

import "./App.css";

import Cluster from "./components/Cluster";
import ClusterDialog from "./components/ClusterDialog";

const muiTheme = getMuiTheme({
    palette: {
        primary1Color: yellow700,
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

const updateInstanceContainerStateInCluster = (cluster, instanceId, containerState) => {
    const masterInstanceId = R.path(["master", "id"], cluster);
    if (R.equals(masterInstanceId, instanceId)) {
        return R.assoc(
            "master",
            R.merge(R.prop("master", cluster), { containerState }),
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
            R.merge(workerToUpdate, { containerState }),
            workers
        ),
        cluster
    );
};

export default class App extends React.Component {
    baseUrl = process.env.REACT_APP_FLINT_SERVER_URL;
    baseWebsocketUrl = process.env.REACT_APP_FLINT_WEBSOCKET_URL;

    state = {
        clusterDialogOpen: false,
        clusters: {},
        instanceSpecs: [],
        socket: null,
        ownerDataSource: Store.get("ownerDataSource"),
    };

    getClusters = () => fetch(`${this.baseUrl}/clusters`)
            .then(response => response.json())
            .then(clusters => this.setState({ clusters: R.indexBy(R.prop("id"), clusters) }));

    getInstanceSpecs = () => fetch(`${this.baseUrl}/instanceSpecs`)
            .then(response => response.json())
            .then(instanceSpecs => this.setState({ instanceSpecs }))

    componentDidMount() {
        this.getInstanceSpecs().then(this.getClusters);

        const socket = new ReconnectingWebSocket(`${this.baseWebsocketUrl}/messaging`);
        socket.onmessage = ({ data }) => {
            const message = JSON.parse(data);
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
            } else if (R.propEq("$type", "InstanceContainerState", message)) {
                const { instanceId, containerState } = message;
                const instanceClusterMap = getInstanceIdClusterIdMap(clusters);
                if (!R.has(instanceId, instanceClusterMap)) {
                    return console.log(`Instance with id ${instanceId} not found`);
                }
                const clusterIdToUpdate = R.prop(instanceId, instanceClusterMap);
                const clusterToUpdate = R.prop(clusterIdToUpdate, clusters);
                const updatedCluster = updateInstanceContainerStateInCluster(
                    clusterToUpdate,
                    instanceId,
                    containerState
                );

                const updatedClusters = R.assoc(updatedCluster.id, updatedCluster, clusters);
                this.setState({ clusters: updatedClusters });
            }
            return undefined;
        };
        this.setState({ socket });
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
            this.setState(R.merge({ ownerDataSource: updatedOwnerDataSource }, updatedState));
        } else {
            this.setState(updatedState);
        }
    };

    render() {
        return (
            <div>
                <MuiThemeProvider muiTheme={muiTheme}>
                    <div>
                        <AppBar title="Flint" showMenuIconButton={false}/>
                        <div className="cluster-container">
                            <div className="clusters">
                                {
                                    mapAndReturnObjectValues(cluster =>
                                        <div className="cluster" key={cluster.id}>
                                             <Cluster
                                                data={cluster}
                                                instanceSpecs={this.state.instanceSpecs}
                                                socket={this.state.socket} />
                                        </div>,
                                        this.state.clusters
                                    )
                                }
                            </div>
                        </div>
                        <ClusterDialog
                            openState={this.state.clusterDialogOpen}
                            close={this.handleClusterDialogClose}
                            socket={this.state.socket}
                            instanceSpecs={this.state.instanceSpecs}
                            ownerDataSource={this.state.ownerDataSource}
                        />
                        <FloatingActionButton className="fab" onTouchTap={this.handleClusterDialogOpen}>
                            <ContentAdd />
                        </FloatingActionButton>
                    </div>
                </MuiThemeProvider>
            </div>
        );
    }
}
