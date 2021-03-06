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

const generateInstanceSpecMenuItem =
    ({ instanceType, hourlyPrice }, key) =>
        <MenuItem key={key} value={instanceType} primaryText={`${instanceType} ($${hourlyPrice}/hr)`} />;

const generatePlacementGroupMenuItem =
    (placementGroup, key) => <MenuItem key={key} value={placementGroup} primaryText={placementGroup} />;

const generateSubnetMenuItem =
    ({ id, availabilityZone }, key) => <MenuItem key={key} value={id} primaryText={`${id} (${availabilityZone})`} />;

export default class ClusterDialog extends React.Component {
    state = {
        clusterName: this.props.defaultClusterName,
        dockerImage: null,
        subnetId: "",
        lifetimeHoursErrorText: "",
        workerCountErrorText: "",
        idleTimeoutCountErrorText: "",
        clusterNameErrorText: "",
        workerBidPriceRatioErrorText: "",
        workerSpecs: [],
        masterInstanceType: "",
        workerInstanceType: "",
        spotPrice: null,
        numWorkers: 1,
        lifetimeHours: 10,
        idleTimeout: 60,
        placementGroup: null,
        workerBidPriceRatioString: "",
    };

    getHourlyPrice = (workerInstanceType) => {
        const getHourlyPrice = R.compose(
            parseFloat,
            R.prop("hourlyPrice"),
            R.find(R.propEq("instanceType", workerInstanceType))
        );
        return getHourlyPrice(this.props.instanceSpecs);
    }

    getSpotPrice = (subnetId, instanceType) => {
        this.setState({ spotPrice: "(fetching)" });
        fetch(`${this.props.serverUrl}/spotPrices?subnetId=${subnetId}&instanceTypes=${instanceType}`)
            .then(response => response.json())
            .then(([{ price: spotPrice = 0.0 }]) => this.setState({ spotPrice }));
    }

    launchCluster = () => {
        const {
            clusterName,
            lifetimeHours,
            idleTimeout,
            masterInstanceType,
            workerInstanceType,
            numWorkers,
            subnetId,
            placementGroup,
            dockerImage,
            isSpotCluster,
            workerBidPriceRatioErrorText,
            workerBidPriceRatioString,
        } = this.state;

        if (!clusterName) {
            this.setState({ clusterNameErrorText: "Please enter a cluster name" });
            return;
        }

        if (isSpotCluster && workerBidPriceRatioErrorText !== "") {
            return;
        }

        const messageType = isSpotCluster ? "LaunchSpotCluster" : "LaunchCluster";
        const clusterSpec = {
            id: uuid(),
            name: clusterName,
            dockerImage,
            ttl: lifetimeHours ? moment.duration(lifetimeHours, "hours").toString() : null,
            idleTimeout: idleTimeout ? moment.duration(idleTimeout, "minutes").toString() : null,
            masterInstanceType,
            workerInstanceType,
            numWorkers,
            subnetId,
            placementGroup,
        };

        const workerHourlyPrice = this.getHourlyPrice(workerInstanceType);
        const workerBidPrice = parseFloat(workerBidPriceRatioString) * workerHourlyPrice;
        const launchMessage = { "bidPrice": workerBidPrice, clusterSpec, "$type": messageType };
        this.props.socket.send(JSON.stringify(launchMessage));
        this.props.close(clusterName);
    }

    componentWillReceiveProps(nextProps) {
        const propsToState = (props) => {
            const dockerImage = R.pathOr("", ["dockerImages", 0], props);
            const instanceSpecs = props.instanceSpecs ? props.instanceSpecs : [];
            const workerSpecs = R.filter(spec => spec.isSpotEligible, instanceSpecs);
            const masterInstanceType = R.pathOr("", [0, "instanceType"], instanceSpecs);
            const workerInstanceType = R.pathOr("", [0, "instanceType"], workerSpecs);
            const subnetId = R.pathOr("", ["subnets", 0, "id"], props);
            const workerBidPriceRatioString =
                this.state.workerBidPriceRatioString !== "" ? this.state.workerBidPriceRatioString : "1.1";
            return {
                dockerImage,
                instanceSpecs,
                workerSpecs,
                masterInstanceType,
                workerInstanceType,
                subnetId,
                workerBidPriceRatioString,
            };
        };

        const currentState = propsToState(this.props);
        const newState = propsToState(nextProps);

        if (!R.equals(currentState, newState)) {
            this.setState(newState);
        }
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
        const idleTimeoutCountErrorText =
            (error === "none" || error === "clean") ? "" : "Please enter a valid idle timeout amount";
        this.setState({ idleTimeoutCountErrorText });
        if (error === "clean") {
            this.setState({ idleTimeout: null });
        }
    };
    onIdleTimeoutCountValid = idleTimeout => this.setState({ idleTimeout });

    onWorkerBidPriceChange = (event, workerBidPriceRatioString) => this.setState({ workerBidPriceRatioString });
    onWorkerBidPriceError = (error) => {
        const workerBidPriceRatioErrorText = (error === "none") ? "" : "Please enter a valid worker bid price ratio";
        this.setState({ workerBidPriceRatioErrorText });
    };

    handleFieldChange = stateName =>
        (event, index, value) => this.setState(R.objOf(stateName, value))

