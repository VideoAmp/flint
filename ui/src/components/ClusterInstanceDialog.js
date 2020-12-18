import React from "react";
import PropTypes from "prop-types";

import Dialog from "material-ui/Dialog";
import FlatButton from "material-ui/FlatButton";

import NumberInput from "material-ui-number-input";

export default class ClusterInstanceDialog extends React.Component {
    static propTypes = {
        cluster: PropTypes.shape().isRequired, // TODO improve validation
        close: PropTypes.func.isRequired,
        openState: PropTypes.bool.isRequired,
        socket: PropTypes.shape().isRequired,
    };

    state = {
        count: 1,
    };

    onInstanceCountError = (error) => {
        const errorText = (error === "none") ? "" : "Please enter a valid instance count (less than 100)";
        this.setState({ errorText });
    };

    onInstanceCountValid = count => this.setState({ count });

    launchWorkers = () => {
        const { count } = this.state;
        const clusterId = this.props.cluster.id;
        const payload = JSON.stringify({ clusterId, count, "$type": "AddWorkers" });
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
                label="Launch"
                primary
                onClick={this.launchWorkers}
            />,
        ];

        return (
            <Dialog
                title="Add Worker"
                actions={instanceDialogActions}
                modal={false}
                open={openState}
                onRequestClose={close}
            >
                <NumberInput
                    id="instance-amount-input"
                    floatingLabelText="Instance Count"
                    defaultValue={1}
                    min={1}
                    max={100}
                    strategy="allow"
                    errorText={this.state.errorText}
                    onError={this.onInstanceCountError}
                    onValid={this.onInstanceCountValid}
                />
            </Dialog>
        );
    }
}
