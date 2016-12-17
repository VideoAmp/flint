import React from 'react';
import R from 'ramda';
import moment from 'moment';

import UUID from 'uuid/v4';

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
        masterInstanceType: "r3.large",
        workerInstanceType: "x1.32xlarge",
        numWorkers: 1,
        lifetimeHours: 10,
        idleTimeout: 15,
    };

    componentWillMount() {
        this.launchCluster = () => {
            const {
                owner,
                lifetimeHours,
                idleTimeout,
                masterInstanceType,
                workerInstanceType,
                numWorkers,
                tag="2.0.1-SNAPSHOT-2.6.0-cdh5.5.1-b49-9273bdd-92"
            } = this.state;

            const clusterSpec = {
                id: UUID(),
                dockerImage: {
                    repo: "videoamp/spark",
                    tag
                },
                owner,
                ttl: moment.duration(lifetimeHours, "hours").toString(),
                idleTimeout: moment.duration(idleTimeout, "minutes").toString(),
                masterInstanceType,
                workerInstanceType,
                numWorkers
            };

            this.props.socket.send(JSON.stringify({ clusterSpec, "$type": "LaunchCluster" }));
            this.props.close();
        }
    }

    onLifetimeHoursCountError = (error) => {
        var lifetimeHoursErrorText = (error === "none") ?
            "" :
            "Please enter a valid lifetime hours amount";

        this.setState({ lifetimeHoursErrorText });
    };
    onLifetimeHoursValid = (lifetimeHours) => this.setState({ lifetimeHours });

    onWorkerCountError = (error) => {
        var workerCountErrorText = (error === "none") ?
            "" :
            "Please enter a valid instance count (less than 100)";

        this.setState({ workerCountErrorText });
    };
    onWorkerCountValid = (numWorkers) => this.setState({ numWorkers });

    onIdleTimeoutCountError = (error) => {
        var idleTimeoutCountErrorText = (error === "none") ?
            "" :
            "Please enter a valid idle timeout amount";

        this.setState({ idleTimeoutCountErrorText });
    };
    onIdleTimeoutCountValid = (idleTimeout) => this.setState({ idleTimeout });

    handleFieldChange = (stateName) =>
        (event, index, value) => this.setState(R.objOf(stateName, value))

    handleOwnerChange = (event) => this.setState({ owner: event.target.value });

    render() {
        const { openState, close } = this.props;

        const clusterDialogActions = [
            <FlatButton
                label="Cancel"
                onTouchTap={close}
            />,
            <FlatButton
                label="Launch"
                primary={true}
                onTouchTap={this.launchCluster}
            />,
        ];

        return (
            <Dialog
                title="New Cluster"
                actions={clusterDialogActions}
                modal={false}
                open={openState}
                onRequestClose={this.props.close}
            >
                <Grid>
                    <Cell>
                        <SelectField value={this.state.tag} onChange={this.handleFieldChange("tag")} floatingLabelText="Build">
                            <MenuItem value="2.1.1-SNAPSHOT" primaryText="2.1.1-SNAPSHOT" />
                            <MenuItem value="2.1.0-SNAPSHOT" primaryText="2.1.0-SNAPSHOT" />
                            <MenuItem value="2.0.3-SNAPSHOT" primaryText="2.0.3-SNAPSHOT" />
                            <MenuItem value="2.0.1-SNAPSHOT" primaryText="2.0.1-SNAPSHOT" />
                        </SelectField>
                    </Cell>
                    <Cell>
                        <TextField
                            onChange={this.handleOwnerChange}
                            hintText="Enter your name here"
                            floatingLabelText="Owner"
                        />
                    </Cell>
                    <Cell>
                        <NumberInput
                            id="lifetime-hours-amount-input"
                            floatingLabelText="Lifetime Hours"
                            defaultValue={this.state.lifetimeHours}
                            min={1}
                            max={12}
                            strategy="allow"
                            errorText={this.state.lifetimeHoursErrorText}
                            onError={this.onLifetimeHoursCountError}
                            onValid={this.onLifetimeHoursValid}
                        />
                    </Cell>
                    <Cell>
                        <SelectField value={this.state.masterInstanceType} onChange={this.handleFieldChange("masterInstanceType")} floatingLabelText="Master Type">
                            <MenuItem value="r3.large" primaryText="r3.large" />
                            <MenuItem value="t2.micro" primaryText="t2.micro" />
                        </SelectField>
                    </Cell>
                    <Cell>
                        <SelectField value={this.state.workerInstanceType} onChange={this.handleFieldChange("workerInstanceType")} floatingLabelText="Worker Type">
                            <MenuItem value="x1.32xlarge" primaryText="x1.32xlarge" />
                            <MenuItem value="c3.8xlarge" primaryText="c3.8xlarge" />
                        </SelectField>
                    </Cell>
                    <Cell>
                        <NumberInput
                            id="worker-count-amount-input"
                            floatingLabelText="Worker Count"
                            defaultValue={this.state.numWorkers}
                            min={1}
                            max={100}
                            strategy="allow"
                            errorText={this.state.workerCountErrorText}
                            onError={this.onWorkerCountError}
                            onValid={this.onWorkerCountValid}
                        />
                    </Cell>
                    <Cell>
                        <NumberInput
                            id="worker-count-amount-input"
                            floatingLabelText="Idle Timeout (mins)"
                            defaultValue={this.state.idleTimeout}
                            min={1}
                            max={10000}
                            strategy="allow"
                            errorText={this.state.idleTimeoutCountErrorText}
                            onError={this.onIdleTimeoutCountError}
                            onValid={this.onIdleTimeoutCountValid}
                        />
                    </Cell>
                </Grid>
            </Dialog>
        );
    }
}
