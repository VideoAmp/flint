import React from 'react';

import Dialog from 'material-ui/Dialog';
import FlatButton from 'material-ui/FlatButton';

import NumberInput from 'material-ui-number-input';

export default class ClusterInstanceDialog extends React.Component {
    state = {
        count: 1,
    };

    onInstanceCountError = (error) => {
        var errorText = (error === "none") ?
            "" :
            "Please enter a valid instance count (less than 100)";

        this.setState({ errorText });
    };

    render() {
        const { close, openState } = this.props;

        const instanceDialogActions = [
            <FlatButton
                label="Cancel"
                onTouchTap={close}
            />,
            <FlatButton
                label="Launch"
                primary={true}
                onTouchTap={close}
            />,
        ];

        return <Dialog
            title="Add Worker"
            actions={instanceDialogActions}
            modal={false}
            open={openState}
            onRequestClose={close}>
            <NumberInput
                id="instance-amount-input"
                floatingLabelText="Instance Count"
                defaultValue={1}
                min={1}
                max={100}
                strategy="allow"
                errorText={this.state.errorText}
                onError={this.onInstanceCountError}
            />
        </Dialog>
    }
}
