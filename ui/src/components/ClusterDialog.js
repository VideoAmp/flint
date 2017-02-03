import React from "react";
import R from "ramda";
import moment from "moment";
import uuid from "uuid/v4";

import { Grid, Cell } from "react-flexr";
import "react-flexr/styles.css";

import Checkbox from "material-ui/Checkbox";
import Dialog from "material-ui/Dialog";
import Divider from "material-ui/Divider";
import FlatButton from "material-ui/FlatButton";
import SelectField from "material-ui/SelectField";
import MenuItem from "material-ui/MenuItem";
import AutoComplete from "material-ui/AutoComplete";
import NumberInput from "material-ui-number-input";

import ClusterTotals from "./ClusterTotals";

const tagFloatingTextLabelStyle = {
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
    overflow: "hidden",
};

const generateInstanceSpec =
    ({ instanceType }, key) => <MenuItem key={key} value={instanceType} primaryText={instanceType} />;

export default class ClusterDialog extends React.Component {
    state = {
        tags: [],
        lifetimeHoursErrorText: "",
        workerCountErrorText: "",
        idleTimeoutCountErrorText: "",
        ownerErrorText: "",
        masterInstanceType: "",
        workerInstanceType: "",
        numWorkers: 1,
        lifetimeHours: 10,
        idleTimeout: 15,
    };

    getDockerImageTags = () => fetch("http://localhost:8080/api/version/1/dockerImages")
            .then(response => response.json())
            .then((dockerImages) => {
                const tags = R.map(R.prop("tag"), dockerImages);
                const getImageNumber = R.compose(parseInt, R.join(""), R.takeLastWhile(x => x !== "-"));
                const sortByImageNumberDesc = R.sortBy(R.compose(R.negate, getImageNumber));
                const sortedTags = sortByImageNumberDesc(tags);
                this.setState({ tags: sortedTags, tag: sortedTags[0] });
                return sortedTags;
            })

    launchCluster = () => {
        const {
            owner,
            lifetimeHours,
            idleTimeout,
            masterInstanceType,
            workerInstanceType,
            numWorkers,
            tag,
            isSpotCluster,
        } = this.state;

        if (!owner) {
            this.setState({ ownerErrorText: "Please enter an owner" });
            return;
        }

        const messageType = isSpotCluster ? "LaunchSpotCluster" : "LaunchCluster";
        const clusterSpec = {
            id: uuid(),
            dockerImage: {
                repo: "videoamp/spark",
                tag,
            },
            owner,
            ttl: moment.duration(lifetimeHours, "hours").toString(),
            idleTimeout: moment.duration(idleTimeout, "minutes").toString(),
            masterInstanceType,
            workerInstanceType,
            numWorkers,
        };
        const bidPrice = R.prop("hourlyPrice", R.find(R.propEq("instanceType", workerInstanceType))(
            this.props.instanceSpecs));

        this.props.socket.send(JSON.stringify({ bidPrice, clusterSpec, "$type": messageType }));
        this.props.close(owner);
    }

    componentWillMount() {
        this.getDockerImageTags();
    }

    componentWillReceiveProps() {
        const defaultInstance = R.pathOr("", ["instanceSpecs", 0, "instanceType"], this.props);
        this.setState({
            masterInstanceType: defaultInstance,
            workerInstanceType: defaultInstance,
        });
    }

    onLifetimeHoursCountError = (error) => {
        const lifetimeHoursErrorText = (error === "none") ? "" : "Please enter a valid lifetime hours amount";
        this.setState({ lifetimeHoursErrorText });
    };
    onLifetimeHoursValid = lifetimeHours => this.setState({ lifetimeHours });

    onWorkerCountError = (error) => {
        const workerCountErrorText = (error === "none") ? "" : "Please enter a valid instance count (less than 100)";
        this.setState({ workerCountErrorText });
    };
    onWorkerCountValid = numWorkers => this.setState({ numWorkers });

    onIdleTimeoutCountError = (error) => {
        const idleTimeoutCountErrorText = (error === "none") ? "" : "Please enter a valid idle timeout amount";
        this.setState({ idleTimeoutCountErrorText });
    };
    onIdleTimeoutCountValid = idleTimeout => this.setState({ idleTimeout });

    handleFieldChange = stateName =>
        (event, index, value) => this.setState(R.objOf(stateName, value))

    handleCheckboxChange = stateName =>
        (event, isInputChecked) =>
            this.setState(R.objOf(stateName, isInputChecked))

    handleOwnerChange = (owner) => {
        const ownerErrorText = owner ? "" : "Please enter an owner";
        this.setState({ ownerErrorText });
        this.setState({ owner });
    }

    render() {
        const { instanceSpecs, ownerDataSource = [], openState, close } = this.props;

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

        const fieldStyles = { width: "100%" };
        const gridStyles = { marginBottom: "0px" };

        return (
            <Dialog
                title="New Cluster"
                actions={clusterDialogActions}
                modal={false}
                open={openState}
                onRequestClose={this.props.close}
                bodyStyle={{ padding: "0px" }}
            >
                <Divider />
                <ClusterTotals
                    instanceSpecs={instanceSpecs}
                    masterInstanceType={this.state.masterInstanceType}
                    workerInstanceType={this.state.workerInstanceType}
                    numWorkers={this.state.numWorkers}
                    active={false}
                />
                <Divider />
                <div style={{ padding: "24px" }}>
                    <Grid style={gridStyles}>
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
                    <Grid style={R.merge(gridStyles, { minHeight: "100px" })}>
                        <Cell>
                            <AutoComplete
                                dataSource={ownerDataSource}
                                onNewRequest={this.handleOwnerChange}
                                onUpdateInput={this.handleOwnerChange}
                                style={fieldStyles}
                                hintText="Enter your name here"
                                errorText={this.state.ownerErrorText}
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
                    <Grid style={gridStyles}>
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
                    <Grid style={R.merge(gridStyles, { minHeight: "100px" })}>
                        <Cell>
                            <NumberInput
                                id="worker-count-amount-input"
                                floatingLabelText="Worker Count"
                                defaultValue={this.state.numWorkers}
                                min={0}
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
                    <Grid style={gridStyles}>
                        <Cell>
                            <Checkbox
                                label="Use Spot Cluster"
                                checked={this.state.isSpotCluster}
                                onCheck={this.handleCheckboxChange("isSpotCluster")}
                            />
                        </Cell>
                    </Grid>
                </div>
            </Dialog>
        );
    }
}
