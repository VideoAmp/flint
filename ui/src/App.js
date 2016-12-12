import React from 'react';
import R from 'ramda';
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

const buildClusterRow = (index) => <div className="cluster" key={index}><Cluster/></div>;
const exampleClusterRows = R.times(buildClusterRow, 6)

const exampleClusters = (
    <div className="clusters">
        {exampleClusterRows}
    </div>
);

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
                            {exampleClusters}
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
