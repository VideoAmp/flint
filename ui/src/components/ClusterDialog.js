import React from 'react';
import R from 'ramda';

import UUID from 'uuid/v4';

import { Grid, Cell } from 'react-flexr';
import 'react-flexr/styles.css'

import Dialog from 'material-ui/Dialog';
import FlatButton from 'material-ui/FlatButton';

import SelectField from 'material-ui/SelectField';
import MenuItem from 'material-ui/MenuItem';
import TextField from 'material-ui/TextField';
import NumberInput from 'material-ui-number-input';

const idleTimeoutMap = {};

export default class ClusterDialog extends React.Component {
    state = {
        lifetimeHoursErrorText: '',
        workerCountErrorText: '',
        idleTimeoutCountErrorText: '',
        masterInstanceType: "r3.large",
        workerInstanceType: "x1.32xlarge",
        numWorkers: 1,
    };

    componentWillMount() {
        this.launchCluster = () => {
            const { owner, idleTimeout, masterInstanceType, workerInstanceType, numWorkers } = this.state;
            const clusterSpec = {
                id: UUID(),
                dockerImage: {
                    repo: "videoamp/spark",
                    tag: "2.0.1-SNAPSHOT-2.6.0-cdh5.5.1-b49-9273bdd-92"
                },
                owner,
                ttl: "PT10H",
                idleTimeout: R.propOr("PT15M", idleTimeout, idleTimeoutMap),
                masterInstanceType,
                workerInstanceType,
                numWorkers
            };
            console.log(clusterSpec);

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

    launchCluster() {}

    handleChange = (stateName) =>
        (event, index, value) => this.setState({ stateName: value });

    handleOwnerChange = (event) => this.setState({ owner: event.target.value });

    render() {
        const { openState, close, socket } = this.props;

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
                        <SelectField value={this.state.tag} onChange={this.handleChange("tag")} floatingLabelText="Build">
                            <MenuItem value="2.1.1-SNAPSHOT" primaryText="2.1.1-SNAPSHOT" />
                            <MenuItem value="2.1.0-SNAPSHOT" primaryText="2.1.0-SNAPSHOT" />
                            <MenuItem value="2.0.3-SNAPSHOT" primaryText="2.0.3-SNAPSHOT" />
                            <MenuItem value="2.0.1-SNAPSHOT" primaryText="2.0.1-SNAPSHOT" />
                        </SelectField>
                    </Cell>
                    <Cell>
                        <TextField
                            value={this.state.owner}
                            onChange={this.handleOwnerChange}
                            hintText="Enter your name here"
                            floatingLabelText="Owner"
                        />
                    </Cell>
                    <Cell>
                        <NumberInput
                            id="lifetime-hours-amount-input"
                            value={this.state.ttl}
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
                        <SelectField value={this.state.masterInstanceType} onChange={this.handleChange("masterInstanceType")} floatingLabelText="Master Type">
                            <MenuItem value="r3.large" primaryText="r3.large" />
                            <MenuItem value="t2.micro" primaryText="t2.micro" />
                        </SelectField>
                    </Cell>
                    <Cell>
                        <SelectField value={this.state.workerInstanceType} onChange={this.handleChange("workerInstanceType")} floatingLabelText="Worker Type">
                            <MenuItem value="x1.32xlarge" primaryText="x1.32xlarge" />
                            <MenuItem value="c3.8xlarge" primaryText="c3.8xlarge" />
                        </SelectField>
                    </Cell>
                    <Cell>
                        <NumberInput
                            id="worker-count-amount-input"
                            value={this.state.workers}
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
                            value={this.state.idleTimeout}
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
