import React from "react";
import R from "ramda";
import Moment from "moment";
import { extendMoment } from "moment-range";

import { Toolbar, ToolbarGroup } from "material-ui/Toolbar";

const moment = extendMoment(Moment);

export default class ClusterTotals extends React.Component {
    state = {
        numberOfCores: 1,
        ramAmount: 1,
        storageAmount: 1,
        totalCostPerHour: NaN,
        totalCost: NaN,
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

    calculateStorageAmount = ({ storage }, numWorkers) => {
        const { devices, storagePerDevice } = storage;

        const totalStorageBytes = (devices * storagePerDevice * numWorkers);
        return totalStorageBytes / (2 ** 30);
    }

    calculateTotalCostPerHour = (masterInstanceTypeInfo, workerInstanceTypeInfo, numMasters, numWorkers) => {
        const { hourlyPrice: masterInstanceTypeCostPerHour } = masterInstanceTypeInfo;
        const { hourlyPrice: workerInstanceTypeCostPerHour } = workerInstanceTypeInfo;

        const totalCost =
            (masterInstanceTypeCostPerHour * numMasters) + (workerInstanceTypeCostPerHour * numWorkers);

        return totalCost.toFixed(2);
    }

    calculateTotalCost = (masterInstanceTypeInfo, master, workerInstanceTypeInfo, workers) => {
        if (this.props.isSpotCluster) {
            return NaN;
        }

        const calculateInstanceTotalCost = (instance, instanceTypeInfo) => {
            const { terminatedAt } = instance;
            const { hourlyPrice } = instanceTypeInfo;
            const endTime = terminatedAt ? moment(terminatedAt) : moment();
            const runInterval = moment.range(moment(instance.launchedAt), endTime);
            const billableHours = runInterval.diff("hours") + 1;
            return billableHours * hourlyPrice;
        };

        let totalMasterCost = 0.0;

        if (master) {
            totalMasterCost = calculateInstanceTotalCost(master, masterInstanceTypeInfo);
        }

        let totalWorkerCost = 0.0;

        if (workers) {
            totalWorkerCost = R.sum(R.map(x => calculateInstanceTotalCost(x, workerInstanceTypeInfo), workers));
        }

        const totalCost = totalMasterCost + totalWorkerCost;

        return totalCost.toFixed(2);
    }

    updateClusterTotals = ({
            instanceSpecs, master, masterInstanceType, workers, workerInstanceType, numMasters, numWorkers }) => {
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
                numMasters,
                numWorkers,
            ),
            totalCost: this.calculateTotalCost(
                masterInstanceTypeInfo,
                master,
                workerInstanceTypeInfo,
                workers
            ),
        });
    }

    componentDidMount() {
        this.updateClusterTotals(this.props);
    }

    componentWillReceiveProps(nextProps) {
        const { instanceSpecs, masterInstanceType, workers, workerInstanceType, numMasters, numWorkers } = this.props;
        const masterTerminatedAt = R.pathOr(null, ["master", "terminatedAt"], this.props);
        const equalsOldProps = R.where({
            instanceSpecs: R.compose(R.isEmpty, R.differenceWith(R.eqBy(R.prop("instanceType")), instanceSpecs)),
            master: R.pathEq(["terminatedAt"], masterTerminatedAt),
            masterInstanceType: R.equals(masterInstanceType),
            workers: R.eqBy(R.map(worker => worker.terminatedAt), workers),
            workerInstanceType: R.equals(workerInstanceType),
            numMasters: R.equals(numMasters),
            numWorkers: R.equals(numWorkers),
        });

        if (!equalsOldProps(nextProps)) {
            this.updateClusterTotals(nextProps);
        }
    }

    render() {
        const { isSpotCluster } = this.props;
        const { numberOfCores, ramAmount, storageAmount, totalCostPerHour, totalCost } = this.state;
        return (
            <Toolbar style={{ backgroundColor: "#F5F5F5" }}>
                <ToolbarGroup style={{ paddingLeft: "24px" }} firstChild={true}>
                    <p>
                        {numberOfCores} cores, {ramAmount} GiB RAM, {storageAmount} GiB Scratch
                        {isSpotCluster ? "" : `, $${totalCostPerHour}/hour` }
                        {totalCost ? `, $${totalCost}` : "" }
                    </p>
                </ToolbarGroup>
            </Toolbar>
        );
    }
}
