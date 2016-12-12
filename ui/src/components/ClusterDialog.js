import React from 'react';

import { Grid, Cell } from 'react-flexr';
import 'react-flexr/styles.css'

import Dialog from 'material-ui/Dialog';
import FlatButton from 'material-ui/FlatButton';

import SelectField from 'material-ui/SelectField';
import MenuItem from 'material-ui/MenuItem';
import TextField from 'material-ui/TextField';
import NumberInput from 'material-ui-number-input';

export default class ClusterDialog extends React.Component {
    state = {
        lifetimeHoursErrorText: '',
        workerCountErrorText: '',
        idleTimeoutCountErrorText: '',
    };

    onLifetimeHoursCountError = (error) => {
        var lifetimeHoursErrorText = (error === "none") ?
            "" :
            "Please enter a valid lifetime hours amount";

        this.setState({ lifetimeHoursErrorText });
    };

    onWorkerCountError = (error) => {
        var workerCountErrorText = (error === "none") ?
            "" :
            "Please enter a valid instance count (less than 100)";

        this.setState({ workerCountErrorText });
    };

    onIdleTimeoutCountError = (error) => {
        var idleTimeoutCountErrorText = (error === "none") ?
            "" :
            "Please enter a valid idle timeout amount";

        this.setState({ idleTimeoutCountErrorText });
    };

    render() {
        const clusterDialogActions = [
            <FlatButton
                label="Cancel"
                onTouchTap={this.props.closeDialog}
            />,
            <FlatButton
                label="Launch"
                primary={true}
                onTouchTap={this.props.closeDialog}
            />,
        ];

        return (
            <Dialog
                title="New Cluster"
                actions={clusterDialogActions}
                modal={false}
                open={this.props.openState}
                onRequestClose={this.props.closeDialog}
            >
                <Grid>
                    <Cell>
                        <SelectField floatingLabelText="Build">
                            <MenuItem value={1} primaryText="2.1.1-SNAPSHOT" />
                            <MenuItem value={2} primaryText="2.1.0-SNAPSHOT" />
                            <MenuItem value={3} primaryText="2.0.3-SNAPSHOT" />
                            <MenuItem value={4} primaryText="2.0.1-SNAPSHOT" />
                        </SelectField>
                    </Cell>
                    <Cell>
                        <TextField
                        hintText="Enter your name here"
                        floatingLabelText="Owner"
                        />
                    </Cell>
                    <Cell>
                        <NumberInput
                            id="lifetime-hours-amount-input"
                            floatingLabelText="Lifetime Hours"
                            defaultValue={1}
                            min={1}
                            max={12}
                            strategy="allow"
                            errorText={this.state.lifetimeHoursErrorText}
                            onError={this.onLifetimeHoursCountError}
                        />
                    </Cell>
                    <Cell>
                        <SelectField floatingLabelText="Master Type">
                            <MenuItem value={1} primaryText="r3.large" />
                            <MenuItem value={2} primaryText="t2.micro" />
                        </SelectField>
                    </Cell>
                    <Cell>
                        <SelectField floatingLabelText="Worker Type">
                            <MenuItem value={1} primaryText="x1.32xlarge" />
                            <MenuItem value={2} primaryText="c3.8xlarge" />
                        </SelectField>
                    </Cell>
                    <Cell>
                        <NumberInput
                            id="worker-count-amount-input"
                            floatingLabelText="Worker Count"
                            defaultValue={1}
                            min={1}
                            max={100}
                            strategy="allow"
                            errorText={this.state.workerCountErrorText}
                            onError={this.onWorkerCountError}
                        />
                    </Cell>
                    <Cell>
                        <NumberInput
                            id="worker-count-amount-input"
                            floatingLabelText="Idle Timeout (mins)"
                            defaultValue={1}
                            min={1}
                            max={10000}
                            strategy="allow"
                            errorText={this.state.idleTimeoutCountErrorText}
                            onError={this.onIdleTimeoutCountError}
                        />
                    </Cell>
                </Grid>
            </Dialog>
        );
    }
}
