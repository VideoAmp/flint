/* eslint-disable react/no-multi-comp */
// TODO: Make methods part of class
import React from "react";
import PropTypes from "prop-types";
import ReactTimeAgo from "react-time-ago";
import R from "ramda";
import moment from "moment";

import { Card, CardActions, CardHeader, CardText } from "material-ui/Card";
import CircularProgress from "material-ui/CircularProgress";
import Divider from "material-ui/Divider";
import IconButton from "material-ui/IconButton";
import SelectField from "material-ui/SelectField";
import MenuItem from "material-ui/MenuItem";
import Trash from "material-ui/svg-icons/action/delete";
import Lock from "material-ui/svg-icons/action/lock";
import LockOpen from "material-ui/svg-icons/action/lock-open";
import Add from "material-ui/svg-icons/content/add";

import javascriptTimeAgo from "javascript-time-ago";

import ClusterTotals from "./ClusterTotals";
import ClusterInstanceDialog from "./ClusterInstanceDialog";
import ClusterTerminateDialog from "./ClusterTerminateDialog";
import Instance from "./Instance";
import dockerImageType from "../types/dockerImage";
import subnetType from "../types/subnet";

javascriptTimeAgo.locale(require("javascript-time-ago/locales/en"));
require("javascript-time-ago/intl-messageformat-global");
require("intl-messageformat/dist/locale-data/en");

const getInstanceMapper = (socket, master, isSpotCluster) =>
    instance => (
        <div key={instance.id}>
            <Instance
                data={instance}
                master={master}
                socket={socket}
                isSpotCluster={isSpotCluster}
            />
            <Divider />
        </div>
    );

const getActiveWorkerCount = workers =>
    workers.filter(
        worker => worker.containerState === "ContainerRunning",
    ).length;

export default class Cluster extends React.Component {
    static propTypes = {
        data: PropTypes.shape({
            dockerImage: dockerImageType,
            id: PropTypes.string.isRequired,
            idleTimeout: PropTypes.string.isRequired,
            launchedAt: PropTypes.string.isRequired,
            master: PropTypes.shape(), // TODO: Improve validation here
            name: PropTypes.string.isRequired,
            subnet: subnetType,
            ttl: PropTypes.string.isRequired,
            workerInstanceType: PropTypes.string.isRequired,
            workers: PropTypes.arrayOf(PropTypes.object), // TODO: Improve validation here
        }),
        dockerImages: PropTypes.arrayOf(dockerImageType),
        handleClusterUpdate: PropTypes.func.isRequired,
        instanceSpecs: PropTypes.arrayOf(
            PropTypes.shape({
                cores: PropTypes.number.isRequired,
                hourlyPrice: PropTypes.number.isRequired,
                instanceType: PropTypes.string.isRequired,
                isSpotEligible: PropTypes.bool.isRequired,
                memory: PropTypes.number.isRequired,
                storage: PropTypes.shape({
                    devices: PropTypes.number.isRequired,
                    storagePerDevice: PropTypes.number.isRequired,
                }),
            }),
        ),
        socket: PropTypes.shape().isRequired,
    };

    state = {
        clusterInstanceDialogOpen: false,
        clusterTerminateDialogOpen: false,
        imageChangeLocked: true,
    };

    handleClusterInstanceDialogOpen = () => {
        this.setState({ clusterInstanceDialogOpen: true });
    };

    handleClusterInstanceDialogClose = () => {
        this.setState({ clusterInstanceDialogOpen: false });
    };

    handleClusterTerminateDialogOpen = () => {
        this.setState({ clusterTerminateDialogOpen: true });
    };

    handleClusterTerminateDialogClose = () => {
        this.setState({ clusterTerminateDialogOpen: false });
    };

    handleImageChangeLockChange = () => {
        this.setState(prevState => ({ imageChangeLocked: !prevState.imageChangeLocked }));
    };

    handleImageChange = (event, index) => {
        const { data: cluster, dockerImages, handleClusterUpdate } = this.props;
        const image = dockerImages[index];
        const currentImage = cluster.dockerImage;

        if (R.equals(currentImage, image)) return;

        const message = {
            clusterId: cluster.id,
            dockerImage: image,
        };
        const changeImageMessage = R.merge(message, { "$type": "ChangeDockerImage" });
        this.props.socket.send(JSON.stringify(changeImageMessage));
        handleClusterUpdate({ imageChangeInProgress: true });
    };

