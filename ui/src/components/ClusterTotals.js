import React from 'react';
import R from 'ramda';

import {Toolbar, ToolbarGroup} from 'material-ui/Toolbar';

export default class ClusterTotals extends React.Component {
    state = {
        numberOfCores: 1,
        ramAmount: 1,
        totalCostPerHour: 0.03
    };

    getInstanceInfo = (instanceType) => {
        return R.find(R.propEq('instanceType', instanceType), this.props.instanceSpecs);
    }

    calculateNumberOfCores = (workerInstanceTypeInfo, numWorkers) => {
        const {cores: workerInstanceTypeCores} = workerInstanceTypeInfo;
        return workerInstanceTypeCores * numWorkers;
    }

    calculateRamAmount = (workerInstanceTypeInfo, numWorkers) => {
        const {memory: workerInstanceTypeRam} = workerInstanceTypeInfo;

        const totalRamBytes = (workerInstanceTypeRam * numWorkers)
        return totalRamBytes / (2 ** 30);
    }

    calculateTotalCostPerHour = (masterInstanceTypeInfo, workerInstanceTypeInfo, numWorkers) => {
        const {hourlyPrice: masterInstanceTypeCostPerHour} = masterInstanceTypeInfo;
        const {hourlyPrice: workerInstanceTypeCostPerHour} = workerInstanceTypeInfo;

        const totalCost =
            masterInstanceTypeCostPerHour + (workerInstanceTypeCostPerHour * numWorkers);

        return totalCost.toFixed(2);

    }

    componentWillReceiveProps(nextProps) {
        const { masterInstanceType, workerInstanceType, numWorkers } = nextProps;
        const masterInstanceTypeInfo = this.getInstanceInfo(masterInstanceType);
        const workerInstanceTypeInfo = this.getInstanceInfo(workerInstanceType);

        this.setState({
            numberOfCores: this.calculateNumberOfCores(
                workerInstanceTypeInfo,
                numWorkers
            ),
            ramAmount: this.calculateRamAmount(
                workerInstanceTypeInfo,
                numWorkers
            ),
            totalCostPerHour: this.calculateTotalCostPerHour(
                masterInstanceTypeInfo,
                workerInstanceTypeInfo,
                numWorkers,
            )
        });
    }

    render() {
        return (
            <Toolbar>
                <ToolbarGroup firstChild={true}>
                    <h5>{this.state.numberOfCores} cores, {this.state.ramAmount}GiB RAM</h5>
                </ToolbarGroup>
                <ToolbarGroup lastChild={true}>
                    <h5>${this.state.totalCostPerHour}/hour</h5>
                </ToolbarGroup>
            </Toolbar>
        )
    }
}
