import React from 'react';

import Dialog from 'material-ui/Dialog';
import FlatButton from 'material-ui/FlatButton';

export default class ClusterInstanceDialog extends React.Component {
    terminateCluster = () => {
        const clusterId = this.props.cluster.id;
        const payload = JSON.stringify({ clusterId, "$type": "TerminateCluster" });
        this.props.socket.send(payload);
        this.props.close();
    }

    render() {
        const { close, openState } = this.props;

        const instanceDialogActions = [
            <FlatButton
                label="Cancel"
                onTouchTap={close}
            />,
            <FlatButton
                label="Kill"
                primary={true}
                onTouchTap={this.terminateCluster}
            />,
        ];

        return <Dialog
            actions={instanceDialogActions}
            modal={false}
            open={openState}
            onRequestClose={close}>
            <p> Are you sure you want to kill this cluster? </p>
        </Dialog>
    }
}
