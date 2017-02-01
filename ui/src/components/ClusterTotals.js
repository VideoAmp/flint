import React from "react";
import R from "ramda";

import { Toolbar, ToolbarGroup } from "material-ui/Toolbar";

export default class ClusterTotals extends React.Component {
    state = {
        numberOfCores: 1,
        ramAmount: 1,
        storageAmount: 1,
        totalCostPerHour: 0.03,
    };

    getInstanceInfo = (instanceSpecs, instanceType) =>
        R.find(R.propEq("instanceType", instanceType), instanceSpecs)

    calculateNumberOfCores = (workerInstanceTypeInfo, numWorkers) => {
        const { cores: workerInstanceTypeCores } = workerInstanceTypeInfo;
        return workerInstanceTypeCores * numWorkers;
    }

    calculateRamAmount = (workerInstanceTypeInfo, numWorkers) => {
        const { memory: workerInstanceTypeRam } = workerInstanceTypeInfo;

        const totalRamBytes = (workerInstanceTypeRam * numWorkers);
        return totalRamBytes / (2 ** 30);
    }

    calculateStorageAmount = (workerInstanceTypeInfo, numWorkers) => {
        const { devices, storagePerDevice } = workerInstanceTypeInfo.storage;

        const totalStorageBytes = (devices * storagePerDevice * numWorkers);
        return totalStorageBytes / (2 ** 30);
    }

    calculateTotalCostPerHour = (masterInstanceTypeInfo, workerInstanceTypeInfo, numWorkers) => {
        const { hourlyPrice: masterInstanceTypeCostPerHour } = masterInstanceTypeInfo;
        const { hourlyPrice: workerInstanceTypeCostPerHour } = workerInstanceTypeInfo;

        const totalCost =
            masterInstanceTypeCostPerHour + (workerInstanceTypeCostPerHour * numWorkers);

        return totalCost.toFixed(2);
    }

    updateClusterTotals = ({ instanceSpecs, masterInstanceType, workerInstanceType, numWorkers }) => {
        const masterInstanceTypeInfo = this.getInstanceInfo(instanceSpecs, masterInstanceType);
        const workerInstanceTypeInfo = this.getInstanceInfo(instanceSpecs, workerInstanceType);

        this.setState({
            numberOfCores: this.calculateNumberOfCores(
                workerInstanceTypeInfo,
                numWorkers
            ),
            ramAmount: this.calculateRamAmount(
                workerInstanceTypeInfo,
                numWorkers
            ),
            storageAmount: this.calculateStorageAmount(
                workerInstanceTypeInfo,
                numWorkers
            ),
            totalCostPerHour: this.calculateTotalCostPerHour(
                masterInstanceTypeInfo,
                workerInstanceTypeInfo,
                numWorkers,
            ),
        });
    }

    componentDidMount() {
        this.updateClusterTotals(this.props);
    }

    componentWillReceiveProps(nextProps) {
        const { instanceSpecs, masterInstanceType, workerInstanceType, numWorkers } = this.props;
        const equalsOldProps = R.where({
            instanceSpecs: R.compose(R.isEmpty, R.differenceWith(R.eqBy(R.prop("instanceType")), instanceSpecs)),
            masterInstanceType: R.equals(masterInstanceType),
            workerInstanceType: R.equals(workerInstanceType),
            numWorkers: R.equals(numWorkers),
        });

        if (!equalsOldProps(nextProps)) {
            this.updateClusterTotals(nextProps);
        }
    }

    render() {
        const { active } =  this.props;
        const { numberOfCores, ramAmount, storageAmount, totalCostPerHour } = this.state;
        return (
            <Toolbar style={{ backgroundColor: "#F5F5F5" }}>
                <ToolbarGroup style={{ paddingLeft: "24px" }} firstChild={true}>
                    <p>
                        {numberOfCores} cores, {ramAmount} GiB RAM, {storageAmount} GiB Scratch
                        {active ? `, $${totalCostPerHour}/hour` : "" }
                    </p>
                </ToolbarGroup>
                <ToolbarGroup style={{ paddingRight: "24px" }} lastChild={true}>
                    <p>
                        { active ? "??? hours remaining" : `$${totalCostPerHour}/hour` }
                    </p>
                </ToolbarGroup>
            </Toolbar>
        );
    }
}
