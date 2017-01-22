import React from 'react';
import {ListItem} from 'material-ui/List';
import IconButton from 'material-ui/IconButton';
import Avatar from 'material-ui/Avatar';
import Trash from 'material-ui/svg-icons/action/delete';

import { green300, yellow300, red300 } from 'material-ui/styles/colors';

import 'react-flexr/styles.css'

const stateColorMap = {
    "Starting": yellow300,
    "Running": green300,
    "Terminated": red300,
};

export default class Instance extends React.Component {
    terminateWorker = (socket, { id }) => {
        const payload = JSON.stringify({ instanceId: id, "$type": "TerminateWorker" });
        socket.send(payload);
    }

    isTerminatable = (data, master) => {
        return data.state !== "Terminated" && !master;
    }

    getStatusIndicatorColor = (state) => stateColorMap[state];

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
                leftAvatar={ <Avatar backgroundColor={this.getStatusIndicatorColor(data.state)} size={15} style={{margin: 12.5}} />}
                rightIconButton={this.isTerminatable(data, master) ? rightIconButton : null}
                disabled={true}
            />
        );
    }
}
