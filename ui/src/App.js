import React from 'react';
import './App.css';

import MuiThemeProvider from 'material-ui/styles/MuiThemeProvider';
import {yellow600} from 'material-ui/styles/colors';
import getMuiTheme from 'material-ui/styles/getMuiTheme';
const muiTheme = getMuiTheme({
    palette: {
        primary1Color: yellow600,
    }
});

import AppBar from 'material-ui/AppBar';

export default {
    render() {
        return (
            <div className="App">
                <MuiThemeProvider muiTheme={muiTheme}>
                    <AppBar title="Flint" />
                </MuiThemeProvider>
                <p className="App-intro">
                    To get started, edit <code>src/App.js</code> and save to reload.
                </p>
            </div>
        );
    }
}
