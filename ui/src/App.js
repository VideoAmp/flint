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
                    R.concat(R.prop("workers", clusterToUpdate), workers),
                    clusterToUpdate
                );

                const updatedClusters = R.assoc(updatedCluster.id, updatedCluster, clusters)
                this.setState({ clusters: updatedClusters });
                console.log("Workers Added");
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
                        <AppBar title="Flint"/>
                        <div className="cluster-container">
                            <div className="clusters">
                                {
                                    mapAndReturnObjectValues(cluster =>
                                        <div className="cluster"
                                             key={cluster.id}>
                                             <Cluster data={cluster} socket={this.state.socket}/>
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
