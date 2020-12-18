import React from "react";
import PropTypes from "prop-types";

import Dialog from "material-ui/Dialog";
import FlatButton from "material-ui/FlatButton";

export default class ClusterInstanceDialog extends React.Component {
    static propTypes = {
        cluster: PropTypes.shape().isRequired, // TODO improve validation
        close: PropTypes.func.isRequired,
        openState: PropTypes.bool.isRequired,
        socket: PropTypes.shape().isRequired,
    };

    terminateCluster = () => {
        const clusterId = this.props.cluster.id;
        const payload = JSON.stringify({ clusterId, "$type": "TerminateCluster" });
        this.props.socket.send(payload);
        this.props.close();
    };

    render() {
        const { close, openState } = this.props;

        const instanceDialogActions = [
            <FlatButton
                label="Cancel"
                onClick={close}
            />,
            <FlatButton
                label="Kill"
                primary
                onClick={this.terminateCluster}
            />,
        ];

        return (
            <Dialog
                actions={instanceDialogActions}
                modal={false}
                open={openState}
                onRequestClose={close}
            >
                <p> Are you sure you want to kill this cluster? </p>
            </Dialog>
        );
    }
}
