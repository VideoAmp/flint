import React from "react";
import R from "ramda";
import CopyToClipboard from "react-copy-to-clipboard";

import { ListItem } from "material-ui/List";
import Snackbar from "material-ui/Snackbar";
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
    "ContainerStopping": yellow300,
};

const leftAvatarStyles = { margin: 12.5 };

export default class Instance extends React.Component {
    state = {
        isBeingTerminated: false,
        copied: false,
    }

    terminateWorker = (socket, { id }) => {
        const payload = JSON.stringify({ "instanceId": id, "$type": "TerminateWorker" });
        socket.send(payload);
        this.setState({ isBeingTerminated: true });
    }

    isTerminatable = (data, master) => data.state !== "Terminated" && !master && !this.state.isBeingTerminated

    // Instance status element logic:
    //   1. Return red dot if instance is terminated
    //   2. Return circular progress if instance is not running
    //   3. Return colored dot according to container state as defined in getInstanceStatusElement
    // The net effect is that red dots and progress indicators are reserved for terminated or
    // non-running instances. Other colored dots indicate Docker container states.
    getInstanceStatusElement = (instanceState, containerState) => {
        const avatar = backgroundColor =>
            <Avatar
                backgroundColor={backgroundColor}
                size={15}
                style={leftAvatarStyles} />;

        if (instanceState === "Terminated") {
            return avatar(red300);
        }

        if (instanceState !== "Running") {
            return <CircularProgress size={15} style={leftAvatarStyles}/>;
        }

        const backgroundColor = R.has(containerState, containerStateColorMap) ?
            containerStateColorMap[containerState] :
            yellow300;

        return avatar(backgroundColor);
    }

    onRightIconButtonClick = () => this.terminateWorker(this.props.socket, this.props.data);

    onSnackbarRequestClose = () => this.setState({ copied: false });

    onIPAddressCopy = () => this.setState({ copied: true })

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
                    touch={true}
                    disabled={this.state.isBeingTerminated || data.containerState !== "ContainerRunning"}>
                    <Link />
                </IconButton>
            );
        }

        return null;
    }

    render() {
        const { data, master, isSpotCluster } = this.props;

        return (
            <div>
                <ListItem
                    leftAvatar={this.getInstanceStatusElement(data.state, data.containerState)}
                    rightIconButton={this.getRightIconButton(data, master)}
                    disabled={true}>
                    <div>
                        {data.instanceType}
                        &nbsp;
                        <CopyToClipboard
                            text={data.ipAddress}
                            onCopy={this.onIPAddressCopy}>
                            <span style={{ cursor: "pointer" }}>{data.ipAddress}</span>
                        </CopyToClipboard>
                        &nbsp;
                        {!master && isSpotCluster ? "Spot " : ""}
                        {master ? "Master" : "Worker"}
                    </div>
                </ListItem>
                <Snackbar
                    open={this.state.copied}
                    message="Copied to clipboard"
                    autoHideDuration={2000}
                    onRequestClose={this.onSnackbarRequestClose}
                />
            </div>
        );
    }
}
