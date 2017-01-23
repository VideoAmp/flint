import React from "react";
import R from "ramda";
import { ListItem } from "material-ui/List";
import IconButton from "material-ui/IconButton";
import Avatar from "material-ui/Avatar";
import Trash from "material-ui/svg-icons/action/delete";
import Link from "material-ui/svg-icons/content/link";

import { green300, yellow300, red300, cyan300 } from "material-ui/styles/colors";
import CircularProgress from "material-ui/CircularProgress";

import "react-flexr/styles.css";

const containerStateColorMap = {
    "ContainerPending": cyan300,
    "ContainerStarting": yellow300,
    "ContainerRunning": green300,
    "ContainerStopped": red300,
};

const leftAvatarStyles = { margin: 12.5 };

export default class Instance extends React.Component {
    state = {
        isBeingTerminated: false,
    }

    terminateWorker = (socket, { id }) => {
        const payload = JSON.stringify({ "instanceId": id, "$type": "TerminateWorker" });
        socket.send(payload);
        this.setState({ isBeingTerminated: true });
    }

    isTerminatable = (data, master) => data.state !== "Terminated" && !master && !this.state.isBeingTerminated

    getInstanceStateElement = (containerState) => {
        if (R.has(containerState, containerStateColorMap)) {
            return (
                <Avatar
                    backgroundColor={containerStateColorMap[containerState]}
                    size={15}
                    style={leftAvatarStyles} />
            );
        }

        return <CircularProgress size={15} style={leftAvatarStyles}/>;
    }

    onRightIconButtonClick = () => this.terminateWorker(this.props.socket, this.props.data);

    getRightIconButton = (data, master) => {
        if (this.isTerminatable(data, master)) {
            return (
                <IconButton onTouchTap={this.onRightIconButtonClick} touch={true}>
                    <Trash />
                </IconButton>
            );
        }

        if (master) {
            return (
                <IconButton
                    href={`http://${data.ipAddress}:8080`}
                    target="_blank"
                    touch={true}>
                    <Link />
                </IconButton>
            );
        }

        return null;
    }

    render() {
        const { data, master } = this.props;

        return (
            <ListItem
                primaryText={`${data.instanceType} ${data.ipAddress} ${master ? "Master" : "Worker"}`}
                leftAvatar={this.getInstanceStateElement(data.containerState)}
                rightIconButton={this.getRightIconButton(data, master)}
                disabled={true}
            />
        );
    }
}
