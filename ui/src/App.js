import React from 'react';
import './App.css';

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

const generateClusters = (clusters) => clusters.map(
    cluster => <div className="cluster" key={cluster.id}><Cluster data={cluster} /></div>
)

export default class App extends React.Component {
    state = {
        clusterDialogOpen: false,
        clusters: [],
    };

    getClusters = () => {
        fetch("http://localhost:8080/api/version/1/clusters")
            .then((response) => response.json())
            .then((clusters) => this.setState({ clusters }));
    };

    componentDidMount() {
        this.getClusters();
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
                                {generateClusters(this.state.clusters)}
                            </div>
                        </div>
                        <ClusterDialog
                            openState={this.state.clusterDialogOpen}
                            closeDialog={this.handleClusterDialogClose}
                        />
                        <FloatingActionButton onTouchTap={this.handleClusterDialogOpen}></FloatingActionButton>
                    </div>
                </MuiThemeProvider>
            </div>
        );
    }
}
