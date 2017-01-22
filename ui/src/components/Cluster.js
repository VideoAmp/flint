import React from 'react';

import {Card, CardActions, CardHeader, CardText} from 'material-ui/Card';
import Divider from 'material-ui/Divider';
import IconButton from 'material-ui/IconButton';
import Trash from 'material-ui/svg-icons/action/delete';
import Add from 'material-ui/svg-icons/content/add';

import ClusterInstanceDialog from './ClusterInstanceDialog'
import Instance from './Instance';

const getInstanceMapper = (socket, master) =>
    (instance) => (
        <div key={instance.id}>
            <Instance
                data={instance}
                master={master}
                socket={socket}/>
            <Divider />
        </div>
    )

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
        const {socket, data: cluster} = this.props;
        const {owner, dockerImage, master, workers=[]} = cluster;

        // TODO: return short-form image tag
        const clusterTitle =
            `${owner} ${dockerImage.tag.split("-")[0]}`

        return (
            <div>
                <Card>
                    <CardHeader
                        title={clusterTitle}
                        titleStyle={{ "fontSize": "24px"}}
                        textStyle={{ paddingRight: "0px" }}
                        style={{ paddingRight: "0px" }}
                    >
                        <div style={{float: 'right', marginTop: "-11px"}}>
                            <IconButton>
                                <Trash/>
                            </IconButton>
                            <IconButton style={{ marginRight: "4px" }} onTouchTap={this.handleClusterInstanceDialogOpen}>
                                <Add />
                            </IconButton>
                        </div>
                    </CardHeader>
                    <CardText style={{ padding: "0px" }}>
                        <Divider />
                        { getInstanceMapper(socket, true)(master) }
                        { workers.map(getInstanceMapper(socket, false)) }
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
