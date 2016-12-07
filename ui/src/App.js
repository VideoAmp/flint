import React from 'react';
import R from 'ramda';
import './App.css';
import { Grid, Cell } from 'react-flexr';
import 'react-flexr/styles.css';

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

const buildClusterRow = (index) => <Cell key={index}><Cluster/></Cell>;
const exampleClusterRows = R.times(buildClusterRow, 2)

const buildCluster = (index) => <Grid key={index}>{exampleClusterRows}</Grid>;
const exampleClusters = R.times(buildCluster, 2)

export default {
    render() {
        return (
            <div>
                <MuiThemeProvider muiTheme={muiTheme}>
                    <div>
                        <AppBar title="Flint"/>
                        {exampleClusters}
                    </div>
                </MuiThemeProvider>
            </div>
        );
    }
}
