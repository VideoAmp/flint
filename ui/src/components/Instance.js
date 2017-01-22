import React from 'react';
import R from 'ramda';
import {ListItem} from 'material-ui/List';
import IconButton from 'material-ui/IconButton';
import Avatar from 'material-ui/Avatar';
import Trash from 'material-ui/svg-icons/action/delete';

import { green300, yellow300, red300, cyan300 } from 'material-ui/styles/colors';
import CircularProgress from 'material-ui/CircularProgress';

import 'react-flexr/styles.css'

const stateColorMap = {
    "Pending": cyan300,
    "Starting": yellow300,
    "Running": green300,
    // "Terminating":
    "Terminated": red300,
};

const leftAvatarStyles = { margin: 12.5 };

export default class Instance extends React.Component {
    state = {
        isBeingTerminated: false,
    }

    terminateWorker = (socket, { id }) => {
        const payload = JSON.stringify({ instanceId: id, "$type": "TerminateWorker" });
        socket.send(payload);
        this.setState({ isBeingTerminated: true });
    }

    isTerminatable = (data, master) => {
        return data.state !== "Terminated" && !master && !this.state.isBeingTerminated;
    }

    getStatusIndicatorColor = (state) => stateColorMap[state];

    getInstanceStateElement = (state) => R.has(state, stateColorMap) ?
         <Avatar backgroundColor={stateColorMap[state]} size={15} style={leftAvatarStyles} /> :
         <CircularProgress size={15} style={leftAvatarStyles}/>;

    render() {
        const {data, master, socket} = this.props;

        const onRightIconButtonClick = () => this.terminateWorker(socket, data);
        const rightIconButton = (
            <IconButton onTouchTap={onRightIconButtonClick} touch={true}>
                <Trash />
            </IconButton>
        );

        return (
            <ListItem
                primaryText={`${data.ipAddress} ${master ? "Master" : "Worker"}`}
                leftAvatar={this.getInstanceStateElement(data.state)}
                rightIconButton={this.isTerminatable(data, master) ? rightIconButton : null}
                disabled={true}
            />
        );
    }
}