    handleWorkerInstanceTypeChange = (event, index, workerInstanceType) => {
        const { isSpotCluster, subnetId } = this.state;
        this.setState({ workerInstanceType });
        this.refreshSpotPrice(isSpotCluster, workerInstanceType, subnetId);
    }

    handleSubnetIdChange = (event, index, subnetId) => {
        const { workerInstanceType, isSpotCluster } = this.state;
        this.setState({ subnetId });
        this.refreshSpotPrice(isSpotCluster, workerInstanceType, subnetId);
    }

    handleSpotClusterCheckboxChange = (event, isSpotCluster) => {
        const { workerInstanceType, subnetId } = this.state;
        this.setState({ isSpotCluster });
        this.refreshSpotPrice(isSpotCluster, workerInstanceType, subnetId);
    }

    refreshSpotPrice = (isInputChecked, workerInstanceType, subnetId) => {
        if (isInputChecked && workerInstanceType !== "" && subnetId !== "") {
            this.getSpotPrice(subnetId, workerInstanceType);
        }
    }

    handleClusterNameChange = (clusterName) => {
        const clusterNameErrorText = clusterName ? "" : "Please enter a cluster name";
        this.setState({ clusterNameErrorText, clusterName });
    }

    render() {
        const { instanceSpecs, clusterNameDataSource = [], subnets, openState, close, dockerImages } = this.props;
        const { workerSpecs } = this.state;

        const clusterDialogActions = [
            <FlatButton
                label="Cancel"
                onClick={close}
            />,
            <FlatButton
                label="Launch"
                primary={true}
                onClick={this.launchCluster}
            />,
        ];

        const fieldStyles = { width: "100%" };
        const gridStyles = { marginBottom: "0px" };
        const workerBidPriceRatioStyles = {
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
                    numMasters={1}
                    numWorkers={this.state.numWorkers}
                    isSpotCluster={this.state.isSpotCluster}
                />
                <Divider />
                <div style={{ padding: "24px" }}>
                    <Grid style={gridStyles}>
                        <Cell>
                            <SelectField
                                labelStyle={tagFloatingTextLabelStyle}
                                autoWidth={true}
                                fullWidth={true}
                                value={this.state.dockerImage}
                                onChange={this.handleFieldChange("dockerImage")}
                                floatingLabelText="Build">
                                {
                                    dockerImages.map((image, key) =>
                                        <MenuItem key={key} value={image} primaryText={image.tag} />
                                    )
                                }
                            </SelectField>
                        </Cell>
                    </Grid>
                    <Grid style={R.merge(gridStyles, { minHeight: "100px" })}>
                        <Cell>
                            <AutoComplete
                                dataSource={clusterNameDataSource}
                                searchText={this.state.clusterName}
                                onNewRequest={this.handleClusterNameChange}
                                onUpdateInput={this.handleClusterNameChange}
                                style={fieldStyles}
                                hintText="Enter your cluster name here"
                                errorText={this.state.clusterNameErrorText}
                                floatingLabelText="Cluster Name"
                            />
                        </Cell>
                        <Cell>
                            <NumberInput
                                id="lifetime-hours-amount-input"
                                floatingLabelText="Lifetime Hours"
                                defaultValue={this.state.lifetimeHours}
                                min={1}
                                max={24}
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
                                {instanceSpecs.map(generateInstanceSpecMenuItem)}
                            </SelectField>
                        </Cell>
                        <Cell>
                            <SelectField
                                value={this.state.workerInstanceType}
                                onChange={this.handleWorkerInstanceTypeChange}
                                floatingLabelText="Worker Type"
                                fullWidth={true}>
                                {workerSpecs.map(generateInstanceSpecMenuItem)}
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
                                id="idle-timeout-amount-input"
                                floatingLabelText="Idle Timeout (mins)"
                                defaultValue={this.state.idleTimeout}
                                min={1}
                                max={180}
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
                            <SelectField
                                value={this.state.subnetId}
                                onChange={this.handleSubnetIdChange}
                                floatingLabelText="Subnet"
                                fullWidth={true}>
                                {subnets.map(generateSubnetMenuItem)}
                            </SelectField>
                        </Cell>
                        <Cell>
                            <SelectField
                                value={this.state.placementGroup}
                                onChange={this.handleFieldChange("placementGroup")}
                                floatingLabelText="Placement Group"
                                fullWidth={true}>
                                {this.props.placementGroups.map(generatePlacementGroupMenuItem)}
                            </SelectField>
                        </Cell>
                    </Grid>
                    <Grid style={R.merge(gridStyles, { minHeight: "100px" })}>
                        <Cell align="center">
                            <Checkbox
                                label="Use Spot Cluster"
                                defaultChecked={this.state.isSpotCluster}
                                onCheck={this.handleSpotClusterCheckboxChange}
                            />
                        </Cell>
                        <Cell style={workerBidPriceRatioStyles}>
                            <NumberInput
                                id="worker-bid-price-input"
                                floatingLabelText="Spot Worker Bid Price Ratio"
                                value={this.state.workerBidPriceRatioString}
                                min={0}
                                max={10}
                                strategy="allow"
                                errorText={this.state.workerBidPriceRatioErrorText}
                                onChange={this.onWorkerBidPriceChange}
                                onError={this.onWorkerBidPriceError}
                                style={R.merge(fieldStyles, workerBidPriceRatioStyles)}
                            />
                            Current spot price: {this.state.spotPrice}
                        </Cell>
                    </Grid>
                </div>
            </Dialog>
        );
    }
}
