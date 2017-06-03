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

import getDockerImageTags from "../api/getDockerImageTags";

const tagFloatingTextLabelStyle = {
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
    overflow: "hidden",
};

const generateInstanceSpec =
    ({ instanceType }, key) => <MenuItem key={key} value={instanceType} primaryText={instanceType} />;

export default class ClusterDialog extends React.Component {
    state = {
        owner: this.props.defaultOwner,
        tags: [],
        lifetimeHoursErrorText: "",
        workerCountErrorText: "",
        idleTimeoutCountErrorText: "",
        ownerErrorText: "",
        workerBidPriceErrorText: "",
        masterInstanceType: "",
        workerInstanceType: "",
        numWorkers: 1,
        lifetimeHours: 10,
        idleTimeout: null,
        workerBidPrice: 0,
        workerBidPriceString: "",
    };

    getBidPrice = (workerInstanceType) => {
        const getBidPrice = R.compose(
            parseFloat,
            R.prop("hourlyPrice"),
            R.find(R.propEq("instanceType", workerInstanceType))
        );
        return getBidPrice(this.props.instanceSpecs);
    }

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
            workerBidPriceErrorText,
            workerBidPrice,
        } = this.state;

        if (!owner) {
            this.setState({ ownerErrorText: "Please enter an owner" });
            return;
        }

        if (isSpotCluster && workerBidPriceErrorText !== "") {
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
            ttl: lifetimeHours ? moment.duration(lifetimeHours, "hours").toString() : null,
            idleTimeout: idleTimeout ? moment.duration(idleTimeout, "minutes").toString() : null,
            masterInstanceType,
            workerInstanceType,
            numWorkers,
        };

        this.props.socket.send(JSON.stringify({ "bidPrice": workerBidPrice, clusterSpec, "$type": messageType }));
        this.props.close(owner);
    }

    componentWillMount() {
        getDockerImageTags().then(sortedTags => this.setState({ tags: sortedTags, tag: sortedTags[0] }));
    }

    componentWillReceiveProps() {
        const defaultInstance = R.pathOr("", ["instanceSpecs", 0, "instanceType"], this.props);
        const defaultWorkerBidPrice =
            parseFloat(R.pathOr("", ["instanceSpecs", 0, "hourlyPrice"], this.props));
        this.setState({
            masterInstanceType: defaultInstance,
            workerInstanceType: defaultInstance,
            workerBidPrice: defaultWorkerBidPrice,
        });
    }

    onLifetimeHoursCountError = (error) => {
        const lifetimeHoursErrorText =
            (error === "none" || error === "clean") ? "" : "Please enter a valid lifetime hours amount";
        this.setState({ lifetimeHoursErrorText });
        if (error === "clean") {
            this.setState({ lifetimeHours: null });
        }
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

    onWorkerBidPriceChange = (event, value) => this.setState({ workerBidPriceString: value });
    onWorkerBidPriceError = (error) => {
        const workerBidPriceErrorText = (error === "none") ? "" : "Please enter a valid worker bid price";
        this.setState({ workerBidPriceErrorText });
    };
    onWorkerBidPriceValid = workerBidPrice => this.setState({
        workerBidPrice,
        workerBidPriceString: workerBidPrice.toString(),
    });

    handleFieldChange = stateName =>
        (event, index, value) => this.setState(R.objOf(stateName, value))

    handleCheckboxChange = stateName =>
        (event, isInputChecked) =>
            this.setState(R.objOf(stateName, isInputChecked))

    handleOwnerChange = (owner) => {
        const ownerErrorText = owner ? "" : "Please enter an owner";
        this.setState({ ownerErrorText, owner });
    }

    handleWorkerInstanceTypeChange = stateName =>
        (event, index, value) => {
            const bidPrice = this.getBidPrice(value);
            this.setState({
                workerBidPriceString: bidPrice.toString(),
                [stateName]: value,
            });
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
        const workerBidPriceStyles = {
            visibility: this.state.isSpotCluster ? "visible" : "hidden",
        };

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
                                searchText={this.state.owner}
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
                                onChange={this.handleWorkerInstanceTypeChange("workerInstanceType")}
                                floatingLabelText="Worker Type"
                                fullWidth={true}>
                                {this.props.instanceSpecs.map(generateInstanceSpec)}
                            </SelectField>
                        </Cell>
                    </Grid>
                    <Grid style={gridStyles}>
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
                                style={R.merge(fieldStyles, { display: "none" })}
                            />
                        </Cell>
                    </Grid>
                    <Grid style={R.merge(gridStyles, { minHeight: "100px" })}>
                        <Cell style={{ alignSelf: "center" }}>
                            <Checkbox
                                label="Use Spot Cluster"
                                defaultChecked={this.state.isSpotCluster}
                                onCheck={this.handleCheckboxChange("isSpotCluster")}
                            />
                        </Cell>
                        <Cell>
                            <NumberInput
                                id="worker-bid-price-input"
                                floatingLabelText="Spot Worker Bid Price"
                                value={this.state.workerBidPriceString}
                                min={0}
                                max={50}
                                strategy="allow"
                                errorText={this.state.workerBidPriceErrorText}
                                onChange={this.onWorkerBidPriceChange}
                                onError={this.onWorkerBidPriceError}
                                onValid={this.onWorkerBidPriceValid}
                                style={R.merge(fieldStyles, workerBidPriceStyles)}
                            />
                        </Cell>
                    </Grid>
                </div>
            </Dialog>
        );
    }
}
