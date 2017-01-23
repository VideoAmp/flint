import React from 'react';
import './App.css';

import ReconnectingWebSocket from 'reconnecting-websocket';
import R from 'ramda';

import Cluster from './components/Cluster';
import ClusterDialog from './components/ClusterDialog'

import FloatingActionButton from 'material-ui/FloatingActionButton';
import ContentAdd from 'material-ui/svg-icons/content/add';
import MuiThemeProvider from 'material-ui/styles/MuiThemeProvider';
import {yellow700} from 'material-ui/styles/colors';
import getMuiTheme from 'material-ui/styles/getMuiTheme';
const muiTheme = getMuiTheme({
    palette: {
        primary1Color: yellow700,
    }
});

import AppBar from 'material-ui/AppBar';

const mapAndReturnObjectValues = R.compose(R.values, R.map);

const getInstanceClusterIdPairs = ([clusterId, cluster]) => {
  const { master, workers } = cluster;
  const masterPair = [master.id, clusterId];
  const workerPairs = R.map(({id: instanceId}) => [instanceId, clusterId], workers);
  return R.prepend(masterPair, workerPairs);
}

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
}

export default class App extends React.Component {
    state = {
        clusterDialogOpen: false,
        clusters: {},
        instanceSpecs: [],
        socket: null,
    };

    getClusters = () => {
        return fetch("http://localhost:8080/api/version/1/clusters")
            .then((response) => response.json())
            .then((clusters) => this.setState({ clusters: R.indexBy(R.prop("id"), clusters) }));
    };

    getInstanceSpecs = () => {
        return fetch("http://localhost:8080/api/version/1/instanceSpecs")
            .then((response) => response.json())
            .then((instanceSpecs) => this.setState({ instanceSpecs }));
    }

    componentDidMount() {
        this.getInstanceSpecs().then(this.getClusters);

        const socket = new ReconnectingWebSocket("ws://localhost:8080/api/version/1/messaging")
        socket.onmessage = ({ data }) => {
            var message = JSON.parse(data);
            console.log(message);

            const { clusters } = this.state;
            if(R.propEq("$type", "ClustersAdded", message)) {
                const { clusters: newClusters } = message;
                const updatedClusterState = R.merge(clusters, R.indexBy(R.prop("id"), newClusters))
                this.setState({ clusters: updatedClusterState });
                console.log("Launched new cluster");
            } else if (R.propEq("$type", "WorkersAdded", message)) {
                const { clusterId, workers } = message;
                const clusterToUpdate = R.prop(clusterId, clusters);
                const updatedCluster = R.assoc(
                    "workers",
                    R.unionWith(R.eqBy(R.prop("id")), R.prop("workers", clusterToUpdate), workers),
                    clusterToUpdate
                );

                const updatedClusters = R.assoc(updatedCluster.id, updatedCluster, clusters)
                this.setState({ clusters: updatedClusters });
                console.log("Workers Added");
            } else if (R.propEq("$type", "InstanceContainerState", message)) {
                const { instanceId, containerState } = message;
                const instanceClusterMap = getInstanceIdClusterIdMap(clusters);
                const clusterIdToUpdate = R.prop(instanceId, instanceClusterMap);
                const clusterToUpdate = R.prop(clusterIdToUpdate, clusters);
                if (!clusterToUpdate) {
                    return;
                }
                const updatedCluster = updateInstanceContainerStateInCluster(clusterToUpdate, instanceId, containerState);

                const updatedClusters = R.assoc(updatedCluster.id, updatedCluster, clusters)
                this.setState({ clusters: updatedClusters });
            }
        }
        this.setState({ socket });
    };

    handleClusterDialogOpen = () => {
        this.setState({ clusterDialogOpen: true });
    };

    handleClusterDialogClose = () => {
        this.setState({ clusterDialogOpen: false });
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
                                        <div className="cluster"
                                             key={cluster.id}>
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
