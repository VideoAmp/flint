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

const getClusterId = cluster => cluster.id || cluster.instanceId;

const addCluster = (clusters, cluster) => {
    const newCluster = R.merge({
        id: getClusterId(cluster),
        workers: [],
    }, cluster);

    return R.merge(clusters, R.objOf(newCluster.id, newCluster));
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
            if(R.prop("$type", message) === "ClusterLaunchAttempt") {
                this.setState({
                    clusters: addCluster(this.state.clusters, message.clusterSpec)
                });
                console.log("Launched new cluster");
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
                                             key={getClusterId(cluster)}>
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
