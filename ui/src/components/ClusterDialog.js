import React from 'react';
import R from 'ramda';
import moment from 'moment';

import UUID from 'uuid/v4';

import ClusterTotals from './ClusterTotals'

import { Grid, Cell } from 'react-flexr';
import 'react-flexr/styles.css'

import Dialog from 'material-ui/Dialog';
import FlatButton from 'material-ui/FlatButton';

import SelectField from 'material-ui/SelectField';
import MenuItem from 'material-ui/MenuItem';
import TextField from 'material-ui/TextField';
import NumberInput from 'material-ui-number-input';

const tagFloatingTextLabelStyle = {
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
    overflow: "hidden",
};

const generateInstanceSpec =
    ({instanceType}, key) => <MenuItem key={key} value={instanceType} primaryText={instanceType} />

export default class ClusterDialog extends React.Component {
    state = {
        tags: [],
        lifetimeHoursErrorText: '',
        workerCountErrorText: '',
        idleTimeoutCountErrorText: '',
        masterInstanceType: "",
        workerInstanceType: "",
        numWorkers: 1,
        lifetimeHours: 10,
        idleTimeout: 15,
    };

    getDockerImageTags = () => {
        return fetch("http://localhost:8080/api/version/1/dockerImages")
            .then((response) => response.json())
            .then((dockerImages) => {
                const tags = R.map(R.prop("tag"), dockerImages);
                this.setState({ tags, tag: tags[0] });
                return tags;
            });
    }

    launchCluster = () => {
        const {
            owner,
            lifetimeHours,
            idleTimeout,
            masterInstanceType,
            workerInstanceType,
            numWorkers,
            tag
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

    componentWillMount() {
        this.getDockerImageTags();
    }

    componentWillReceiveProps() {
        const defaultInstance = R.pathOr("", ["instanceSpecs", 0, "instanceType"], this.props);
        this.setState({
            masterInstanceType: defaultInstance,
            workerInstanceType: defaultInstance,
        })
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
        const { instanceSpecs, openState, close } = this.props;

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

        const fieldStyles = { width: '100%' };

        return (
            <Dialog
                title="New Cluster"
                actions={clusterDialogActions}
                modal={false}
                open={openState}
                onRequestClose={this.props.close}
            >
                <ClusterTotals
                    instanceSpecs={instanceSpecs}
                    masterInstanceType={this.state.masterInstanceType}
                    workerInstanceType={this.state.workerInstanceType}
                    numWorkers={this.state.numWorkers}
                    active={false}
                />
                <Grid>
                    <Cell>
                        <SelectField
                            labelStyle={tagFloatingTextLabelStyle}
                            autoWidth={true}
                            fullWidth={true}
                            value={this.state.tag}
                            onChange={this.handleFieldChange("tag")}
                            floatingLabelText="Build">
                            {
                                this.state.tags.map((tag, key) =>
                                    <MenuItem key={key} value={tag} primaryText={tag} />
                                )
                            }
                        </SelectField>
                    </Cell>
                </Grid>
                <Grid>
                    <Cell>
                        <TextField
                            onChange={this.handleOwnerChange}
                            style={fieldStyles}
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
                            style={fieldStyles}
                        />
                    </Cell>
                </Grid>
                <Grid>
                    <Cell>
                        <SelectField
                            value={this.state.masterInstanceType}
                            onChange={this.handleFieldChange("masterInstanceType")}
                            floatingLabelText="Master Type"
                            fullWidth={true}>
                            {this.props.instanceSpecs.map(generateInstanceSpec)}
                        </SelectField>
                    </Cell>
                    <Cell>
                        <SelectField
                            value={this.state.workerInstanceType}
                            onChange={this.handleFieldChange("workerInstanceType")}
                            floatingLabelText="Worker Type"
                            fullWidth={true}>
                            {this.props.instanceSpecs.map(generateInstanceSpec)}
                        </SelectField>
                    </Cell>
                </Grid>
                <Grid>
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
                            style={fieldStyles}
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
                            style={fieldStyles}
                        />
                    </Cell>
                </Grid>
            </Dialog>
        );
    }
}
