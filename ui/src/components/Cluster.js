import React from "react";
import R from "ramda";

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

import ClusterTotals from "./ClusterTotals";
import ClusterInstanceDialog from "./ClusterInstanceDialog";
import ClusterTerminateDialog from "./ClusterTerminateDialog";
import Instance from "./Instance";

import getDockerImageTags from "../api/getDockerImageTags";

const getInstanceMapper = (socket, master, isSpotCluster) =>
    instance => (
        <div key={instance.id}>
            <Instance
                data={instance}
                master={master}
                socket={socket}
                isSpotCluster={isSpotCluster}/>
            <Divider />
        </div>
    );

const getActiveWorkerCount = workers =>
  workers.filter(
    worker => worker.containerState === "ContainerRunning"
  ).length;

export default class Cluster extends React.Component {
    state = {
        tags: [],
        clusterInstanceDialogOpen: false,
        clusterTerminateDialogOpen: false,
        imageChangeLocked: true,
    };

    componentWillMount() {
        getDockerImageTags().then(sortedTags => this.setState({ tags: sortedTags, tag: sortedTags[0] }));
    }

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

    handleImageChange = (event, index, imageTag) => {
        const cluster = this.props.data;
        const currentImageTag = cluster.dockerImage.tag;

        if (currentImageTag === imageTag) return;

        const message = {
            clusterId: cluster.id,
            dockerImage: {
                repo: "videoamp/spark",
                tag: imageTag,
            },
        };
        this.props.socket.send(JSON.stringify(R.merge(message, { "$type": "ChangeDockerImage" })));
        cluster.imageChangeInProgress = true;
        // Update the UI to reflect the new value of cluster.imageChangeInProgress
        this.forceUpdate();
    };

    render() {
        const { socket, instanceSpecs, data: cluster } = this.props;
        const { owner, dockerImage, master, workers = [], workerInstanceType, workerBidPrice } = cluster;
        const isSpotCluster = !R.isNil(workerBidPrice);
        // TODO: return short-form image tag
        const clusterTitle =
            `${owner} ${dockerImage.tag.split("-")[0]}`;

        const imageLockIcon = () => {
            if (cluster.imageChangeInProgress) {
                return <CircularProgress size={20} />;
            }

            return this.state.imageChangeLocked ||
                cluster.master.containerState !== "ContainerRunning" ?
                <Lock /> : <LockOpen />;
        };

        return (
            <div>
                <Card>
                    <CardHeader
                        title={clusterTitle}
                        titleStyle={{ "fontSize": "24px" }}
                        textStyle={{ paddingRight: "0px" }}
                        style={{ paddingBottom: "0px", paddingRight: "0px" }}>
                        <div style={{ float: "right", marginTop: "-11px" }}>
                            <IconButton onTouchTap={this.handleClusterTerminateDialogOpen}>
                                <Trash />
                            </IconButton>
                            <IconButton
                                style={{ marginRight: "4px" }}
                                onTouchTap={this.handleClusterInstanceDialogOpen}>
                                <Add />
                            </IconButton>
                        </div>
                        <div>
                          <IconButton
                              iconStyle={{ width: "20px", height: "20px" }}
                              style={{ width: "20px", height: "20px", padding: "0px" }}
                              onTouchTap={this.handleImageChangeLockChange}
                              disabled={cluster.imageChangeInProgress ||
                                cluster.master.containerState !== "ContainerRunning"}>
                              { imageLockIcon() }
                          </IconButton>
                          <SelectField
                              style={{ width: "auto", paddingLeft: "10px" }}
                              underlineDisabledStyle={{ display: "none" }}
                              labelStyle={{ fontSize: "14px" }}
                              disabled={
                                this.state.imageChangeLocked ||
                                cluster.imageChangeInProgress ||
                                cluster.master.containerState !== "ContainerRunning"
                              }
                              value={dockerImage.tag}
                              onChange={this.handleImageChange}>
                              {
                                  this.state.tags.map((tag, key) =>
                                      <MenuItem key={key} value={tag} primaryText={tag} />
                                  )
                              }
                          </SelectField>
                        </div>
                    </CardHeader>
                    <CardText style={{ padding: "0px" }}>
                        <Divider />
                        { getInstanceMapper(socket, true, isSpotCluster)(master) }
                        { workers.map(getInstanceMapper(socket, false, isSpotCluster)) }
                    </CardText>
                    <CardActions style={{ padding: "0px" }}>
                        <ClusterTotals
                            instanceSpecs={instanceSpecs}
                            masterInstanceType={master.instanceType}
                            workerInstanceType={workerInstanceType}
                            numWorkers={getActiveWorkerCount(workers)}
                            active={true}
                        />
                    </CardActions>
                </Card>
                <ClusterInstanceDialog
                    openState={this.state.clusterInstanceDialogOpen}
                    close={this.handleClusterInstanceDialogClose}
                    socket={socket}
                    cluster={cluster}/>
                <ClusterTerminateDialog
                    openState={this.state.clusterTerminateDialogOpen}
                    close={this.handleClusterTerminateDialogClose}
                    socket={socket}
                    cluster={cluster}/>
            </div>
        );
    }
}
