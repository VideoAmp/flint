import React from "react";
import PropTypes from "prop-types";
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
};

const leftAvatarStyles = { margin: 12.5 };

export default class Instance extends React.Component {
    static propTypes = {
        data: PropTypes.shape().isRequired, // TODO: Improve validation
        master: PropTypes.bool.isRequired,
        isSpotCluster: PropTypes.bool,
        socket: PropTypes.shape().isRequired,
    };

    static defaultProps = {
        isSpotCluster: false,
    };

    state = {
        copied: false,
    };

    onRightIconButtonClick = () => this.terminateWorker(this.props.socket, this.props.data);

    onSnackbarRequestClose = () => this.setState({ copied: false });

    onIPAddressCopy = () => this.setState({ copied: true });

    getRightIconButton = (data, master) => {
        const { state, containerState } = data;
        if (!master && state !== "Terminating" && state !== "Terminated" &&
                containerState !== "ContainerStopping" && containerState !== "ContainerStopped") {
            return (
                <IconButton onClick={this.onRightIconButtonClick} touch>
                    <Trash />
                </IconButton>
            );
        }

        const { ipAddress } = data;

        if (master && containerState === "ContainerRunning") {
            return (
                <IconButton
                    href={`http://${ipAddress}:8080`}
                    target="_blank"
                    touch
                >
                    <Link />
                </IconButton>
            );
        }

        return null;
    };

    getInstanceStatusElement = (instanceState, containerState) => {
        const avatar = backgroundColor =>
            (<Avatar
                backgroundColor={backgroundColor}
                size={15}
                style={leftAvatarStyles}
            />);

        if (instanceState === "Terminated") {
            return avatar(red300);
        }

        if (instanceState !== "Running" || !R.has(containerState, containerStateColorMap)) {
            return <CircularProgress size={15} style={leftAvatarStyles} />;
        }

        const backgroundColor = containerStateColorMap[containerState];

        return avatar(backgroundColor);
    };

    terminateWorker = (socket, { id }) => {
        const payload = JSON.stringify({ "instanceId": id, "$type": "TerminateWorker" });
        socket.send(payload);
    };

    render() {
        const { data, master, isSpotCluster } = this.props;
        const { instanceType, ipAddress, state, containerState } = data;

        return (
            <div>
                <ListItem
                    leftAvatar={this.getInstanceStatusElement(state, containerState)}
                    rightIconButton={this.getRightIconButton(data, master)}
                    disabled
                >
                    <div>
                        {instanceType}
                        &nbsp;
                        <CopyToClipboard
                            text={R.defaultTo("", ipAddress)}
                            onCopy={this.onIPAddressCopy}
                        >
                            <span style={{ cursor: "pointer" }}>{ipAddress}</span>
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