    render() {
        const { imageChangeLocked } = this.state;
        const { socket, instanceSpecs, data: cluster, dockerImages } = this.props;
        const { name: clusterName, dockerImage, master, workers = [], workerInstanceType,
            workerBidPrice, ttl, idleTimeout, subnet, placementGroup, launchedAt } = cluster;
        const { id: subnetId, availabilityZone } = subnet;
        const isSpotCluster = !R.isNil(workerBidPrice);
        // TODO: return short-form image tag
        const clusterTitle =
            `${clusterName} ${dockerImage.tag.split("-")[0]}`;

        const getImageLockIcon = () => {
            if (cluster.imageChangeInProgress) {
                return <CircularProgress size={20} />;
            }

            const isLocked = imageChangeLocked || master.containerState !== "ContainerRunning";
            return isLocked ? <Lock /> : <LockOpen />;
        };

        const imageChangeForbidden =
            cluster.imageChangeInProgress || master.containerState !== "ContainerRunning";

        // Hide the trash button if the cluster is already on its way out to avoid requesting the
        // termination of a cluster that's already terminating
        const clusterTerminationButtonDisplay =
            master.state === "Terminating" ||
            master.state === "Terminated" ||
            master.containerState === "ContainerStopping" ||
            master.containerState === "ContainerStopped" ? "none" : "inline-block";

        return (
            <div>
                <Card>
                    <CardHeader
                        title={clusterTitle}
                        actAsExpander
                        titleStyle={{ "fontSize": "24px" }}
                        textStyle={{ paddingRight: "0px" }}
                        style={{ paddingBottom: "0px", paddingRight: "0px", whiteSpace: "normal" }}
                    >
                        { // Stop propagation of onTouchTap events to prevent clicks on the buttons
                            // in the card header from expanding the card text
                        }
                        <div style={{ float: "right", marginTop: "-11px" }} onTouchTap={e => e.stopPropagation()}>
                            <IconButton
                                style={{ display: clusterTerminationButtonDisplay }}
                                onClick={this.handleClusterTerminateDialogOpen}
                            >
                                <Trash />
                            </IconButton>
                            <IconButton
                                style={{ marginRight: "4px" }}
                                disabled={master.containerState !== "ContainerRunning"}
                                onClick={this.handleClusterInstanceDialogOpen}
                            >
                                <Add />
                            </IconButton>
                        </div>
                    </CardHeader>
                    <CardText style={{ padding: "0px 16px" }}>
                        <div style={{ display: "flex", alignItems: "center" }}>
                            <IconButton
                                iconStyle={{ width: "20px", height: "20px" }}
                                style={{ width: "20px", height: "20px", padding: "0px" }}
                                onClick={this.handleImageChangeLockChange}
                                disabled={imageChangeForbidden}
                            >
                                { getImageLockIcon() }
                            </IconButton>
                            <SelectField
                                style={{ width: "auth", paddingLeft: "10px" }}
                                autoWidth
                                underlineDisabledStyle={{ display: "none" }}
                                labelStyle={{ fontSize: "14px" }}
                                disabled={imageChangeLocked || imageChangeForbidden}
                                value={dockerImage.tag}
                                onChange={this.handleImageChange}
                            >
                                {
                                    dockerImages.map(image =>
                                        <MenuItem key={image.tag} value={image.tag} primaryText={image.tag} />,
                                    )
                                }
                            </SelectField>
                        </div>
                    </CardText>
                    <CardText style={{ fontSize: "14px", paddingTop: "0px" }} expandable>
                        <div>
                            Launched {" "}
                            <ReactTimeAgo locale="en-US">
                                {new Date(launchedAt)}
                            </ReactTimeAgo>
                        </div>
                        <div>{ ttl ? (
                            <div>
                                Expires {" "}
                                <ReactTimeAgo locale="en-US">
                                    {new Date(moment(launchedAt).add(moment.duration(ttl)))}
                                </ReactTimeAgo>
                            </div>
                        ) : "" }
                        </div>
                        <div>{ idleTimeout ? (
                            <div>
                                Idle timeout is {moment.duration(idleTimeout).asMinutes()}m
                            </div>
                        ) : "" }
                        </div>
                        <div>Launched in { subnetId } in { availabilityZone }</div>
                        <div>{ placementGroup ? <div>Placement group is { placementGroup }</div> : "" }</div>
                        <div>{ workerBidPrice ? <div>Worker bid price is ${ workerBidPrice }/hr</div> : "" }</div>
                    </CardText>
                    <CardText style={{ padding: "0px" }}>
                        <Divider />
                        { getInstanceMapper(socket, true, isSpotCluster)(master) }
                        { workers.map(getInstanceMapper(socket, false, isSpotCluster)) }
                    </CardText>
                    <CardActions style={{ padding: "0px" }}>
                        <ClusterTotals
                            instanceSpecs={instanceSpecs}
                            master={master}
                            masterInstanceType={master.instanceType}
                            workers={workers}
                            workerInstanceType={workerInstanceType}
                            numMasters={master.state !== "Terminated" ? 1 : 0}
                            numWorkers={getActiveWorkerCount(workers)}
                            isSpotCluster={isSpotCluster}
                        />
                    </CardActions>
                </Card>
                <ClusterInstanceDialog
                    openState={this.state.clusterInstanceDialogOpen}
                    close={this.handleClusterInstanceDialogClose}
                    socket={socket}
                    cluster={cluster}
                />
                <ClusterTerminateDialog
                    openState={this.state.clusterTerminateDialogOpen}
                    close={this.handleClusterTerminateDialogClose}
                    socket={socket}
                    cluster={cluster}
                />
            </div>
        );
    }
}
