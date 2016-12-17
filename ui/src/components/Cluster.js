import React from 'react';

import {Card, CardActions, CardHeader, CardText} from 'material-ui/Card';
import Trash from 'material-ui/svg-icons/action/delete';
import Add from 'material-ui/svg-icons/content/add';

import ClusterInstanceDialog from './ClusterInstanceDialog'
import Instance from './Instance';

const generateInstance =
    (instance, master = false) => <Instance key={instance.id} data={instance} master={master} />

export default class Cluster extends React.Component {
    state = {
        clusterInstanceDialogOpen: false,
    };

    handleClusterInstanceDialogOpen = () => {
        this.setState({ clusterInstanceDialogOpen: true });
    };

    handleClusterInstanceDialogClose = () => {
        this.setState({ clusterInstanceDialogOpen: false });
    };

    render() {
        const cluster = this.props.data;
        const {owner, dockerImage, master, workers=[]} = cluster;

        // TODO: return short-form image tag
        const clusterTitle =
            `${owner} ${dockerImage.tag.split("-")[0]}`

        return (
            <div>
                <Card>
                    <CardHeader
                        title={clusterTitle}
                        titleStyle={{ "fontSize": "125%"}}
                    >
                        <Trash/>
                        <Add onTouchTap={this.handleClusterInstanceDialogOpen}/>
                    </CardHeader>
                    <CardText>
                        { master ? generateInstance(master, true) : null }
                        { workers.map(generateInstance) }
                    </CardText>
                    <CardActions style={{ "backgroundColor": "#ccc"}}>
                        <p>2 cores, 2GB RAM, $0.039/hr</p>
                    </CardActions>
                </Card>
                <ClusterInstanceDialog
                    openState={this.state.clusterInstanceDialogOpen}
                    close={this.handleClusterInstanceDialogClose}
                    socket={this.props.socket}
                    cluster={cluster}/>
            </div>
        );
    }
}
