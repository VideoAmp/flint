import React from 'react';
import R from 'ramda';
import './App.css';

import Cluster from './components/Cluster';

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

export default {
    render() {
        return (
            <div>
                <MuiThemeProvider muiTheme={muiTheme}>
                    <div>
                        <AppBar title="Flint"/>
                        <div className="cluster-container">
                            {exampleClusters}
                        </div>
                    </div>
                </MuiThemeProvider>
            </div>
        );
    }
}
